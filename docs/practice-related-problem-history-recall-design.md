# Practice Chat 跨会话代码提交历史召回设计

## 文档状态

- 状态：阶段一与阶段二历史提交 Tool 均已实施；阶段二总开关和源码详情二级开关默认开启，可按环境变量关闭
- 适用场景：`PRACTICE_CHAT`、Practice Code Review
- 关联设计：`practice-chat-agent-design.md`、`practice-chat-system-prompt-assembly-design.md`、`practice-code-review-technical-design.md`、`agent-run-tool-result-compaction-design.md`、`agent-tool-catalog.md`
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

## 7. 阶段二：历史提交按需读取 Tool

### 7.1 范围与闭环

Phase 1 的索引只负责发现历史题。用户继续追问“之前错在哪里”“我是否真的修复过”“把那版代码拿来和当前思路比较”时，模型不能从摘要臆测，也不能把 `pp_*` 当作数据库键。本阶段为同一 Agent run 增加受限的只读展开能力：

```text
Prompt 历史题索引
  -> get_practiced_problem_overview
  -> list_practice_problem_submissions
  -> read_practice_submission_detail（仅明确代码级请求）
  -> 基于受信事实给出当前题的迁移提示
  -> submit_practice_code_review 写入新的正式 Review
  -> 下一轮重新生成索引和 reviewHistorySummary
```

三个 Tool 都只读取 `practice_code_review` 的正式 Review。它们不会创建历史记录、更新学习画像、改变当前计划进度或替代 `submit_practice_code_review`。普通聊天中的代码片段、未保存的草稿和其他会话的自由文本始终不属于本阶段的数据源。

工具名称与职责固定如下：

| Tool | 目的 | 是否返回代码 |
| --- | --- | --- |
| `get_practiced_problem_overview` | 确认一题的正式提交规模和最新结论，决定是否继续展开 | 否 |
| `list_practice_problem_submissions` | 分页读取某题各次正式提交的结构化 Review 时间线 | 否 |
| `read_practice_submission_detail` | 展开单次提交的评审代码和受限 Review 事实 | 是，需明确意图 |

本阶段不实现 `search_submitted_problems`、任意 slug 查询、任意 Review ID 查询或双历史版本 diff。用户需要比较当前消息中的代码和一条旧提交时，第三个 Tool 已足够；两个历史提交的受控 diff 是独立的高敏感度需求，后续单独设计，不向第三个 Tool 增加第二个 `submissionRef`。

### 7.2 公共协议与失败语义

所有 Tool 使用 JSON object 入参，`additionalProperties=false`，不接受 `userId`、`problemSlug`、`reviewId`、`planId`、`phaseIndex` 或 `sessionId`。身份、当前 run、locale 和 capability scope 均从 `AgentExecutionContext.requestMetadata` 的服务端受信 metadata 取得。

成功结果的 `status` 固定为 `OK`。各 Tool 可返回以下不含用户数据的失败状态：

| 状态 | 含义 | 适用 Tool |
| --- | --- | --- |
| `UNAVAILABLE` | ref、cursor 或 scope 伪造、跨用户、跨 run、过期或已释放；统一处理，不泄露资源是否存在 | 全部 |
| `BUDGET_EXHAUSTED` | 当前 run 的历史读取或代码可见字符预算已用尽 | 全部 |
| `USER_INTENT_REQUIRED` | 当前用户消息没有明确要求查看旧代码或代码级复盘 | `read_practice_submission_detail` |
| `FAILED` | 参数格式错误或受控的数据访问失败；仅可给出稳定 `failureCode`，不得包含 SQL、slug、Review ID 或正文 | 全部 |

模型不能通过失败类型区分某个 ref 是不存在、属于其他用户还是仅已过期。Tool 失败不影响当前 Practice Chat 的普通回复，也不阻断正式 Code Review。

### 7.3 `get_practiced_problem_overview`

该 Tool 解决“这道历史题我做过多少次、最近结果是什么、是否值得展开”的问题。它是总览，不承担逐次 Review 列表职责，也不返回完整反馈。

输入：

```json
{
  "problemRef": "pp_a8K2..."
}
```

`problemRef` 必填，必须来自本次 Prompt 的两组历史题索引。每个 `problemRef` 在一个 run 内最多读取一次。

成功输出：

