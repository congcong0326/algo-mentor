# 学习画像 Review 全量事实快照与按需取证设计

## 文档信息

- 设计日期：2026-08-18
- 文档性质：增量优化设计，待评审
- 适用项目：`algo-mentor`
- 前置设计：`docs/ai-memory-system-claim-evidence-redesign.md`
- 当前实现入口：`LearnerMemoryCodeReviewUpdateService`、`LearnerMemoryCodeReviewPromptBuilder`

## 一、背景与结论

当前 Code Review 画像更新会为每个题目选择最新正式 Review，形成最多十道不同题目的横向窗口，再将该窗口渲染给模型。这个策略适合回答“用户当前是否能完成这类题”，但会遮蔽同题较早版本的功能性失败和修正过程。

预发布验证用户的实际数据说明了这一偏差：

| 指标 | 实际值 |
| --- | ---: |
| 正式 Review 总数 | 9 |
| 通过 / 失败 | 6 / 3 |
| 覆盖题目数 | 5 |
| 各题最新版本通过数 | 5 / 5 |
| 各题首次版本通过数 | 3 / 5 |
| 功能性失败后已修正数 | 3 |
| 未修正功能性失败数 | 0 |

现有模型只看到五条最新通过 Review，因此会合理但片面地输出“未出现功能性实现问题”“波动主要是命名或格式问题”。该问题不是模型自行忽略事实，而是模型输入没有携带完整的失败、修正和样本期限信息。

本设计的结论是：

> 所有正式 Review 必须参与画像事实计算；模型默认读取服务端生成的紧凑事实快照，而不是全量原始 Review 或源码。只有判断存在歧义时，模型才按当前用户、当前批次和调用预算受限地读取具体 Review 证据或版本差异。

## 二、目标与非目标

### 2.1 目标

- 同时准确表达当前能力、首次可靠性、已修正挑战和未解决风险。
- 让失败记录参与总结，但不把已经修正的一次性失误永久固化为“弱点”。
- 保持模型输入有界，避免随着用户历史增长而把全部 Review 正文或代码注入 Prompt。
- 让“快速”“稳定”“主要是格式问题”等强措辞能够由确定性事实校验。
- 保留 claim 句子级引用和具体 Review 的可追溯性。
- 支持模型在必要时查看指定 Review 的详细证据或失败版本与修正版本的差异。

### 2.2 非目标

- 不改变正式 Code Review 的评分、通过门槛和题目完成语义。
- 不允许后台画像 Agent 默认读取全部原始代码。
- 不把所有历史失败都视为当前风险，也不建设“错题清单式”画像。
- 不让模型计算通过率、推断版本顺序或自行判断记录是否已经修正。
- 不变更固定每用户积累五条正式 Review 后触发一次后台更新的队列语义。

## 三、产品语义

画像不再把“优点”与“弱点”视为唯一的二元结果，而是区分以下三类判断：

| 判断类型 | 用户可见含义 | 形成条件 |
| --- | --- | --- |
| 当前掌握 | 当前最新提交在某类题中的表现 | 每题最新版本、跨题聚合 |
| 已修正挑战 | 曾经出现功能性问题，后续版本已修正 | 同题失败到通过的受信轨迹 |
| 仍需关注 | 当前仍失败、或跨题重复且尚未修正的问题 | 最新失败或重复未解决证据 |

“已修正挑战”是本设计新增的关键语义。它既避免隐藏学习过程，也避免把已经解决的问题贴成长期负面标签。

对于本次样本，合理结论应为：

```text
当前掌握：5 道数组题的最新版本均高分通过。
已修正挑战：合并边界、元素保留方向和慢指针维护曾造成错误，后续版本均已修正。
仍需关注：当前没有未修正的功能性错误；需要继续在新题中验证首次建模与边界覆盖的稳定性。
```

## 四、目标架构

```text
正式 Code Review（全部历史）
        |
        v
Review Fact Snapshot Builder（服务端确定性聚合）
        |
        +-- 全量统计、首次可靠性、最新表现
        +-- 每题轨迹、已修正 / 未修正状态
        +-- 标签聚合、样本时间跨度
        |
        v
Code Review Profile Agent
        |
        +-- 默认：读取紧凑事实快照
        +-- 按需：读取 Review 证据 / 版本差异
        |
        v
结构化 claim 操作 + evidence roles
        |
        v
服务端语义校验、原子写入、文档投影与引用
```

`Review Fact Snapshot Builder` 是本设计的核心边界：数据库事实、版本排序、统计计算和状态判断全部在服务端完成；模型只负责判断哪些事实值得形成画像，以及如何以克制、准确的自然语言表达。

