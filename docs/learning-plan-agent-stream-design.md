# 学习计划大模型流式生成设计

## 背景

当前 `POST /api/learning-plans/drafts` 已经接入 AI 治理 admission/lifecycle，但学习计划内容仍由 `LearningPlanAgentService` 在本地静态生成。它根据用户目标、周期、水平、偏好标签和题库搜索结果拼接阶段、目标和推荐题目，没有调用 `LlmGateway` 或 OpenAI provider。

后续希望把学习计划草案生成交给大模型，并让用户在等待期间看到模型仍在工作。这个等待反馈不应直接展示完整模型 token 或半成品 JSON，而应展示简短的后台状态，例如“正在分析目标...”“正在搜索候选题...”。最终仍返回结构化、可校验、可保存的 `LearningPlanDraftResponse`。

项目已经有通用 Agent loop：

```text
AgentLoopRunner
  -> AgentLoopLifecycle
  -> AgentStreamEvent
  -> AgentConversationController
  -> LlmStreamSseMapper
  -> 调试型 SSE 事件
```

现有 `AgentConversationController` 更适合调试和开发观察，会返回较完整的模型执行过程，包括 token、工具调用、step、run 结束等事件。学习计划页面需要的是更克制的产品级状态提示。因此本设计不修改现有调试接口语义，而是在同一组 `AgentStreamEvent` 上叠加一层“用户工作状态投影”。

## 目标

- 使用大模型生成学习计划草案，替换当前静态拼接方案。
- 新增学习计划流式创建接口，最终输出结构化草案并落库。
- 复用现有 `AgentLoopRunner`、工具调用、取消、max steps、AI 治理和 trace 能力。
- 复用题库工具，让模型可以探索本地题库，同时后端校验并补齐最终题目，避免幻觉题目。
- 提供通用的用户工作状态事件，后续聊天页、AI 讲解页也能复用同一套“小框状态”机制。
- 保留现有调试型流式接口，不影响 `AgentConversationController` 的事件明细。

## 非目标

- 不把学习计划生成做成只靠模型自由文本解析的流程。
- 不把完整中间 JSON、工具参数、工具结果原样暴露给普通用户界面。
- 不在本次设计中移除或重写现有 `/api/learning-plans/drafts` 同步接口。
- 不改 `AgentLoopLifecycle` 的核心事件语义；新增逻辑应作为下游事件消费或 API 层包装。

## 两类流式视图

### Debug Profile

Debug Profile 是现有 `AgentConversationController` 的定位。

事件特点：

- 保留完整 `agent_run_start`、`agent_step_start`、`content_delta`、`tool_call_start`、`tool_call_delta`、`agent_tool_start`、`agent_tool_end`、`agent_run_end`、`agent_error` 等事件。
- 适合调试模型真实输出、工具调用参数、step 行为和错误。
- 不做过度截断、翻译或隐藏。

这个 profile 继续由现有 `LlmStreamSseMapper` 支撑，不因学习计划接入而改变。

### User Work Profile

User Work Profile 是新增的产品级状态投影。

它消费同样的 `AgentStreamEvent`，但对外只发布简化事件：

- `work_start`
- `work_progress`
- `work_tool_start`
- `work_tool_end`
- `work_done`
- `work_error`

它的职责是：

- 把模型文本 delta 聚合成短 preview。
- 对 preview 做截断和节流。
- 把工具名映射成用户可读文案。
- 隐藏原始工具参数、完整工具结果和中间 JSON。
- 统一错误事件格式。
- 让多个业务页面可以复用同一个状态小框组件。

## 通用工作状态投影

建议新增通用组件：

```text
AgentWorkStatusProjector
  输入: AgentStreamEvent
  输出: AgentWorkStatusEvent
  配置: AgentWorkStatusProfile
```

`AgentWorkStatusProfile` 用于描述业务场景：

- `scenario`：场景标识，例如 `learning_plan`、`learning_chat`。
- `progressPrefix`：模型文本预览前缀，例如“正在规划”“正在思考”。
- `toolLabels`：工具名到展示文案的映射。
- `previewMaxChars`：预览最大字符数，建议 24。
- `progressThrottleMillis`：进度事件节流，建议 500ms。
- `emitDone`：是否在 agent run 结束时发送 `work_done`。

学习计划场景示例：

```text
scenario: learning_plan
progressPrefix: 正在规划
toolLabels:
  list_problem_filters -> 正在查询题库标签
  search_problems -> 正在搜索候选题
  get_problem_statement -> 正在读取题目信息
previewMaxChars: 24
progressThrottleMillis: 500
```

