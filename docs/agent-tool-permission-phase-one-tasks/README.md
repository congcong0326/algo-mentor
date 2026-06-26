# Agent Tool 权限人在回路阶段一执行拆解

## 文档定位

本文把 `docs/agent-tool-permission-phase-one-design.md` 拆成按依赖排序的工程执行任务。每个任务以小闭环为目标，默认可在 30~60 分钟内完成并单独验证。

执行约束：

- 只实现设计文档明确提出的阶段一能力。
- 不恢复旧的 `PracticeTurnClassifier -> CodeReviewTurnCapability` 自动 Review 链路。
- 不实现 forced tool calling、后端意图识别、持久化 pending decision、多实例共享状态或永久授权记忆。
- 不重构无关代码；任务涉及文件以本清单列出的文件和同包测试为边界。
- 每个任务完成后优先运行最小相关测试；整体验收前再运行更大范围测试。

## 任务 01：确认阶段零状态并补齐受信用户 metadata

### 任务目标

确认旧 post-run 自动 Review capability 已下线，并把当前登录用户 ID 写入 `AgentRequest.metadata`，为后续权限 owner 校验和 Review tool 提供受信上下文。

### 涉及文件

- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationService.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/model/AgentRuntimeMetadataKeys.java`
- `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationServiceTest.java`
- `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeTurnOrchestratorTest.java`

### 具体修改点

- 在 `AgentRuntimeMetadataKeys` 新增受信用户 ID 常量，名称待确认问题 1 确认后再落地。
- 在 `AgentConversationService.toConversationRun(...)` 组装 metadata 时写入 `command.userId()`。
- 检查 `PracticeTurnOrchestrator` 仍只负责校验练习会话并启动普通聊天 run，不新增 Review 执行逻辑。
- 更新或新增测试断言：prepare practice chat run 后，`AgentRequest.metadata` 包含受信用户 ID、`taskId`、`turnId`、`runDbId`、practice 引用 metadata。

### 验收标准

- `AgentConversationService` 生成的 `AgentRequest.metadata` 能稳定读取当前用户 ID。
- 旧自动 Review capability 相关测试没有重新引入旧类或旧 metadata。
- 缺少用户 ID 的非预期路径不会被静默伪造为其他用户。

### 风险点

- 用户 ID key 与治理侧 metadata key 不一致会导致后续 owner 校验读取失败。
- `findRunByIdempotencyKey(String, String)` 目前构造了默认 userId，需要确认该路径是否会进入权限场景。

## 任务 02：新增 agent-core 权限基础模型

### 任务目标

在 `agent-core` 建立权限决策的纯模型和常量，不接入 runner，也不引入等待逻辑。

### 涉及文件

- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionBehavior.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionDecisionType.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionCheck.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionDecisionPlan.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionAuthorization.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionRequest.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionDecision.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionMetadataKeys.java`
- `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionModelTest.java`

### 具体修改点

- 新增 `ALLOW`、`DENY`、`ASK`、`PASSTHROUGH` 行为枚举。
- 新增 `ALLOW`、`DENY` 决策类型枚举。
- 新增 `AgentToolPermissionCheck`，包含 `AgentLoopContext`、`stepIndex`、`LlmToolCall`、`AgentTool`、`trustedMetadata`。
- 新增 `AgentToolPermissionDecisionPlan`，提供静态工厂方法：`allow`、`deny`、`ask`、`passthrough`。
- 新增 sealed authorization：`Allowed` 与 `SyntheticResult`。
- 新增请求、决策 record 与 metadata key 常量。
- 基础模型做必要参数校验和防御性拷贝。

### 验收标准

- 模型类可以独立编译并通过单元测试。
- `ASK` plan 必须包含可展示的 `displayName`、`reason` 和非空 preview。
- `PASSTHROUGH` 不携带业务决策信息。
- metadata key 没有散落在其他新代码中。

### 风险点

- 过早把业务字段放进 core 模型会污染 `agent-core`。
- `JsonNode` synthetic result 与 Map preview 的边界需要保持清晰。

## 任务 03：实现权限 hook 链与默认策略

### 任务目标

提供可插拔的权限决策链，实现按顺序执行、第一个非 passthrough 生效、默认 allow、hook 异常安全失败。

### 涉及文件

- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionHook.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionHookChain.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/ToolNamePermissionHook.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/DefaultPermissionHook.java`
- `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionHookChainTest.java`
- `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/permission/ToolNamePermissionHookTest.java`

### 具体修改点

- 新增 hook 接口：`order()` 与 `evaluate(...)`。
- `AgentToolPermissionHookChain` 按 `order` 排序并执行 hooks。
- 所有 hook 返回 `PASSTHROUGH` 时返回默认 `ALLOW`。
- hook 抛出异常时返回 `DENY` plan，`policySource` 标识失败来源，metadata 中保留低敏错误类型。
- `ToolNamePermissionHook` 支持按工具名配置 behavior，主要服务设计文档中的工具名策略。
- `DefaultPermissionHook` 兜底返回 `ALLOW`。

### 验收标准

- hook 顺序稳定。
- 第一个 `ALLOW`、`DENY` 或 `ASK` 会停止链路。
- passthrough 能继续到下一 hook。
- hook 异常不会允许执行工具。
- 默认策略保持现有低风险工具行为不变。

### 风险点

- `ToolNamePermissionHook` 如果和业务 hook 都匹配同一工具，order 决定最终行为，需要在装配任务里明确顺序。
- hook 异常的错误信息不能包含 tool arguments 中的敏感内容。

## 任务 04：实现 synthetic result factory

### 任务目标

统一生成用户拒绝、超时、取消和策略拒绝的 tool result，保证这些结果可回填给模型。

### 涉及文件

- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionResultFactory.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/model/AgentToolResultTypes.java`
- `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionResultFactoryTest.java`

