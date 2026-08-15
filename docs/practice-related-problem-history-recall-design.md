# Practice Chat 跨会话代码提交历史召回设计

## 文档状态

- 状态：阶段一实施基线；阶段二 Tool 仅保留演进设计
- 适用场景：`PRACTICE_CHAT`、Practice Code Review
- 关联设计：`practice-chat-agent-design.md`、`practice-chat-system-prompt-assembly-design.md`、`practice-code-review-technical-design.md`
- 首期事实来源：`practice_code_review`

## 1. 背景

Practice Chat 的跨会话辅导，应首先建立在用户实际提交过的代码上。用户在一道题上的多次正式 Code Review，包含代码、评测结论和关键反馈，能够反映其尝试、修正和最终通过的过程；这比只依据题目完成状态或普通聊天文本更可靠。

题目关联关系仍然有价值，但它只回答“哪些历史提交与当前题可能相关”，不再作为用户历史读取的业务边界。历史数据的主语固定为“当前用户在某一道题上的正式代码提交”。

当前关键链路已经提供接入位置：

```text
PracticeSessionService
  -> 创建或恢复练习会话、写入题面 seed

AgentConversationService
  -> 每轮读取当前题、计划、对话历史和运行时上下文
  -> 组装 PromptAssembly
  -> 启动 Practice Chat Agent run

submit_practice_code_review
  -> 启动 Practice Code Review 子 Agent
  -> 持久化一条正式代码 Review
```

因此，跨会话代码提交历史不写入题面 seed，也不伪装成普通 assistant 历史消息。它应在每轮作为用户级运行时上下文注入；需要更多细节时，再由同一 Agent run 内的只读 Tool 按需读取。

## 2. 目标与非目标

### 2.1 目标

1. 以低 token 成本让模型知道用户曾在哪些题提交过正式代码。
2. 高亮当前题的关联题中、用户确实有正式提交记录的题目。
3. 用一条紧凑摘要保留同题多次 Review 的关键过程，避免只展示最终满分版本而丢失中间修正。
4. 只有在用户明确询问、需要历史类比，或有高置信度卡住信号时，才按需读取某题总览、提交历史或代码详情。
5. 所有数据读取绑定当前用户；跨计划汇总同一题的提交历程，但不影响当前计划的完成判断。

### 2.2 非目标

- 不在每轮 Prompt 注入代码正文、完整 Review Markdown、完整聊天历史或笔记正文。
- 不把普通聊天中的代码片段当作正式提交事实。
- 不在 Prompt 组装时发起额外 LLM 请求来回填或概括历史数据。
- 不把 `LEETCODE_SIMILAR` 解读为前置关系、更简单版本或相同解法保证。
- 不让模型使用任意 `userId`、`sessionId`、`problemSlug` 或 Review ID 查询历史。

## 3. 正式提交与跨计划语义

### 3.1 正式提交事实

首期“用户提交过代码”严格指 `practice_code_review` 中成功持久化的正式 Review：它对应当前题的完整代码提交，并持久化代码语言、评测结论和 Review 反馈。代码片段、伪代码、错误日志和未形成正式 Review 的聊天消息不进入本设计的历史索引。

### 3.2 同题历史跨计划聚合

同一用户、同一 `problemSlug` 的正式 Review 组成一条题目级提交历程，无论它们来自哪个学习计划、阶段或 practice session。用户在计划 A 首次失败、计划 B 重做后通过，这两次尝试都应参与跨会话辅导。

历史窗口使用真实提交顺序：

```text
WHERE user_id = :userId
  AND problem_slug = :problemSlug
ORDER BY created_at DESC, id DESC
LIMIT :historyLimit
```

查询得到最近窗口后，按 `created_at ASC, id ASC` 交给轨迹计算和 Code Review Prompt。`versionNo` 只在一个 practice session 内有序，不能用于跨计划排序，也不能在跨计划摘要中称为全局“第 N 版”。摘要应使用“此前两次提交”等时间语义。

`planId`、`phaseIndex` 和 `sessionId` 继续服务于当前计划进度、完成 gate、前端历史页和审计追溯，但不参与跨会话历史摘要的筛选。未来若题目内容发生破坏性变更，应引入题目内容版本作为边界，不能用 `planId` 替代。

## 4. 每轮 Prompt 注入

### 4.1 两组最小索引

每轮注入两组题目索引。它们只负责发现历史和定位相关题，不提前提供完整分析结论：

```text
用户曾正式提交代码的题目
  -> 与当前题相关、且用户曾正式提交代码的题目
```

第二组是用户全部正式提交历史中的关联子集，不受第一组最近题目窗口限制。两组中出现同一道题时可以重复：第一组提供最近提交目录，第二组明确指出哪些经历可作为当前题的迁移线索。