```json
{
  "type": "practiced_problem_overview",
  "status": "OK",
  "problem": {
    "problemRef": "pp_a8K2...",
    "title": "Subarray Sum Equals K",
    "tags": ["Prefix Sum", "Hash Table"],
    "formalSubmissionCount": 3,
    "passedSubmissionCount": 1,
    "firstSubmittedAt": "2026-07-12T09:20:00Z",
    "latestSubmission": {
      "submissionRef": "ps_m4Q...",
      "submittedAt": "2026-08-13T11:42:00Z",
      "language": "JAVA",
      "totalScore": 9.2,
      "passed": true,
      "reviewHistorySummary": "此前两次提交遗漏空前缀初始化；当前版本补齐初始化，Review 已通过。"
    }
  }
}
```

`title`、`tags` 来自 scope 中已经由受信题库目录补齐的条目，保持与 Prompt 索引一致。计数以同一用户、同一题、跨计划的全部正式 Review 为范围；最早和最新时间按 `created_at`、`id` 的稳定顺序确定。返回值不包含裸 slug、数据库 ID、计划/session 信息、代码、完整 Review Markdown、检测证据，以及逐次扣分或改进建议。

### 7.4 `list_practice_problem_submissions`

该 Tool 在总览不足以回答问题时，按稳定时间顺序展开正式提交的 Review 事实。它不读取 `normalizedCode`，也不签发可跨 run 使用的分页标识。

输入：

```json
{
  "problemRef": "pp_a8K2...",
  "cursor": "",
  "limit": 3
}
```

- `problemRef` 必填，含义同总览 Tool。
- `cursor` 必填；首页传空字符串，后续页只能使用同一 Tool 上次返回的 opaque cursor。
- `limit` 必填；默认使用 `3`，最小 `1`，最大 `5`。
- 排序固定为 `created_at DESC, id DESC`，即最新提交在前；不接受模型提供排序或筛选条件。

成功输出：

```json
{
  "type": "practice_problem_submission_list",
  "status": "OK",
  "problemRef": "pp_a8K2...",
  "submissions": [
    {
      "submissionRef": "ps_m4Q...",
      "submittedAt": "2026-08-13T11:42:00Z",
      "language": "JAVA",
      "totalScore": 9.2,
      "passed": true,
      "deductionReasons": [],
      "improvementSuggestions": ["可补充边界条件的说明。"],
      "affectedTags": ["PREFIX_SUM"],
      "reviewHistorySummary": "此前两次提交遗漏空前缀初始化；当前版本补齐初始化，Review 已通过。"
    },
    {
      "submissionRef": "ps_t9L...",
      "submittedAt": "2026-08-02T10:10:00Z",
      "language": "JAVA",
      "totalScore": 6.8,
      "passed": false,
      "deductionReasons": ["遗漏空前缀初始化，无法处理前缀和恰好等于 k 的情况。"],
      "improvementSuggestions": ["初始化 frequency[0] = 1。"],
      "affectedTags": ["PREFIX_SUM"],
      "reviewHistorySummary": "当前版本遗漏空前缀初始化，Review 未通过。"
    }
  ],
  "hasMore": false,
  "nextCursor": null
}
```

每一项的 `submissionRef` 在生成列表结果时由 scope 签发，映射到唯一的 `userId + problemSlug + reviewId`。`deductionReasons` 与 `improvementSuggestions` 各最多返回三条、每条使用既有 Review 字段的受控长度；字段缺失时返回空数组，不补造结论。列表不返回 `versionNo`，因为它只在单个 practice session 内有序，不能表示跨计划版本；也不返回代码、完整 Markdown、检测证据、数据库 ID 或计划/session 信息。

cursor 必须封装本次查询的题目 scope 和上一页末尾的 `created_at + id` keyset 位置，不能把时间或 ID 作为模型可构造的入参。cursor 过期、跨 run 或与 `problemRef` 不匹配时统一返回 `UNAVAILABLE`。

### 7.5 `read_practice_submission_detail`

该 Tool 仅在用户明确要求查看旧代码、对旧版本进行代码级复盘，或将当前用户消息中的代码与某一历史提交比较时使用。它读取一条正式提交的评审代码和必要的结构化 Review，不返回完整 Markdown 或检测证据。

输入：

```json
{
  "submissionRef": "ps_m4Q..."
}
```

`submissionRef` 必填，必须来自本 run 的提交列表或总览中的 `latestSubmission`。不提供 `includeCode`、第二个历史版本 ref、代码范围或任意其他开关；“是否可看代码”由服务端根据当前用户消息校验，不由模型自我声明。

成功输出：

