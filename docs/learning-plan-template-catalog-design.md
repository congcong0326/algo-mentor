# 学习计划模板一级分类与推荐展示研发设计

## 1. 文档结论

学习计划模板页面第一版只引入一级分类和人工推荐顺序，用较小改动解决模板数量增加后的选择成本问题。

页面固定提供以下入口：

```text
推荐 | 系统学习 | 面试备战 | 专题突破 | 语言与岗位
```

核心决策：

- 每个模板只属于一个一级分类，不增加二级分类。
- 「推荐」不是分类，而是聚合所有 `recommendedOrder` 非空模板的默认视图。
- 推荐顺序由项目维护者根据模板价值人工策展，不使用点击量、热度或模型评分。
- 公共 API 不再返回来源 commit、来源数据路径、题目匹配数、缺失数、匹配标记和原始 metadata。
- 后端继续保存完整来源与匹配审计数据，用于导入校验、草案完整性校验和排障。

## 2. 背景与问题

当前聚合 seed 已包含 35 个模板、1705 条题目引用。模板列表接口一次返回全部模板，前端以双列大卡片完整铺开，并自动选中接口返回的第一个模板。

当前实现存在以下问题：

- 模板数量增加后，用户需要逐张阅读卡片，缺少从学习目标出发的一级入口。
- 后端列表默认按周期升序和模板 ID 排序，前端默认选中第一条，因此默认方案不代表项目推荐。
- 单张卡片同时展示摘要、路线规模、来源 commit 和题目匹配统计，信息密度过高。
- `sourceCommit`、`matchedProblemCount`、`missingProblemCount` 等导入审计字段直接进入公共模板 DTO。
- 模板生成草案时，来源 commit、来源数据路径、匹配统计和完整 problem refs 又被写入草案 metadata；草案和正式方案接口会继续原样返回这些 metadata。
- 用户选择模板时会请求模板详情，但当前创建面板实际只需要编程语言和默认训练节奏，不需要完整阶段引用和审计字段。

本次设计同时处理页面索引和 API 边界，避免只在前端隐藏内部字段。

## 3. 目标

- 通过一级分类将 35 个模板划分为少量、稳定、容易理解的入口。
- 默认展示项目策展的高价值模板，降低首次选择成本。
- 保持现有模板生成草案的确定性流程和完整性校验不变。
- 收紧模板、草案和正式方案的公共响应，只返回用户需要的产品字段。
- 保持分类与推荐顺序由 seed 管理，能够随模板内容一起评审、导入和回滚。

## 4. 非目标

第一版明确不做：

- 二级分类或多层目录。
- 基于点击、草案创建或正式方案数量的热度排序。
- 基于学习画像、能力雷达或历史进度的个性化推荐。
- 前端自由排序、复杂筛选器或多标签组合查询。
- 管理员在线调整分类与推荐顺序的配置页面。
- 向普通用户展示模板导入质量、缺失题目或来源版本审计信息。

## 5. 产品信息架构

### 5.1 一级入口

页面顶部使用单层标签页：

| 入口 | 语义 |
| --- | --- |
| 推荐 | 展示 `recommendedOrder` 非空的模板，按推荐顺序排列 |
| 系统学习 | 从编程实现力、算法基础到完整知识路线的长期学习模板 |
| 面试备战 | 面试核心题单、冲刺路线、经典路线和高频复盘模板 |
| 专题突破 | 围绕单个或一组关联算法专题集中训练的模板 |
| 语言与岗位 | SQL、JavaScript、Pandas 等语言或岗位能力路线 |

默认进入「推荐」，并自动选中 `recommendedOrder = 1` 的模板。

### 5.2 卡片信息

模板卡片只展示：

- 标题；
- 一句话摘要；
- 难度或适合人群中的一个主要信息；
- 实际可练题数；
- 默认周期和训练节奏；
- 推荐模板的「推荐」标记。

卡片不展示：

- 来源 commit；
- 来源数据路径；
- 来源题目总数；
- 匹配题目数和缺失题目数；
- 本地匹配比例；
- 导入或整理 metadata。

来源名称和来源链接可以在模板详情中作为正常归因保留，但不进入列表卡片。

### 5.3 选中区域

现有选中模板后的适合人群、完成目标、级别、标准节奏和语言设置继续保留。一级索引只减少当前可见模板数量，不在本次重构整个创建流程。

切换分类时：

