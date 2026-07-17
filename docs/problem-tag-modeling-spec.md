# 题目标签建模完整闭环规格

更新时间：2026-07-17

状态：规格已确认，待拆分实施计划

> 来源设计：`docs/ai-learner-profile-data-model-and-storage-design.md` 第四章“题目标签建模”。
>
> 当前实现：`problem.tag_values`、`problem.tag_labels_en`、`problem.tag_labels_zh` 三个等长数组。

## 一、规格目标

本规格将题目标签从 `problem` 表中的三个数组规范化为稳定标签目录和题目标签关联，使同一套标签身份能够被以下功能共同使用：

- 题目列表和题目详情；
- 题库标签筛选；
- 标签过滤项和题目数量统计；
- 能力雷达；
- 后续标签 AI 评价。

本规格是完整闭环规格，不以“新增两张表”作为完成标准。数据库迁移、历史数据回填、题目导入双写、读取切换、一致性校验和测试必须一起落地。

## 二、当前基线

### 2.1 当前存储

`problem` 表通过以下三个数组保存标签：

```text
tag_values
tag_labels_en
tag_labels_zh
```

数据库使用 `ck_problem_tag_array_lengths` 保证三个数组长度相同，并通过 `idx_problem_tag_values` GIN 索引支持标签过滤。

### 2.2 当前读写位置

当前标签数组至少被以下链路直接使用：

| 链路 | 当前实现 |
|---|---|
| 题目 seed 导入 | `MyBatisProblemRepository.upsertProblem` 创建 PostgreSQL 数组并写入 `problem` |
| 题目列表筛选 | `ProblemMapper.xml` 使用 `tag = ANY(p.tag_values)` |
| 题目列表与详情 | `ProblemMapper.xml` 将三个数组转成换行分隔字符串 |
| 标签过滤项 | `countProblemsByTag` 使用 `unnest` |
| 能力雷达 | `AbilityProfileMapper.xml` 使用 `unnest` 展开标签 |
| 对外题目模型 | `ProblemTag(value, label)` |

因此读取切换不能只修改题库筛选 SQL，必须同时覆盖列表、详情、过滤项和能力雷达。

## 三、目标与非目标

### 3.1 目标

- 为每个标签提供稳定的数据库 `id` 和契约值 `value`；
- 统一维护中英文标签名称；
- 使用题目标签关联表表达多对多关系；
- 保留每道题当前标签顺序；
- 让题库查询和能力雷达读取规范化关系；
- 让题目导入在一个事务中同步新表和旧数组；
- 保持现有 HTTP API 和 Java `ProblemTag(value, label)` 契约不变；
- 提供迁移后和每次 seed 导入后的完整一致性校验；
- 保留随时回退到旧数组读取的能力，直到用户确认删除旧数组。

### 3.2 非目标

- 本规格不删除、停写或重命名旧标签数组；
- 不删除 `idx_problem_tag_values` 和 `ck_problem_tag_array_lengths`；
- 不新增标签管理后台；
- 不自动合并语义相近但 `value` 不同的标签；
- 不根据某次解法判断标签是否实际参与；
- 不缓存 `problem_count`；
- 不引入标签别名、父子标签或图关系；
- 不修改能力雷达评分公式；
- 不修改前端 API 类型和展示交互。

## 四、标签契约

### 4.1 标签身份

`problem_tag.value` 是稳定业务契约，使用 LeetCode 标签 slug，例如：

```text
array
binary-search
dynamic-programming
hash-table
```

规则如下：

- 写入前执行 `trim`；
- 空值不允许进入标签目录；
- 大小写和连字符保持 seed 中的规范 slug，不在数据库中二次转换；
- `value` 一旦进入数据库，不因展示名称变化而修改；
- 同一 `value` 在标签目录中只有一组规范中英文名称，seed 中的名称变体按 4.2 收敛。

### 4.2 标签名称

名称来源和回退顺序固定为：