```json
{
  "type": "practice_submission_detail",
  "status": "OK",
  "submission": {
    "submissionRef": "ps_m4Q...",
    "submittedAt": "2026-08-13T11:42:00Z",
    "language": "JAVA",
    "reviewedCode": "public int subarraySum(int[] nums, int k) { ... }",
    "review": {
      "totalScore": 9.2,
      "passed": true,
      "scoreBreakdown": {
        "correctness": 4.0,
        "complexity": 2.0,
        "edgeCases": 1.5,
        "codeQuality": 0.75,
        "problemFit": 1.0
      },
      "deductionReasons": [],
      "improvementSuggestions": ["可补充边界条件的说明。"],
      "affectedTags": ["PREFIX_SUM"],
      "reviewHistorySummary": "此前两次提交遗漏空前缀初始化；当前版本补齐初始化，Review 已通过。"
    }
  }
}
```

`reviewedCode` 是当时实际进入正式 Review 的归一化代码，不承诺保留用户聊天消息的 Markdown 围栏、空白格式或无关文本。它是本阶段唯一可能返回源码的字段。若当前消息未通过显式代码意图校验，Tool 返回 `USER_INTENT_REQUIRED` 且不查询、读取或返回代码；这不是用户确认弹窗，也不会把原始用户消息反馈给模型。

详情 Tool 每个 run 最多执行一次。它不能用于遍历历史提交或构造历史版本 diff；直接比较两个旧版本必须以后续独立 Tool 实施，并拥有单独的双 ref 授权与 diff 预算。

### 7.6 run-local capability scope

`problemRef` 与 `submissionRef` 都是 capability，不是数据标识。建议新增独立的 `PracticeSubmissionHistoryRunScopeRegistry`，而不复用学习者记忆 scope：二者的对象、敏感度、预算和释放时机不同。

scope 必须至少保存：

```text
scopeRef（随机、不可枚举）
userId
locale
problemRef -> { problemSlug, title, tags }
submissionRef -> { problemSlug, reviewId }
overview 已读取的 problemRef 集合
概览/列表共享调用计数
详情调用计数
详情 Tool 结果及范围续读的可见字符计数
expiresAt
```

`PracticeSubmissionHistoryContextProvider` 在生成 Prompt 可见条目时，同时产出仅服务端可见的 scope input；不能从已经裁剪的 `PracticeSubmissionHistoryEntry` 反推或暴露 slug、Review ID。`AgentConversationService.assemblePracticeChatContext` 在非幂等 replay 中打开 scope，将仅含 `scopeRef` 的 metadata 交给 Agent request，并把 scope lease 与现有 recall、Review trajectory lease 组合为同一个 `AgentRunResource`。run 终态、准备失败、取消和超时均必须释放 lease；幂等 replay 不创建新 ref 或 scope。

Tool 执行过程固定为：先根据 metadata 解析有效 scope，再根据 ref 取得服务端保存的 user 和数据键，最后以 `user_id` 为首个数据库范围条件查询。不得存在“根据 ref 查不到就退回任意 slug/Review ID 查询”的路径。scope 默认有效期为 15 分钟，run 释放后立即不可用。

Phase 2 不扩展 Phase 1 的窄索引 repository 去读取代码。应新增专用的 `PracticeSubmissionHistoryToolRepository`，职责限定为：

```text
findOverview(userId, problemSlug)
findSubmissions(userId, problemSlug, afterCreatedAt, afterReviewId, limit)
findSubmissionDetail(userId, problemSlug, reviewId)
```

三个查询都以 `user_id` 和 scope 授权的题目/Review 为条件；列表使用 keyset pagination，不使用 offset。详情查询仅在 scope 已解析 `submissionRef` 后执行，避免单独暴露按 ID 读取 Review 的 repository 能力。

### 7.7 预算、长代码与 `read_tool_result`

本阶段使用以下固定 run 预算：

| 预算 | 上限 | 说明 |
| --- | --- | --- |
| 概览和列表调用 | 合计 2 次 | 允许“总览 + 首页”或“两个列表页”；同题概览最多一次 |
| 代码详情调用 | 1 次 | 仅显式代码级意图通过后保留 |
| 详情结果范围续读 | 最多 2 次 | 仅适用于本阶段详情 Tool 产生的结果 |
| 详情 Tool 结果可见字符 | 合计 16,000 | 包含详情首次可见内容和后续范围读取 |
| 列表单页 | 最多 5 条 | 默认 3 条 |