聊天场景示例：

```text
scenario: learning_chat
progressPrefix: 正在思考
toolLabels:
  search_problems -> 正在查询相关题目
  get_problem_statement -> 正在读取题目内容
```

### 通用事件格式

`work_start`

```json
{
  "runId": "uuid",
  "scenario": "learning_plan",
  "message": "开始生成学习计划"
}
```

`work_progress`

```json
{
  "runId": "uuid",
  "scenario": "learning_plan",
  "message": "正在规划：根据你的目标拆分...",
  "preview": "根据你的目标拆分..."
}
```

`work_tool_start`

```json
{
  "runId": "uuid",
  "scenario": "learning_plan",
  "toolName": "search_problems",
  "message": "正在搜索候选题"
}
```

`work_tool_end`

```json
{
  "runId": "uuid",
  "scenario": "learning_plan",
  "toolName": "search_problems",
  "message": "候选题搜索完成"
}
```

`work_done`

```json
{
  "runId": "uuid",
  "scenario": "learning_plan",
  "message": "生成完成"
}
```

`work_error`

```json
{
  "runId": "uuid",
  "scenario": "learning_plan",
  "code": "RESPONSE_PARSE_FAILED",
  "message": "学习计划生成失败，请稍后重试。",
  "retryable": true
}
```

## 学习计划流式接口

新增接口：

```text
POST /api/learning-plans/drafts/stream
Accept: text/event-stream
Content-Type: application/json
```

请求体复用 `LearningPlanCreateDraftRequest`。

响应事件包含两类：

- 通用工作状态事件：`work_start`、`work_progress`、`work_tool_start`、`work_tool_end`、`work_done`、`work_error`。
- 学习计划业务结果事件：`draft_ready`、`draft_error`。

`draft_ready` data 使用现有 `LearningPlanDraftResponse`：

```json
{
  "draftId": 100,
  "status": "GENERATED",
  "assistantMessage": "已生成学习计划草案。",
  "missingFields": [],
  "draftPlan": {
    "title": "..."
  }
}
```

`draft_error` 用于学习计划业务失败，例如模型输出 JSON 无法映射为草案、校验失败、题目补齐失败等。底层 Agent 错误可同时被投影为 `work_error`，业务层再发更贴近学习计划语义的 `draft_error`。

## 服务端编排

建议新增学习计划专用流式编排服务：

```text
LearningPlanDraftStreamService
  -> 创建初始 LearningPlanDraft，状态 COLLECTING
  -> 构造 AgentRequest
  -> 调用 AgentLoopRunner.stream(...)
  -> 订阅 AgentStreamEvent
  -> 使用 AgentWorkStatusProjector 发送 work_* 事件
  -> 捕获 AgentLoopObserver.onFinalOutput 的结构化输出
  -> 映射为 LearningPlanDraftPlan
  -> 后端校验、题目补齐、落库
  -> 发送 draft_ready
```

第一阶段不新增 `LearningPlanDraftStatus.GENERATING`。生成过程中的“正在运行”由 SSE `work_*` 事件表达；草案落库状态仍复用现有 `COLLECTING`、`GENERATED`、`GENERATION_FAILED`、`CONFIRMED` 等状态。

这里推荐使用 `AgentLoopObserver.onFinalOutput` 捕获最终 `AgentOutput`，而不是把完整结构化结果塞进 `AgentRunEnd` 的 SSE metadata。原因：

- `AgentRunEnd` 是通用终态事件，不应该携带业务 DTO。
- Debug Profile 不需要被业务结构污染。
- 学习计划落库需要强类型校验和领域处理，放在 application 边界更合适。

### AgentRequest

学习计划生成请求应启用 provider-native structured output：

- `responseFormat` 使用 `LlmResponseFormat.JsonSchema`。
- `structuredOutput.strategy` 使用 `PROVIDER_NATIVE`。
- `schemaName` 使用 `learning_plan_draft`。
- `schemaVersion` 使用 `v1`。
- `required` 为 `true`。

prompt 应明确：

- 输出必须符合学习计划 JSON schema。
- 推荐题目必须来自工具返回的本地题库候选。
- 不允许编造题目 slug、标题、难度或标签。
- 如题库候选不足，可以输出较少题目，并在 metadata 标记推荐不完整。
- 计划阶段、目标、验收标准和复盘建议使用中文。

### 题库工具策略

采用混合策略：