## 五、事实快照契约

### 5.1 基本原则

1. 所有属于当前用户的正式 Review 都参与全量统计和每题轨迹计算。
2. 默认 Prompt 不包含原始代码、完整 Review Markdown 或无限历史记录。
3. 单题轨迹保留完整状态结论；较长历史可将早期版本压缩为计数和问题类别，最近版本保留可读摘要。
4. 所有数字、题目状态、失败类别和时间范围由服务端给出，模型不得改写其含义。
5. 快照必须注明观察期和样本规模。短期样本不得支撑“长期稳定”“通常快速”等结论。

### 5.2 推荐领域对象

应用层新增不可变的 `LearnerReviewFactSnapshot`，由 `LearnerReviewFactSnapshotBuilder` 构建。其关键字段如下：

```text
coverage
  reviewCount
  distinctProblemCount
  earliestReviewAt / latestReviewAt
  historyDepth: EARLY_SAMPLE | ESTABLISHED

overall
  passedReviewCount / failedReviewCount
  latestByProblem: passedCount, totalCount, averageScore
  firstAttemptByProblem: passedCount, totalCount
  functionalFailureCount, recoveredFailureCount, unresolvedFailureCount

problemTrajectories[]
  problemSlug, tagIds
  attempts[]: reviewId, versionNo, passed, score, normalizedFindingSummary
  currentStatus: PASSED_FIRST_ATTEMPT | RECOVERED | RECOVERED_AFTER_REGRESSION | ACTIVE_RISK

tagFacts[]
  tagId, problemCount, reviewCount
  latestPassedCount / firstAttemptPassedCount
  functionalFailureCount / recoveredFailureCount / unresolvedFailureCount
```

`normalizedFindingSummary` 由服务端从正式 Review 的扣分原因、改进建议和受控分类字段构造，禁止直接放入用户源代码、完整聊天消息或模型长篇 Markdown。

### 5.3 本次预发布样本

本次用户的完整快照应至少表达下列事实：

| 题目 | 轨迹 | 当前状态 |
| --- | --- | --- |
| `merge-sorted-array` | v1 失败：遗漏剩余元素边界；v2 通过 | `RECOVERED` |
| `remove-element` | v1 失败：保留方向与题意相反；v2 通过 | `RECOVERED` |
| `remove-duplicates-from-sorted-array` | v1 通过；v2 失败：slow 指针维护错误；v3 通过 | `RECOVERED_AFTER_REGRESSION` |
| `remove-duplicates-from-sorted-array-ii` | v1 通过 | `PASSED_FIRST_ATTEMPT` |
| `majority-element` | v1 通过，仅命名和冗余判断建议 | `PASSED_FIRST_ATTEMPT` |

数组标签事实为：最新 `5/5` 通过、首次 `3/5` 通过、历史功能性失败 `3` 次且均已修正。双指针标签事实为：最新 `4/4` 通过、首次 `2/4` 通过、历史功能性失败 `3` 次且均已修正。

## 六、Agent 输入与输出

### 6.1 Prompt 输入

Prompt 由三部分组成：

1. 受管理系统提示：说明三类判断的定义、禁止过强措辞和证据规则。
2. 服务端事实快照：包含第五章的结构化、只读事实。
3. 当前 ACTIVE claim、允许 scope、容量和快照令牌：维持现有原子更新及并发语义。

系统提示必须明确：

- “当前掌握”不能掩盖历史功能性失败；如果存在已修正失败，应按“已修正挑战”描述或保持不作结论。
- “仍需关注”只能来自未修正或跨题持续的问题，不能由一次已修正失败生成。
- `快速`、`通常`、`持续`、`稳定`、`主要是非功能性问题`等词只能在快照给出相应门槛满足标记时使用。
- 样本为 `EARLY_SAMPLE` 时，只能使用“本窗口”“当前已覆盖题目”等限定表达。

### 6.2 结构化输出

第一阶段不要求修改 claim 主表；模型在现有 GENERAL_OBSERVATION 与 TAG_ASSESSMENT scope 内输出以下 `observationType`，该字段先作为 Agent schema 与校验字段：

| `observationType` | 允许 scope | 说明 |
| --- | --- | --- |
| `CURRENT_STRENGTH` | 通用观察、标签评价 | 当前最新表现的正向判断 |
| `RECOVERED_CHALLENGE` | `REVIEW_AND_GROWTH_PERFORMANCE`、必要时标签评价 | 失败到修正的成长轨迹 |
| `ACTIVE_RISK` | 实现错误模式、标签评价 | 当前或反复未修正的问题 |

