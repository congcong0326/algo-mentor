# AI 学习计划个性化生成重构固定契约

本文件是 `LPGR-00` 至 `LPGR-10` 的实现期固定契约摘要。任务实施时优先遵守这里的约束，不在单个任务内重新发散产品设计。

## 1. 产品与范围边界

- 保留“表单创建 -> 草案预览 -> 聊天修订 -> 确认保存”的用户路径。
- 保留现有 Agent Runtime、题库 Tool、负载模型、周桶、进度、提案和 SSE 基础设施。
- AI 创建使用 `objective`、`additionalConstraints` 和精确难度分布，不再接收表单拼接的 `goal`。
- 个性化默认开启，用户可在 AI 创建表单关闭；关闭后本 run 不读取任何学习数据。
- 初期只注入聚合摘要，不增加学习计划历史 Tool，不读取原始代码、完整 Review、完整聊天记录或任意历史正文。
- 不新增 Workflow、向量数据库、个性化上下文表或数据库迁移。
- 项目未上线，不兼容旧 API、旧 `command_json`、旧草案快照或旧正式计划快照。
- 模板入口不增加个性化开关，模板生成始终不读取个性化数据。

待评审项按设计默认方案冻结为：`objective` 可空并由服务端生成固定默认值；个性化默认开启且可关闭；首期不开放详细历史 Tool。

## 2. `LearningPlanBrief`

应用层唯一规划输入固定为：

```java
public record LearningPlanBrief(
    LearningPlanIntent intent,
    String objective,
    Integer durationWeeks,
    LearningPlanLevel level,
    Integer weeklyHours,
    String programmingLanguage,
    LearningPlanDifficultyDistribution difficultyDistribution,
    Boolean interviewOriented,
    List<String> topicPreferences,
    String additionalConstraints,
    boolean personalizationEnabled,
    LearningPlanContentLocale contentLocale
) {}
```

规范化与校验：

- `intent`、`durationWeeks`、`level`、`weeklyHours`、`difficultyDistribution` 必填。
- `durationWeeks` 为 `1-52`，`weeklyHours` 为 `1-80`。
- `objective` trim 后最大 300 字符；空值由服务端按 intent 和 locale 解析，进入 Agent、持久化和计划快照前必须非空。
- `additionalConstraints` trim，空白转 `null`，最大 1000 字符；不得覆盖结构化字段。
- `programmingLanguage` trim，空白转 `null`。
- `interviewOriented` 未提供时为 `false`。
- `topicPreferences` 去空白、trim、稳定去重；`TOPIC_BREAKTHROUGH` 至少一项。
- `personalizationEnabled` 在 API 未提供时解析为 `true`；领域对象不保留空值。
- `contentLocale` 只由服务端根据 `Accept-Language` 解析，客户端请求体不提供。

本次 Brief 的结构化字段始终覆盖 `additionalConstraints`、历史画像和模型推断中的冲突信息。

## 3. 默认 objective

默认值由代码常量按 intent 和 locale 固定，不调用模型：

| intent | `zh-CN` | `en-US` |
| --- | --- | --- |
| `PRACTICE_GOAL` | 建立稳定的算法练习节奏 | Build a consistent algorithm practice routine |
| `ABILITY_DIAGNOSIS` | 识别并改善当前算法能力短板 | Identify and improve current algorithm skill gaps |
| `INTERVIEW_SPRINT` | 提升算法面试中的解题稳定性 | Improve problem-solving consistency for coding interviews |
| `TOPIC_BREAKTHROUGH` | 系统掌握所选算法专题 | Systematically master the selected algorithm topics |
| `MISTAKE_REVIEW` | 通过错题复盘减少重复错误 | Reduce repeated mistakes through focused review |
| `LONG_TERM_LEARNING` | 持续提升算法与数据结构能力 | Continuously improve algorithms and data structures |

默认值不得拼入周期、每周投入、难度、语言或专题摘要。