`reviewedCode` 可能超过单个 Tool result 的 inline 阈值。运行时仍使用既有 `ToolResultCompactor`：完整详情结果可保存为当前 run 的 `resultRef`，模型只看到预算内 preview，随后通过已有 `read_tool_result` 读取有限范围。必须为本阶段新增 `ToolResultReadGuard` 实现，依据结果 provenance、scopeRef 和上述详情预算限制 `read_tool_result`；不能因通用范围读取而绕过“详情最多一次、最多两次续读、16,000 字符”的约束。

详情结果中包含用户源码，属于高敏感度 Tool result：不得投影到用户可见聊天消息、普通业务日志、低基数 metrics 或 Prompt 索引。实现前必须确认 Tool result blob 和 Agent 审计快照对该 provenance 使用与正式 Review 源码一致或更短的留存策略；不能为范围续读无意创建无期限的第二份源码副本。

### 7.8 模型调用规则

系统 Prompt 增加以下行为约束：

```text
历史提交只用于帮助当前题的迁移学习，不是当前题答案。
普通辅导优先使用最小索引和向用户追问；仅在用户明确询问历史、需要类比或明显卡住时读取概览或列表。
读取列表后只引用与当前问题直接相关的一项受信事实，不逐条朗读历史。
只有用户明确要求查看旧代码、代码级复盘，或要求把当前消息中的代码与旧版本比较时，才调用 read_practice_submission_detail。
不得把题目关联关系描述为必然相同解法；不得声称用户已经掌握某种能力。
```

Tool 仅用于补充受信事实，不替代正常教学推进。用户没有请求代码时，即使模型认为旧代码“可能有帮助”，也必须先用摘要或结构化反馈引导，而不是展开源码。

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
- Phase 2 的 capability scope 仅保存在应用节点内存，并随 run resource 释放；不得写入普通会话 metadata、system prompt、用户可见消息或跨 run 缓存。
- 源码详情的 raw Tool result 必须标记专用 provenance，并受专用范围读取 guard、留存策略和审计访问控制约束。

建议记录：

```text
submissionProblemIndexCount
relatedSubmissionIndexCount
reviewHistorySummaryAvailableCount
reviewHistoryReadCount
reviewHistoryReadFailure
reviewHistorySummaryFallback
practiceSubmissionHistoryToolCall{tool,status}
practiceSubmissionHistoryScopeRejected{reasonCategory}
practiceSubmissionHistoryCodeIntentRejected
practiceSubmissionHistoryDetailVisibleChars
practiceSubmissionHistoryDetailRangeReadCount
practiceSubmissionHistoryToolDataAccessFailure
```

其中 `tool` 仅允许三个固定 Tool 名，`status` 和 `reasonCategory` 仅允许低基数枚举值。不得把 `problemRef`、`submissionRef`、cursor、slug、Review ID、代码字符片段或 Review 文本作为 metric tag 或日志字段。

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

### 10.4 Phase 2 Tool 契约

- 三个 Tool 的 JSON Schema 都拒绝额外字段，且不接受用户、题目 slug、Review ID、计划或 session 标识。
- `get_practiced_problem_overview` 只返回聚合计数、稳定时间、最新提交摘要和受信题目展示字段；不得混入逐次反馈或源码。
- `list_practice_problem_submissions` 默认返回 3 条、最大 5 条；按 `created_at DESC, id DESC` 排序，跨计划 `versionNo` 重复时仍稳定。
- 列表 cursor 使用 keyset pagination；连续翻页没有重复、遗漏或倒序，伪造、跨题或跨 run cursor 统一为 `UNAVAILABLE`。
- 列表的每条记录都可签发本 run 的 `submissionRef`；同一 Review 不因多次分页得到可互换的跨 run ref。
- `read_practice_submission_detail` 只接受 scope 已签发的 `submissionRef`，并且只返回该正式 Review 的归一化代码和定义字段中的结构化 Review。
- 列表结果不包含 `normalizedCode`、Review Markdown、检测证据或数据库 ID；详情结果不包含完整 Review Markdown、检测证据、计划/session 和聊天历史。

### 10.5 Scope、安全与预算