### 具体修改点

- 新增生成 denied、timeout、cancelled synthetic result 的方法。
- result 字段遵循设计文档：`type`、`toolName`、`toolCallId`、`permissionRequestId`、`message`、`reason`、`retryable`。
- 如已有 `AgentToolResultTypes` 管理 tool result 类型，则增加权限相关类型常量。
- 不把完整 tool arguments 写入 synthetic result。

### 验收标准

- 拒绝结果 type 为 `tool_permission_denied`，不标记 retryable。
- 超时结果 type 为 `tool_permission_timeout`，标记 retryable。
- 取消结果能明确表达 run cancelled，且不执行真实工具。
- 输出 JSON 结构稳定，便于前端和模型消费。

### 风险点

- result 文案过业务化会让 core 依赖 practice 场景；core 文案应保持通用或只描述工具未执行。
- 如果 type 常量分散，前端判断 `agent_tool_end` 时容易出现不一致。

## 任务 05：实现内存权限 coordinator

### 任务目标

实现阶段一内存 pending request、等待用户决策、超时、取消和 owner 校验，不接入 HTTP controller。

### 涉及文件

- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionCoordinator.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/InMemoryAgentToolPermissionCoordinator.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionDecisionResult.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionException.java`
- `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/permission/InMemoryAgentToolPermissionCoordinatorTest.java`

### 具体修改点

- 定义 coordinator 接口：`authorize(...)` 与 `decide(...)`。
- `ALLOW` 返回 `AgentToolPermissionAuthorization.Allowed`。
- `DENY` 返回 synthetic denied result。
- `ASK` 创建 `AgentToolPermissionRequest`，保存 pending request，并等待 future 完成、timeout 或 cancellation。
- `decide(...)` 校验 request 存在、owner 匹配、未过期、未决策，并完成 future。
- 定义供 controller 映射 HTTP 状态使用的异常或结果类型。
- 等待完成后移除 pending request。

### 验收标准

- allow/deny/ask 三种 plan 都有明确结果。
- 用户允许后返回 `Allowed`。
- 用户拒绝、超时、取消后返回 synthetic result，真实工具不会执行。
- 非 owner 决策失败。
- 重复决策、过期决策、未知 request 有可区分错误。
- pending request 在完成后清理。

### 风险点

- 测试不能依赖 60 秒真实 timeout，应允许注入短 timeout 或 clock。
- cancellation 与用户决策竞争时要保证只有一个结果生效。
- 内存实现只适用于单实例，文档和配置说明需保留这个限制。

## 任务 06：补充权限 SSE 事件与 lifecycle 门面

### 任务目标

让权限请求、决策、超时成为核心流事件，并在 lifecycle 中提供 runner 可调用的 `beforeToolExecution` 门禁点。

### 涉及文件

- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentStreamEvent.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentStreamEventNames.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentLoopLifecycle.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentLoopObserver.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionGuard.java`
- `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/AgentStreamEventTest.java`
- `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/AgentLoopLifecycleTest.java`

### 具体修改点

- `AgentStreamEventNames` 新增 `tool_permission_request`、`tool_permission_decision`、`tool_permission_timeout`。
- `AgentStreamEvent` 新增对应 record，并加入 sealed permits。
- `AgentLoopObserver` 新增默认权限事件回调，用于后续指标和审计。
- `AgentLoopLifecycle` 新增 `beforeToolExecution(...)`，内部调用 `AgentToolPermissionGuard`。
- `AgentLoopLifecycle` 新增发布权限事件的方法，供 coordinator 或 guard 调用。
- `AgentToolPermissionGuard` 从 `AgentLoopContext` 构造 check，组合 hook chain 与 coordinator。

### 验收标准

- 权限事件 record 校验 runId、stepIndex、toolCallId、toolName、permissionRequestId。
- lifecycle 能发布权限 request/decision/timeout 事件并通知 observer。
- runner 后续只需要调用 lifecycle，不直接依赖业务 hook。

### 风险点

- coordinator 需要发布 SSE，但不应直接依赖 `SubmissionPublisher`；发布边界应通过 lifecycle/回调保持清晰。
- sealed interface 新增 permits 容易遗漏导致编译失败。

## 任务 07：把权限门禁接入 AgentLoopRunner

### 任务目标

在每次真实工具执行前经过不可绕过的权限门禁，并支持 `Allowed` 与 `SyntheticResult` 两条路径。

### 涉及文件

- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentLoopRunner.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentLoopLifecycle.java`
- `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/AgentLoopRunnerTest.java`

### 具体修改点

- 扩展 `AgentLoopRunner` 构造函数，保留旧构造函数兼容，默认使用 allow 权限链和内存/no-op coordinator。
- 创建 `AgentLoopLifecycle` 时传入权限 guard 或权限依赖。
- 在找到 `AgentTool` 后、`toolStarted(...)` 前调用 `lifecycle.beforeToolExecution(...)`。
- `Allowed`：保持现有 `agent_tool_start -> executeTool -> afterToolCall -> agent_tool_end -> compact -> tool result` 流程。
- `SyntheticResult`：不发送 `agent_tool_start`，但执行 `afterToolCall`、`agent_tool_end`、compaction 和 tool result 追加。
- 未知工具仍在权限检查前抛 `UNKNOWN_TOOL`。

### 验收标准

- 默认 allow 下现有工具调用测试仍通过。
- deny/timeout synthetic result 不执行真实 tool。
- synthetic result 会进入下一轮 LLM 上下文，run 可继续生成最终回复。
- `agent_tool_start` 只在真实工具执行前发送。
- synthetic result 仍发送 `agent_tool_end`。
- 未知工具行为保持现状。

### 风险点

- 如果 synthetic result 未追加为 tool message，下一轮 LLM tool-calling 协议会不完整。
- 如果 `afterToolCall` 对 synthetic result 不兼容，可能误触发现有 interceptor 逻辑。

## 任务 08：新增后端配置属性与 Spring 装配

状态：已完成。完成备注：配置前缀已落地为 `algo-mentor.agent.tool-permission`，支持 `enabled`、`timeout`、`cleanup-interval`；`enabled=false` 时保留 guard，清空业务 hooks，并使用默认 allow coordinator。阶段一未落地完整 `tool-name-policies` 配置 map，Review ASK 通过业务 hook 注册实现。

### 任务目标

把权限 hook chain、coordinator、guard 装配进应用上下文，并把 timeout/enabled 配置暴露出来。

### 涉及文件

- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorAiConfiguration.java`
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorConfigurationKeys.java`
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/AgentToolPermissionProperties.java`
- `backend/mentor-api/src/main/resources/application.yml`
- `backend/mentor-api/src/test/java/org/congcong/algomentor/api/config/MentorAiConfigurationTest.java`

### 具体修改点

- 新增配置前缀 `algo-mentor.agent.tool-permission`。
- 支持至少 `enabled`、`timeout`、`cleanup-interval`；`tool-name-policies` 是否在阶段一落地见待确认问题 2。
- 注册 `AgentToolPermissionHookChain` bean，汇总所有 `AgentToolPermissionHook`。
- 注册 `InMemoryAgentToolPermissionCoordinator` bean。
- 注册 `AgentToolPermissionGuard` bean。
- 修改 `agentLoopRunner(...)` bean 构造，传入权限 guard 或相关依赖。
- 在默认配置中记录单实例内存 pending request 限制。

### 验收标准

- 未声明业务 hook 时，默认 allow 不破坏现有工具。
- 配置 timeout 能影响 coordinator 等待时间。
- `enabled=false` 的行为明确：要么不装配权限门禁，要么装配默认 allow；具体语义需在实现前确认。
- Spring 配置测试能证明 runner 使用权限 guard。

### 风险点

- `ConditionalOnMissingBean` 可能导致测试上下文中使用了旧构造路径，权限 guard 未实际接入。
- `enabled=false` 如果绕过所有权限，会影响高权限工具安全边界，需要产品/研发确认。

## 任务 09：新增权限决策 API

状态：已完成。完成备注：已新增 `POST /api/agent/tool-permissions/{permissionRequestId}/decision`，body 只接收 `decision` 和 `reason`；controller 通过 `CurrentUserIdProvider` 获取当前认证用户并交给 coordinator 做 owner 校验，覆盖 `401/403/404/409/400` 映射。

### 任务目标

提供前端提交用户允许/拒绝的 HTTP API，并严格做登录与 owner 校验。

### 涉及文件

- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AgentToolPermissionController.java`
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/agent/model/AgentToolPermissionDecisionRequest.java`
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/agent/model/AgentToolPermissionDecisionResponse.java`
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/ApiContractConstants.java`
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AgentToolPermissionExceptionHandler.java`
- `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/AgentToolPermissionControllerTest.java`

### 具体修改点

- 新增路径常量：`/api/agent/tool-permissions/{permissionRequestId}/decision`。
- request body 只包含 `decision` 和 `reason`。
- controller 从 `CurrentUserIdProvider` 获取当前用户，不接受前端声明 userId。
- 调用 `AgentToolPermissionCoordinator.decide(...)`。
- 映射错误语义：未登录 401、非 owner 403、不存在 404、已决策/已过期 409、非法 decision 400。
- response 返回 `permissionRequestId`、`decision`、`accepted`。

### 验收标准

- 登录 owner 可以提交 allow/deny。
- 非 owner 不能决策。
- 已决策、过期、不存在 request 有明确 HTTP 状态。
- path 中 request id 是唯一 request id，body 不重复传 id。

### 风险点

- 当前认证异常/ApiResponse 包装已有项目约定，controller 需要复用现有错误响应风格。
- API 成功只代表决策被接收，不代表真实工具已执行完成，前端文案不能误导。

## 任务 10：映射权限 SSE 到 API 层

状态：已完成。完成备注：SSE mapper 已支持 `tool_permission_request`、`tool_permission_decision`、`tool_permission_timeout`，输出低敏字段，不暴露 owner user id、完整 arguments 或敏感 metadata。

### 任务目标

让核心权限事件通过现有 SSE mapper 传给前端。

### 涉及文件

- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/service/LlmStreamSseMapper.java`
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/service/SseEventNames.java`
- `backend/mentor-api/src/test/java/org/congcong/algomentor/api/service/LlmStreamSseMapperTest.java`

### 具体修改点

- `SseEventNames` 新增权限事件名常量。
- `LlmStreamSseMapper.toSseEvent(...)` 支持 `AgentStreamEvent.ToolPermissionRequest`、`ToolPermissionDecision`、`ToolPermissionTimeout`。
- data record 字段按设计文档输出。
- 不输出 owner user id、完整 arguments 或敏感 metadata。

### 验收标准

- 三类权限 SSE 事件名和字段完整。
- `tool_permission_request` 包含 `expiresAt` 和低敏 `preview`。
- `tool_permission_decision` 包含 decision、reason、decidedAt。
- `tool_permission_timeout` 包含 reason、expiredAt。

### 风险点

- mapper data 字段命名如果与前端类型不一致，会导致弹窗无法展示。
- SSE 不应泄漏 request metadata 中的完整上下文。

## 任务 11：实现 Practice Code Review Agent Tool

状态：已完成。完成备注：`PracticeCodeReviewAgentTool` 已实现，工具从 trusted metadata、`PracticeSessionRepository` 和 `AgentTurnMessageLookupRepository.findByRunId(...)` 获取 user/run/session/message/code 上下文，不信任模型 arguments；成功结果 type 为 `practice_code_review_submitted`。

### 任务目标

新增 `submit_practice_code_review` 工具，复用现有 `PracticeCodeReviewService`，真实执行时生成或复用 Review 记录并返回摘要 JSON。

### 涉及文件

- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewAgentTool.java`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewAgentToolNames.java`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewToolResultMapper.java`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionRepository.java`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeTurnContext.java`
- `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewAgentToolTest.java`

### 具体修改点

- 新增工具名常量：`submit_practice_code_review`、`userIntent`、`notes`。
- 实现 `AgentTool.spec()`，描述工具使用边界和输入 schema。
- `execute(...)` 从 `AgentExecutionContext.requestMetadata()` 读取受信 userId、practice session 相关 metadata、run db id、task id、turn id。
- 校验当前 run 属于 practice chat，session 存在且属于当前用户。
- 获取本轮用户消息或当前会话最近用户提交，用于构造 `PracticeTurnContext`。
- 调用 `PracticeCodeReviewService.review(...)`。
- 用 `PracticeCodeReviewToolResultMapper` 输出 `practice_code_review_submitted` 摘要结果。
- 已存在同一用户消息 Review 时复用现有结果，不重复调用 Review 模型。

### 验收标准

- 工具不信任模型 arguments 中的 userId、sessionId、problemSlug 或完整代码。
- 缺少必要 metadata 时失败。
- 用户/session 不匹配时失败。
- 正常路径能调用 Review service 并返回稳定 JSON。
- 复用已有 Review 时不重复生成。

### 风险点

- 现有 metadata 里可能没有 `practiceSessionId` 或 `userMessageId`，需要确认从 repository/task message 中取值的可靠路径。
- `PracticeTurnContext` 当前字段是否足够支撑 tool 场景，若不足只能做最小补充。

## 任务 12：实现 Practice Code Review 权限 hook

状态：已完成。完成备注：`PracticeCodeReviewPermissionHook` 对 `submit_practice_code_review` 返回 `ASK`，order 为 `ToolNamePermissionHook.DEFAULT_ORDER - 50`；preview 使用受信上下文构造，并脱敏 authorization、cookie、api key、JWT-like token、bearer/JWT 类内容。

### 任务目标

让 `submit_practice_code_review` 命中 `ASK`，并生成低敏 preview，不执行 Review。

### 涉及文件

- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewPermissionHook.java`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewAgentToolNames.java`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionRepository.java`
- `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewPermissionHookTest.java`

### 具体修改点

- 非 `submit_practice_code_review` 返回 `PASSTHROUGH`。
- Review 工具返回 `ASK`，displayName 为“提交代码 Review”，reason 表达“模型请求执行一次正式代码 Review”。
- 从受信 metadata 和 repository 构造 preview：problemSlug、problemTitle、languageHint、codeLength、最多 500 字符 codePreview、effects。
- preview 无法安全构造时返回工具级 fallback preview。
- 不读取模型 arguments 作为授权事实。

### 验收标准

- Review 工具一定返回 ASK。
- preview 截断代码，不包含完整历史、Authorization、API key、JWT、Cookie。
- 缺少上下文时权限机制仍可工作。
- hook 不执行 Review，也不等待用户。

### 风险点

- 代码 preview 来源如果取错，可能暴露历史对话或展示错误代码。
- hook order 如果晚于 `ToolNamePermissionHook` 的 allow 策略，ASK 可能被绕过。

## 任务 13：注册 Review tool 与 permission hook

状态：已完成。完成备注：`AgentConversationApiAutoConfiguration` 已注册 Review tool 与 permission hook，让 practice chat 场景可收集到 `submit_practice_code_review` 工具并在执行前命中业务 ASK hook。

### 任务目标

在 Spring 自动配置中注册 Review tool 和业务权限 hook，让 practice chat 场景可被模型自主调用且执行前询问用户。

### 涉及文件

- `backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfiguration.java`
- `backend/mentor-api/src/test/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfigurationTest.java`
- `backend/mentor-api/src/test/java/org/congcong/algomentor/api/config/MentorAiConfigurationTest.java`

### 具体修改点

- 在依赖齐备时注册 `PracticeCodeReviewAgentTool` bean。
- 在依赖齐备时注册 `PracticeCodeReviewPermissionHook` bean。
- 确认 `AgentToolRegistry(List<AgentTool>)` 能收集到 Review tool。
- 确认 `AgentToolPermissionHookChain(List<AgentToolPermissionHook>)` 能收集到 Review hook。
- 配置 hook order，确保 Review hook 对该工具生效。

### 验收标准

- 有 `PracticeCodeReviewService`、`PracticeSessionRepository` 等依赖时，Review tool bean 存在。
- Review permission hook bean 存在并进入 hook chain。
- 依赖缺失时不破坏非 practice agent 功能。

### 风险点

- 自动配置类包路径是 `org.congcong.algomentor.mentor.api.autoconfigure`，要保持现有 imports 文件和条件装配风格。
- 如果 tool bean 注册在 `AgentToolRegistry` 创建之后，可能不会被收集，需要依赖 Spring bean 创建顺序验证。

## 任务 14：调整 practice chat prompt 的工具边界

状态：已完成。完成备注：practice chat prompt 已加入 `submit_practice_code_review` 使用边界，明确拒绝或超时后不得声称正式 Review 已完成。

### 任务目标

在练习聊天系统 prompt 中加入 Review tool 使用边界，提高模型自主调用概率，但不把 prompt 作为安全边界。

### 涉及文件

- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptConstants.java`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptSectionProvider.java`
- `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptSectionProviderTest.java`

### 具体修改点

- 增加设计文档中的工具使用说明：何时调用、何时不要调用、拒绝/超时后如何继续回复。
- 文案保持中文项目文档风格，但系统 prompt 可按现有 prompt 语言风格处理。
- 不新增后端代码意图识别，不恢复旧 classifier 自动 Review。

### 验收标准

- practice chat prompt 包含 `submit_practice_code_review` 工具名。
- prompt 明确正式 Review 会生成记录并可能影响完成状态。
- prompt 明确普通概念、语法、局部 bug、提示、复杂度分析不要调用工具。
- prompt 明确用户拒绝或超时后不要声称已完成正式 Review。

### 风险点

- prompt 太长可能挤占上下文预算；应只添加必要边界说明。
- 不能让 prompt 暗示工具可绕过用户确认。

## 任务 15：补充权限指标与日志

状态：已完成。完成备注：`agent-core` 已提供 `AgentToolPermissionMetrics` 与 no-op 实现，Micrometer adapter 在 `mentor-api` 配置层实现；指标 tag 保持低基数，`reason` 只进入低敏日志。

### 任务目标

实现设计文档要求的低基数 metrics 和低敏日志，便于观察权限请求、决策、超时和高权限工具执行。

### 涉及文件

- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/InMemoryAgentToolPermissionCoordinator.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionHookChain.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/AgentToolPermissionMetrics.java`
- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission/NoopAgentToolPermissionMetrics.java`
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorAiConfiguration.java`
- `backend/mentor-api/src/test/java/org/congcong/algomentor/api/config/MentorAiConfigurationTest.java`

### 具体修改点

- 新增 metrics 接口，默认 no-op，避免 core 强依赖 Micrometer。
- 在 hook chain 记录 `agent.tool.permission.hook.decisions`。
- 在 coordinator 记录 requests、decisions、timeout、latency。
- 在真实高权限工具执行路径记录 `agent.tool.execution.high_permission`，具体接入点需与任务 07/13 协同。
- 日志只记录 runId、toolName、toolCallId、permissionRequestId、decision、reason、latency、expiresAt。

### 验收标准

- 默认无 MeterRegistry 时功能正常。
- 有 MeterRegistry 时指标按设计文档低基数 tag 输出。
- 日志不输出完整代码、Authorization、token、Cookie 或完整 tool arguments。

### 风险点

- agent-core 当前未直接使用 Micrometer，不能为了指标破坏模块边界。
- `policySource`、`reason` tag 需要低基数，不能使用异常消息或用户输入。

## 任务 16：前端类型与 API 封装

状态：已完成。完成备注：前端类型已补充权限 SSE 事件和决策请求/响应，`api.ts` 已新增权限决策 API 封装。

### 任务目标

让前端能识别权限 SSE 事件并提交决策 API。

### 涉及文件

- `frontend/src/types/api.ts`
- `frontend/src/services/api.ts`
- `frontend/src/services/api.test.ts`

### 具体修改点

- `SseEventName` 增加 `tool_permission_request`、`tool_permission_decision`、`tool_permission_timeout`。
- 新增 `AgentToolPermissionDecisionType`、`AgentToolPermissionRequestEvent`、`AgentToolPermissionDecisionEvent`、`AgentToolPermissionTimeoutEvent`。
- 新增 `AgentToolPermissionDecisionRequest`、`AgentToolPermissionDecisionResponse`。
- `api.ts` 新增 `decideAgentToolPermission(permissionRequestId, request, signal?)`，POST 到设计路径。
- 复用现有 JSON headers、CSRF/header 处理和 `toApiRequestError`。