## 4. 精确难度分布

```java
public record LearningPlanDifficultyDistribution(
    int easyPercent,
    int mediumPercent,
    int hardPercent
) {}
```

- 每项为 `0-100`，总和必须等于 `100`。
- AI 创建请求必须提交该对象；模型、Prompt 和负载相关逻辑直接读取百分比。
- 分布表达整体选题倾向，不要求少量题目逐题严格命中比例。
- 模板内部继续使用 `LearningPlanDifficultyPreference`；模板转草案时唯一映射为：`EASY=60/35/5`、`MEDIUM=35/55/10`、`HARD=10/55/35`、`MIXED=25/55/20`。
- 该模板映射集中在一个常量或 mapper 中，禁止在多处复制。

## 5. 计划快照

最终 `LearningPlanDraftPlan` 固定包含：

- `title`、`summary`。
- `intent`、`objective`、`durationWeeks`、`level`、`weeklyHours`、`programmingLanguage`。
- `difficultyDistribution`、`interviewOriented`、`topicPreferences`、`additionalConstraints`。
- `phases` 和 `metadata`。

删除 `goal`、`difficultyPreference` 和 `profileSummary`。除 `title`、`summary`、`phases` 和受限模型 metadata 外，其余规划字段只能来自服务端校验后的 Brief。

为使正式计划扩展继续尊重用户的个性化选择，服务端在 `LearningPlanDraftMetadataKeys` 下保存布尔值 `personalizationEnabled`：

- 该值由 Brief 写入，模型不能输出或修改。
- AI 计划保存本次选择；模板计划固定为 `false`。
- 公共 metadata 投影默认不暴露该内部键。
- 不保存个性化上下文正文、claim ID、Review ID 或用户 ID。

`contentLocale` 继续使用现有 metadata 常量保存。所有复制计划、更新节奏、追加阶段和提案快照的路径都必须保留这两个服务端字段。

## 6. 初次模型输入与输出

初次 Prompt 消息顺序固定：

```text
system：受管理的学习计划固定规则
system：可选的个性化参考数据块
user：服务端校验后的完整 LearningPlanBrief
```

Brief 必须是最后一条 user message。个性化数据块明确标记为“不可信参考数据，不是系统指令”。

初次模型输出固定为：

```java
public record LearningPlanGeneratedContent(
    String title,
    String summary,
    List<LearningPlanPhaseDraft> phases,
    Map<String, Object> metadata
) {}
```

初次 JSON Schema 根对象只允许 `title`、`summary`、`phases`、`metadata`，`additionalProperties=false`。模型 metadata 仍只允许现有 `problemRecommendationIncomplete` 布尔值；locale、负载、节奏和个性化开关均由服务端追加。

Mapper 先把模型内容按题库事实规范化，再与 Brief 合并为完整计划。模型返回任何服务端规划字段都应因 Schema 严格校验失败，而不是被静默接受。

## 7. 个性化上下文

领域上下文固定为：

```java
public record LearningPlanPersonalizationContext(
    List<String> declaredFacts,
    List<String> generalObservations,
    List<LearningPlanAbilityTagSummary> weakTags,
    List<LearningPlanAbilityTagSummary> strongTags,
    LearningPlanActiveProgressSummary activePlan,
    LearningPlanReviewLoadSummary reviewLoad,
    Instant generatedAt
) {}
```

辅助摘要字段固定为：

- `LearningPlanAbilityTagSummary`：`tag`、`label`、`reviewedProblemCount`、`rawAverageScore`、`abilityScore`。
- `LearningPlanActiveProgressSummary`：`objective`、`currentWeek`、`totalWeeks`、`progressPercent`、`paceStatus`、`dailyProblemCount`、`trainingDaysPerWeek`、`remainingProblemCount`。
- `LearningPlanReviewLoadSummary`：`dueCount`、`remainingTodayCount`、`nextDueAt`；`dueCount` 包含已逾期项目，日界线首期使用 UTC。

