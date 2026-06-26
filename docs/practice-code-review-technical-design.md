# 练习代码 Review 技术设计

## 背景

`docs/practice-code-review-product-design.md` 定义了练习聊天中的代码 Review 闭环：用户粘贴完整 LeetCode 题解代码后，系统自动识别、生成 AI Review、保存多版本记录，并用最近一次有效 Review 决定题目能否标记完成。

当前代码基础已经具备：

- `practice_session` 作为计划题练习会话锚点。
- `PracticeMessageStreamService` 通过 `AgentConversationRunCoordinator` 承载题目聊天 SSE。
- `agent_message` 保存题面 seed、用户消息和 AI 回复。
- `learning_plan_problem_progress` 保存题目进度。
- `llm-core` 和 `agent-core` 已支持 provider-native JSON Schema 结构化输出。

本设计选择在现有 practice chat 流程上增量扩展，引入轻量的练习回合编排层，而不是引入完整 LangGraph/DAG 工作流引擎，也不新增在线判题、编译运行或独立代码编辑器。

## 目标

- 用户在练习聊天中发送完整题解代码后，自动生成结构化 Review 记录。
- 同一 `计划 + 阶段 + 题目 + 会话` 支持多次 Review 版本。
- Review 记录保存代码快照、语言、识别证据、上下文摘要、评分、扣分原因和改进建议。
- 完成题目的资格由最近一次有效 Review 决定。
- 前端 Review 抽屉展示真实版本历史，不再只是占位空态。
- 普通片段讨论、报错咨询、伪代码和非本题代码不污染正式 Review 记录。

## 非目标

- 不做在线运行、编译、判题。
- 不保证 Review 分数等同于 LeetCode 真实通过结果。
- 不实现 Review 版本 diff。
- 不做人工 override 完成门槛。
- 不做完整用户画像页面。
- 不新增异步 worker 或消息队列；第一版在服务端练习回合编排内同步执行 capability。

## 方案选择

采用方案 A 的轻量编排版：接入现有 practice chat 流程，在一次用户消息 run 结束后由服务端 `PracticeTurnOrchestrator` 执行 `PracticeTurnCapability`，自动识别并落 Review。

流程分为两层：

1. 练习聊天仍由 `PracticeMessageStreamService -> PracticeTurnOrchestrator -> AgentConversationRunCoordinator -> AgentLoopRunner` 流式生成用户可见回复。
2. `PracticeTurnOrchestrator` 管理一次练习回合的生命周期：预分类、启动聊天 run、捕获 run 结束、组装稳定上下文、执行回合 capability。
3. 代码 Review 是第一个 `PracticeTurnCapability`。当本次用户消息疑似代码提交时，`CodeReviewTurnCapability` 调用 `PracticeCodeReviewService`，使用非流式结构化输出识别和评分，保存正式 Review 记录，并在 `agent_run_end` metadata 中带回轻量结果。

用户可见聊天回复和结构化 Review 记录是两个产物。第一版通过 practice chat prompt 让代码提交场景的回复使用 Review 风格；完成资格、抽屉分数和画像数据以后端结构化 Review 记录为准。

不把代码 Review 包装成模型主动调用的 tool。原因是 Review 记录会影响完成门槛，属于服务端业务事实生成流程，是否写库必须由服务端规则和业务校验决定；模型可以参与识别和评分，但不拥有最终触发权。

## 总体架构

```text
PracticeChatWorkbench
  -> POST /api/practice-sessions/{sessionId}/messages/stream
  -> PracticeSessionController
  -> PracticeMessageStreamService
       -> PracticeTurnOrchestrator
            -> PracticeTurnClassifier 低成本预判
            -> AgentConversationRunCoordinator 生成聊天回复
            -> PracticeTurnContext 组装本次回合事实
            -> PracticeTurnCapabilityRegistry
                 -> CodeReviewTurnCapability
                      -> PracticeCodeReviewService 结构化识别与评分
                           -> PracticeCodeReviewPromptBuilder
                           -> LlmGateway.complete(JsonSchema)
                           -> PracticeCodeReviewStructuredOutputMapper
                           -> PracticeCodeReviewRepository
            -> AgentRunEnd metadata 附加 capability 摘要
  -> 前端流结束后 refresh session/messages/reviews
```