如果第二阶段需要在用户界面中按三类明确分组，再将 `observation_type` 作为受控枚举持久化到 `learner_memory_claim_revision`；第一阶段仍可依靠 scope 与受控文本投影兼容现有存储。

### 6.3 文案规则

| 条件 | 允许的表达 | 禁止的表达 |
| --- | --- | --- |
| 最新版本全部通过、历史存在已修正功能性失败 | “当前最新版本表现稳定，曾在 X 上出错后修正” | “未出现功能性问题”“波动只有格式问题” |
| 首次通过率不足约定门槛 | “首次建模仍需在新题中验证” | “通常能快速识别” |
| 样本跨度短 | “本窗口”“已覆盖题目” | “长期稳定”“一贯” |
| 存在未修正失败 | “仍需关注 X” | 将其只归为“已修正挑战” |

具体阈值应配置化并在产品评审中确认，不能由模型自定义。初版可先采用保守策略：只要有效回看期内存在功能性失败，就禁止“仅非功能性问题”的结论。

## 七、按需查看具体提交

### 7.1 三层证据读取

| 层级 | 默认可见性 | 内容 | 适用场景 |
| --- | --- | --- | --- |
| 事实快照 | 始终注入 | 统计、轨迹、受控失败摘要 | 绝大多数画像判断 |
| Review 证据 | 按需工具 | 扣分原因、改进建议、上下文摘要、受控检测证据 | 判断两次失败是否同类、确认是否已修正 |
| 版本差异 / 归一化代码 | 极少数按需工具 | 失败版与修正版的最小差异 | Review 摘要不足以判断算法根因时 |

模型在本次样本中无需读取全部代码。若准备声明“用户有跨题的双指针不变量短板”，应先比较 `merge-sorted-array`、`remove-element` 和 `remove-duplicates-from-sorted-array` 的失败到修正轨迹；若三者根因并不一致，则不得形成统一长期弱点。

### 7.2 工具边界

- 只能读取事实快照已授权的当前用户 Review ID。
- 先调用轨迹或 Review evidence 工具；仅在不足以判定时调用版本比较或代码详情工具。
- 每个画像更新 run 最多 3 次详情类调用，单次和总字符预算沿用 Agent Tool Catalog 的受控限制。
- 读取结果仅用于当前 run，必须写入 Agent 审计；claim 只能引用实际读取或快照中已声明的 Review。
- 画像文档的引用抽屉默认展示低敏 Review 摘要和跳转链接，不展示原始代码。

## 八、证据校验调整

现有 `CROSS_PROBLEM_RECURRENCE` 只验证不同题目数量，不能表达“多个失败均已修正”。需要补充以下规则：

1. `CURRENT_STRENGTH` 的 evidence 对每个引用题目必须是该题的最新正式 Review，且状态为通过。
2. `RECOVERED_CHALLENGE` 必须至少引用一个同题的失败版本和其后续通过版本；若声称跨题恢复，至少两道题各有完整失败到通过轨迹。
3. `ACTIVE_RISK` 必须满足：引用最新版本失败，或同类功能性问题在至少两道不同题中仍未被修正。
4. 存在功能性失败时，`CURRENT_STRENGTH` 不得使用“没有功能性问题”“仅格式问题”等排他性语句。

第二阶段新增 `CROSS_PROBLEM_RECOVERY` evidence pattern，并由服务端校验每个参与题目均包含有序的失败和通过版本。该变更涉及 `LearnerMemoryEvidenceContract`、数据库 check constraint、结构化输出 schema、Prompt 和测试，必须作为一次完整跨模块契约迁移完成。

## 九、用户侧投影

第一阶段保持 `/api/me/learner-profile` 的连续文档形态，但按章节优先展示：

1. 当前掌握。
2. 已修正挑战。
3. 仍需关注。

每条句子仍使用现有 citation 编号。对于恢复类 claim，证据抽屉按时间顺序展示“失败版本 -> 修正版本”，使用户能理解结论不是凭空产生的。

第二阶段若产品确认需要显式分组，再基于持久化的 `observation_type` 输出稳定分组。不得通过前端关键词猜测 claim 类型。

## 十、实施计划

### 阶段 A：事实快照与保守文案

