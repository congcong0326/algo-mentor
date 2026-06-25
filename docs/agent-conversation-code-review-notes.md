# Agent Conversation 代码 Review 改进点

## 背景

本次 review 聚焦 `AgentConversationRunCoordinator.stream(...)` 的职责、幂等回放逻辑、task 级运行锁，以及前端刷新/重复提交时的用户体验。

当前 `stream(...)` 的主要职责是应用层编排：

- 创建或复用会话 run；
- 处理同一 task 的并发运行锁；
- 根据 `Idempotency-Key` 识别重复提交；
- 组装 `AgentRequest` 并启动 `AgentLoopRunner`；
- 返回可被 Controller/SSE 层订阅的 `Flow.Publisher<AgentStreamEvent>`。

## 已发现问题

### 1. 刷新页面后无法恢复正在生成的回答

当前前端刷新页面后会生成新的 `Idempotency-Key`。如果原 run 仍在执行，用户再次对同一个 `taskId` 发送请求时，后端会因为 task 级锁仍被占用返回 `AGENT_RUN_IN_PROGRESS`。

这能防止同一个 task 并发创建多个 Agent run，但用户体验不完整：页面刷新后既看不到原 SSE 输出，也不能继续接回正在生成的回答，只能看到“当前会话正在生成回答”。

改进方向：

- 前端收到 `AGENT_RUN_IN_PROGRESS` 时，展示专门的生成中状态，不要把错误直接作为 assistant 消息追加到聊天内容中；
- 后端提供查询当前 task 运行状态的接口，例如 `GET /api/agent/conversations/tasks/{taskId}/active-run`；
- 前端刷新后先查询 active run，若存在 running run，则禁用发送并展示“回答仍在生成中”；
- run 完成后重新拉取会话消息列表，恢复最终回答。

### 2. 幂等回放只回放 run 身份事件，不回放模型输出

当前相同 `Idempotency-Key` 命中已有 run 时，`replayPublisher(...)` 只发送：

- `agent_run_start`
- `agent_run_end`

metadata 中会标记 `idempotentReplay = true`。

这能告诉调用方“这个请求已经对应一个已有 run”，但不会重放历史 token、工具调用、assistant 消息或完整 SSE 事件。它更适合防重复提交，不适合断线续传。

改进方向：

- 注释和命名中明确“幂等回放”不是完整内容回放；
- 如需支持刷新恢复，应持久化 assistant 输出或 Agent stream event；
- 新增按 `runId` 或 `idempotencyKey` 查询完整 run 结果/事件的接口。

### 3. 前端 Idempotency-Key 生命周期偏短

当前行为：

- `PracticeChatWorkbench` 每次发送消息都会调用 `generateClientId()` 生成新 key；
- `AiDebugConsole` 将 key 放在 React state 中，页面刷新后也会重新生成；
- 如果请求头缺失，Controller 会在后端生成随机 key，但这个 key 不会回传给前端用于后续重试。

这意味着刷新页面后通常无法用旧 key 识别同一次用户动作。

改进方向：

- 同一次用户提交在网络重试、超时重试时必须复用同一个 key；
- 对需要恢复的场景，前端应把当前 run 的 `idempotencyKey/runId/taskId` 暂存到 `sessionStorage` 或后端会话状态中；
- 对核心接口可以考虑要求前端显式传入 `Idempotency-Key`，避免服务端生成后前端无法复用。

### 4. 409 冲突响应需要产品化处理

`AgentConversationRunInProgressException` 当前会映射成 HTTP `409 CONFLICT`，错误码为 `AGENT_RUN_IN_PROGRESS`。

这在协议层是合理的，但前端需要把它作为可恢复状态处理，而不是普通失败。

改进方向：

- 前端 API 层识别 `AGENT_RUN_IN_PROGRESS`；
- 聊天 UI 展示“已有回答正在生成”状态；
- 提供“查看当前生成状态”或“稍后刷新结果”的操作；
- 避免将该错误文案混入 assistant 正文。

### 5. 系统提示词组装机制不统一

当前项目中实际核心业务主要是生成学习计划和练习聊天，但提示词入口并不统一：

- 练习聊天使用 `PracticeChatPromptSectionProvider`、`PracticeChatPromptProfileResolver` 和 `DefaultPromptAssembler` 动态组装；
- 流式生成学习计划使用 `LearningPlanDraftPromptBuilder` 手写 `system + user` prompt；
- legacy 普通会话仍保留 `AgentConversationService.DEFAULT_MENTOR_SYSTEM_PROMPT`，并通过 `ContextAssembler` 组装；
- `AgentRunPreparationRequest.systemPrompt` 仍作为 run prepare 的字段存在，但在 practice chat 路径中不会进入最终 `AgentRequest.messages()`。

这不会直接造成模型请求里的系统提示词冲突，因为 practice chat 分支会绕过 `draft.systemPrompt()`，使用专用 prompt assembler。但从代码阅读和后续演进角度看，职责边界不够清晰：run 准备、会话持久化和业务 prompt 组装混在同一条调用链里，容易让人误判某个场景到底用了哪套系统提示词。

改进方向：

- 统一成“按业务场景组装 Prompt”的模型，把生成计划和练习聊天都作为 first-class prompt profile；
- 将 `LearningPlanDraftPromptBuilder` 迁移为 `LearningPlanDraftPromptSectionProvider` 和 `LearningPlanDraftPromptProfileResolver`；
- 将学习计划提示词拆成平台/安全规则、计划生成规则、本地题库使用规则、用户画像与计划参数、结构化输出约束等 section；
- 明确 `AgentConversationService.DEFAULT_MENTOR_SYSTEM_PROMPT` 仅用于 legacy 普通会话，或在业务路径收敛后移除；
- 弱化或移除 `AgentRunPreparationRequest.systemPrompt` 对业务 prompt 的承载职责，让 prepare run 只负责 task/turn/run/message 的创建和恢复。

## 建议优先级

### P0：澄清当前语义

- 保留现有锁和幂等逻辑；
- 在代码注释、接口文档中说明当前幂等回放只返回 run 身份事件；
- 前端识别 `AGENT_RUN_IN_PROGRESS` 并展示专门状态。
- 标注 `DEFAULT_MENTOR_SYSTEM_PROMPT` 的 legacy 语义，避免误以为它参与所有业务场景。

### P1：支持刷新后的结果恢复

- 后端提供 task 当前 run/status 查询接口；
- 后端提供按 task 拉取最近消息的接口；
- 前端刷新后先恢复 task 状态，再决定是否允许发送新消息。
- 将学习计划生成迁移到与练习聊天一致的 Prompt profile/section 体系。

### P2：支持完整断线续传

- 持久化 Agent stream event 或 assistant 增量输出；
- 支持按 `runId` 从某个事件序号继续读取；
- 前端保存 `runId` 和 last event cursor，刷新后重新订阅或补拉事件。

## 相关代码位置

- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationRunCoordinator.java`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationService.java`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/stream/LearningPlanDraftPromptBuilder.java`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptSectionProvider.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/context/ContextAssembler.java`
- `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/repository/PostgresAgentConversationRepository.java`
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AgentConversationController.java`
- `frontend/src/learning-plans/PracticeChatWorkbench.tsx`
- `frontend/src/ai-debug/AiDebugConsole.tsx`
- `frontend/src/services/api.ts`