每个条目只保留以下字段：

```text
problemRef
title
tags
reviewHistorySummary（可选）
```

其中：

- `problemRef` 是本次 run 签发的 opaque ref，供模型后续精确读取该题；不暴露或依赖裸 `problemSlug`。
- `title` 让模型能识别并自然提及题目。
- `tags` 提供最小算法语义，帮助模型判断是否值得使用这段历史。
- `reviewHistorySummary` 是该题最新正式 Review 上的近期 Review 历程摘要；旧数据没有该字段时省略，不臆造。

第一版不注入难度、提交次数、提交时间、语言、分数、是否通过、扣分原因、改进建议、关联类型或关联来源。这些信息各自要么不足以改变是否查历史的决定，要么会让模型在用户尚未需要时带着结论辅导；需要时由后续 Tool 读取。

示例：

```text
你曾正式提交代码的题目（仅作为可查询索引，不代表已掌握）：
- [pp_a8K2] Subarray Sum Equals K：Prefix Sum、Hash Table
  提交历程摘要：此前两次提交遗漏空前缀初始化；当前版本补齐初始化，Review 已通过。

与当前题相关的历史代码提交（仅作为迁移线索）：
- [pp_a8K2] Subarray Sum Equals K：Prefix Sum、Hash Table
  提交历程摘要：此前两次提交遗漏空前缀初始化；当前版本补齐初始化，Review 已通过。
```

不应枚举用户的全部历史题目。首期固定选择规则如下：

| 索引 | 数据范围 | 排序 | 容量 |
| --- | --- | --- | --- |
| 用户最近正式提交题目 | 当前用户全部正式 Review，每题仅取最新一条 | 最新 Review 的 `created_at DESC, id DESC` | 最多 5 道不同题目 |
| 当前题关联的历史提交题目 | 当前题已解析关联题与当前用户全部正式 Review 的交集，每题仅取最新一条 | 最新 Review 的 `created_at DESC, id DESC` | 最多 3 道不同题目 |

第二组的题目即使不在第一组最近 5 道中，仍必须注入。两组均可使用同一条最新 Review 的 `reviewHistorySummary`；出现重复条目时，保持相同内容。

### 4.2 Section 边界

两组索引是用户级动态上下文，均使用 `RUNTIME_CONTEXT`、`SERVER_VALIDATED` 和 `NO_CACHE`。建议 section 顺序为：

```text
practice.context.training
practice.context.submitted-problems
practice.context.related-submitted-problems
```

Practice Chat 的消息顺序为：

```text
system: STATIC_INSTRUCTION
system: SCENARIO_POLICY
system: RUNTIME_CONTEXT / practice.context.training
system: RUNTIME_CONTEXT / practice.context.submitted-problems
system: RUNTIME_CONTEXT / practice.context.related-submitted-problems
system: MEMORY_SUMMARY（可选）
history: HISTORY
user: CURRENT_USER_MESSAGE
```

这两组索引不进入 managed system prompt 定义、profile 快照或 session seed，也不能修改当前题的权威事实。它们从动态 section 开始变化，不影响稳定 system/scenario 前缀的跨会话缓存复用。

### 4.3 Phase 1 读取边界

新增 `PracticeSubmissionHistoryContextProvider` 作为 Phase 1 唯一的 Prompt 索引读取边界。它接收当前用户、当前题 slug 和 locale，返回两组已裁剪条目；其中 Review 历史通过批量查询读取，题名和标签通过题库目录补齐。

`AgentConversationService.assemblePracticeChatContext` 必须在 `PromptAssembly` 前调用该 provider，将结果作为独立变量交给 `PracticeChatPromptSectionProvider` 渲染。provider 自身不读取代码正文、完整 Review Markdown 或聊天内容，也不注册任何历史读取 Tool。

Phase 1 的 `problemRef` 是本次 Prompt 组装内的 opaque 标识，仅用于固定条目形状；当前阶段不提供解析该 ref 的 Tool。Phase 2 开放 Tool 时，必须以同一 provider 的受信结果签发可解析的 run-local capability，不能信任模型自行构造的值。

## 5. `reviewHistorySummary`

### 5.1 字段语义

`reviewHistorySummary` 是“截至本次正式 Review，该用户在该题最近 Review 历程的精简摘要”。它既要说明过去关键问题，也要说明当前提交的方案或结果；它不是单次代码摘要，也不是对用户能力的长期判断。

示例：

```text
此前两次提交分别遗漏空前缀初始化和复杂度控制；当前版本使用前缀和与哈希表补齐了初始化，Review 已通过。
```

字段约束：