- 如果当前选中模板属于新的结果集，保持选中；
- 否则选中新结果集的第一条；
- 推荐视图为空时展示空状态，但 seed 校验应保证首版至少存在一条推荐模板。

## 6. 分类模型

### 6.1 公共枚举

在学习计划模板领域增加统一枚举：

```java
public enum LearningPlanTemplateCatalogCategory {
  SYSTEMATIC_LEARNING,
  INTERVIEW_PREP,
  TOPIC_BREAKTHROUGH,
  LANGUAGE_AND_ROLE
}
```

该枚举是跨 seed、数据库、后端 DTO 和前端类型的公共契约，不使用自由字符串。

### 6.2 分类原则

- 每个模板只能有一个 `catalogCategory`。
- 分类依据是用户选择模板时的主要目标，不按 LeetCode、NeetCode、代码随想录等资料来源分类。
- `level`、`programmingLanguage`、`interviewOriented` 和 `topicPreferences` 继续作为模板属性，不提升为一级分类。
- 同一个模板即使同时具有长期学习和面试属性，也只选择最主要的产品入口。

### 6.3 当前 35 个模板分类

#### 系统学习：5 个

| templateId | 模板 |
| --- | --- |
| `programming_skills_implementation_foundation` | 编程基础与实现力计划 |
| `cn_algorithm_foundation_12weeks` | 中文系统刷题入门计划 |
| `carl_algorithm_roadmap_full` | 代码随想录完整刷题路线 |
| `leetcode_patterns_beginner_roadmap` | 算法模式入门训练计划 |
| `labuladong_algo_thinking` | labuladong 核心算法框架训练计划 |

#### 面试备战：8 个

| templateId | 模板 |
| --- | --- |
| `neetcode_150_systematic_interview` | NeetCode 150 系统面试计划 |
| `neetcode_blind_75_interview_core` | NeetCode Blind 75 面试核心计划 |
| `tih_best_practice_50_5weeks` | 5 周面试冲刺计划 |
| `sword_offer_classic` | 剑指 Offer 经典面试计划 |
| `cracking_coding_interview_classic` | 程序员面试金典系统训练计划 |
| `leetcode_75_core_sprint` | LeetCode 75 核心冲刺计划 |
| `leetcode_top_interview_150` | LeetCode 面试经典 150 题计划 |
| `leetcode_top_100_liked_revision` | Top 100 Liked 复盘计划 |

#### 专题突破：18 个

| templateId | 模板 |
| --- | --- |
| `tih_algorithm_essentials` | 算法面试核心专题计划 |
| `topic_dynamic_programming_foundation` | 动态规划专项突破计划 |
| `topic_dp_advanced` | 动态规划进阶专项计划 |
| `topic_graph_bfs_dfs` | 图论专项突破计划 |
| `topic_binary_search_boundaries` | 二分与边界专项计划 |
| `topic_sliding_window_two_pointers` | 滑动窗口与双指针专项计划 |
| `topic_tree_binary_tree_foundation` | 树与二叉树专项 |
| `topic_backtracking_foundation` | 回溯专项突破 |
| `topic_heap_priority_queue` | 堆与优先队列专项 |
| `topic_greedy_strategies` | 贪心策略专项 |
| `topic_stack_monotonic` | 栈与单调栈专项 |
| `topic_bit_manipulation` | 位运算专项 |
| `topic_linked_list` | 链表专项 |
| `topic_union_find_and_advanced_graph` | 并查集与进阶图论专项 |
| `topic_prefix_sum_difference` | 前缀和与差分专项 |
| `topic_trie_and_string_advanced` | 字典树与字符串进阶 |
| `topic_intervals_scheduling` | 区间与调度专项 |
| `topic_data_structure_design` | 数据结构设计专项 |

#### 语言与岗位：4 个

| templateId | 模板 |
| --- | --- |
| `leetcode_sql_50` | LeetCode SQL 50 系统训练计划 |
| `leetcode_javascript_30_days` | LeetCode JavaScript 30 天训练计划 |
| `leetcode_pandas_introduction` | LeetCode Pandas 入门训练计划 |
| `leetcode_pandas_30_days` | LeetCode Pandas 30 天进阶计划 |

## 7. 推荐模型

### 7.1 字段语义

模板增加可空字段：

```java
Integer recommendedOrder;
```

语义固定为：