持久化边界：

- 聊天消息继续保存在 `agent_message`。
- Review 事实独立保存在 `practice_code_review`。
- 题目进度仍保存在 `learning_plan_problem_progress`。
- `practice_session` 不保存完整 Review 内容，只在响应模型中聚合 latest review 和 completion gate。

## 数据模型

新增迁移：`backend/mentor-api/src/main/resources/db/migration/V13__practice_code_review_schema.sql`。

```sql
CREATE TABLE IF NOT EXISTS practice_code_review (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  phase_index INTEGER NOT NULL,
  problem_slug VARCHAR(255) NOT NULL,
  practice_session_id BIGINT NOT NULL REFERENCES practice_session(id) ON DELETE CASCADE,
  version_no INTEGER NOT NULL,
  user_message_id BIGINT NOT NULL REFERENCES agent_message(id),
  assistant_message_id BIGINT NULL REFERENCES agent_message(id),
  agent_run_id BIGINT NULL REFERENCES agent_run(id),
  raw_code TEXT NOT NULL,
  normalized_code TEXT NOT NULL,
  language VARCHAR(64) NOT NULL,
  detection_evidence_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  context_summary TEXT NOT NULL,
  total_score NUMERIC(4, 1) NOT NULL,
  correctness_score NUMERIC(3, 1) NOT NULL,
  complexity_score NUMERIC(3, 1) NOT NULL,
  edge_case_score NUMERIC(3, 1) NOT NULL,
  code_quality_score NUMERIC(3, 1) NOT NULL,
  problem_fit_score NUMERIC(3, 1) NOT NULL,
  passed BOOLEAN NOT NULL,
  deduction_reasons_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  improvement_suggestions_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  review_markdown TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_practice_code_review_session_version UNIQUE (practice_session_id, version_no),
  CONSTRAINT uk_practice_code_review_user_message UNIQUE (practice_session_id, user_message_id),
  CONSTRAINT ck_practice_code_review_score CHECK (
    total_score >= 0 AND total_score <= 10
    AND correctness_score >= 0 AND correctness_score <= 4
    AND complexity_score >= 0 AND complexity_score <= 2
    AND edge_case_score >= 0 AND edge_case_score <= 2
    AND code_quality_score >= 0 AND code_quality_score <= 1
    AND problem_fit_score >= 0 AND problem_fit_score <= 1
  )
);

CREATE INDEX IF NOT EXISTS idx_practice_code_review_latest
  ON practice_code_review(practice_session_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_practice_code_review_user_problem
  ON practice_code_review(user_id, plan_id, phase_index, problem_slug, created_at DESC);
```

说明：

- `assistant_message_id` 允许为空，用于避免持久化 observer 极端延迟时阻塞正式 Review。正常路径应写入本次 AI 回复消息 ID。
- `agent_run_id` 保存本次 run 的数据库 ID，便于审计和排查。
- `version_no` 在同一个 `practice_session_id` 内递增。
- `uk_practice_code_review_user_message` 保证同一条用户消息不会因客户端重试生成多个有效 Review。
- `passed` 由应用层按 `total_score >= 6` 计算后落库。

## 应用层模型与端口

在 `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice` 新增以下模型：

- `PracticeTurnContext`：一次用户消息回合的稳定上下文，包含 session、题目、用户消息、run、消息引用、预分类结果和治理 metadata。
- `PracticeTurnClassification`：低成本预分类结果，描述本次消息是否疑似代码、语言提示、识别证据和提取出的代码。
- `PracticeTurnCapabilityResult`：回合 capability 执行结果，用于聚合 SSE metadata、指标和日志。
- `PracticeCodeReview`：Review 领域对象，包含完整详情。
- `PracticeCodeReviewSummary`：列表和 session 聚合使用的摘要。
- `PracticeCodeReviewScore`：五个维度分和总分。
- `PracticeCodeReviewEvidence`：识别证据，例如代码块语言、关键字、题目函数名、类名。
- `PracticeCompletionGate`：完成资格判断结果。
- `PracticeReviewResult`：代码 Review capability 结果，区分 `NOT_CODE_LIKE`、`NOT_COMPLETE_SUBMISSION`、`SAVED`、`FAILED`。

