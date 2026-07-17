# 题目标签建模闭环研发实施计划

更新时间：2026-07-17

状态：待实施

依据：`docs/problem-tag-modeling-spec.md`

## 一、计划目标

本计划把题目标签从 `problem.tag_values`、`problem.tag_labels_en`、`problem.tag_labels_zh`
三个平行数组迁移到 `problem_tag` 标签目录和 `problem_tag_assignment` 题目关联，同时保持旧数组持续双写，完成以下闭环：

- 创建并回填规范化标签表；
- 使用统一规范化结果驱动标签目录、题目关联和旧数组写入；
- 将题目列表、详情、标签筛选、过滤项和能力雷达切换到规范化关系；
- 在迁移后和每次完整 seed 导入后执行一致性校验；
- 保持现有 Java、HTTP 和前端契约不变；
- 保留回退到旧数组读取的能力，不在本次变更中删列或停写。

本计划的完成标准不是“新表已创建”，而是迁移、双写、读取切换、验证和回滚路径均已落地。

## 二、当前基线与约束

截至 2026-07-17，当前实现基线如下：

| 范围 | 当前状态 | 本次处理 |
| --- | --- | --- |
| 数据存储 | `problem` 三个等长标签数组 | 保留并持续双写，新增目录和关联表 |
| 题目 seed | `ProblemSeedImporter` 逐题调用 `ProblemRepository.upsertProblem` | 改为整批规范化后在同一事务内双写 |
| 标签写入 | `MyBatisProblemRepository` 独立去重和 label fallback | 删除 Repository 内重复规则，统一交给规范化器 |
| 列表与详情 | `ProblemMapper.xml` 读取数组并转为换行文本 | 改从关联表按 `ordinal` 聚合 |
| 标签筛选 | `tag = ANY(p.tag_values)` | 改为关联表 `EXISTS` 查询 |
| 过滤项 | `unnest` 数组并使用 `MIN(label)` | 改为标签目录名称和关联数量 |
| 能力雷达 | `AbilityProfileMapper.xml` 展开数组 | 改为目录和关联表，评分公式不变 |
| API/前端 | `ProblemTag(value, label)` 及现有响应类型 | 不变，无计划内前端功能改动 |
| Flyway 版本 | 当前共享最高版本为 `V32` | 实施前再次检查，预计使用 `V33` |

当前完整 seed 的已确认基线为 3591 道题、72 个标签，其中 23 个标签 `value` 存在名称变体。名称收敛属于主流程，不作为异常兜底处理。

实施约束：

- `ProblemSeedImporter.importSeed` 现有 `@Transactional` 是双写事务边界，不拆成多个事务；
- `ProblemSeedRunner` 可通过 `algo-mentor.problem.seed.enabled` 在启动时执行导入，因此迁移和双写代码必须作为同一可发布单元；
- 新表上线后，任何 seed 导入都必须同时更新新旧存储；
- 读取切换后不得新增对旧数组的读取依赖；
- `active=false` 只影响新的筛选、统计和能力雷达，不删除历史关联；
- 不修改前端类型、路由、交互和展示结构；
- 不修改能力雷达评分、低频标签门槛和最新 Review 选择规则；
- 不删除旧数组、GIN 索引或数组长度约束。

## 三、关键实施决策

### 3.1 统一规范化流程

标签规范化采用整批两阶段处理：

```text
读取完整 seed
  -> 逐题按下标组合 value / labelEn / labelZh
  -> 校验、fallback、题内去重
  -> 汇总全量标签名称候选及频次
  -> 生成稳定标签目录
  -> 用目录名称回写每道题的规范化标签
  -> 在一个事务中写目录、problem 数组和 assignment
  -> 执行导入后一致性校验
  -> 提交事务
```

`ProblemSeedTagNormalizer` 是 fallback、去重、名称频次和稳定决胜规则的唯一 Java 实现。Repository 只消费规范化结果，不再独立处理三数组。

### 3.2 历史重复标签处理

迁移按规格的题内规则处理重复项：

- 完全重复的 `value + label_en + label_zh` 保留第一次出现；
- 同一题内相同 `value` 对应不同 label 时终止迁移；
- 除移除完全重复项外，标签 `value` 和相对顺序不变；
- 迁移最终从规范化关联重建三个兼容数组，保证数组数量和 assignment 数量一致。