- 正整数，数字越小展示越靠前；
- 非空表示进入「推荐」视图；
- `null` 表示只在所属一级分类中展示；
- 不是热度、点击量、动态评分或模型输出；
- 推荐顺序通过模板源数据变更和代码评审维护。

### 7.2 首批推荐顺序

| recommendedOrder | templateId | 选择原因 |
| ---: | --- | --- |
| 1 | `leetcode_75_core_sprint` | 路线覆盖均衡、周期适中、当前本地题目完整，适合最多用户 |
| 2 | `cn_algorithm_foundation_12weeks` | 中文入门门槛低、每周负担合理，适合作为第一条系统路线 |
| 3 | `carl_algorithm_roadmap_full` | 中文完整知识路线，适合长期构建算法知识体系 |
| 4 | `leetcode_top_interview_150` | 官方完整面试路线，适合希望系统覆盖高频题型的进阶用户 |
| 5 | `labuladong_algo_thinking` | 强调算法框架和迁移能力，适合已有刷题经验的用户 |
| 6 | `topic_dynamic_programming_foundation` | 高频薄弱专题、周期短、目标清晰，代表专题突破入口 |

其他模板的 `recommendedOrder` 首版均为 `null`。

### 7.3 价值判断标准

人工调整推荐顺序时按以下顺序评估：

1. 用户覆盖面和目标清晰度；
2. 路线结构与阶段设计质量；
3. 周期和每周投入是否容易执行；
4. 当前本地可执行完整度；
5. 与已有推荐模板是否形成互补。

本地匹配完整度只作为内部推荐门禁。普通用户只能看到实际可练题数，不看到匹配、缺失或导入质量数据。

## 8. Seed 与数据模型改造

### 8.1 模板源数据

每个 `data/learning-plan-template-sources/templates/<templateId>/template.json` 增加：

```json
{
  "catalogCategory": "INTERVIEW_PREP",
  "recommendedOrder": 1
}
```

非推荐模板省略 `recommendedOrder` 或显式写 `null`。为减少无意义差异，生成规范统一为非推荐模板省略该字段。

### 8.2 聚合 seed

`learning_plan_templates.jsonl` 必须包含 `catalogCategory`，并按需包含 `recommendedOrder`。聚合生成器负责原样传递字段，不根据 `intent` 或语言自行推断分类。

生成校验增加：

- `catalogCategory` 必填且必须属于固定枚举；
- `recommendedOrder` 为空或为大于 0 的整数；
- 所有非空 `recommendedOrder` 在一次 seed 中唯一；
- 至少存在一个推荐模板；
- 当前首批 seed 的推荐顺序应从 1 连续到 6，避免页面出现无意义空档。

### 8.3 数据库

在 `learning_plan_template` 增加：

```sql
catalog_category VARCHAR(40) NOT NULL,
recommended_order INTEGER,
CONSTRAINT ck_learning_plan_template_recommended_order
  CHECK (recommended_order IS NULL OR recommended_order > 0)
```

增加普通索引：

```sql
CREATE INDEX idx_learning_plan_template_catalog
  ON learning_plan_template(catalog_category, recommended_order, default_duration_weeks, template_id);
```

数据库不增加 `recommended_order` 唯一约束。唯一性由 seed 导入前校验保证，避免未来交换两个推荐顺序时逐行 upsert 产生临时冲突。查询仍使用 `template_id` 作为最终稳定排序键。

迁移使用下一个可用 Flyway 版本，按当前迁移序列预计为：

```text
V44__learning_plan_template_catalog.sql
```

迁移需要为已有 35 个模板完成分类和推荐顺序回填，再将 `catalog_category` 收紧为非空。模板源数据和聚合 seed 是后续维护的最终事实源，迁移回填只用于现有数据库平滑升级。

### 8.4 后端领域与持久化

同步修改：

- `LearningPlanTemplate`；
- `LearningPlanTemplateSeedRecord`；
- `LearningPlanTemplateRow`；
- MyBatis result map、upsert 和查询字段；
- seed reader、导入校验和测试内构造数据。

列表稳定排序规则：

```text
recommendedOrder 非空优先
recommendedOrder 升序
defaultDurationWeeks 升序
templateId 升序
```

前端仍会按一级分类过滤，但后端返回顺序必须稳定，避免不同调用方得到不一致结果。

## 9. 公共 API 设计

### 9.1 模板列表

继续使用：

```http
GET /api/learning-plan-templates
```

35 个模板规模较小，第一版仍一次返回全部摘要，由前端本地完成一级分类过滤，不增加 category 查询参数和分页。

