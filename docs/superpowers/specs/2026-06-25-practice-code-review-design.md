# 练习代码 Review 设计

> 状态：历史方案，已废弃。本文描述的是服务端 `PracticeTurnCapability` 自动 Review 方向；当前方向已调整为 `submit_practice_code_review` Agent tool + 权限人在回路 + 主模型自主判断。不要继续按本文新增或恢复 `CodeReviewTurnCapability`、`PracticeTurnCapabilityRegistry` 或 `practiceCapabilities` 自动写库链路。

## 背景

`docs/practice-code-review-product-design.md` 和 `docs/practice-code-review-technical-design.md` 已定义练习聊天中的代码 Review 闭环：用户在题目练习会话里粘贴完整 LeetCode 题解代码后，系统自动识别、生成结构化 Review、保存多版本记录，并用最近一次有效 Review 决定题目能否标记完成。

当前代码基础已经具备题目练习会话、学习计划题目进度、Agent runtime 持久化、practice chat SSE、AI governance、provider-native JSON Schema 结构化输出和前端聊天工作台。缺口是：没有独立 Review 事实表，没有服务端回合 capability 编排，没有完成 gate，也没有真实 Review 历史抽屉。

本设计以现有 practice chat 为主链路增量扩展，不引入在线判题、编译运行、独立代码编辑器、异步 worker、消息队列或通用工作流引擎。

## 目标

- 用户发送完整题解代码后，自动生成一条有效 Review。
- 同一练习会话支持多个 Review 版本，版本号在 `practice_session_id` 内递增。
- Review 保存代码快照、语言、识别证据、上下文摘要、评分、扣分原因、改进建议、Markdown 点评和本轮消息引用。
- 普通聊天、片段讨论、报错咨询、伪代码和非本题代码不生成正式 Review。
- 完成题目的资格由最近一次有效 Review 决定，及格线为 6/10。
- 前端展示真实 Review 历史、最新分数和完成资格原因。
- Review capability 失败不影响本轮聊天回复成功展示。

## 非目标

- 不做在线运行、编译、判题。
- 不保证 Review 分数等同于 LeetCode 真实通过结果。
- 不实现 Review 版本 diff。
- 不做人工 override 完成门槛。
- 不新增完整用户画像页面。
- 不让模型通过 tool call 主动决定是否写入正式 Review。

## 用户体验

练习聊天输入区提示用户可以粘贴完整 LeetCode 代码生成 Review。用户发送消息后，聊天回复仍按当前 SSE 流展示；如果消息是完整代码提交，后端在 run 结束后生成结构化 Review 并保存。流结束后前端刷新 session、messages 和 reviews。

完成按钮常驻显示。没有有效 Review 时禁用并提示“完成前需要先粘贴完整代码完成一次 AI Review。”；最近 Review 未及格时禁用并提示当前分数和 6 分门槛；最近 Review 已及格时允许标记完成。若前端状态过期，后端仍强校验并返回稳定错误码。

Review 抽屉展示最新 Review 分数、是否及格、版本列表、提交时间、语言、代码快照、点评、扣分原因、改进建议和完成资格影响。空态文案为“还没有 Review 记录。粘贴完整 LeetCode 代码后，AI 会自动生成 Review。”

## 架构

采用服务端轻量回合编排：

```text
PracticeSessionController
  -> PracticeMessageStreamService
       -> PracticeTurnOrchestrator
            -> PracticeTurnClassifier
            -> AgentConversationRunCoordinator
            -> AgentTurnMessageLookupRepository
            -> PracticeTurnCapabilityRegistry
                 -> CodeReviewTurnCapability
                      -> PracticeCodeReviewService
                           -> PracticeCodeReviewPromptBuilder
                           -> LlmGateway.complete(JsonSchema)
                           -> PracticeCodeReviewStructuredOutputMapper
                           -> PracticeCodeReviewRepository
```

`PracticeMessageStreamService` 保持 HTTP/SSE 适配入口，负责加载 session、构造治理 metadata、调用 orchestrator 和 touch session。`PracticeTurnOrchestrator` 管理一次用户消息回合：预分类、启动聊天 run、捕获 `AgentRunEnd`、回查本轮 user/assistant message、构造 `PracticeTurnContext`、执行 capability，并把 capability 摘要合并进 `AgentRunEnd.metadata`。