### 验收标准

- 类型字段与 `LlmStreamSseMapper` 输出一致。
- API method、路径、body 正确。
- 错误响应沿用 `ApiRequestError`。

### 风险点

- 如果前后端 event data 字段命名不一致，运行时无法类型保护，需要在任务 10/16 同步。
- CSRF header 处理应复用现有 `apiFetch`，不要新写 fetch 绕过认证。

## 任务 17：前端权限确认弹窗状态与提交

状态：已完成。完成备注：`PracticeChatWorkbench` 已使用轻量原生 modal 展示权限请求；允许/拒绝会调用决策 API，决策成功后保持当前 SSE 流，modal 当前在收到 `tool_permission_decision` SSE 时关闭。

### 任务目标

在 `PracticeChatWorkbench` 收到权限请求后展示确认弹窗，允许用户 allow/deny，并保持当前 SSE 流继续等待。

### 涉及文件

- `frontend/src/learning-plans/PracticeChatWorkbench.tsx`
- `frontend/src/App.css` 或当前工作台样式文件
- `frontend/src/i18n/locales.ts`
- `frontend/src/learning-plans/PracticeChatWorkbench.test.tsx`

### 具体修改点

- 新增 `PendingToolPermission` 状态。
- `streamPracticeMessage` 的 `onEvent` 中处理 `tool_permission_request`，打开弹窗。
- 弹窗展示 displayName、reason、题目、语言、代码长度、短代码预览、effects。
- 允许/拒绝按钮调用 `decideAgentToolPermission(...)`。
- 提交中禁用按钮，失败时保留弹窗并展示错误。
- 收到 `tool_permission_decision` 或 `tool_permission_timeout` 后关闭对应弹窗。
- 决策提交后不 abort 当前 SSE。

### 验收标准

- 收到 request 后用户可以清楚看到即将执行的工具和影响。
- 点击允许发送 `ALLOW/user_confirmed`。
- 点击拒绝发送 `DENY/user_rejected`。
- API 失败时可重试。
- timeout 能关闭弹窗并展示“本次未执行”状态。
- streaming 状态下输入区仍按现有逻辑禁用，不影响 SSE 后续内容追加。

### 风险点

- 当前组件较大，新增弹窗时不要顺手重构消息流和布局。
- 弹窗文案需要 i18n；新增 key 需同时补齐中英文资源。
- 设计要求不要用可见文本解释功能机制，弹窗只展示本次授权必要信息和影响。

## 任务 18：前端 Review 成功后的刷新策略

状态：已完成。完成备注：Review 成功后沿用 run end 统一刷新 session/messages/reviews 的策略；拒绝或超时 synthetic result 不误触发新增 Review 展示。

### 任务目标

当 Review tool 真实成功后刷新 Review 历史和完成门禁；拒绝或超时不误刷新 Review 记录。

### 涉及文件

- `frontend/src/learning-plans/PracticeChatWorkbench.tsx`
- `frontend/src/types/api.ts`
- `frontend/src/learning-plans/PracticeChatWorkbench.test.tsx`

### 具体修改点

- 在 `agent_tool_end` 事件中判断 `toolName === submit_practice_code_review`。
- 解析 result type：`practice_code_review_submitted` 时触发 `refreshReviews(...)` 或标记 run end 后刷新。
- synthetic result type 为 `tool_permission_denied` 或 `tool_permission_timeout` 时不立即刷新 Review 历史。
- 保留现有 `agent_run_end` 后统一刷新 session/messages/reviews 行为，避免 UI 状态陈旧。

### 验收标准

- Review 成功后 Review 抽屉和完成门禁最终刷新。
- 用户拒绝/超时不会显示新增 Review。
- 不影响普通聊天内容流式展示。

### 风险点

- 如果立即刷新和 run end 统一刷新同时触发，可能造成重复请求；实现时可先做简单去重或只标记需要刷新。
- result 是 `JsonNode` 透传到前端，类型保护需要足够保守。

## 任务 19：端到端后端行为测试

状态：已完成。完成备注：已补充 runner 权限闭环测试和 practice Review flow 测试；主工作区聚焦 Maven 验证通过，`AgentLoopRunnerTest` 27 个测试、`PracticeCodeReviewFlowTest` 4 个测试均通过。

### 任务目标

覆盖从 LLM tool call 到权限请求、决策、真实工具执行或 synthetic result 的核心后端闭环。

### 涉及文件

- `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/AgentLoopRunnerTest.java`
- `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewFlowTest.java`
- `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/practice/PracticeSessionControllerTest.java`
- 必要的测试 fixture/helper 文件

### 具体修改点

- 新增 runner 测试：allow 正常执行、deny 不执行、ask allow 执行、ask deny 不执行、ask timeout 不执行、取消不悬挂、事件顺序正确。
- 新增 practice flow 测试：模型请求 Review tool 后先产生权限 request，允许前无 Review 记录，允许后有 Review 记录。
- 新增拒绝/超时 flow 测试：无 Review 记录，run 正常结束。
- 保持未知工具错误行为不变。

### 验收标准

- 后端核心闭环不依赖真实 LLM 或外部服务。
- 测试明确断言真实工具执行次数。
- 测试明确断言 synthetic result 被追加并让 run 继续。
- 测试不会等待真实 60 秒。