新增轻量回合编排接口：

```java
public interface PracticeTurnCapability {
  String name();
  boolean supports(PracticeTurnContext context);
  PracticeTurnCapabilityResult afterTurn(PracticeTurnContext context);
}
```

```java
public final class PracticeTurnCapabilityRegistry {
  private final List<PracticeTurnCapability> capabilities;

  public List<PracticeTurnCapabilityResult> executeAfterTurn(PracticeTurnContext context) {
    return capabilities.stream()
        .filter(capability -> capability.supports(context))
        .map(capability -> capability.afterTurn(context))
        .toList();
  }
}
```

第一版只注册 `CodeReviewTurnCapability`。后续自动摘要、错题标签归因、提示次数统计、薄弱点记录等能力可以新增 capability，不改消息流主链路。

`supports` 只表达 capability 是否适用于当前回合类型或是否已启用，不承载具体业务判定。代码 Review 是否真的生成记录，由 `CodeReviewTurnCapability.afterTurn` 根据 `PracticeTurnClassification` 和模型识别结果返回 `NOT_CODE_LIKE`、`NOT_COMPLETE_SUBMISSION` 或 `SAVED`。

新增端口：

```java
public interface PracticeCodeReviewRepository {
  PracticeCodeReview save(PracticeCodeReviewDraft draft);
  Optional<PracticeCodeReview> findLatest(long userId, long sessionId);
  List<PracticeCodeReviewSummary> findSummaries(long userId, long sessionId);
  Optional<PracticeCodeReview> findById(long userId, long sessionId, long reviewId);
}
```

为拿到本次用户消息和 AI 回复引用，新增 agent runtime 读取端口：

```java
public interface AgentTurnMessageLookupRepository {
  Optional<AgentTurnMessages> findByRunId(long runId);
}
```

`AgentTurnMessages` 包含：

- `runId`
- `turnId`
- `userMessage`
- `assistantMessage`

PostgreSQL 实现从 `agent_run.turn_id -> agent_turn.user_message_id / assistant_message_id -> agent_message` 查询。这样 Review 服务不依赖“最新两条消息”推断，也不依赖前端临时消息 ID。

## 服务设计

### PracticeTurnOrchestrator

`PracticeTurnOrchestrator` 是一次练习消息的应用层编排入口。它不是通用 DAG 引擎，只表达 practice chat 当前需要的线性生命周期。

职责：

1. 查询并校验 `practice_session`、学习计划和题目上下文。
2. 调用 `PracticeTurnClassifier` 做低成本预分类。
3. 调用 `AgentConversationRunCoordinator` 生成用户可见聊天回复。
4. 在 `AgentRunEnd` 时读取本次 run 对应的 user/assistant message 引用。
5. 构造 `PracticeTurnContext`。
6. 调用 `PracticeTurnCapabilityRegistry.executeAfterTurn(context)`。
7. 将 capability 结果合并到 `AgentRunEnd.metadata` 后透传给 SSE。

`PracticeMessageStreamService` 保持为 HTTP/SSE 适配入口，负责调用 orchestrator、透传 publisher、touch session 和异常映射，不直接依赖代码 Review 业务。

### PracticeTurnClassifier

低成本预分类只决定是否值得让某个 capability 继续处理，不直接落 Review，也不替代模型最终识别。

第一版分类重点是代码提交候选。

输入：

- 当前用户消息。
- `PracticeChatProblemDetail`。
- 学习计划题目标题、slug、frontendId。

信号：

- Markdown fenced code block。
- `class Solution`、`public`、`def`、`function`、`return`、`import`、`package` 等常见代码关键字。
- 大括号、缩进、分号、函数签名等结构。
- 当前题目标题、slug、题号或从题面中提取到的入口函数名。

输出：