```text
label_en = leetcode.com topicTags[].name
label_zh = leetcode.cn topicTags[].translatedName

label_en 缺失 -> value
label_zh 缺失 -> label_en -> value
```

当前完整 seed 中确实存在同一 slug 的名称变体，例如：

```text
graph       -> Graph / Graph Theory
union-find  -> Union Find / Union-Find
array       -> 数组 / Array（中文缺失时的英文回退）
```

截至 2026-07-17，`data/seed/problems.jsonl` 包含 3591 道题、72 个 tag，其中 23 个 tag value 存在至少两组名称表示，因此规范名称选择是迁移必需逻辑，不是异常兜底。

因此跨题目的名称差异不能直接判定为导入失败。规范名称使用完整 seed 的稳定统计结果：

- `label_en`：选择该 `value` 下出现次数最多的非空英文名称；次数相同按字符串升序选择；
- `label_zh`：先排除与同一次出现的 `label_en` 相同或与 `value` 相同的英文回退值，再从真正的中文翻译中选择出现次数最多者；没有真正中文翻译时回退规范 `label_en`；次数相同按字符串升序选择；
- 选择过程必须与题目遍历顺序无关，不能使用第一条、最后一条或数据库 `MIN` 代替频次规则；
- 生成规范目录后，每道题的兼容 label 数组也改写为该 `value` 的规范名称。

当后续完整 seed 对某个标签提供了统一的新名称时，允许按 `value` 更新 `label_en` 和 `label_zh`。

### 4.3 题目内标签顺序

`problem_tag_assignment.ordinal` 保存当前 seed 中该题标签的顺序，从 `0` 开始。

规范化时：

- 三个数组必须先按下标组合成完整标签对象，再执行去重；
- 完全重复的标签按 `value` 去重并保留第一次出现的位置；
- 同一题内相同 `value` 对应不同 label 时导入失败；
- 不能先对 `tag_values` 单独 `distinct`，再按去重后的下标读取原 label 数组；
- 旧数组和关联表必须由同一份规范化标签列表生成。

当前 seed 已按稳定顺序输出标签，本规格不额外按 `value` 重新排序。

### 4.4 active 语义

`problem_tag.active` 表示标签是否继续参与新的题库筛选和统计：

- seed 中出现的标签写为 `active=true`；
- 已停用标签重新出现在可信 seed 中时恢复为 `active=true`；
- 完整 seed 中暂时没有出现的标签不自动改为 `active=false`；
- 停用操作留给后续显式维护流程；
- 历史题目关联不因 `active=false` 自动删除。

## 五、数据模型

### 5.1 标签目录

迁移使用当前共享 Flyway 版本空间中的下一可用版本。按现有版本序列，预计文件为：

```text
backend/mentor-api/src/main/resources/db/migration/V33__problem_tag_normalization.sql
```

实施前必须再次确认仓库中没有其他模块占用 `V33`。

```sql
CREATE TABLE problem_tag (
  id BIGSERIAL PRIMARY KEY,
  value VARCHAR(120) NOT NULL,
  label_en VARCHAR(160) NOT NULL,
  label_zh VARCHAR(160) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_problem_tag_value UNIQUE (value),
  CONSTRAINT ck_problem_tag_value_not_blank CHECK (btrim(value) <> ''),
  CONSTRAINT ck_problem_tag_label_en_not_blank CHECK (btrim(label_en) <> ''),
  CONSTRAINT ck_problem_tag_label_zh_not_blank CHECK (btrim(label_zh) <> '')
);
```

`problem_count` 不进入标签表。题目数量通过 `problem_tag_assignment` 实时聚合。

### 5.2 题目标签关联

```sql
CREATE TABLE problem_tag_assignment (
  problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE,
  tag_id BIGINT NOT NULL REFERENCES problem_tag(id) ON DELETE RESTRICT,
  ordinal SMALLINT NOT NULL,
  PRIMARY KEY (problem_id, tag_id),
  CONSTRAINT uk_problem_tag_assignment_ordinal UNIQUE (problem_id, ordinal),
  CONSTRAINT ck_problem_tag_assignment_ordinal CHECK (ordinal >= 0)
);

CREATE INDEX idx_problem_tag_assignment_tag_problem
  ON problem_tag_assignment (tag_id, problem_id);
```