### 风险点

- 当前流式 runner 使用后台线程和 publisher，测试要避免竞态和悬挂。
- 若测试 helper 过度侵入生产代码，会扩大改动面。

## 任务 20：最小人工联调与文档更新

状态：已完成。完成备注：本次只更新文档，不改代码或测试；已补充 code index、设计文档落地备注、任务完成备注、阶段一限制和真实验证命令记录。

### 任务目标

完成阶段一最小联调说明，记录单实例限制、配置项、验证命令和未覆盖风险。

### 涉及文件

- `docs/code-index.md`
- `docs/agent-tool-permission-phase-one-design.md`
- `docs/agent-tool-permission-phase-one-tasks/README.md`
- `backend/mentor-api/src/main/resources/application.yml`
- 可选：`docs/agent-conversation-code-review-notes.md`

### 具体修改点

- 更新 `docs/code-index.md`，补充 permission 包、controller、Review tool/hook、前端权限弹窗入口。
- 在设计或任务文档中记录阶段一内存 pending request 单实例限制。
- 记录建议验证命令：agent-core 权限测试、mentor-application Review tool/hook 测试、mentor-api controller/SSE 测试、frontend 相关测试。
- 不把联调结果写成“已通过”，除非实际执行过。

### 验收标准

- 后续开发者能通过 code index 找到权限核心类、API 和前端入口。
- 配置项和单实例限制明确。
- 待确认问题被更新为已确认结论或保留为后续事项。

### 风险点

- 文档更新可能落后于代码改动；建议作为阶段一收尾任务执行。
- 不要在文档里承诺阶段二 forced tool calling 已实现。

## 待确认问题

1. 受信用户 ID metadata key 使用哪个？
   - 方案 A：在 `AgentRuntimeMetadataKeys` 新增 `USER_ID = "userId"`。
   - 方案 B：复用治理侧已有 `AiGovernanceMetadataKeys.USER_ID = "aiUserId"`。
   - 影响：权限 owner 校验、Review tool、日志/trace 全链路必须一致。
   - 阶段一结论：采用方案 A，已落地为 `AgentRuntimeMetadataKeys.USER_ID = "userId"`。

2. 阶段一是否落地 `tool-name-policies` 配置 map？
   - 设计文档允许第一阶段先通过 Spring bean 注册 `PracticeCodeReviewPermissionHook`。
   - 影响：如果不落地 map，`ToolNamePermissionHook` 只保留基础能力或延后；如果落地，需要明确与业务 hook 的优先级。
   - 阶段一结论：未落地完整配置 map；业务 hook 通过 Spring 注册。

3. `algo-mentor.agent.tool-permission.enabled=false` 的语义是什么？
   - 选项：禁用权限功能并默认 allow；或仍保留高权限业务 hook 强制 ASK。
   - 影响：安全边界和本地调试体验。
   - 阶段一结论：保留 guard，清空业务 hooks，coordinator 使用默认 allow。

4. 权限 request ID 格式是否需要固定前缀 `perm_`？
   - 设计示例使用 `perm_123`，实现可用 UUID，但前后端展示和日志检索可能受影响。
   - 阶段一结论：当前格式为 `"perm_" + 去 hyphen UUID`。

5. Review tool 获取“本轮用户消息”和 `practiceSessionId/userMessageId` 的可靠来源是什么？
   - 当前设计要求来自服务端受信上下文、repository 和 agent runtime repository。
   - 需要确认现有 metadata 是否已包含 session/message id，还是需要新增 repository 查询方法。
   - 阶段一结论：来自 trusted metadata、`PracticeSessionRepository` 和 `AgentTurnMessageLookupRepository.findByRunId(...)`。

6. 决策 API 是否放入现有 auth/security 白名单之外的默认受保护路径即可？
   - 需要确认未登录 401 的处理与项目现有 Spring Security 配置一致。
   - 阶段一结论：决策 API 由当前认证保护，controller 通过 `CurrentUserIdProvider` 获取当前用户。

7. 权限 observer/metrics 是否本阶段必须完整接入 Micrometer？
   - 设计文档列出指标要求，但为了保持任务小闭环，可以先完成 no-op metrics 接口和关键事件，再接 Micrometer 实现。
   - 阶段一结论：`agent-core` 提供 metrics 接口和 no-op；Micrometer adapter 在 API 配置里实现，`agent-core` 不依赖 Micrometer。

8. 前端弹窗样式使用现有组件/样式还是新增轻量原生 modal？
   - 需遵守现有工作台样式，不引入新的 UI 库。
   - 阶段一结论：使用 `PracticeChatWorkbench` 中的轻量原生 modal。

9. 用户提交决策成功后弹窗立即关闭还是显示“已提交，等待继续生成”？
   - 设计文档两个行为都允许；需要确定最终交互。
   - 阶段一结论：decision API 成功后保持 SSE 流，modal 目前在收到 `tool_permission_decision` SSE 时关闭。

10. 超时后是否需要向前端显示 toast/内联状态？
    - 当前项目没有统一 toast；如无统一组件，应在弹窗或消息区用现有错误/状态样式表达。
    - 阶段一结论：使用现有弹窗/状态文案；中文“本次未执行。”，英文 “This action was not run.”。