摘要响应保留或增加：

- `templateId`；
- `title`；
- `summary`；
- `catalogCategory`；
- `recommendedOrder`；
- `intent`；
- `level`；
- `programmingLanguage`；
- `interviewOriented`；
- `topicPreferences`；
- `targetAudience`；
- `expectedOutcome`；
- `plannedProblemCount`；
- `defaultDurationWeeks`；
- `defaultWeeklyHours`；
- `defaultLoadSummary`；
- `defaultRhythmSettings`。

`plannedProblemCount` 表示用户按当前模板能够实际进入草案的题目数量。该字段可以由后端根据内部匹配结果计算，但名称和语义不得暴露匹配过程。

摘要响应删除：

- `sourceCommit`；
- `problemCount`；
- `matchedProblemCount`；
- `missingProblemCount`。

`programmingLanguage` 加入摘要后，模板创建面板不再需要在每次选中模板时请求完整详情来初始化语言。

### 9.2 模板详情

继续使用：

```http
GET /api/learning-plan-templates/{templateId}
```

详情可以保留目标、前置要求、适用边界、阶段信息以及来源名称和来源链接，但必须删除：

- `sourceCommit`；
- `sourceDataPath`；
- 原始 `metadata`；
- `problemCount`；
- `matchedProblemCount`；
- `missingProblemCount`；
- problem ref 的 `matchedProblem`；
- problem ref 的原始 metadata 和纯审计顺序字段。

第一版创建面板不依赖模板详情接口。若详情仍返回阶段，阶段只返回用户可理解的阶段内容和 `plannedProblemCount`，不返回完整原始 problem refs。后续确需增加路线预览时，再定义只包含实际可练题目的产品 DTO。

### 9.3 草案与正式方案响应

内部 `LearningPlanDraftPlan.metadata` 继续保存模板来源、匹配统计和 problem refs，因为当前模板草案完整性校验依赖这些数据。

公共响应不得直接返回内部 metadata。响应映射增加统一白名单投影，只允许现有前端确实使用的产品字段，例如：

- `dailyProblemCount`；
- `trainingDaysPerWeek`；
- `coveragePolicy`；
- `loadSummary`；
- `loadRisk`；
- `weeklyBuckets`；
- `nextTrainingPackage`。

以下内容即使仍保存在数据库中，也不得出现在草案和正式方案响应：

- 嵌套 `template` 审计对象；
- `sourceCommit`；
- `sourceDataPath`；
- `matchedProblemCount`；
- `missingProblemCount`；
- `problemRefs`；
- `matchedProblem`；
- 任意未经白名单确认的 metadata。

正式方案详情已经拥有 `loadSummary`、`weeklyBuckets`、`rhythmSettings` 等一等字段，前端应优先使用这些字段，逐步减少对通用 metadata 的依赖。

### 9.4 管理与审计边界

第一版不新增管理员模板审计 API。来源 commit、数据路径、匹配数、缺失数和导入运行记录继续通过数据库、seed 产物和后端测试排查。

未来需要在线治理时，应新建独立的管理员 DTO 和 `/api/admin/...` 接口，不能重新复用普通用户模板响应。

## 10. 前端改造

### 10.1 类型

前端增加：

```ts
export type LearningPlanTemplateCatalogCategory =
  | 'SYSTEMATIC_LEARNING'
  | 'INTERVIEW_PREP'
  | 'TOPIC_BREAKTHROUGH'
  | 'LANGUAGE_AND_ROLE';
```

`LearningPlanTemplateSummaryResponse` 增加 `catalogCategory`、`recommendedOrder`、`programmingLanguage` 和 `plannedProblemCount`，删除来源 commit 和匹配统计字段。

### 10.2 视图状态

`LearningPlanTemplateCreatePanel` 增加本地状态：

```ts
type TemplateCatalogView = 'RECOMMENDED' | LearningPlanTemplateCatalogCategory;
```

初始值为 `RECOMMENDED`。过滤规则：

- `RECOMMENDED`：`recommendedOrder != null`；
- 其他视图：`catalogCategory === currentView`。

推荐视图按 `recommendedOrder` 升序；分类视图沿用接口稳定顺序。

### 10.3 详情请求简化

创建面板当前使用模板详情的主要原因是读取 `programmingLanguage` 和默认节奏。将语言加入摘要后：