实施前必须对真实数据库和 `data/seed/problems.jsonl` 执行重复标签审计。若发现规格未覆盖的新冲突形态，先补充规格，不在迁移 SQL 中静默猜测。

### 3.3 名称稳定决胜

迁移 SQL 和 Java 规范化器使用同一顺序：

1. 候选出现次数降序；
2. 次数相同时按字符串升序；
3. 英文空值回退到 `value`；
4. 中文先排除与同次英文名称或 `value` 相同的回退候选；
5. 无真正中文候选时回退到规范英文名称。

迁移 SQL 的字符串决胜显式使用稳定排序规则，Java 测试使用同一候选集验证结果，避免数据库默认 collation 导致环境间漂移。

### 3.4 PostgreSQL 集成测试

本功能依赖 PostgreSQL 数组、`unnest ... WITH ORDINALITY`、lateral join、Flyway 事务和约束，不能只靠 H2 或 Mapper XML 解析测试验证。

计划在 `mentor-api` 增加 Testcontainers PostgreSQL 测试基座，并用 Maven Failsafe 执行 `*IT.java`：

- 单元测试继续由 Surefire 和 `make backend-test` 执行；
- PostgreSQL 集成测试通过新增的 `make backend-it` 执行；
- `make build` 仍作为最终全量构建门禁；
- Docker 不可用时必须明确记录 IT 未运行，不能用 XML 加载成功替代数据库验证。

### 3.5 发布拆分

代码可以分 PR 合并，但发布只允许按以下顺序：

```text
发布 A：V33 迁移 + seed 双写 + 导入后一致性校验
  -> 完整 seed 导入两次
  -> 一致性和幂等检查通过
发布 B：题库和能力雷达读取切换
  -> API 对照和用户测试
  -> 继续保留旧数组双写
```

禁止上线只有 V33 回填、但导入器仍只写旧数组的中间版本。

## 四、目标代码边界

### 4.1 新增模型与服务

```text
backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/
  model/
    ProblemSeedTag.java
    ProblemTagDefinition.java
    NormalizedProblemSeed.java
    ProblemTagNormalizationResult.java
  service/
    ProblemSeedTagNormalizer.java
    ProblemTagConsistencyValidator.java
```

职责：

- `ProblemSeedTag`：单题内已经完成 fallback 和题内去重的标签，`ordinal` 从 0 开始；
- `ProblemTagDefinition`：按 `value` 唯一的规范目录项；
- `NormalizedProblemSeed`：原题目数据和规范化标签的组合；
- `ProblemTagNormalizationResult`：整批规范目录和规范化题目列表；
- `ProblemSeedTagNormalizer`：唯一规范化规则入口；
- `ProblemTagConsistencyValidator`：导入事务提交前执行有序数组、关联和 active 状态检查。

### 4.2 新增持久化边界

```text
backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/
  mapper/
    ProblemTagMapper.java
  mapper/model/
    ProblemTagCatalogUpsertRow.java
    ProblemTagAssignmentRow.java
  repository/
    ProblemTagRepository.java
    MyBatisProblemTagRepository.java

backend/mentor-api/src/main/resources/mapper/problem/
  ProblemTagMapper.xml
```

`ProblemTagRepository` 第一版只负责：

```java
void upsertCatalog(List<ProblemTagDefinition> catalog);

void replaceAssignments(String problemSlug, List<ProblemSeedTag> tags);
```

一致性查询可以复用 `ProblemTagMapper`，但 `ProblemSeedImporter` 只依赖 `ProblemTagConsistencyValidator`，不直接调用 Mapper。

### 4.3 保持不变的边界

- `ProblemTag(value, label)` 不变；
- `ProblemRow` 的三个文本投影字段第一版不变；
- `ProblemMapper.xml` 继续拥有题目列表、详情、筛选和过滤项查询；
- `AbilityProfileMapper.xml` 继续拥有能力雷达聚合；
- Controller、Agent tools、学习计划题目目录和前端 API 类型不感知存储切换。

## 五、分阶段任务

## 阶段 0：建立数据库验证基线

### Task 1：补齐 PostgreSQL 集成测试入口和旧读模型基线