## 阶段一落地确认

- Spring 装配：配置前缀为 `algo-mentor.agent.tool-permission`，字段为 `enabled`、`timeout`、`cleanup-interval`；默认配置已写入 `application.yml`，并注明单实例 pending request 限制。
- `enabled=false` 语义：保留 `AgentToolPermissionGuard`，业务 hooks 为空，coordinator 默认 allow。
- trusted user key：`AgentRuntimeMetadataKeys.USER_ID = "userId"`。
- request id 格式：`"perm_" + UUID 去 hyphen`。
- 阶段一没有落地完整 `tool-name-policies` 配置 map；Review ASK 策略通过 `PracticeCodeReviewPermissionHook` 作为 Spring bean 注册。
- 决策 API：`POST /api/agent/tool-permissions/{permissionRequestId}/decision`，body 只接收 `decision/reason`；当前用户来自 `CurrentUserIdProvider`，不接收前端声明的 `userId`。
- 决策 API 错误映射：未登录 `401`，非 owner `403`，不存在或已清理 `404`，已决策/已过期 `409`，非法 decision `400`。
- SSE：`tool_permission_request`、`tool_permission_decision`、`tool_permission_timeout` 已映射到 API 层，输出低敏字段。
- Review tool：`PracticeCodeReviewAgentTool` 从 trusted metadata、`PracticeSessionRepository`、`AgentTurnMessageLookupRepository.findByRunId(...)` 读取 user/run/session/message/code，不信任模型 arguments；成功结果 type 为 `practice_code_review_submitted`。
- Review hook：`PracticeCodeReviewPermissionHook` 对 Review 工具返回 `ASK`，preview 脱敏 auth/cookie/api key/jwt/bearer/JWT-like token，order 为 `ToolNamePermissionHook.DEFAULT_ORDER - 50`。
- Prompt：practice chat prompt 已加入 `submit_practice_code_review` 使用边界，拒绝或超时后不得声称正式 Review 已完成。
- Metrics/logs：`agent-core` 只有 `AgentToolPermissionMetrics` 和 no-op，Micrometer adapter 在 API 配置层；低基数 tags 包括 `toolName`、`behavior`、`policySource`、`decision`、`outcome`，`reason` 只进入日志。
- 前端：类型/API 已补齐，`PracticeChatWorkbench` 使用轻量原生 modal；decision API 成功后不 abort SSE，modal 在收到 decision SSE 时关闭。
- Review 刷新：Review 成功后沿用 run end 统一刷新 session/messages/reviews，拒绝或超时不误展示新增 Review。
- 阶段一限制：`InMemoryAgentToolPermissionCoordinator` 只适合单实例；pending request 不持久化，实例重启或多实例路由切换会导致请求丢失或不可决策。
- 阶段一非目标仍成立：不实现 forced tool calling、不做后端代码提交意图识别、不实现永久授权记忆。

## 验证记录

以下为本阶段已实际运行过的验证命令记录；不要理解为全量验收。

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=AgentConversationApiAutoConfigurationTest,MentorAiConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest=PracticeChatPromptSectionProviderTest -Dsurefire.failIfNoSpecifiedTests=false test
```

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=InMemoryAgentToolPermissionCoordinatorTest,AgentToolPermissionHookChainTest,MentorAiConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

```bash
npm --cache ./.npm --prefix frontend test -- --run src/services/api.test.ts
```

```bash
npm --cache ./.npm --prefix frontend test -- --run src/learning-plans/PracticeChatWorkbench.test.tsx
```

```bash
npm --cache ./.npm --prefix frontend run build
```

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest=AgentLoopRunnerTest,PracticeCodeReviewFlowTest -Dsurefire.failIfNoSpecifiedTests=false test
```

`npm --cache ./.npm --prefix frontend run build` 已通过，但出现 Vite chunk-size warning。最后一条 Maven E2E 聚焦命令在主工作区重新运行并通过，覆盖 `AgentLoopRunnerTest` 27 个测试和 `PracticeCodeReviewFlowTest` 4 个测试。前六条为早期分任务验证记录；阶段一收尾阶段另有下方聚焦复核。

阶段一收尾时还执行过一组更完整的聚焦复核：

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=AgentLoopRunnerTest,AgentLoopLifecycleTest,AgentStreamEventTest,AgentToolPermissionModelTest,AgentToolPermissionHookChainTest,ToolNamePermissionHookTest,AgentToolPermissionResultFactoryTest,InMemoryAgentToolPermissionCoordinatorTest,PracticeCodeReviewAgentToolTest,PracticeCodeReviewPermissionHookTest,PracticeCodeReviewFlowTest,PracticeChatPromptSectionProviderTest,AgentToolPermissionControllerTest,LlmStreamSseMapperTest,AgentConversationApiAutoConfigurationTest,MentorAiConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

该命令已通过，覆盖 `agent-core` 70 个测试、`mentor-application` 21 个测试、`mentor-api` 35 个测试。

```bash
npm --cache ./.npm --prefix frontend test -- --run src/services/api.test.ts src/learning-plans/PracticeChatWorkbench.test.tsx
```

该命令已通过，覆盖 2 个前端测试文件、27 个测试。随后再次执行 `npm --cache ./.npm --prefix frontend run build`，构建通过，仍有 Vite chunk-size warning。