代码 Review 是第一种 `PracticeTurnCapability`。它由服务端规则触发，模型只参与识别和评分，不拥有最终写库触发权。这样可以保证完成 gate、幂等、访问控制和错误隔离由服务端业务规则控制。

## 数据模型

新增 Flyway 迁移：

```text
backend/mentor-api/src/main/resources/db/migration/V13__practice_code_review_schema.sql
```

新增 `practice_code_review` 表，核心字段包括：

- `user_id`、`plan_id`、`phase_index`、`problem_slug`、`practice_session_id`
- `version_no`
- `user_message_id`、`assistant_message_id`、`agent_run_id`
- `raw_code`、`normalized_code`、`language`
- `detection_evidence_json`、`context_summary`
- `total_score`、`correctness_score`、`complexity_score`、`edge_case_score`、`code_quality_score`、`problem_fit_score`
- `passed`
- `deduction_reasons_json`、`improvement_suggestions_json`
- `review_markdown`
- `created_at`

约束：

- `(practice_session_id, version_no)` 唯一。
- `(practice_session_id, user_message_id)` 唯一，保证同一用户消息不会重复生成 Review。
- 分数范围使用数据库 check 约束兜底。
- `practice_session_id` 级联删除；消息和 run 引用保留审计关系。

## 应用层契约

新增领域模型和端口放在 `backend/mentor-application/.../practice`：

- `PracticeTurnContext`：一次用户消息回合的稳定上下文。
- `PracticeTurnClassification`：低成本预分类结果。
- `PracticeTurnCapability`、`PracticeTurnCapabilityRegistry`、`PracticeTurnCapabilityResult`：回合 capability 扩展点。
- `PracticeCodeReview`、`PracticeCodeReviewDraft`、`PracticeCodeReviewSummary`、`PracticeCodeReviewScore`、`PracticeCodeReviewEvidence`：Review 领域对象。
- `PracticeCompletionGate`：完成资格结果。
- `PracticeReviewResult`、`PracticeReviewStatus`：Review capability 结果。
- `PracticeCodeReviewRepository`：Review 读写端口。
- `AgentTurnMessageLookupRepository`：按 run 数据库 ID 回查本轮 user/assistant message。

`PracticeReviewStatus` 稳定值：

- `NOT_CODE_LIKE`
- `NOT_COMPLETE_SUBMISSION`
- `SAVED`
- `FAILED`

`PracticeCompletionGate.reasonCode` 稳定值：

- `NO_REVIEW`
- `LATEST_REVIEW_FAILED`
- `PASSED`
- `ALREADY_COMPLETED`

完成 gate 规则：

```text
progress 已 COMPLETED -> ALREADY_COMPLETED
没有 Review -> NO_REVIEW
最近 Review totalScore < 6 -> LATEST_REVIEW_FAILED
最近 Review totalScore >= 6 -> PASSED
```

`PracticeSessionService.updateProgressStatus(..., COMPLETED)` 写进度前必须调用 gate。禁止完成时抛 `LearningPlanException`，错误码为：

- `PRACTICE_COMPLETION_REVIEW_REQUIRED`
- `PRACTICE_COMPLETION_REVIEW_NOT_PASSED`

## 结构化 Review

新增常量类 `PracticeCodeReviewConstants` 统一管理公共契约：

- `SCENARIO = "practice_code_review"`
- `SCHEMA_NAME = "practice_code_review_result"`
- `SCHEMA_VERSION = "v1"`
- `PASS_SCORE = 6.0`
- SSE metadata 根 key：`practiceCapabilities`
- code review capability key：`codeReview`

Review 调用使用 `LlmGateway.complete()` 和 `LlmResponseFormat.JsonSchema`，不使用流式输出。JSON Schema 顶层字段包括：

- `isCodeSubmission`
- `belongsToCurrentProblem`
- `isCompleteLeetCodeSolution`
- `language`
- `rawCode`
- `normalizedCode`
- `evidence`
- `contextSummary`
- `scores`
- `passed`
- `deductionReasons`
- `improvementSuggestions`
- `reviewMarkdown`

落库条件：

```text
isCodeSubmission == true
belongsToCurrentProblem == true
isCompleteLeetCodeSolution == true
rawCode 和 normalizedCode 非空
```

应用层校验：