索引职责：

- `PRIMARY KEY (problem_id, tag_id)`：读取一道题的全部标签并防止重复；
- `UNIQUE (problem_id, ordinal)`：保持一个题目内的稳定顺序；
- `(tag_id, problem_id)`：标签筛题、过滤项统计和能力雷达聚合。

## 六、历史数据迁移

### 6.1 迁移前校验

迁移必须在插入新表前检查以下条件，任一失败都终止 Flyway：

- 三个数组长度一致；
- `tag_values` 不包含空白值；
- 同一题内没有标签值与 label 错位；
- 单题标签数量不超过 `SMALLINT` 可表达范围。

迁移应输出存在名称变体的 `value`、候选名称和出现次数，作为可审计诊断；名称变体本身不阻塞迁移。同一题内重复 `value` 却对应不同 label 仍视为数据错误并阻塞迁移。

### 6.2 回填标签目录

回填时使用多数组 `unnest ... WITH ORDINALITY` 按位置展开：

```sql
CROSS JOIN LATERAL unnest(
  p.tag_values,
  p.tag_labels_en,
  p.tag_labels_zh
) WITH ORDINALITY AS t(value, label_en, label_zh, ordinal)
```

按 4.2 的频次和稳定决胜规则为每个 `value` 计算规范 `label_en` / `label_zh`，再插入唯一标签目录。所有历史标签初始写为 `active=true`。

禁止分别展开三个数组后再按文本 JOIN，避免重复 label 或相同名称导致错误配对。

### 6.3 回填题目关联

再次使用同一个多数组 `unnest ... WITH ORDINALITY`，通过 `problem_tag.value` 定位 `tag_id`：

```text
problem.id
+ problem_tag.id
+ ordinal - 1
-> problem_tag_assignment
```

回填完成后，迁移必须验证：

```text
每道题 tag_values 数量 = assignment 数量
每个 assignment 都能匹配 problem_tag
按 ordinal 聚合后的 value 数组 = problem.tag_values
按 ordinal 聚合后的英文 label 数组 = problem.tag_labels_en
按 ordinal 聚合后的中文 label 数组 = problem.tag_labels_zh
```

为满足 label 数组一致性，迁移在目录和 assignment 回填后，按 `problem_id + ordinal` 将旧 `tag_labels_en` / `tag_labels_zh` 改写为目录中的规范名称。`tag_values` 的值和顺序不变。

校验失败时迁移整体回滚，不允许留下部分回填结果。

## 七、题目导入双写

### 7.1 事务边界

现有 `ProblemSeedImporter.importSeed` 已使用 `@Transactional`。新标签目录、题目关联和旧数组必须继续在该事务中写入：

```text
读取完整 seed
  -> 规范化所有题目的标签
  -> 校验全局标签目录一致性
  -> upsert problem_tag
  -> 逐题 upsert problem（继续写旧数组）
  -> 逐题 replace problem_tag_assignment
  -> 事务提交
```

任意题目的标签目录或关联写入失败时，题目主表、标签目录和全部关联一起回滚。

### 7.2 内部规范化模型

新增内部模型，避免继续把三个平行数组作为持久化逻辑的主要表示：

```java
public record ProblemSeedTag(
    String value,
    String labelEn,
    String labelZh,
    int ordinal
) {}
```

增加仅在导入链路使用的标签目录对象和规范化结果对象：

```java
public record ProblemTagDefinition(
    String value,
    String labelEn,
    String labelZh
) {}

public record NormalizedProblemSeed(
    ProblemSeedRecord problem,
    List<ProblemSeedTag> tags
) {}
```

原始 `problems.jsonl` 格式和 `ProblemSeedRecord` 的三个数组字段第一版保持兼容。规范化器负责把它们转换成 `ProblemSeedTag` 列表。