- 新增 `LearnerReviewFactSnapshot`、`ProblemReviewTrajectory`、`TagReviewFacts` 等应用层模型和 builder。
- 扩展 Code Review 历史查询，批量读取当前用户的 Review 元数据、版本、评分、标签和受控摘要。
- 将 `LearnerMemoryCodeReviewUpdateService` 的模型输入由“仅最新横向窗口”改为“当前窗口 + 事实快照”。
- 更新 `LearnerMemoryCodeReviewPromptBuilder` 与系统提示，加入样本期限、历史失败和修正规则。
- 修改输出 schema，增加不持久化的 `observationType` 并在 mapper 中校验。
- 不新增数据库表或迁移；当前 Agent run 的输入消息与 claim evidence 继续承担审计和追溯。

### 阶段 B：恢复类证据协议

- 新增 `CROSS_PROBLEM_RECOVERY` pattern、相应 Review role 序列与服务端 validator。
- 为跨题恢复 claim 建立严格的题目级失败到通过校验。
- 必要时增加 `observation_type` 持久化字段及文档投影支持。
- 为失败、修正、反复失败和当前风险分别补充 schema 测试与 PostgreSQL 迁移测试。

### 阶段 C：按需证据探索与界面

- 保持事实快照默认输入，接入/强化 Review evidence、trajectory 和版本比较工具的授权范围与预算。
- 恢复类 citation 抽屉展示有序轨迹和“已修正”状态。
- 若持久化类型已完成，前端将三类判断展示为稳定分组，不改变句子级引用和跳转。

## 十一、测试与验收

### 11.1 单元测试

- 快照 builder 对 9 条样本计算出 `6/3` 总通过失败、`5/5` 最新通过、`3/5` 首次通过和 `3/0` 已修正/未修正功能性失败。
- 同题 `FAILED -> PASSED` 被识别为 `RECOVERED`；`PASSED -> FAILED -> PASSED` 被识别为 `RECOVERED_AFTER_REGRESSION`。
- 标签统计不把同题多版本误计为多个题目。
- 模型输出含历史功能性失败时，禁止生成“仅格式/命名波动”等排他性文案。
- `ACTIVE_RISK` 和 `RECOVERED_CHALLENGE` 的 evidence 结构不满足时被服务端拒绝。

### 11.2 集成测试

- 构造本次预发布的 9 条 Review 数据，验证模型输入同时包含全量统计和五条题目轨迹。
- 验证 claim 的引用能跳转到失败版和修正版，且只属于当前用户。
- 验证详情工具不能读取快照范围外的 Review、其他用户 Review 或无授权代码。
- 验证历史增长到大样本时，Prompt 大小受限且全量统计不丢失。

### 11.3 验收文案

针对本次样本，最终画像必须能表达以下事实，且不得产生相反结论：

```text
当前数组与双指针题的最新提交均已通过；
历史上出现过三次功能性错误，均在后续版本完成修正；
当前没有未修正的功能性错误；
样本集中在两天内，不能将此写成长期能力定论。
```

## 十二、观测与发布

新增或扩展以下指标：

| 指标 | 含义 |
| --- | --- |
| `learner_memory.review_snapshot.review_count` | 每次快照参与的 Review 数量分布 |
| `learner_memory.review_snapshot.recovered_count` | 已修正功能性失败数分布 |
| `learner_memory.review_snapshot.unresolved_count` | 未修正功能性失败数分布 |
| `learner_memory.profile.detail_tool_calls` | 按需详情工具调用数和结果 |
| `learner_memory.profile.claim_type` | 三类判断的生成、拒绝和替换数量 |
| `learner_memory.profile.language_guard_rejected` | 因强措辞与事实冲突被拒绝的次数 |

发布顺序：

1. 先在预发布以只读方式生成快照并记录与当前画像的差异，不写入 claim。
2. 使用本次样本和更多真实样本人工评审文案，确认“当前掌握 / 已修正挑战 / 仍需关注”边界。
3. 开启阶段 A 写入，保留现有引用和回滚能力。
4. 观察模型成本、详情工具调用、非法输出和用户反馈后，再实施阶段 B、C。

## 十三、待确认决策

- “首次通过率不足”与“稳定”的具体阈值应由产品确定，还是首期仅使用保守语言规则？
- 有效回看期采用全量历史统计 + 最近 N 条详情，还是引入时间衰减？
- `RECOVERED_CHALLENGE` 是否在默认画像中始终展示，还是仅在近期或与当前题目相关时展示？
- 第二阶段是否持久化 `observation_type` 并在前端显式分组？
- 版本比较工具是否允许向后台画像 Agent 返回受限归一化代码，还是只返回结构化 diff 摘要？