```java
public record PracticeTurnClassification(
    boolean codeSubmissionCandidate,
    String extractedCode,
    String languageHint,
    List<PracticeCodeReviewEvidence> evidence
) {}
```

当 `codeSubmissionCandidate=false` 时，`CodeReviewTurnCapability.afterTurn(context)` 返回 `NOT_CODE_LIKE`，不调用 Review 模型。用户消息仍按普通聊天处理。

### CodeReviewTurnCapability

`CodeReviewTurnCapability` 是服务端 tool-like capability，但不是模型主动 tool call。触发权在服务端，执行结果进入业务事实表。

职责：

1. 根据 `PracticeTurnClassification` 判断是否处理。
2. 调用 `PracticeCodeReviewService.reviewAfterTurn(context)`。
3. 将 `PracticeReviewResult` 转换成 `PracticeTurnCapabilityResult`。
4. 记录 Review 指标和低基数 metadata。

失败策略：

- capability 内部异常不让聊天 run 失败。
- 调用失败返回 `FAILED`，并在 `agent_run_end` metadata 中体现。
- 幂等 replay 时不重新调用模型，只查询已存在 Review。

### PracticeCodeReviewService

职责：

1. 构造 Review prompt。
2. 调用 `LlmGateway.complete()` 获取 JSON Schema 结构化结果。
3. 校验模型输出并落库。
4. 返回 `PracticeReviewResult` 给 capability。

方法形态：

```java
public PracticeReviewResult reviewAfterTurn(PracticeTurnContext context);
```

失败策略：

- 模型判定不是当前题完整提交：返回 `NOT_COMPLETE_SUBMISSION`，不落库。
- 模型调用失败或 JSON 无法解析：记录 warn 和指标，返回 `FAILED`，不影响聊天消息成功展示。
- 落库唯一约束冲突：查询已有 Review 并返回 `SAVED`，保证幂等。

### PracticeCompletionGate

完成门槛只看最近一次有效 Review。

```text
progress 已 COMPLETED
  -> canComplete=false, reasonCode=ALREADY_COMPLETED
没有 Review
  -> canComplete=false, reasonCode=NO_REVIEW
最近 Review totalScore < 6
  -> canComplete=false, reasonCode=LATEST_REVIEW_FAILED
最近 Review totalScore >= 6
  -> canComplete=true, reasonCode=PASSED
```

`PracticeSessionService.updateProgressStatus(..., COMPLETED)` 在写入进度前调用 gate。禁止完成时抛出 `LearningPlanException`，错误码使用稳定常量：

- `PRACTICE_COMPLETION_REVIEW_REQUIRED`
- `PRACTICE_COMPLETION_REVIEW_NOT_PASSED`

错误消息直接返回前端展示。

## Review 结构化输出

Review 不使用流式输出，使用 `LlmGateway.complete()` 和 `LlmResponseFormat.JsonSchema`。

新增常量类：

```java
public final class PracticeCodeReviewConstants {
  public static final String SCENARIO = "practice_code_review";
  public static final String SCHEMA_NAME = "practice_code_review_result";
  public static final String SCHEMA_VERSION = "v1";
  public static final BigDecimal PASS_SCORE = new BigDecimal("6.0");
}
```

JSON Schema 顶层字段：

```json
{
  "isCodeSubmission": true,
  "belongsToCurrentProblem": true,
  "isCompleteLeetCodeSolution": true,
  "language": "java",
  "rawCode": "...",
  "normalizedCode": "...",
  "evidence": [
    {"type": "ENTRY_FUNCTION", "value": "climbStairs"}
  ],
  "contextSummary": "...",
  "scores": {
    "correctness": 3.0,
    "complexity": 2.0,
    "edgeCases": 1.0,
    "codeQuality": 1.0,
    "problemFit": 1.0,
    "total": 8.0
  },
  "passed": true,
  "deductionReasons": ["..."],
  "improvementSuggestions": ["..."],
  "reviewMarkdown": "..."
}
```

落库条件：

```text
isCodeSubmission == true
belongsToCurrentProblem == true
isCompleteLeetCodeSolution == true
```

应用层校验：