- 使用一到两句受控的、面向学习者的文本。
- 只陈述代码和 Review 可验证的事实，不写“已经掌握”“一直不擅长”等能力判断。
- 不包含代码正文、行号、完整 Review Markdown、完整聊天内容或无关个人信息。
- 首次正式 Review 没有历史时，只概括当前提交的核心方案和 Review 结论。
- 它表示“最近 Review 历程”，而非无限长的完整生涯；当前窗口最多覆盖五次 Review。

### 5.2 存储与读取

字段存储在每条 `practice_code_review` 记录上，代表该记录生成时的历史快照。Flyway 迁移新增可空的 `review_history_summary TEXT NULL`，以兼容既有 Review；新产生的正式 Review 必须写入非空摘要。跨会话索引查询该题最新正式 Review 时，读取其 `reviewHistorySummary`。第一版不新建用户-题目总览表，也不为既有 Review 发起额外模型调用进行回填；既有记录缺少该字段时，索引只展示 `problemRef`、题名和标签。

现有 `contextSummary` 不复用为该字段。它没有“同题近期 Review 历程”的稳定语义和长度约束；新字段必须拥有独立的 structured output、领域模型和数据库契约。

## 6. Code Review 生成时序

### 6.1 有界历史读取

`PracticeCodeReviewService` 必须先完成现有的同一用户消息幂等查询。仅当没有既有 Review、即将启动子 Agent 时，才按当前用户和当前题读取最近最多四条历史正式 Review。重放或幂等命中直接返回已保存 Review，不重新读取历史，也不重新生成摘要。

历史读取由 `PracticeCodeReviewService` 单点拥有，并将结果装入扩展后的 `PracticeCodeReviewAgentInput`；`PracticeCodeReviewAgentTool` 只负责构造不含历史的基础调用，不得自行读取或拼接历史。加上本次待评审提交，摘要窗口最多五条，与现有 `ReviewTrajectoryService` 的窗口边界一致。

这是一条索引查询，不是 N+1 读取。查询结果按 `created_at ASC, id ASC` 重新排列后，以如下固定形状注入 Code Review Prompt：

```text
historicalReviews:
- historyPosition: 1
  passed: false
  primaryFinding: 前缀和的初始计数缺失
```

`historyPosition` 只是当前窗口内的时间顺序，不对应跨计划版本号。`primaryFinding` 固定取该 Review 的第一条非空 `deductionReason`；没有扣分原因时取第一条非空 `improvementSuggestion`；两者都没有时省略。历史输入不包含旧代码、`normalizedCode`、完整 Review Markdown、笔记正文、聊天内容、分数、计划或 session 信息。

```text
一次 Code Review
  = 原有一次 Code Review LLM 请求
  + 一次 userId + problemSlug 的 LIMIT 4 窄查询
  + 有界的历史事实 Prompt 输入
```

历史读取失败不能阻断当前 Code Review。此时仍完成当前代码的正式 Review，`reviewHistorySummary` 只描述当前提交，不声称拥有历史信息。

### 6.2 同一次 LLM 输出

Practice Code Review 的 provider-native structured output 新增 `reviewHistorySummary`。子 Agent 在已有的当前代码、题面和受控历史事实基础上，与评分、评测结论和反馈在同一次请求中生成该字段；不增加第二次模型调用。

该字段在 JSON Schema、领域校验和数据库写入中统一限制为最多 200 个字符。输出 Mapper 必须规范化空白；摘要为空或超过限制时，不得使正式 Code Review 失败，而是根据本次已经归一化的 `passed` 和 `primaryFinding` 生成保守降级文本。降级文本只能陈述本次 Review 结论，不得声称读取过历史。

实现需要同步更新：

- `PracticeCodeReviewJsonSchema` 和 schema version；
- `PracticeCodeReviewStructuredOutputMapper`；
- `PracticeCodeReviewDraft`、`PracticeCodeReview` 与持久化行模型；
- Flyway 迁移中的可空 `practice_code_review.review_history_summary` 列；
- Code Review Prompt 的输出约束和历史事实输入；
- 读取近期题目 Review 的窄查询和 mapper。

现有 `ReviewTrajectoryService` 可复用来计算最近五条 Review 的持续、已解决和新增问题，但不应把其原始 JSON 直接注入模型。Prompt 只提供生成摘要所需的有界事实。

## 7. 后续按需 Tool 演进（不属于 Phase 1）

初始 Prompt 只提供索引。模型需要具体历史事实时，后续 Tool 使用通用的题目引用，而不是 `relatedProblemRef` 专用接口：

```text
get_practiced_problem_overview(problemRef)
list_practice_problem_submissions(problemRef, cursor, limit)
read_practice_submission_detail(submissionRef)
```

前两个能力分别读取某题的受限总览和提交历史。第三个能力只在用户明确要求查看旧代码、比较版本或进行代码级复盘时开放；提交历史列表本身不携带代码正文。若未来需要在未注入索引的历史题中检索，再单独设计 `search_submitted_problems`，第一版不开放任意 slug 查询。