**目标：** 在改 SQL 前建立可重复的迁移、双写和切换前后对照环境。

**主要文件：**

- Modify: `backend/pom.xml`
- Modify: `backend/mentor-api/pom.xml`
- Modify: `Makefile`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/support/PostgresIntegrationTestSupport.java`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/ProblemTagNormalizationMigrationIT.java`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/ProblemTagReadModelIT.java`

**实施步骤：**

- [ ] 在 `mentor-api` 增加 Testcontainers JUnit Jupiter 和 PostgreSQL 测试依赖。
- [ ] 配置 Maven Failsafe 执行 `*IT.java`，不改变现有 Surefire 单元测试命名规则。
- [ ] 在 `Makefile` 增加 `backend-it`，统一使用 `./.m2/repository`。
- [ ] 测试基座支持将 Flyway 先迁移到 V32、装载旧数组 fixture，再迁移到最新版本。
- [ ] fixture 覆盖无标签、单标签、多标签、名称变体、空 label 回退和多次 Review。
- [ ] 在迁移前保存旧数组查询的标签顺序、筛选数量、过滤项数量和能力雷达结果，作为迁移后对照值。
- [ ] 增加 Flyway 共享版本空间断言，实施时确认 `V33` 没有被其他模块占用。

**验收：**

- `make backend-test` 和 `make backend-it` 可以独立执行；
- 集成测试真实运行 PostgreSQL，不使用 H2 模拟数组和 lateral SQL；
- 基线 fixture 能稳定输出读取切换前的预期结果。

## 阶段 1：创建、回填并验证规范化数据

### Task 2：实现 V33 标签规范化迁移

**目标：** 以加法迁移创建目录和关联表，完成历史数据规范化回填，并在 Flyway 事务提交前执行硬校验。

**主要文件：**

- Create: `backend/mentor-api/src/main/resources/db/migration/V33__problem_tag_normalization.sql`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/config/FlywayMigrationResourceTest.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/ProblemTagNormalizationMigrationIT.java`

**实施步骤：**

- [ ] 创建 `problem_tag`，包含唯一 `value`、中英文非空名称、`active` 和时间字段。
- [ ] 创建 `problem_tag_assignment`，包含复合主键、题内 ordinal 唯一约束、非负约束和两个外键。
- [ ] 创建 `(tag_id, problem_id)` 反向查询索引。
- [ ] 在写入新表前校验数组长度、空白 value、题内 label 冲突和 ordinal 上限。
- [ ] 使用多数组 `unnest ... WITH ORDINALITY` 保持 value 和两个 label 的位置关系。
- [ ] 对完全重复标签保留第一项，对冲突重复项执行 `RAISE EXCEPTION`。
- [ ] 统计名称候选频次，按稳定决胜规则生成唯一目录；名称变体使用 `RAISE NOTICE` 输出候选和次数，不阻断迁移。
- [ ] 回填 assignment，并把 PostgreSQL ordinality 转为从 0 开始的 `SMALLINT`。
- [ ] 从目录和 assignment 按 ordinal 重建三个旧数组，使兼容副本使用规范名称。
- [ ] 不更新无关业务字段，不因数据回填制造无意义的题目 `updated_at` 排序变化。
- [ ] 在迁移末尾执行数组/关联有序一致性、assignment 连续性、悬空引用和目录唯一性校验，失败时回滚整个迁移。
- [ ] 保留 `idx_problem_tag_values` 和 `ck_problem_tag_array_lengths`。

**关键测试：**

- V32 fixture 升级到 V33 后表、约束和索引存在；
- 名称频次和字符串决胜结果符合规格；
- 中文英文回退被真正中文候选替代；
- 完全重复项只保留第一次出现的位置；
- 相同 value 对应冲突 label 时迁移整体失败且不留下部分表数据；
- 无标签题目得到空数组和零 assignment；
- 迁移后有序数组一致性查询返回 0 行。