- 五个维度分不能超过上限：正确性 0-4、复杂度 0-2、边界 0-2、代码质量 0-1、题意贴合 0-1。
- `total` 必须等于五个维度分之和；允许 0.1 以内误差，保存前归一化为求和结果。
- `passed` 以应用层 `total >= 6` 计算为准。
- 如果 `correctness <= 2` 且总分大于 5，保存前把总分截断为 5，并在 evidence 中记录 `CORRECTNESS_BLOCKING_CAP`。

## API

扩展 `PracticeSessionResponse`：

- `latestReview?: PracticeCodeReviewSummary | null`
- `completionGate: PracticeCompletionGate`

新增 API：

```text
GET /api/practice-sessions/{sessionId}/reviews
GET /api/practice-sessions/{sessionId}/reviews/{reviewId}
```

列表响应 `PracticeCodeReviewHistoryResponse` 包含：

- `latestReview`
- `reviews`
- `completionGate`

详情响应 `PracticeCodeReviewDetailResponse` 包含摘要字段、代码快照、证据、上下文摘要、维度分、扣分原因、改进建议、Markdown 点评和消息引用。

所有 Review 查询必须带当前登录用户 ID 过滤，跨用户访问返回不存在语义，不泄露资源存在性。

## 前端

改造 `frontend/src/learning-plans/PracticeChatWorkbench.tsx`：

- 加载 session 后保存 `latestReview` 和 `completionGate`。
- 新增 reviews 状态，流结束后刷新 messages、session 和 reviews。
- 完成按钮根据 `completionGate.canComplete` 禁用，并展示 gate message。
- Review 抽屉从占位空态升级为真实版本历史和详情。
- composer placeholder 改为“输入你的思路、问题，或粘贴完整 LeetCode 代码生成 Review...”
- composer 上方展示短提示“粘贴完整 LeetCode 代码后，AI 会自动生成 Review 记录并更新完成资格。”

新增前端类型和 API 函数集中在 `frontend/src/types/api.ts` 与 `frontend/src/services/api.ts`。

## 安全与隐私

- 日志不得输出完整代码、完整聊天内容、Authorization、API key 或用户隐私内容。
- Review 失败日志只记录 sessionId、runId、错误码和异常类型。
- Review 表所有查询按 `user_id` 隔离。
- 结构化 Review 调用 metadata 写入 `practiceSessionId`、`planId`、`phaseIndex`、`problemSlug`、`reviewCandidate=true`，不写完整代码。
- Review capability 异常隔离，不让聊天 run 失败。

## 指标

新增 Micrometer 指标：

- `practice.code_review.candidate.count`
- `practice.code_review.saved.count`
- `practice.code_review.not_complete.count`
- `practice.code_review.failed.count`
- `practice.code_review.duration`
- `practice.completion.blocked.no_review.count`
- `practice.completion.blocked.review_failed.count`

标签只使用低基数字段：`language`、`passed`、`failureCode`。不把 `problemSlug` 作为默认指标标签。

## 测试

后端重点覆盖：

- 预分类：完整 `class Solution`、完整函数、代码片段、报错日志、伪代码、普通聊天。
- 结构化输出 mapper：非完整提交、分数边界、总分归一化、passed 归一化、正确性截断。
- Review service：完整提交保存、非本题不保存、模型失败不落库、唯一约束幂等复用。
- capability：非候选不调模型、保存成功、失败隔离、幂等 replay 不重复调用模型。
- orchestrator：`AgentRunEnd` 后组装 context、执行 capability、合并 metadata、保留原 SSE 事件顺序。
- completion gate：无 Review、最近失败、历史通过但最近失败、最近通过、已完成。
- repository/API：版本递增、最近 Review、用户隔离、列表、详情、完成接口强校验。

前端重点覆盖：

- session 响应渲染 latest review 和 completion gate。
- 完成按钮禁用原因。
- Review 抽屉空态、版本列表和详情切换。
- SSE 结束后刷新 reviews。
- 进度完成失败时展示后端 gate 错误消息。

## 验收标准

- 粘贴完整当前题题解代码后，系统保存一条 Review，前端抽屉可见。
- 同一会话多次提交生成递增版本。
- 普通聊天、片段、报错和非本题代码不会生成正式 Review。
- 没有 Review 或最近 Review 未达到 6 分时，后端阻止标记完成，前端显示明确原因。
- 最近 Review 达到 6 分时，可以标记完成。
- Review capability 失败时聊天回复仍正常结束，metadata 标记失败，数据库不产生脏记录。
- 后端相关 Maven 测试和前端相关 Vitest 通过。