- 五个维度分不能超过产品定义上限。
- `total` 必须等于五个维度分之和；允许 0.1 以内小数误差，保存前归一化为求和结果。
- `passed` 必须等于 `total >= 6`；如不一致，以应用层计算为准。
- 如果 `correctness <= 2` 且模型仍给出 `total > 5`，保存前把 `total` 截断到 5，并在 metadata/evidence 中记录 `CORRECTNESS_BLOCKING_CAP`。
- `rawCode` 和 `normalizedCode` 不能为空；为空时视为结构化输出无效，不落库。

## Prompt 设计

`PracticeCodeReviewPromptBuilder` 组装以下上下文：

- 平台角色：算法刷题代码 Review 助教。
- 当前题目事实：题号、标题、slug、难度、标签、题面摘要、样例摘要。
- 学习计划事实：planId、phaseIndex、阶段目标、计划题原因。
- 当前用户提交：`PracticeTurnClassifier` 提取出的代码和原始消息。
- 聊天上下文：本次提交前最近若干条 `agent_message`，排除题面 seed，并做长度裁剪。
- 评分规则：正确性 0-4、复杂度 0-2、边界条件 0-2、代码质量 0-1、思路表达与题意贴合 0-1。
- 识别规则：普通片段、报错、伪代码、非本题代码必须返回 false，不生成正式 Review。

practice chat 的普通回复 prompt 同步增加一条场景策略：

- 如果当前用户消息看起来是完整代码提交，按 Review 风格回复，覆盖正确性、复杂度、边界、质量和下一步建议。
- 如果只是片段或报错，正常答疑并引导粘贴完整 `Solution` 代码生成 Review。

## 回合流集成

改造方向是让 `PracticeMessageStreamService` 委托 `PracticeTurnOrchestrator` 创建 publisher。`PracticeMessageStreamService` 不直接判断代码 Review，也不直接调用 `PracticeCodeReviewService`。

```text
PracticeMessageStreamService.stream(...)
  -> PracticeTurnOrchestrator.streamTurn(...)
       -> classify user message
       -> coordinator.stream(...)
       -> onNext(content_delta/message_end/...) 正常透传
       -> onNext(AgentRunEnd)
            -> touchLastMessageAt
            -> assemble PracticeTurnContext
            -> capabilityRegistry.executeAfterTurn(context)
            -> merge capability metadata into AgentRunEnd
            -> 透传给 SSE subscriber
```

`AgentRunEnd.metadata` 增加轻量字段：

```json
{
  "practiceCapabilities": {
    "codeReview": {
      "status": "SAVED",
      "reviewId": 123,
      "versionNo": 2,
      "totalScore": 7.0,
      "passed": true
    }
  }
}
```

状态枚举：

- `NOT_CODE_LIKE`
- `NOT_COMPLETE_SUBMISSION`
- `SAVED`
- `FAILED`

前端不依赖 SSE metadata 展示完整 Review，只用它决定是否立即刷新 reviews。即使 metadata 丢失，流结束后的 session/reviews 刷新仍能恢复状态。

幂等处理：

- 相同 `idempotencyKey` 的重试会触发 coordinator replay。
- replay metadata 中存在 `idempotentReplay=true` 时，`CodeReviewTurnCapability` 不再调用模型，只查询已有 Review。
- `uk_practice_code_review_user_message` 作为数据库兜底。

## 与模型 tool call 的边界

代码 Review 不设计为 `review_code` 这类模型主动 tool call。

原因：

- 是否生成正式 Review 会影响完成门槛，必须由服务端规则、幂等和业务校验控制。
- 模型可能漏调或误调 tool，导致完整提交没有 Review，或片段/报错被落为正式 Review。
- tool 带写库副作用时，需要处理重复 tool call、run 重试、异常恢复和嵌套模型调用，第一版复杂度过高。
- Review 服务内部还要调用 LLM 做结构化评分，如果包成 agent tool，会形成一次 agent run 内嵌套 LLM 调用，治理、trace、超时和错误归因都会更绕。

本设计采用服务端 tool-like capability：能力有清晰名称、`supports` 判断、`afterTurn` 执行和结构化结果，但触发权在 `PracticeTurnOrchestrator`。