`ProblemSeedImporter`、`ProblemRepository` 和 `ProblemTagRepository` 必须共用同一个 `NormalizedProblemSeed`，不能让 Repository 再次从三个原始数组独立执行 fallback、去重或排序。`ProblemRepository.upsertProblem` 相应改为接收 `NormalizedProblemSeed`，并从其中的 `tags` 生成兼容数组。

### 7.3 全局目录校验

`ProblemSeedImporter` 在开始数据库写入前，根据全部 `NormalizedProblemSeed` 统计：

```text
Map<tagValue, label candidate frequencies>
  -> Map<tagValue, ProblemTagDefinition>
```

规范名称按 4.2 计算。计算完成后，重新将每个 `NormalizedProblemSeed.tags` 中的 label 替换为对应 `ProblemTagDefinition` 的规范名称，再交给两个 Repository。这样可避免逐题 upsert 时由文件顺序决定目录和兼容数组内容。

### 7.4 标签目录 upsert

同一批 seed 中的唯一标签只 upsert 一次：

```sql
INSERT INTO problem_tag (value, label_en, label_zh, active)
VALUES (...)
ON CONFLICT (value) DO UPDATE
SET label_en = EXCLUDED.label_en,
    label_zh = EXCLUDED.label_zh,
    active = TRUE,
    updated_at = NOW()
WHERE (
  problem_tag.label_en,
  problem_tag.label_zh,
  problem_tag.active
) IS DISTINCT FROM (
  EXCLUDED.label_en,
  EXCLUDED.label_zh,
  TRUE
);
```

未出现在本次 seed 中的目录行保持原样。

### 7.5 关联替换

每道题 upsert 完成后，按该题当前规范化标签执行完整替换：

```text
DELETE current assignments by problem_id
INSERT current assignments in ordinal order
```

完整替换可以正确处理标签新增、删除和顺序变化。题目标签数量很小，第一版不需要设计 assignment 差量合并。

### 7.6 旧数组双写

`problem.tag_values`、`tag_labels_en` 和 `tag_labels_zh` 继续写入，但必须从同一个 `List<ProblemSeedTag>` 生成：

```text
tag_values    = tags.map(value)
tag_labels_en = tags.map(labelEn)
tag_labels_zh = tags.map(labelZh)
```

禁止旧数组和关联表各自执行一套去重、排序或 fallback 逻辑。

## 八、读取链路切换

### 8.1 权威读取来源

本规格落地后：

- 题目标签的应用读取以 `problem_tag` 和 `problem_tag_assignment` 为权威来源；
- 旧数组只作为兼容副本和回滚保障；
- 新代码不得新增对旧数组的读取依赖。

### 8.2 题目列表与详情

`ProblemMapper.xml` 的列表和详情查询改为从关联表按 `ordinal` 聚合标签。

为了保持现有 `ProblemRow` 和 `MyBatisProblemRepository.tags(...)` 映射，可以继续输出：

```text
tag_values_text
tag_labels_en_text
tag_labels_zh_text
```

但这三个投影必须来自规范化关系，而不是 `problem` 数组。推荐使用一个按 `problem_id` 聚合的 lateral 子查询：

```sql
LEFT JOIN LATERAL (
  SELECT
    string_agg(pt.value, E'\n' ORDER BY pta.ordinal) AS tag_values_text,
    string_agg(pt.label_en, E'\n' ORDER BY pta.ordinal) AS tag_labels_en_text,
    string_agg(pt.label_zh, E'\n' ORDER BY pta.ordinal) AS tag_labels_zh_text
  FROM problem_tag_assignment pta
  JOIN problem_tag pt ON pt.id = pta.tag_id
  WHERE pta.problem_id = p.id
) normalized_tags ON TRUE
```

没有标签的题目使用空字符串回退，保持当前 Java 映射行为不变。

列表查询必须使用这种每题最多返回一行的聚合子查询，不能直接把 assignment JOIN 到分页主查询后再依赖 Java 去重，否则会破坏 `COUNT`、`LIMIT/OFFSET` 和排序语义。