**阶段门禁：**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dtest=FlywayMigrationResourceTest test
make backend-it
```

## 阶段 2：统一 seed 标签规范化

### Task 3：实现整批标签规范化器

**目标：** 在任何数据库写入前生成唯一且稳定的标签目录和每题规范化标签列表。

**主要文件：**

- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/model/ProblemSeedTag.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/model/ProblemTagDefinition.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/model/NormalizedProblemSeed.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/model/ProblemTagNormalizationResult.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/service/ProblemSeedTagNormalizer.java`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/service/ProblemSeedTagNormalizerTest.java`

**实施步骤：**

- [ ] 校验三个数组长度一致，空数组视为无标签。
- [ ] 先按下标组合完整标签对象，再执行 trim、fallback 和去重。
- [ ] 拒绝空白 value；不改变 value 大小写和连字符。
- [ ] 完全重复项保留第一次出现，并重新生成连续的 0-based ordinal。
- [ ] 同题相同 value 对应不同规范化前 label 时立即失败。
- [ ] 统计整批英文、中文名称候选频次，使用稳定决胜规则生成目录。
- [ ] 目录生成后，再把每题标签 label 替换成目录规范名称。
- [ ] 返回不可变的 catalog 和 normalized problems，避免 Repository 修改结果。
- [ ] 错误消息包含问题 slug、tag value 和冲突类型，但不输出题面或其他大段数据。

**关键测试：**

- 正常三数组转换和 0-based ordinal；
- 空 label fallback；
- 空 value、数组长度不一致和题内冲突拒绝；
- 完全重复去重保留第一次位置；
- 多题名称候选按频次选择；
- 同频候选按字符串升序；
- 中文候选优先真正翻译，无中文时回退规范英文；
- 调换题目遍历顺序后 catalog 和每题结果完全一致；
- 使用真实 3591 道题 seed 时得到 3591 个规范化问题和 72 个目录项。

**阶段门禁：**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dtest=ProblemSeedTagNormalizerTest test
```

## 阶段 3：实现导入双写和导入后校验

### Task 4：新增标签 Repository、调整题目写入边界

**目标：** 让目录、新关联和旧数组在现有导入事务中由同一份规范化数据完成写入。

**主要文件：**

- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/mapper/ProblemTagMapper.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/mapper/model/ProblemTagCatalogUpsertRow.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/mapper/model/ProblemTagAssignmentRow.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/repository/ProblemTagRepository.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/repository/MyBatisProblemTagRepository.java`
- Create: `backend/mentor-api/src/main/resources/mapper/problem/ProblemTagMapper.xml`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/service/ProblemTagConsistencyValidator.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/service/ProblemSeedImporter.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/repository/ProblemRepository.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/repository/MyBatisProblemRepository.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/mapper/model/ProblemUpsertRow.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorApiMyBatisConfiguration.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/service/ProblemSeedImporterTest.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/repository/MyBatisProblemRepositoryTest.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/learningplan/service/LearningPlanTemplateSeedImportServiceTest.java`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/mapper/ProblemTagMapperXmlTest.java`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/repository/MyBatisProblemTagRepositoryTest.java`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/ProblemTagSeedImportIT.java`

**实施步骤：**

- [ ] `ProblemSeedImporter` 先读取完整 seed 和推荐理由，再一次性调用规范化器。
- [ ] 在任何写入前解析 `ProblemRepository`、`ProblemTagRepository` 和一致性校验依赖，缺失时直接失败。
- [ ] 先按唯一 value 批量 upsert catalog，同一批标签只 upsert 一次。
- [ ] catalog upsert 只在名称或 active 实际变化时更新 `updated_at`。
- [ ] 已停用标签重新出现在 seed 时恢复 `active=true`；未出现标签保持原状态。
- [ ] `ProblemRepository.upsertProblem` 改为接收 `NormalizedProblemSeed`，旧数组严格从 `tags` 映射生成。
- [ ] 删除 `MyBatisProblemRepository` 中独立的 value 去重和 label fallback 逻辑。
- [ ] 每题 upsert 后按 slug 删除旧 assignment，再按 ordinal 插入当前 assignment。
- [ ] 标签删除、新增和重排均通过完整替换表达，不实现差量合并。
- [ ] 全部问题写完后，在事务内执行有序数组一致性、ordinal 连续性和本批标签 active 校验；存在异常时抛错回滚。
- [ ] 保持推荐理由合并、frontendId 冲突清理和题目其他字段 upsert 行为不变。
- [ ] 更新所有 `ProblemRepository` 测试替身，避免接口签名变化破坏学习计划模板测试。

**关键测试：**

- catalog 首次写入、重复写入和名称更新；
- inactive 标签被可信 seed 重新激活，未出现标签不自动停用；
- assignment 新增、删除和重排；
- 数组和 assignment 始终来自同一标签列表；
- 完整 seed 连续导入两次后目录数、关联数、名称和 ordinal 不漂移；
- 无变化时 problem 和 catalog 的 `updated_at` 不变化；
- 中途注入异常时 problem、catalog 和 assignment 全部回滚；
- 冲突 label 在首次数据库写入前失败；
- 导入后一致性校验失败时事务不提交。

**阶段门禁：**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest=ProblemSeedTagNormalizerTest,ProblemSeedImporterTest,MyBatisProblemRepositoryTest,MyBatisProblemTagRepositoryTest,ProblemTagMapperXmlTest \
  test
make backend-it
```