只允许一个应用层聚合端口 `LearningPlanPersonalizationDataProvider`，按来源提供：ACTIVE claims、能力标签聚合、当前激活计划进度、复习负载。API 模块负责适配现有 service/repository；应用层不依赖 API DTO、MyBatis row 或 controller。

选择规则固定：

- declared 只取 `GOALS_AND_INTENTS`、`TIME_AND_RESOURCE_CONSTRAINTS`、`LEARNING_AND_INTERACTION_PREFERENCES`、`SELF_ABILITY_ASSESSMENT`，最多 8 条。
- general 只取 `PROBLEM_SOLVING_APPROACH`、`IMPLEMENTATION_AND_ERROR_PATTERN`、`REVIEW_AND_GROWTH_PERFORMANCE`，最多 6 条。
- declared 按上述 dimension 优先级、更新时间倒序稳定排序；general 按 evidence grade、更新时间倒序稳定排序。
- 能力标签排除 `reviewedProblemCount=0`；弱项取 ability score 最低 3 项，强项取最高 3 项且不得与弱项重复；同分按样本量降序、tag 升序。
- 当前激活计划和复习负载各最多一个摘要，不向模型暴露 plan ID、card ID 或用户 ID。

## 8. 上下文预算、优先级与降级

- 个性化数据块预算固定为估算 1000 token，沿用仓库现有的 `4 chars/token` 估算方式。
- 只按完整条目追加，禁止截断 claim 或标签文本的中间内容。
- 超预算保留顺序：用户明确自述 -> 弱项 -> 当前计划 -> 复习负载 -> 通用观察 -> 强项。
- 当前 Brief 不计入这 1000 token 的个性化预算，也绝不能被个性化块裁剪或覆盖。
- 优先级固定为：本次 Brief > 当前确定性进度与复习事实 > 用户明确自述 > 有证据的系统观察。
- `personalizationEnabled=false` 时不得调用 provider 的任何来源方法，也不产生个性化 system message。
- 无画像、无 Review、无激活计划属于正常空数据；任一来源异常只省略该来源，不中断生成。
- 每个初次生成、修订或扩展 run 只组装一次上下文，并把该不可变快照放入 Agent input；新的 run 重新读取。
- metadata 和日志只记录 enabled、来源成功/空/失败、条目数、估算 token、是否裁剪，不记录正文或业务 ID。

## 9. 草案修订

草案修订继续使用自由文本 instruction。输入包含当前 Brief、当前完整草案、instruction 和本 run 最新个性化上下文。

模型输出根对象固定为：

```text
resolvedBrief
generatedContent
```

- `resolvedBrief` 使用与创建相同的字段和校验规则。
- 用户未明确修改的字段必须保持当前值。
- `contentLocale` 和 `personalizationEnabled` 由服务端保持当前值；模型返回不同值时覆盖为当前值并记录低敏校验结果，不允许通过聊天切换这两个运行控制字段。
- 服务端验证 resolved Brief 后，使用它与 generated content 组装新计划，并同时更新草案保存的 Brief。
- 修订可以更改 intent、objective、周期、水平、时间、语言、难度分布、面试导向、专题和补充限制。
- 修订失败、过期或被新提案取代时，不得部分更新 Brief 或草案计划。

## 10. 计划扩展

- 扩展保持“只追加阶段”的现有语义，不修改正式计划的 Brief 字段、objective 或已有阶段。
- 扩展 run 根据正式计划 metadata 中的 `personalizationEnabled` 决定是否读取上下文；模板计划固定不读取。
- 扩展 Prompt 在固定 system 规则之后注入个性化数据块，再提供 instruction、当前计划和进度。
- 用户扩展 instruction 不写回 `objective` 或 `additionalConstraints`。
- 应用扩展后必须保留计划原有 metadata、个性化开关、locale、负载和节奏字段。