- 伪造、跨用户、跨题、跨 run、过期和已释放的 `problemRef`、`submissionRef`、cursor 都不得触发数据查询，并统一返回 `UNAVAILABLE`。
- Idempotent replay 不打开新历史 scope，不生成新 ref，不允许旧 ref 在 replay 中复用。
- run 正常结束、准备失败、取消、超时和异常时均释放历史 scope；释放后所有 ref 立即失效。
- overview 与 list 合计第三次调用返回 `BUDGET_EXHAUSTED`；同一题 overview 第二次调用也必须被拒绝。
- detail 第二次调用返回 `BUDGET_EXHAUSTED`；详情 Tool 的 `USER_INTENT_REQUIRED` 不读取代码，且不消耗详情调用或源码可见字符预算。
- 只有当前用户消息明确包含查看旧代码、代码级复盘，或与当前消息代码比较的意图时 detail 才可执行。一般的“我之前哪里错了”只允许概览或列表。
- `read_tool_result` 对详情 Tool 的 resultRef 最多允许两次范围读取，首次详情 preview 与续读总可见字符不超过 16,000；其他 Tool result 不受该专用 guard 的误拦截。
- scope、Tool result 和日志中均不能出现跨用户数据；集成测试必须验证查询 SQL 的用户范围先于题目和 Review 条件。

### 10.6 Repository 与端到端联调

- PostgreSQL 集成测试覆盖跨计划聚合、通过次数、最早/最新时间、同时间按 ID 的稳定排序和 keyset 翻页。
- 详情查询必须证明错误用户、错误题目或未授权 Review ID 无法命中，即使该数据库记录真实存在。
- Practice Chat 集成测试覆盖 Prompt 索引与 scope 中的 `problemRef` 一致、Agent Definition 按开关注入三个 Tool、run resource 合并释放和 SSE 中不投影源码 Tool result。
- 长代码场景验证 ToolResultCompactor 生成 `resultRef`，并验证受限 `read_tool_result` 能在预算内续读、预算耗尽后拒绝。
- 本阶段不修改正式 Review 写入语义；提交新代码后，仍由 `submit_practice_code_review` 产生新的 `reviewHistorySummary`，后续新 run 才可见该历史事实。

## 11. 分阶段发布

### 阶段一：Review 历程摘要与 Prompt 索引（已实施）

- 新增 `reviewHistorySummary` structured output 和持久化字段。
- Code Review 前读取最近四条同题、跨计划历史窄行。
- 修正同题历史查询为按 `created_at`、`id` 排序。
- 旧数据不回填，字段缺失时安全省略。
- 注入用户正式提交题目索引和关联提交题目索引。
- 固定最近提交组最多 5 道、关联提交组最多 3 道，以及各自的时间排序。
- 条目仅使用本设计固定字段，不注册历史读取 Tool。
- 关联题索引只作为迁移线索，不自动展开详情。

### 阶段二：历史提交 Tool（已实施，默认开启）

1. 已新增 `PracticeSubmissionHistoryRunScopeRegistry`、专用 `PracticeSubmissionHistoryToolRepository`、三个 Tool、常量契约和 Micrometer 指标；Phase 1 Prompt 索引的公开字段保持不变。
2. `AgentConversationService` 会在非 replay 的 Practice Chat run 中打开并合并释放历史 scope；Practice Chat Definition 仅在 capability 已完整装配时加入对应 Tool 白名单。Tool、开关、读写副作用与预算已同步到 `agent-tool-catalog.md`。
3. 总开关 `PRACTICE_CHAT_SUBMISSION_HISTORY_TOOL_ENABLED` 默认 `true`。关闭时不创建历史 scope、不把三个 Tool 加入 Practice Chat 白名单，Phase 1 Prompt 索引继续照常工作。
4. 代码详情开关 `PRACTICE_CHAT_SUBMISSION_HISTORY_CODE_DETAIL_ENABLED` 默认 `true`，并以总开关为前置条件。关闭源码详情开关时只暴露 overview 和 list。
5. 代码详情使用 `read_practice_submission_detail` provenance 的专用 `ToolResultReadGuard`，验证了 preview/resultRef 的两次范围读取和 16,000 字符合计上限。详情原始 Tool result 仅进入 run 内模型上下文和受保护的管理员审计链路，不投影到用户 SSE；详情 blob 沿用 Agent run 的 30 天诊断留存与管理员审计访问控制，正式 `practice_code_review` 事实不随 blob 清理删除。
6. 发布时先在小范围用户中观察 Tool 调用量、scope 拒绝、数据访问失败、代码意图拒绝和上下文字符预算；发现越权、误触发或源码意外投影时，优先关闭代码详情二级开关，必要时关闭总开关，无需回滚正式 Review。

本阶段未增加业务表或回填任务；历史 Tool 直接读取既有正式 Review 事实。

## 12. 待确认事项

1. 关联题候选是否同时包含反向关系；不影响跨计划 Review 历程语义。
2. 源码详情 Tool result 沿用 Agent run 的 30 天诊断留存和管理员审计访问控制；如后续合规要求进一步缩短源码 blob 留存，应以独立迁移和留存评审实施。