- 模型可以调用题库工具探索题库。
- 后端最终校验所有题目是否存在于本地题库。
- 后端可根据 `topicPreferences`、`difficultyPreference`、阶段 focus tag 补齐候选题。
- 后端拒绝或剔除模型返回但本地不存在的题目。

推荐工具：

- `list_problem_filters`
- `search_problems`
- `get_problem_statement`

最终 `LearningPlanDraftPlan` 中的 `LearningPlanProblemDraft` 必须满足：

- `slug` 存在。
- `difficulty` 与本地题库一致。
- `title/titleCn/tags` 以本地题库为准。
- `orderIndex` 由后端重排。

## 与现有同步接口的关系

第一阶段保留：

```text
POST /api/learning-plans/drafts
```

新接口完毕后将该接口删除，并且清理相关代码

## 前端接入

学习计划页面新增流式创建逻辑：

- 提交表单后调用 `/api/learning-plans/drafts/stream`。
- 展示通用 `AgentWorkIndicator` 小框。
- 收到 `work_*` 事件时更新小框状态。
- 收到 `draft_ready` 时关闭生成态并展示现有草案预览。
- 收到 `draft_error` 或 `work_error` 时展示失败状态和重试入口。

`AgentWorkIndicator` 应设计为业务无关组件：

- 输入当前 `work_*` event。
- 展示一行状态文案。
- 不展示原始工具参数。
- 不展示完整模型输出。
- 可以被后续聊天页复用。

## 错误处理

常见失败类型：

- AI 治理拒绝：额度、并发、策略不允许。
- LLM provider 错误：超时、限流、认证失败、内容过滤。
- 工具执行失败：题库不可用、搜索参数非法。
- 结构化输出失败：模型未返回合法 JSON 或不符合 schema。
- 领域校验失败：阶段数量、题目数量、字段缺失、题目不存在。
- 客户端断开：取消上游订阅，停止继续生成。

处理原则：

- provider/tool/agent 错误投影为 `work_error`。
- 学习计划领域错误额外发 `draft_error`。
- 日志和 trace 保留详细错误；SSE 给用户的错误文案应脱敏。
- 取消和断开不应继续落库半成品草案，除非已经完成并进入 `draft_ready` 阶段。

## 可观测性与治理

学习计划流式接口应继续使用 AI 治理：

- `AiPurpose.LEARNING_PLAN`
- `AiRunSource.LEARNING_PLAN_DRAFT`
- `streaming=true`
- request size 复用当前计算方式
- usage 从 LLM stream usage 或 Agent observer 中汇总
- 成功、失败、取消分别更新 lifecycle

trace 中可以保留完整 Debug Profile 级别的事件和工具结果引用，但普通用户 SSE 只暴露 User Work Profile。

## 兼容性影响

- 不改变 `AgentConversationController`。
- 不改变 `LlmStreamSseMapper` 的现有事件输出。
- 不改变 `AgentLoopLifecycle` 的已有事件语义。
- 新增 `AgentWorkStatusProjector` 是下游消费者，不影响现有 Agent loop 执行。
- 新增学习计划流式接口后，前端可渐进切换。

## 测试建议

后端测试：

- `AgentWorkStatusProjectorTest`
  - `ContentDelta` 聚合、截断、节流。
  - tool start/end 映射为用户文案。
  - error 映射为 `work_error`。
- `LearningPlanDraftStreamServiceTest`
  - 成功生成结构化草案并保存 draft。
  - 工具返回候选题后模型输出题目被本地校验。
  - 模型返回不存在 slug 时后端剔除或补齐。
  - 结构化输出非法时发 `draft_error`。
  - 客户端取消时取消上游订阅。
- `LearningPlanControllerTest`
  - `/drafts/stream` 返回 `text/event-stream`。
  - 未登录请求被拒绝。
  - AI 治理拒绝时返回统一错误。

前端测试：

- 流式创建时状态小框随 `work_*` 事件更新。
- `work_progress` 只展示短 preview。
- `draft_ready` 后展示现有草案预览。
- `draft_error/work_error` 后允许用户重试。

## 实施顺序建议

1. 新增通用 `AgentWorkStatusProjector` 和事件模型。
2. 新增学习计划 JSON schema、prompt builder 和结构化输出 mapper。
3. 新增 `LearningPlanDraftStreamService`，复用 `AgentLoopRunner` 和题库工具。
4. 新增 `/api/learning-plans/drafts/stream` controller 方法。
5. 前端新增 `AgentWorkIndicator`，学习计划创建流程切换到流式接口。
6. 删除旧接口以及相关废弃逻辑，确认无回归。