### 8.3 题目标签筛选

`ProblemFilters` 中的标签条件改为：

```sql
AND EXISTS (
  SELECT 1
  FROM problem_tag_assignment pta
  JOIN problem_tag pt ON pt.id = pta.tag_id
  WHERE pta.problem_id = p.id
    AND pt.value = #{tag}
    AND pt.active = TRUE
)
```

不再使用：

```sql
#{tag} = ANY(p.tag_values)
```

### 8.4 标签过滤项和题目数量

`countProblemsByTag` 改为直接聚合规范化关系：

```sql
SELECT
  pt.value,
  CASE
    WHEN #{locale} = 'en-US' THEN pt.label_en
    ELSE pt.label_zh
  END AS label,
  COUNT(*) AS problem_count
FROM problem_tag pt
JOIN problem_tag_assignment pta ON pta.tag_id = pt.id
WHERE pt.active = TRUE
GROUP BY pt.id
ORDER BY label ASC, pt.value ASC
```

标签目录中的名称是唯一展示值，不再通过 `MIN(label)` 从题目数组中猜测当前名称。

### 8.5 能力雷达

`AbilityProfileMapper.xml` 保持现有评分公式，只替换标签展开方式：

```text
problem
  -> problem_tag_assignment
  -> problem_tag
  -> 按 tag 统计题目数
  -> 连接当前用户每题最新 Review
  -> 计算 reviewedProblemCount 和 rawAverageScore
```

具体规则保持不变：

- 只使用 `active=true` 的标签；
- 题目数不少于 `MIN_PROBLEM_COUNT` 才进入雷达；
- 同一道题只取用户最新一次 Review；
- 一题的分数仍贡献给该题全部标签；
- 默认展示中文 `label_zh`；
- 排序仍为 `problem_count DESC, value ASC`。

### 8.6 API 兼容

以下外部契约不变：

- 题目列表和详情中的 `tags`；
- `ProblemTag(value, label)`；
- 标签筛选参数继续使用 `value`；
- `GET /api/abilities/profile` 响应字段和评分公式；
- 前端 `ProblemTag` 和能力雷达类型。

本规格不要求前端改动；现有前端测试应继续通过。API 结构和 tag `value` 不变，但历史上同一 value 的英文名称变体和中文回退文本会收敛为目录中的规范 label，这属于预期数据规范化。

## 九、Repository 与 Mapper 边界

### 9.1 新增标签写入边界

建议新增：

```text
api/problem/model/ProblemSeedTag.java
api/problem/model/ProblemTagDefinition.java
api/problem/model/NormalizedProblemSeed.java
api/problem/service/ProblemSeedTagNormalizer.java
api/problem/repository/ProblemTagRepository.java
api/problem/repository/MyBatisProblemTagRepository.java
api/problem/mapper/ProblemTagMapper.java
resources/mapper/problem/ProblemTagMapper.xml
```

`ProblemTagRepository` 第一版只需要两个职责：

```java
void upsertCatalog(List<ProblemTagDefinition> catalog);

void replaceAssignments(String problemSlug, List<ProblemSeedTag> tags);
```

具体签名可以根据 MyBatis 批量参数调整，但不得让 `ProblemSeedImporter` 直接依赖 Mapper 或 SQL。

### 9.2 现有读取边界

- `ProblemMapper.xml` 继续拥有题目列表、详情、筛选和过滤项 SQL；
- `AbilityProfileMapper.xml` 继续拥有能力雷达聚合 SQL；
- `MyBatisProblemRepository` 继续映射 `ProblemRow -> ProblemTag`；
- Controller 和前端不感知底层存储切换。

### 9.3 常量与契约

表名和列名保留在迁移和 Mapper SQL 中，不额外建立无收益的 Java 常量。以下跨类语义应使用现有或新增模型表达：

- `ProblemSeedTag`；
- `ProblemTagDefinition`；
- `NormalizedProblemSeed`；
- 标签 `value`；
- `active` 语义；
- ordinal 从 `0` 开始。