## 阶段 4：切换题库读取

### Task 5：切换列表、详情、筛选和过滤项

**目标：** 让题库所有标签读取以规范化关系为权威来源，同时保持当前分页和 API 映射不变。

**主要文件：**

- Modify: `backend/mentor-api/src/main/resources/mapper/problem/ProblemMapper.xml`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/mapper/ProblemMapperXmlTest.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/repository/MyBatisProblemRepositoryTest.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/ProblemTagReadModelIT.java`
- Verify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/problem/ProblemControllerTest.java`
- Verify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/tool/ProblemAgentToolsTest.java`

**实施步骤：**

- [ ] 提取可复用的规范化标签 lateral join 和文本投影 SQL 片段。
- [ ] 列表和详情从 assignment 关联目录，按 `ordinal` 聚合为现有三个文本列别名。
- [ ] 无标签题目使用空字符串，保持 `MyBatisProblemRepository.tags(...)` 行为。
- [ ] 列表主查询每题始终只返回一行，不把 assignment 直接 JOIN 到分页结果集。
- [ ] `countProblems` 和 `findProblems` 的标签条件统一改为 active 标签 `EXISTS`。
- [ ] `countProblemsByTag` 从目录和 assignment 聚合，按 locale 选择目录名称。
- [ ] 列表和详情仍显示历史 assignment，包括 inactive 标签；inactive 只从筛选和统计中排除。
- [ ] XML 测试增加反向断言，确保读取 SQL 不再出现 `ANY(p.tag_values)` 或标签数组 `unnest`。

**关键测试：**

- 列表和详情标签 value、label、数量和顺序一致；
- 多标签题目不会导致分页重复、总数膨胀或排序变化；
- 无标签题目仍返回空 `tags`；
- active 标签可筛选，inactive 标签不出现在筛选项且不能用于新筛选；
- 中英文 locale 返回对应目录名称；
- 固定 fixture 切换前后 tag value、顺序和题目数量一致；
- Controller 和 Agent tool 响应结构不变。

**阶段门禁：**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest=ProblemMapperXmlTest,MyBatisProblemRepositoryTest,ProblemControllerTest,ProblemAgentToolsTest \
  test
make backend-it
```

## 阶段 5：切换能力雷达

### Task 6：改造能力标签聚合并做等价回归

**目标：** 只替换标签展开来源，不改变能力雷达业务语义和响应结构。

**主要文件：**

- Modify: `backend/mentor-api/src/main/resources/mapper/ability/AbilityProfileMapper.xml`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/ability/mapper/AbilityProfileMapperXmlTest.java`
- Verify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/ability/service/AbilityProfileServiceTest.java`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/ability/AbilityProfileMapperIT.java`

**实施步骤：**

- [ ] 用 `problem -> problem_tag_assignment -> problem_tag` 替换数组展开 CTE。
- [ ] 只统计 `problem_tag.active=true` 的标签。
- [ ] 继续按问题 slug 连接 Review，保留同题只取最新 Review 的窗口函数。
- [ ] 一题分数继续贡献给该题全部 active 标签。
- [ ] 保留 `MIN_PROBLEM_COUNT`、无 Review 返回 `0.0` 和现有排序。
- [ ] 默认名称继续使用 `label_zh`，不修改 API DTO。
- [ ] XML 测试断言不再包含 `unnest(p.tag_values, ...)`。

**关键测试：**

- 同题多 Review 只使用最新记录；
- 一题多个标签分别贡献分数；
- inactive 和低频标签被排除；
- 无 Review 标签仍返回且得分为 0；
- 固定 fixture 切换前后 tag 集合、problemCount、reviewedProblemCount 和分数一致；
- `AbilityProfileService` 范围、弱项排序和空状态行为不变。

**阶段门禁：**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest=AbilityProfileMapperXmlTest,AbilityProfileServiceTest \
  test
make backend-it
```