## API 契约

### Session 响应扩展

`PracticeSessionResponse` 增加：

```ts
export interface PracticeCompletionGate {
  canComplete: boolean;
  reasonCode: 'NO_REVIEW' | 'LATEST_REVIEW_FAILED' | 'PASSED' | 'ALREADY_COMPLETED';
  message: string;
  latestScore?: number;
  passScore: number;
}

export interface PracticeCodeReviewSummary {
  id: number;
  versionNo: number;
  language: string;
  totalScore: number;
  passed: boolean;
  createdAt: string;
}
```

```ts
export interface PracticeSessionResponse {
  session: PracticeSessionSummary;
  problem: PracticeProblemSummary;
  messages: PracticeMessage[];
  activeRun?: PracticeActiveRun | null;
  latestReview?: PracticeCodeReviewSummary | null;
  completionGate: PracticeCompletionGate;
}
```

### Review 列表

```text
GET /api/practice-sessions/{sessionId}/reviews
```

响应：

```ts
export interface PracticeCodeReviewHistoryResponse {
  latestReview: PracticeCodeReviewSummary | null;
  reviews: PracticeCodeReviewSummary[];
  completionGate: PracticeCompletionGate;
}
```

### Review 详情

```text
GET /api/practice-sessions/{sessionId}/reviews/{reviewId}
```

响应：

```ts
export interface PracticeCodeReviewDetail extends PracticeCodeReviewSummary {
  rawCode: string;
  normalizedCode: string;
  evidence: PracticeCodeReviewEvidence[];
  contextSummary: string;
  scores: PracticeCodeReviewScore;
  deductionReasons: string[];
  improvementSuggestions: string[];
  reviewMarkdown: string;
  userMessageId: number;
  assistantMessageId?: number | null;
}
```

### 完成接口

复用现有：

```text
PATCH /api/practice-sessions/{sessionId}/progress-status
```

当请求 `COMPLETED` 时新增 gate 校验。禁止完成返回 `ApiResponse.error`，错误码为上文定义的稳定业务码。

## 前端设计

改造 `frontend/src/learning-plans/PracticeChatWorkbench.tsx`：

- 加载 session 后保存 `latestReview` 和 `completionGate`。
- 完成按钮常驻显示；根据 `completionGate.canComplete` 禁用。
- 禁用时桌面端在按钮旁展示短说明，移动端直接显示说明文本。
- composer placeholder 改为 `输入你的思路、问题，或粘贴完整 LeetCode 代码生成 Review...`。
- composer 上方增加短提示：`粘贴完整 LeetCode 代码后，AI 会自动生成 Review 记录并更新完成资格。`
- SSE `agent_run_end` 后执行 `refreshMessages()`、`refreshSession()`、`refreshReviews()`。

新增或拆分组件：

- `ReviewHistoryDrawer`：真实 Review 抽屉。
- `ReviewScoreBadge`：分数和是否及格。
- `ReviewVersionList`：版本列表。
- `ReviewDetailPanel`：代码快照、点评、建议。
- `CompletionGateHint`：完成资格说明。

空态：

```text
还没有 Review 记录。粘贴完整 LeetCode 代码后，AI 会自动生成 Review。
```

未及格说明：

```text
最近一次 Review 为 5/10，达到 6 分后可标记完成。
```

## 安全与隐私

- 不在日志中输出完整代码、用户聊天内容、Authorization 或 API key。
- Review 失败日志只记录 `sessionId`、`runId`、错误码和异常类型。
- AI governance metadata 增加 `practiceSessionId`、`planId`、`phaseIndex`、`problemSlug`、`reviewCandidate=true`。
- 结构化 Review 调用设置独立 purpose/source，便于后续用量统计。
- Review 表按 `user_id` 做访问校验；所有查询必须带当前登录用户。

## 指标

新增 Micrometer 指标：

- `practice.code_review.candidate.count`
- `practice.code_review.saved.count`
- `practice.code_review.not_complete.count`
- `practice.code_review.failed.count`
- `practice.code_review.duration`
- `practice.completion.blocked.no_review.count`
- `practice.completion.blocked.review_failed.count`