`problemRef` 与未来的 `submissionRef` 都是 run-local opaque capability。服务端从可信执行上下文取得用户，并在每次读取时验证该引用属于当前用户。题目关联关系只影响关联索引的候选生成，不扩大 Tool 的用户数据读取范围。

## 8. 模型使用规则

模型必须遵守：

```text
历史提交仅用于迁移学习，不是当前题答案。
题目出现在索引中只说明存在正式提交，不代表用户已掌握。
优先让用户解释当前题思路；用户明确询问、需要类比或高置信度卡住时，再引用摘要或读取详情。
不得在用户未要求时展示完整旧代码。
不得把关联关系解释为必然相同解法。
```

当 `reviewHistorySummary` 可用时，模型应只取其中一条与当前题相关的事实转化为下一步提示，不原样复述索引。例如：

```text
你之前在前缀和题中曾漏掉初始状态。先检查当前解法在处理第一个元素前，是否也需要显式定义一个初始计数。
```

## 9. 成本、安全与可观测性

- 每轮索引使用受控字段，不读取代码正文；候选数遵从 Prompt 预算。
- 每次 Code Review 至多读取四条历史窄行，加上当前提交总窗口最多五条。
- 不把用户级索引放入 `CACHEABLE_STATIC` 或 `CACHEABLE_BY_PROFILE`。
- 静态题目关系可以按题目缓存；用户 Review 历史不得使用跨用户缓存。
- 所有历史查询以 `userId` 为首个范围条件；不记录完整题目列表、摘要正文、代码正文或自由文本反馈到低基数 metadata。
- 历史读取或摘要生成的降级不得影响当前题普通聊天和 Code Review 的完成。

建议记录：

```text
submissionProblemIndexCount
relatedSubmissionIndexCount
reviewHistorySummaryAvailableCount
reviewHistoryReadCount
reviewHistoryReadFailure
reviewHistorySummaryFallback
```

## 10. 测试与验收

### 10.1 Code Review 摘要

- 首次正式 Review 生成仅描述当前提交的摘要。
- 同题存在多次 Review 时，摘要保留过去关键问题和当前结论。
- 通过的最终版本不会抹掉最近窗口中可验证的中间问题。
- 摘要不包含代码正文、完整 Markdown、能力推断或未受信内容。
- 历史读取失败时仍保存当前 Review，并生成不声称历史的降级摘要。
- 幂等重试命中既有 Review 时，不查询历史，也不重新执行子 Agent。

### 10.2 跨计划顺序

- 同一用户、同一题在多个计划中的 Review 全部进入历史窗口。
- 跨计划 `versionNo` 重复或倒序时，仍严格按 `created_at`、`id` 排序。
- 当前计划完成 gate 仍只使用既有计划和 session 范围，不能因跨计划摘要改变。

### 10.3 Phase 1 Prompt 索引

- 两组索引条目只包含 `problemRef`、题名、标签和可选摘要。
- 最近提交组按每题最新 Review 的 `created_at DESC, id DESC` 选择最多 5 道不同题目。
- 关联提交组从全部用户正式提交历史中选择，不能因不在最近提交组而遗漏；最多 3 道不同题目。
- 没有正式 Review 的题目不进入“已正式提交代码”索引。
- 关联组只包含当前题关联范围内、且用户存在正式 Review 的题目。
- 索引不改变稳定 system section 的 content hash，也不会跨用户命中用户状态缓存。

### 10.4 后续 Tool 验收（不属于 Phase 1）

- Phase 2 必须拒绝伪造、跨用户或过期的 `problemRef`、`submissionRef`。
- 提交历史列表不返回代码正文；代码详情只在明确代码级复盘时开放。

## 11. 分阶段发布

### 阶段一：Review 历程摘要与 Prompt 索引

- 新增 `reviewHistorySummary` structured output 和持久化字段。
- Code Review 前读取最近四条同题、跨计划历史窄行。
- 修正同题历史查询为按 `created_at`、`id` 排序。
- 旧数据不回填，字段缺失时安全省略。
- 注入用户正式提交题目索引和关联提交题目索引。
- 固定最近提交组最多 5 道、关联提交组最多 3 道，以及各自的时间排序。
- 条目仅使用本设计固定字段，不注册历史读取 Tool。
- 关联题索引只作为迁移线索，不自动展开详情。

### 阶段二：通用只读 Tool

- 提供题目总览和提交历史读取。
- 用户明确要求代码级复盘时，再提供提交详情或有界 diff。

## 12. 待确认事项

1. 关联题候选是否同时包含反向关系；不影响跨计划 Review 历程语义。
2. 通用 Tool 的首期开放开关和灰度策略。