## 阶段 6：全量验收与发布观察

### Task 7：执行完整数据、构建和回滚门禁

**目标：** 在交付前证明规范化关系、兼容数组和对外行为一致，并准备无破坏性回滚。

**主要文件：**

- Modify: `docs/code-index.md`
- Verify: `docs/problem-tag-modeling-spec.md`
- Verify: `data/seed/problems.jsonl`
- Verify: `frontend/src/types/api.ts`
- Verify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/problem/model/ProblemTag.java`

**实施步骤：**

- [ ] 对真实完整 seed 执行两次导入，记录问题数、目录数、assignment 数和第二次导入变化数。
- [ ] 执行有序数组一致性 SQL，结果必须为 0 行。
- [ ] 检查目录唯一性、悬空引用、ordinal 连续性和 active 状态。
- [ ] 对照读取切换前后的标签筛选数量、过滤项数量和能力雷达 fixture。
- [ ] 检查新代码读取 SQL，确保没有新增旧数组读取依赖。
- [ ] 确认旧数组、`idx_problem_tag_values` 和 `ck_problem_tag_array_lengths` 仍存在。
- [ ] 确认前端类型和 HTTP 契约未改变，不启动 Vite 开发服务器。
- [ ] 记录发布 A、发布 B 的验证结果和回滚条件。
- [ ] 用户测试期间保持旧数组双写，不创建任何删列迁移。

**最终验证：**

```bash
make backend-test
make backend-it
make frontend-test
make build
```

如环境无法运行 Testcontainers，交付说明必须明确列出未运行的 PostgreSQL IT 和剩余风险；单元测试通过不能替代迁移与 SQL 集成验证。

## 六、PR 与发布建议

### PR 1：测试基座和规范化模型

包含 Task 1 和 Task 3，不改变生产读取来源。先把规则测试和 PostgreSQL 对照 fixture 固定下来。

### PR 2：迁移、双写和一致性校验

包含 Task 2 和 Task 4。该 PR 合并后应用仍可继续读取旧数组，但新导入已经保持两套存储一致。

发布 A 必须至少包含本 PR，启动前确认 V33 未被占用。发布后立即执行完整 seed 双次导入和一致性检查。

### PR 3：题库和能力雷达读取切换

包含 Task 5 和 Task 6。只在发布 A 的数据验证通过后发布。

### PR 4：全量回归和发布记录

包含 Task 7 的文档、结果记录和必要的测试补强，不包含旧数组清理。

## 七、测试矩阵

| 层级 | 重点 | 测试方式 | 通过标准 |
| --- | --- | --- | --- |
| 规范化单元测试 | 下标组合、fallback、去重、频次、稳定决胜 | JUnit | 所有规格分支有确定结果 |
| Mapper XML | statement 可加载、SQL 不再读旧数组 | MyBatis XML parser | statement 完整，反向断言通过 |
| Repository 单元测试 | 参数映射、目录 upsert、assignment 替换 | Mockito/JUnit | Repository 不再重复规范化 |
| Flyway IT | V32 到 V33、失败回滚、约束和索引 | PostgreSQL Testcontainers | 迁移成功或按预期整体失败 |
| Seed 导入 IT | 单事务双写、幂等、异常回滚 | PostgreSQL Testcontainers | 新旧存储一致，重复导入不漂移 |
| 题库查询 IT | 列表、详情、分页、筛选、过滤项 | PostgreSQL Testcontainers | 切换前后 value、顺序和数量一致 |
| 能力雷达 IT | active、低频、最新 Review、多标签贡献 | PostgreSQL Testcontainers | 切换前后计数和分数一致 |
| API 回归 | DTO、locale、Agent tools | Spring/JUnit | 响应结构不变 |
| 前端回归 | 现有题库和能力类型消费 | Vitest/build | 无类型和构建回归 |

## 八、发布检查清单

### 发布 A 前

- [ ] 共享 Flyway 版本空间确认 V33 可用。
- [ ] 生产或目标环境旧数组审计无未处理冲突。
- [ ] V33 迁移 IT、完整 seed 双写 IT 和回滚测试通过。
- [ ] 确认发布包同时包含迁移和双写代码。
- [ ] 确认没有删列、删索引或停止数组写入。

### 发布 A 后

- [ ] Flyway V33 成功。
- [ ] 完整 seed 导入成功，重复导入成功。
- [ ] 有序数组一致性查询返回 0 行。
- [ ] `problem_tag` 目录数量与完整 seed 唯一 value 数一致。
- [ ] 当前 seed 出现的标签全部为 active。
- [ ] 旧版本数组读取仍可正常工作。

### 发布 B 前

- [ ] 发布 A 数据观察通过。
- [ ] 题库查询和能力雷达切换前后对照测试通过。
- [ ] 分页、多标签和 inactive fixture 已覆盖。
- [ ] API 和前端契约无变化。

### 发布 B 后

- [ ] 题目列表、详情和标签筛选正常。
- [ ] 中英文过滤项名称和数量正常。
- [ ] 能力雷达标签集合、数量和评分正常。
- [ ] seed 再次导入后读取结果不漂移。
- [ ] 用户测试期间继续保留旧数组双写。

## 九、风险与控制

| 风险 | 影响 | 控制措施 |
| --- | --- | --- |
| SQL 与 Java 名称决胜不一致 | 迁移后再次导入会改名 | 共用 fixture，分别验证 V33 和规范化器输出 |
| 先去重 value 再读取 label | value 与 label 错位 | 强制先按下标组合对象，再题内去重 |
| 迁移和双写分开发版 | seed 导入造成新旧存储漂移 | 发布 A 强制绑定 V33 与双写 |
| 列表直接 JOIN assignment | 分页重复和总数膨胀 | 使用每题一行的 lateral 聚合 |
| inactive 语义误用 | 历史详情缺标签或筛选异常 | 仅筛选、过滤项、雷达限制 active；详情保留历史关联 |
| assignment 替换中途失败 | 单题标签丢失 | 保持整个 seed 导入事务，异常整体回滚 |
| 无变化导入更新时间漂移 | 题目排序和审计噪音 | problem/catalog upsert 使用 `IS DISTINCT FROM` |
| 只做 XML 测试 | PostgreSQL 方言错误未发现 | 强制 Flyway、导入和查询 PostgreSQL IT |
| 提前清理旧数组 | 无快速应用回退路径 | 本计划明确禁止删列和停写 |

## 十、完成定义

以下条件全部满足后，才能将本计划状态更新为“已完成”：

- [ ] `problem_tag` 和 `problem_tag_assignment` 已创建并完成历史回填。
- [ ] V33 迁移前置校验、名称变体审计和迁移后硬校验均已实现。
- [ ] 统一规范化器覆盖题内和全局名称规则。
- [ ] seed 导入使用同一份规范化标签双写目录、关联和旧数组。
- [ ] 完整 seed 重复导入幂等，异常可整体回滚。
- [ ] 每次完整 seed 导入提交前执行一致性校验。
- [ ] 题目列表、详情、标签筛选和过滤项全部读取规范化关系。
- [ ] 能力雷达读取规范化关系，评分和计数保持等价。
- [ ] `ProblemTag(value, label)`、HTTP API 和前端类型不变。
- [ ] PostgreSQL 迁移、导入、查询和能力雷达 IT 通过。
- [ ] 有序数组一致性查询返回 0 行。
- [ ] 旧数组、GIN 索引和数组长度约束仍保留并持续双写。
- [ ] 用户完成发布 B 后的功能测试，未发现需要回退的问题。

## 十一、后续清理门禁

旧数组清理不属于本计划。只有用户明确发起新的清理任务后，才允许另行设计和实施：

```text
停止写入 tag_values / tag_labels_en / tag_labels_zh
删除 Java 和 MyBatis 数组兼容代码
删除 idx_problem_tag_values
删除 ck_problem_tag_array_lengths
删除三个数组列
补充数据库备份、回滚和发布观察方案
```

在收到明确指令前，不得把任何上述清理内容混入本计划的实现 PR。