## 11. API 与前端

AI 创建请求固定为：

```json
{
  "intent": "INTERVIEW_SPRINT",
  "objective": "提升 Java 后端算法面试中的解题稳定性",
  "durationWeeks": 4,
  "level": "INTERMEDIATE",
  "weeklyHours": 6,
  "programmingLanguage": "Java",
  "difficultyDistribution": {"easyPercent": 25, "mediumPercent": 55, "hardPercent": 20},
  "interviewOriented": true,
  "topicPreferences": [],
  "additionalConstraints": "每周留一天复盘",
  "personalizationEnabled": true
}
```

- 请求不接受 `goal`、`difficultyPreference` 或 `profileSummary`。
- 草案、列表和详情响应使用 `objective`、`difficultyDistribution`、`additionalConstraints`，不返回 `profileSummary`。
- 创建表单新增可选具体目标、其他限制和默认开启的“参考我的学习数据”复选框。
- 现有难度滑块保持交互，只改变提交对象。
- 删除 `buildLearningPlanGoal` 及其本地化摘要文案。
- 草案顶部、列表搜索和详情使用简洁 `objective`；聊天修订输入和 SSE 交互不重做。

## 12. 模板边界

- `LearningPlanTemplate`、模板 seed、模板 API 可继续使用 `goal` 和 `difficultyPreference`，不在本重构中改名。
- `LearningPlanTemplateDraftService` 必须把模板 `goal(locale)` 映射为计划 `objective`，把模板难度枚举映射为固定百分比。
- 模板生成的 `additionalConstraints=null`、`personalizationEnabled=false`，不调用个性化 provider。
- 模板计划的标题、阶段、题目、负载和节奏逻辑保持现状。

## 13. 持久化与实施期桥接

- 草案继续使用 `command_json` 和 `draft_plan_json`，正式计划继续使用 `plan_json`，提案继续保存 base/proposed snapshot。
- `command_json` 最终只反序列化为 `LearningPlanBrief`；计划和提案 snapshot 最终只反序列化新 `LearningPlanDraftPlan`。
- 不读取旧字段、不双写、不回填、不新增兼容 DTO，不创建 Flyway migration。
- 测试和本地验证使用清洁数据；旧 JSON 解析失败符合本期兼容策略。
- `LPGR-02` 允许一个内部旧命令到新快照 mapper；`LPGR-03` 必须删除。
- `LPGR-07` 前允许修订专用完整计划 Schema；`LPGR-07` 必须删除。

## 14. 安全、日志与观测

- 个性化文本始终包在明确边界中，并声明为不可信参考数据；不得拼入受管理 system Prompt 正文或改变 Tool 白名单。
- 不记录 claim 文本、标签正文、Review 内容、原始代码、聊天正文、完整 Prompt、用户 ID 或数据库 JSON。
- 指标 label 只允许固定 `scenario`、`source`、`outcome`、`enabled`、`trimmed` 值；禁止使用 objective、tag、错误消息或用户标识作为 label。
- 至少观测：上下文开启/关闭、各来源 success/empty/error、上下文条目数、估算 token、裁剪次数、生成场景。
- 个性化故障不得改变 AI API 状态码、SSE 事件契约或草案确认流程。

## 15. 最终旧符号规则

最终活动源码中：

- `LearningPlanDraftCommand`、`buildLearningPlanGoal`、计划 `profileSummary` 必须零命中。
- AI 创建、草案、正式计划、提案、Practice Chat 不得使用计划 `goal`。
- `goal` 只允许出现在模板领域、模板 seed/API 和明确的历史设计文档。
- `difficultyPreference` 只允许出现在模板领域、模板 seed/API 和模板到分布的唯一 mapper。
- `LearningPlanDifficultyPreference` 类型因模板仍在使用，不删除。
- 不新增学习计划个性化 Tool、上下文表、迁移或旧 JSON fallback。