不允许在多个类中分别实现标签 fallback 和去重规则，统一由 `ProblemSeedTagNormalizer` 负责。

## 十、一致性校验

### 10.1 有序数组一致性

迁移后和每次完整 seed 导入测试中，都要执行以下等价校验：

```sql
SELECT p.id, p.slug
FROM problem p
LEFT JOIN LATERAL (
  SELECT
    array_agg(pt.value ORDER BY pta.ordinal) AS tag_values,
    array_agg(pt.label_en ORDER BY pta.ordinal) AS tag_labels_en,
    array_agg(pt.label_zh ORDER BY pta.ordinal) AS tag_labels_zh
  FROM problem_tag_assignment pta
  JOIN problem_tag pt ON pt.id = pta.tag_id
  WHERE pta.problem_id = p.id
) normalized_tags ON TRUE
WHERE p.tag_values IS DISTINCT FROM COALESCE(
        normalized_tags.tag_values, ARRAY[]::TEXT[])
   OR p.tag_labels_en IS DISTINCT FROM COALESCE(
        normalized_tags.tag_labels_en, ARRAY[]::TEXT[])
   OR p.tag_labels_zh IS DISTINCT FROM COALESCE(
        normalized_tags.tag_labels_zh, ARRAY[]::TEXT[]);
```

查询必须返回 `0` 行。

### 10.2 目录完整性

还需验证：

- assignment 不存在悬空 `problem_id` 或 `tag_id`；
- 同一 `value` 只有一条目录记录；
- 所有 assignment 的 ordinal 连续从 `0` 开始；
- 所有题目的 assignment 数量与数组数量相同；
- 当前 seed 出现的标签全部为 `active=true`；
- 题库标签过滤结果在切换前后数量一致；
- 能力雷达 tag 集合和题目数在切换前后一致。

### 10.3 导入结果

完整 seed 重复导入两次后：

- `problem_tag` 行数不增加；
- `problem_tag_assignment` 行数不增加；
- 标签名称和 ordinal 不漂移；
- 题目和标签目录的 `updated_at` 不因无变化导入产生无意义更新；
- assignment 可以按相同内容执行删除后重建，但最终键集合和 ordinal 必须完全一致；
- 数组和规范化关系仍完全一致。

## 十一、测试规格

### 11.1 规范化器单元测试

覆盖：

- 正常三数组转换；
- 空 label fallback；
- 空 value 拒绝；
- 完全重复 tag 去重并保留第一次 ordinal；
- 重复 value 对应不同 label 时拒绝；
- 数组长度不一致时拒绝；
- 多题相同 value 名称一致时通过；
- 多题相同 value 名称不同时按频次选择；
- 候选次数相同时按字符串升序稳定决胜；
- 中文候选优先真正翻译，只有英文回退时使用规范英文名称；
- 问题遍历顺序变化不影响规范名称。

### 11.2 Mapper 和 Repository 测试

覆盖：

- tag catalog upsert 幂等；
- 标签名称统一变化时更新；
- seed 中出现的 inactive 标签重新激活；
- seed 未出现的标签不自动停用；
- assignment 完整替换可处理新增、删除和重排；
- 无标签题目保持空列表；
- 列表与详情按 ordinal 返回相同标签顺序；
- 标签筛选只读取规范化关系；
- 过滤项按 locale 返回目录名称和正确题目数。

### 11.3 能力雷达回归

更新 `AbilityProfileMapperXmlTest` 和相关聚合测试，覆盖：

- 不再包含 `unnest(p.tag_values, ...)`；
- 同题多 Review 仍只取最新版本；
- 一题多个标签仍分别贡献；
- 低频标签仍被 `MIN_PROBLEM_COUNT` 排除；
- 无 Review 标签仍返回 `0.0`；
- 切换前后固定 fixture 的 tag、problemCount 和分数一致。

### 11.4 导入集成测试

使用 PostgreSQL 集成测试或项目后续统一数据库 fixture 验证：