指标标签控制在低基数：

- `language`
- `passed`
- `failureCode`

不把 `problemSlug` 作为默认指标标签，避免高基数。

## 错误处理

- Review capability 失败不让聊天 run 失败；用户仍能看到 AI 回复。
- Review 失败时 `agent_run_end` metadata 标记 `practiceCapabilities.codeReview.status=FAILED`，前端刷新后仍无新增 Review。
- 完成门槛接口是强校验；即使前端状态过期，后端仍阻止无有效 Review 或最近 Review 未及格的完成请求。
- 如果用户先有 7 分 Review，后一次提交得到 5 分，最近 Review 更新后 completion gate 变为不可完成。

## 测试计划

后端单元测试：

- `PracticeCodeReviewStructuredOutputMapperTest`：分数边界、总分归一化、passed 归一化、非完整提交不落库。
- `PracticeCompletionGateTest`：无 Review、最近失败、历史通过但最近失败、最近通过、已完成。
- `PracticeTurnClassifierTest`：完整 `class Solution`、完整函数、代码片段、报错日志、伪代码、普通聊天。
- `CodeReviewTurnCapabilityTest`：非候选消息返回 `NOT_CODE_LIKE` 且不调模型、完整提交保存、模型判定非本题不保存、幂等 replay 不重复调用模型、失败不影响聊天流。
- `PracticeCodeReviewServiceTest`：结构化 Review prompt、完整提交保存、模型判定非本题不保存、幂等冲突复用已有 Review。
- `PracticeTurnOrchestratorTest`：`AgentRunEnd` 后组装 context、执行 capability、合并 metadata、replay 不重复触发、异常隔离。

后端 API/Repository 测试：

- `MyBatisPracticeCodeReviewRepositoryTest`：版本号递增、最近 Review、用户隔离、唯一约束。
- `PracticeCodeReviewControllerTest`：列表、详情、跨用户禁止访问。
- `PracticeSessionControllerTest`：session 响应包含 completion gate，完成接口按 gate 阻止或允许。
- Flyway migration resource test 覆盖 `V13__practice_code_review_schema.sql`。

前端测试：

- Review 抽屉空态。
- 有 Review 时展示版本、分数、代码快照和建议。
- 完成按钮在无 Review、未及格、已及格三种状态下展示正确文案。
- 发送疑似代码后，`agent_run_end` 触发 reviews/session 刷新。
- 后端完成接口返回 gate 错误时展示错误消息。

## 实施切分

第一阶段：后端数据和 gate

- 新增 Review 表迁移。
- 新增 Review 领域模型、repository 端口和 MyBatis 实现。
- 扩展 `PracticeSessionService` 聚合 latest review 和 completion gate。
- 完成接口接入 gate。

第二阶段：练习回合编排和结构化 Review capability

- 新增 `PracticeTurnOrchestrator`、`PracticeTurnContext`、`PracticeTurnClassifier`、`PracticeTurnCapability` 和 registry。
- 新增 `CodeReviewTurnCapability`、prompt builder、JSON Schema、mapper 和 `PracticeCodeReviewService`。
- `PracticeMessageStreamService` 委托 orchestrator 管理 run 后 capability 执行。
- practice chat prompt 增加代码提交回复策略。
- 增加 Review 相关指标和治理 metadata。

第三阶段：API 和前端闭环

- 新增 reviews 列表和详情 API。
- 扩展前端类型与 API client。
- 将 Review 抽屉从占位升级为版本历史。
- 完成按钮改为 gate 驱动。
- 更新中英文文案。

## 后续演进

- 将慢 capability 拆到异步 worker，支持 pending 状态和重试。
- 当出现多分支、多步骤、可恢复工作流需求时，再评估是否引入通用 workflow/graph 引擎。
- 为题库增加显式入口函数、代码模板和语言签名字段，提高识别准确率。
- 增加 Review 版本 diff。
- 基于 Review 事实表聚合用户画像、薄弱标签和阶段复盘。
- 对结构化输出失败引入一次自动修复调用。