- 列表加载完成即可初始化选中模板、语言和节奏；
- 切换模板不再自动请求完整详情；
- 草案生成仍只提交 `templateId`、语言和训练节奏，由服务端读取完整模板。

这可以避免用户浏览模板时反复传输阶段 refs 和内部审计数据。

### 10.4 国际化与可访问性

中英文资源增加五个入口名称和「推荐」标记。标签页使用 `tablist`、`tab` 和 `tabpanel` 语义，并正确维护 `aria-selected`。

分类切换、卡片选择和键盘焦点不得改变现有草案提交逻辑。

## 11. 测试设计

### 11.1 Seed 与导入测试

- 缺少 `catalogCategory` 时生成或导入失败；
- 非法分类值失败；
- `recommendedOrder <= 0` 失败；
- 推荐顺序重复失败；
- 当前首批 6 个推荐模板顺序与设计一致；
- 导入后分类和推荐顺序正确写入数据库；
- 重复导入保持幂等。

### 11.2 后端 API 测试

- 列表返回 4 类合法分类；
- 推荐顺序稳定；
- `plannedProblemCount` 等于实际可进入模板草案的题量；
- 列表和详情 JSON 不存在 `sourceCommit`、`sourceDataPath`、`problemCount`、`matchedProblemCount`、`missingProblemCount`；
- problem ref 或阶段响应不存在 `matchedProblem` 和原始 metadata；
- 模板草案响应不存在嵌套模板审计 metadata；
- 正式方案详情不存在来源 commit、匹配统计和完整 problem refs；
- 删除公共字段不影响模板草案完整性校验和确认保存。

测试应断言字段在 JSON 中不存在，而不是只断言值为 `null`。

### 11.3 前端测试

- 默认展示「推荐」并按 1 到 6 排序；
- 四个一级分类数量分别为 5、8、18、4；
- 切换分类后只展示对应模板；
- 当前选中模板离开结果集时自动选择第一条；
- 卡片不展示 commit、匹配数、缺失数和缺失提示；
- 选择模板不再触发详情请求；
- 提交请求仍包含正确的 `templateId`、语言和训练节奏；
- 中英文入口和可访问性语义正确。

## 12. 发布与迁移顺序

建议按以下顺序落地：

1. 更新模板源数据和 seed 生成器校验；
2. 重新生成聚合 seed；
3. 增加数据库迁移、领域字段和 MyBatis 映射；
4. 更新导入服务并导入新版 seed；
5. 收紧模板、草案和正式方案公共 DTO；
6. 更新前端类型、一级索引和卡片展示；
7. 运行 seed、后端和前端最小相关测试；
8. 前后端按同一版本发布，避免旧前端依赖已删除字段。

数据库变更为加列和索引，不删除内部审计字段。应用回滚时可以保留新增列；若需要回滚推荐页面，只需回滚应用版本，不需要逆向删除数据列。

## 13. 风险与控制

### 13.1 推荐顺序主观

推荐顺序是人工策展，天然包含判断。通过明确价值标准、代码评审和 seed 版本记录保证可解释性。第一版不把它包装成客观热度。

### 13.2 分类长期膨胀

当前只允许 4 个分类。新增模板优先放入已有分类；只有现有分类无法表达新的主要学习目标时，才通过独立设计评审增加枚举值。

### 13.3 API 破坏性变更

删除模板和 metadata 字段会影响前端测试及潜在调用方。项目当前前后端同仓维护，应同步修改并同版本发布。测试必须覆盖字段不存在和前端不再读取旧字段。

### 13.4 内部 metadata 误透传

禁止在 Controller 或响应 record 中直接暴露领域 `LearningPlanDraftPlan` 的原始 metadata。公共映射必须使用明确白名单，新增 metadata key 默认不可见。

## 14. 验收标准

- 页面默认只展示 6 个推荐模板，而不是一次铺开 35 个模板。
- 用户可以通过 4 个一级分类查看全部模板，分类总数与 seed 一致。
- 推荐顺序与本设计固定的 1 到 6 一致。
- 页面不出现来源 commit、匹配数、缺失数或缺失提示。
- 浏览和选择模板不请求包含完整 problem refs 的详情数据。
- 所有普通用户模板、草案和正式方案 API 均不返回内部审计字段或原始 metadata。
- 模板导入审计、完整性校验和确定性草案生成行为保持不变。
- 不引入热度统计、个性化推荐、二级分类或新的管理员配置页面。