- V33 回填后数组与关联表完全一致；
- 完整 seed 导入在一个事务中完成；
- 同一题内重复 value 对应冲突 label 时整个导入回滚；
- 第二次导入幂等；
- 删除某题 seed 中一个标签后，数组和 assignment 同时删除；
- 导入过程中异常不会留下半套 catalog 或 assignment。

### 11.5 API 回归

现有题目列表、详情、过滤项和能力雷达 API 的响应结构保持不变。除已知名称变体被规范化外，tag value、顺序、数量、题目数和雷达分数必须保持一致。前端无需知道标签存储已经切换。

建议验证命令：

```bash
make backend-test
make build
```

需要真实 PostgreSQL 的迁移和回填验证使用 `*IT.java` 与 Maven Failsafe，在实施计划中单独列出运行方式。

## 十二、交付顺序

### 阶段一：迁移与回填

1. 新增 V33 迁移；
2. 创建标签目录和关联表；
3. 校验并回填历史数组；
4. 执行有序数组一致性检查。

### 阶段二：导入双写

1. 增加统一标签规范化器；
2. 导入前校验全局标签目录；
3. upsert 标签目录；
4. 题目 upsert 继续写数组；
5. 完整替换题目标签关联；
6. 增加导入幂等与回滚测试。

### 阶段三：读取切换

1. 题目列表和详情改读关联表；
2. 标签筛选和过滤项改读关联表；
3. 能力雷达改读关联表；
4. 保持 Java 和 HTTP API 不变；
5. 执行切换前后对照测试。

### 阶段四：用户测试观察

1. 运行完整后端测试和构建；
2. 由用户执行本地功能测试；
3. 保持旧数组持续双写；
4. 不停止数组写入，不删除数组列；
5. 等待用户明确发起后续清理任务。

## 十三、验收标准

规格完成必须同时满足：

- `problem_tag` 和 `problem_tag_assignment` 已创建并完成历史回填；
- 导入器使用同一份规范化标签列表双写数组和关联表；
- 同一完整 seed 重复导入幂等；
- 题目列表、详情、标签筛选、过滤项和能力雷达全部读取规范化关系；
- 对外 `ProblemTag(value, label)` 和 API 响应不变；
- 有序数组一致性查询返回 `0` 行；
- 切换前后固定数据集的标签数量、顺序、题目数和雷达结果一致；
- 旧数组、GIN 索引和数组长度约束仍然保留；
- 没有任何删列或停止双写操作。

仅创建表或完成回填，不算闭环完成。

## 十四、风险与回滚

### 14.1 主要风险

- 名称频次规则实现不一致，导致目录和兼容数组选择不同 label；
- 规范化时先去重 value 后读取 label，造成标签错位；
- 题目列表 JOIN 产生重复题目，破坏分页；
- 标签筛选切换后题目数量变化；
- 能力雷达切换后标签集合或题目计数变化；
- 导入只更新数组或只更新 assignment，造成双写漂移；
- inactive 标签处理不一致，导致筛选项消失。

### 14.2 回滚方式

本次数据库变更是加法迁移，旧数组保持完整：

- 应用查询出现问题时，可以回退到仍读取数组的上一版本；
- 新表可以继续保留，不需要在故障处理中执行破坏性 DROP；
- 因为新导入器持续双写数组，回退应用后仍能读取最新标签；
- 修复规范化查询后再次发布，不需要重新抓取题库数据；
- 严禁通过删除新表或旧数组进行临时止损。

## 十五、旧数组删除门禁

旧数组清理不属于本规格的实施范围。

只有在用户完成测试并明确要求后，才能另起变更处理：

```text
停止导入器写 tag_values / tag_labels_en / tag_labels_zh
删除 MyBatis 和 Java 中的数组兼容代码
删除 idx_problem_tag_values
删除 ck_problem_tag_array_lengths
删除三个数组列
补充回滚和数据库备份方案
```

在收到明确指令前，即使规范化读取已经稳定，也必须继续双写并保留三个数组。
