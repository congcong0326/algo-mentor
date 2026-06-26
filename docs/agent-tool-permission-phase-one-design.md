# Agent Tool 权限人在回路阶段一研发设计

## 文档定位

本文是 `docs/agent-forced-tool-calling-design.md` 中“阶段一：自主 Tool Call + 人在回路权限”的详细研发设计修订版，重点调整为：

- 借鉴 Claude Code 一类 agent runtime 的工具前置权限控制思路，把权限判断建模为 tool execution 前的 hook/policy 链。
- `AgentLoopRunner` 不承载具体权限策略，只保留一个不可绕过的工具执行门禁点。
- `ASK` 的人在回路等待、SSE 事件、决策 API 和 synthetic tool result 仍由服务端运行时统一处理。

阶段一只解决“模型自主调用高权限工具时，服务端如何安全执行或拒绝”的闭环。它不解决“模型一定调用 Review 工具”的确定性问题，也不引入 `tool_choice=SPECIFIC`、后端代码提交意图识别或显式 Review run。这些留给阶段二。

## 背景与现状

当前 Agent 主循环已经支持模型自主 tool calling：

```text
AgentLoopRunner.runStep
  -> AgentLlmRequestFactory.build(..., toolRegistry.specs(), toolChoice, ...)
  -> LlmGateway.stream(...)
  -> StepCollector 收集 LlmToolCall
  -> messages.add(assistant tool_calls)
  -> 查找 AgentTool
  -> lifecycle.beforeToolCall(...)
  -> lifecycle.toolStarted(...)
  -> tool.execute(...)
  -> lifecycle.afterToolCall(...)
  -> lifecycle.toolEnded(...)
  -> messages.add(tool result)
  -> 下一 step
```

现有边界：

- `AgentTool` 只有 `spec()` 和 `execute(...)`，没有权限语义。
- `AgentLoopLifecycle` 已经集中管理 observer、interceptor 和 SSE 事件发布。
- `AgentLoopInterceptor.beforeToolCall(...)` 能改写 tool call 或抛异常，但当前不适合表达“暂停、发权限请求、等待用户决策、再继续或回填拒绝结果”的完整人在回路控制流。
- `PracticeTurnOrchestrator` 当前已简化为“校验练习会话并启动普通聊天 run”，不再应该执行旧的 post-run 自动 Review capability。
- `PracticeCodeReviewService`、Review repository、历史查询 API、完成门禁和前端 Review 抽屉可以继续复用。

题目代码 Review 适合作为 Agent 工具，但它不是低风险只读工具：

- 会触发额外 AI 调用和成本。
- 会生成正式 Review 记录并落库。
- Review 结果会影响练习完成门禁。
- 如果模型误判用户意图，自动执行 Review 会打扰用户。

因此阶段一需要把权限控制作为工具执行前的强制网关，而不是靠 prompt、工具自身或 controller 入口约束。

## 设计判断

### 是否放在主流程

权限控制必须在主流程的工具执行路径上有一个不可绕过的调用点。否则任何工具只要被 `AgentLoopRunner` 找到并执行，就能绕过权限。

但具体权限规则不应写死在 `AgentLoopRunner`。更合理的分层是：

```text
AgentLoopRunner
  -> lifecycle.beforeToolExecution(...)
       -> AgentToolPermissionHookChain
            -> ToolNamePermissionHook
            -> BusinessPermissionHook
            -> DefaultPermissionHook
       -> AgentToolPermissionCoordinator
            -> ALLOW / DENY / ASK 控制流处理
  -> execute real tool or append synthetic tool result
```

换句话说：

- runner 负责“每次工具执行前必须过权限门”。
- hook/policy 链负责“这次工具调用应该 allow、deny、ask 还是 passthrough”。
- permission coordinator 负责“如果 ask，如何创建请求、发 SSE、等待 API 决策、超时、取消和回填结果”。

这比把权限服务直接塞进 runner 业务逻辑更接近 hook 化设计，也和本项目已有 `AgentLoopLifecycle + Interceptor + Observer` 思路一致。

### 为什么不直接复用 AgentLoopInterceptor.beforeToolCall

`beforeToolCall` 当前语义是改写 `LlmToolCall`，阻断只能抛 `AgentException`。权限人在回路需要更多语义：

- 返回 `ALLOW / DENY / ASK / PASSTHROUGH`。
- `DENY` 不应终止 run，而要生成 tool result 回填给模型。
- `ASK` 需要发布 `tool_permission_request`，等待用户通过独立 API 决策。
- 超时和用户拒绝也要变成可被模型理解的 tool result。
- 权限事件需要进入 SSE、指标和审计。

因此阶段一新增专门的 tool permission hook，而不是把所有能力挤进现有 interceptor。

## 目标

- 引入工具执行前的强制权限门禁点。
- 使用 hook/policy 链表达权限决策，支持 `ALLOW`、`DENY`、`ASK`、`PASSTHROUGH`。
- `submit_practice_code_review` 默认命中 `ASK`。
- `ASK` 通过 SSE 发起权限请求，并通过独立 API 接收用户决策。
- 用户允许后才执行真实工具。
- 用户拒绝、超时或取消时绝不执行真实工具，并把标准 synthetic tool result 回填给模型。
- Review 工具执行成功后，结果进入下一步模型上下文，本轮 assistant 最终回复可以引用 Review 结论。
- 前端收到权限请求后展示确认弹窗，提交决策后保持当前 SSE 流继续等待。
- 权限请求、决策、超时和真实工具执行都具备可观测 metadata 和指标。

## 非目标

- 不实现 forced tool calling。
- 不做后端代码 Review 意图识别。
- 不保证用户粘贴完整代码时模型一定调用 Review 工具。
- 不恢复 `PracticeTurnClassifier -> CodeReviewTurnCapability` 旧自动触发路径。
- 不新增“永久允许”“本会话总是允许”等记忆策略。
- 不在 `agent-core` 写入 code review 业务规则。
- 不做多实例 pending decision 持久化；阶段一先使用内存实现，部署前明确单实例限制。

## 设计原则

- 权限由服务端执行路径强制保证，不能信任模型 prompt 遵守规则。
- `AgentTool` 接口保持稳定，工具自身不承担通用权限职责。
- 权限规则可插拔，runner 不知道具体业务工具名。
- 拒绝和超时是正常控制流，不应抛异常终止 run。
- 权限请求只展示低敏摘要，不发送 API key、Authorization、完整隐私内容或过长代码。
- 权限请求只授权某次 `runId + stepIndex + toolCallId + toolName`。
- 第一阶段优先实现可测试、可审计的最小闭环，不提前引入 Redis、数据库表或复杂授权记忆策略。

## 总体架构

```text
LLM 返回 tool calls
  -> AgentLoopRunner 追加 assistant tool_calls
  -> 查找 AgentTool
  -> lifecycle.beforeToolExecution(...)
       -> AgentToolPermissionGuard.authorize(...)
            -> AgentToolPermissionHookChain.evaluate(...)
            -> hooks 按 order 执行
            -> 第一个非 PASSTHROUGH 结果生效
            -> 否则 default ALLOW
            -> AgentToolPermissionCoordinator.authorize(...)
            -> ALLOW: 返回 allow
            -> DENY: 返回 synthetic denied tool result
            -> ASK:
                 -> 创建 AgentToolPermissionRequest
                 -> lifecycle.toolPermissionRequested(...)
                 -> SSE: tool_permission_request
                 -> 等待 decision API / timeout / cancellation
                 -> lifecycle.toolPermissionDecided 或 toolPermissionTimedOut
                 -> ALLOW: 返回 allow
                 -> DENY/timeout/cancel: 返回 synthetic tool result
  -> 如果 allow:
       lifecycle.toolStarted(...)
       tool.execute(...)
       lifecycle.afterToolCall(...)
       lifecycle.toolEnded(...)
  -> 如果 synthetic result:
       lifecycle.afterToolCall(...)
       lifecycle.toolEnded(...)
  -> ToolResultCompactor.compactForModel(...)
  -> messages.add(tool result)
  -> 下一 step
```

关键点：

- `beforeToolExecution` 是不可绕过的门禁点。
- hook 链只做决策，不直接等待用户。
- `AgentToolPermissionGuard` 组合 hook chain 和 coordinator，是 lifecycle 调用的权限门面。
- `AgentToolPermissionCoordinator` 处理 `ASK` 的长等待和 synthetic result。
- `agent_tool_start` 只在真实工具即将执行时发送。
- 拒绝、超时、取消仍发送 `agent_tool_end`，表示这次 tool call 在协议层已经处理完成。

## 核心抽象

### AgentToolPermissionBehavior

```java
public enum AgentToolPermissionBehavior {
  ALLOW,
  DENY,
  ASK,
  PASSTHROUGH
}
```

语义：

- `ALLOW`：无需询问，允许执行真实工具。
- `DENY`：不执行真实工具，生成拒绝 tool result。
- `ASK`：执行前创建权限请求并等待用户本次确认。
- `PASSTHROUGH`：当前 hook 不决策，交给下一条 hook 或默认策略。

默认策略：

- 未命中任何 hook 时默认 `ALLOW`，避免破坏现有低风险工具。
- `submit_practice_code_review` 通过工具名 hook 配置为 `ASK`。

### AgentToolPermissionCheck

```java
public record AgentToolPermissionCheck(
    AgentLoopContext context,
    int stepIndex,
    LlmToolCall toolCall,
    AgentTool tool,
    Map<String, Object> trustedMetadata
) {
}
```

`trustedMetadata` 来自服务端 `AgentRequest.metadata()`，用于权限和 preview 构造。这里不能混入模型可控参数作为授权事实。

### AgentToolPermissionDecisionPlan

hook 链返回的是“决策计划”，不是最终执行结果：

```java
public record AgentToolPermissionDecisionPlan(
    AgentToolPermissionBehavior behavior,
    String displayName,
    String reason,
    Map<String, Object> preview,
    String policySource
) {
}
```

约束：

- `ALLOW` 可以只携带 `behavior` 和 `policySource`。
- `DENY` 应携带面向模型的拒绝原因。
- `ASK` 必须携带用户可理解的 `displayName`、`reason` 和低敏 `preview`。
- `PASSTHROUGH` 不应携带业务决定。

### AgentToolPermissionHook

```java
public interface AgentToolPermissionHook {
  int order();

  AgentToolPermissionDecisionPlan evaluate(AgentToolPermissionCheck check);
}
```

执行规则：

- 按 `order` 从小到大执行。
- 返回 `PASSTHROUGH` 时继续下一条。
- 返回 `ALLOW`、`DENY` 或 `ASK` 时停止。
- hook 抛出异常时按安全失败处理：默认转为 `DENY`，并记录 `policySource` 和错误 metadata。阶段一不建议因为权限 hook 异常直接执行工具。

第一阶段内置 hook：

- `ToolNamePermissionHook`：按工具名配置 `ALLOW / DENY / ASK`。
- `DefaultPermissionHook`：兜底 `ALLOW`。

业务 hook：

- `PracticeCodeReviewPermissionHook`：识别 `submit_practice_code_review`，返回 `ASK`，并构造 Review 预览。

### AgentToolPermissionHookChain

```java
public final class AgentToolPermissionHookChain {
  public AgentToolPermissionDecisionPlan evaluate(AgentToolPermissionCheck check);
}
```

职责：

- 管理 hook 顺序。
- 保证默认决策。
- 对 hook 异常做安全降级。
- 输出低基数 `policySource` 供日志、指标和 trace 使用。

### AgentToolPermissionCoordinator

```java
public interface AgentToolPermissionCoordinator {
  AgentToolPermissionAuthorization authorize(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan,
      AgentCancellationToken cancellationToken
  );

  AgentToolPermissionDecision decide(
      String permissionRequestId,
      AgentToolPermissionDecisionType decision,
      String reason,
      long userId
  );
}
```

职责：

- `ALLOW`：返回允许执行。
- `DENY`：返回 synthetic denied tool result。
- `ASK`：创建 pending request、发布请求事件、等待用户决策、处理超时/取消。
- 校验 decision API 的所有权和状态。
- 生成统一 synthetic tool result。

`AgentLoopRunner` 只消费 `AgentToolPermissionAuthorization`：

```java
public sealed interface AgentToolPermissionAuthorization {
  record Allowed(...) implements AgentToolPermissionAuthorization {}
  record SyntheticResult(JsonNode result, AgentToolPermissionDecisionPlan plan)
      implements AgentToolPermissionAuthorization {}
}
```

### AgentToolPermissionGuard

```java
public final class AgentToolPermissionGuard {
  public AgentToolPermissionAuthorization authorize(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      AgentTool tool
  );
}
```

职责：

- 从 `AgentLoopContext` 构造 `AgentToolPermissionCheck`。
- 调用 `AgentToolPermissionHookChain.evaluate(...)` 得到决策计划。
- 调用 `AgentToolPermissionCoordinator.authorize(...)` 得到最终 authorization。
- 不包含业务工具规则。

推荐让 `AgentLoopLifecycle.beforeToolExecution(...)` 持有并调用 `AgentToolPermissionGuard`。这样 runner 只依赖 lifecycle，hook chain/coordinator 不需要反向依赖 runner。

## 模块与包结构

### agent-core

新增包：

```text
backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/permission
```

新增模型：

- `AgentToolPermissionBehavior`
- `AgentToolPermissionDecisionType`
- `AgentToolPermissionCheck`
- `AgentToolPermissionDecisionPlan`
- `AgentToolPermissionHook`
- `AgentToolPermissionHookChain`
- `AgentToolPermissionAuthorization`
- `AgentToolPermissionRequest`
- `AgentToolPermissionDecision`
- `AgentToolPermissionCoordinator`
- `InMemoryAgentToolPermissionCoordinator`
- `AgentToolPermissionGuard`
- `AgentToolPermissionResultFactory`
- `AgentToolPermissionMetadataKeys`
- `ToolNamePermissionHook`
- `DefaultPermissionHook`

修改：

- `AgentLoopRunner`：在工具执行前调用 `lifecycle.beforeToolExecution(...)`，根据返回结果执行真实工具或 synthetic result。
- `AgentLoopLifecycle`：新增 `beforeToolExecution`、`toolPermissionRequested`、`toolPermissionDecided`、`toolPermissionTimedOut`。`beforeToolExecution` 内部调用 `AgentToolPermissionGuard`。
- `AgentStreamEvent`：新增权限事件 record。
- `AgentStreamEventNames`：新增权限事件名常量。
- `AgentLoopObserver`：可选增加权限只读事件回调，用于 metrics 和审计。

不修改：

- `AgentTool` 接口。
- `LlmToolCall`、`LlmToolSpec` 等 llm-core 模型。

### mentor-application

新增：

```text
backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewAgentTool.java
backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewAgentToolNames.java
backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewPermissionHook.java
backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewToolResultMapper.java
```

修改：

- `PracticeChatPromptSectionProvider` 或相关 prompt 常量：补充 Review 工具使用边界说明。
- `AgentConversationService.toConversationRun(...)`：把 `AgentConversationCommand.userId()` 写入 `AgentRequest.metadata` 的受信用户字段。
- `AgentConversationApiAutoConfiguration`：注册 Review tool bean 和 Review permission hook bean。

### mentor-api

新增：

```text
backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AgentToolPermissionController.java
backend/mentor-api/src/main/java/org/congcong/algomentor/api/agent/model/AgentToolPermissionDecisionRequest.java
backend/mentor-api/src/main/java/org/congcong/algomentor/api/agent/model/AgentToolPermissionDecisionResponse.java
```

修改：

- `LlmStreamSseMapper`：映射权限 SSE 事件。
- `MentorAiConfiguration`：装配 hook chain、coordinator，并传入 `AgentLoopRunner` 或 `AgentLoopLifecycle` 所需构造参数。
- `ApiContractConstants`：增加权限决策 API 路径常量。

### frontend

修改：

- `frontend/src/types/api.ts`：新增 SSE 权限事件类型、决策请求/响应类型。
- `frontend/src/services/api.ts`：新增 `decideAgentToolPermission(...)`。
- `PracticeChatWorkbench.tsx`：监听 `tool_permission_request`，展示确认弹窗，提交允许/拒绝，处理超时和失败。

## 受信上下文与用户所有权

当前 `AgentConversationCommand` 已经携带受信 `userId`，但阶段一实现需要显式把它写入 `AgentRequest.metadata`。

建议新增稳定 key：

```java
public static final String USER_ID = "userId";
```

也可以复用治理侧已有：

```java
AiGovernanceMetadataKeys.USER_ID = "aiUserId";
```

二者选一后保持全链路一致。权限 hook、coordinator 和 Review tool 必须从该受信字段读取当前用户，不能从以下来源推断授权事实：

- 前端请求体。
- 模型 tool arguments。
- 最近一条数据库记录。
- prompt 中的自然语言内容。

创建 pending permission request 时，coordinator 保存：

- owner user id。
- `runId`。
- `taskId`。
- `practiceSessionId`。
- `toolName`。
- `toolCallId`。
- `expiresAt`。
- `policySource`。

决策 API 只根据 pending request 中的 owner 和当前登录用户校验所有权。

## 权限请求模型

```java
public record AgentToolPermissionRequest(
    String permissionRequestId,
    String runId,
    int stepIndex,
    String toolCallId,
    String toolName,
    String displayName,
    String reason,
    Map<String, Object> preview,
    Instant createdAt,
    Instant expiresAt
) {
}
```

Review 工具预览示例：

```json
{
  "problemSlug": "merge-sorted-array",
  "problemTitle": "合并两个有序数组",
  "languageHint": "java",
  "codeLength": 820,
  "codePreview": "class Solution { ... }",
  "effects": [
    "会调用 AI 生成一次正式代码 Review",
    "会保存 Review 记录",
    "Review 结果可能影响题目完成状态"
  ]
}
```

约束：

- `codePreview` 最多 500 字符，并按行数截断。
- 不包含完整 Authorization、provider API key、JWT、Cookie。
- 不包含完整历史对话。
- 如果无法安全构造业务预览，可以展示工具级说明，不阻塞权限机制。

## 决策模型与 API

### 决策类型

```java
public enum AgentToolPermissionDecisionType {
  ALLOW,
  DENY
}
```

```java
public record AgentToolPermissionDecision(
    String permissionRequestId,
    AgentToolPermissionDecisionType decision,
    String reason,
    Long userId,
    Instant decidedAt
) {
}
```

### API

路径：

```text
POST /api/agent/tool-permissions/{permissionRequestId}/decision
```

请求：

```json
{
  "decision": "ALLOW",
  "reason": "user_confirmed"
}
```

拒绝：

```json
{
  "decision": "DENY",
  "reason": "user_rejected"
}
```

响应：

```json
{
  "success": true,
  "data": {
    "permissionRequestId": "perm_123",
    "decision": "ALLOW",
    "accepted": true
  },
  "timestamp": "2026-06-26T12:00:00Z"
}
```

校验：

- 必须登录。
- `permissionRequestId` 必须存在且仍 pending。
- 当前用户必须是 pending request 的 owner。
- request 未过期。
- request 未被决策过。
- path 中的 `permissionRequestId` 是唯一 request id，body 不再重复传 id。

错误语义：

- 未登录：`401`。
- 无权访问：`403`。
- request 不存在或已清理：`404`。
- 已决策：`409`。
- 已过期：`409`，run 侧按超时拒绝处理。
- 非法 decision：`400`。

## InMemoryAgentToolPermissionCoordinator

阶段一使用内存 pending request：

```java
ConcurrentHashMap<String, PendingPermissionRequest> pendingRequests
```

`PendingPermissionRequest` 包含：

- `AgentToolPermissionRequest request`
- `CompletableFuture<AgentToolPermissionDecision> future`
- `long ownerUserId`
- `Map<String, Object> ownershipMetadata`
- `AtomicBoolean completed`

等待策略：

- 默认 timeout：60 秒，通过配置 `algo-mentor.agent.tool-permission.timeout` 覆盖。
- `ASK` 每次只等待一个 request。
- 超时返回 `DENY`，reason 为 `timeout`。
- cancellation 返回 `DENY`，reason 为 `run_cancelled`。
- 等待完成后从 `pendingRequests` 移除。
- 定期清理已过期 pending request，避免内存泄漏。

并发规则：

- 同一 `permissionRequestId` 只接受第一个有效决策。
- 重复决策返回 `409`。
- 用户拒绝和超时竞争时，以先完成的结果为准。
- `decide` 必须先校验 owner user，再完成 future。

多实例限制：

- 内存版只适用于单 JVM。
- 多实例部署前迁移到 Redis 或 PostgreSQL，并保证 decision API 能路由到创建 request 的实例，或所有实例共享 pending 状态。

## AgentLoopRunner 集成

### 构造与兼容

为了兼容现有测试和装配，新增构造参数时保留旧构造函数。旧构造函数使用默认 allow hook chain 和 no-op coordinator。

建议主构造函数增加：

```java
AgentToolPermissionHookChain permissionHookChain,
AgentToolPermissionCoordinator permissionCoordinator
```

也可以把二者封装为：

```java
AgentToolPermissionGuard permissionGuard
```

`AgentToolPermissionGuard` 内部组合 hook chain 和 coordinator。runner 通过 lifecycle 间接调用：

```java
AgentToolPermissionAuthorization authorization =
    lifecycle.beforeToolExecution(context, stepIndex, toolCall, tool);
```

### 执行顺序

阶段一目标顺序：

```text
effectiveToolCall = lifecycle.beforeToolCall(...)
messages.add(assistant tool_calls)

for toolCall:
  tool = toolRegistry.find(...)
  authorization = lifecycle.beforeToolExecution(context, stepIndex, toolCall, tool)

  if authorization is Allowed:
    lifecycle.toolStarted(...)
    result = executeTool(...)
    result = lifecycle.afterToolCall(...)
    lifecycle.toolEnded(...)

  if authorization is SyntheticResult:
    result = lifecycle.afterToolCall(...)
    lifecycle.toolEnded(...)

  compaction = toolResultCompactor.compactForModel(...)
  messages.add(tool result)
```

权限相关事件发生在 `beforeToolExecution` 内部：

```text
tool_permission_request
tool_permission_decision
tool_permission_timeout
```

`agent_tool_start` 只在真实工具执行前发送。synthetic result 不发送 `agent_tool_start`，但发送 `agent_tool_end`。

### Synthetic Tool Result

用户拒绝：

```json
{
  "type": "tool_permission_denied",
  "toolName": "submit_practice_code_review",
  "toolCallId": "call_1",
  "permissionRequestId": "perm_123",
  "message": "用户拒绝执行代码 Review 工具。",
  "reason": "user_rejected",
  "retryable": false
}
```

超时：

```json
{
  "type": "tool_permission_timeout",
  "toolName": "submit_practice_code_review",
  "toolCallId": "call_1",
  "permissionRequestId": "perm_123",
  "message": "等待用户确认超时，代码 Review 工具未执行。",
  "reason": "timeout",
  "retryable": true
}
```

取消：

```json
{
  "type": "tool_permission_denied",
  "toolName": "submit_practice_code_review",
  "toolCallId": "call_1",
  "permissionRequestId": "perm_123",
  "message": "本次运行已取消，工具未执行。",
  "reason": "run_cancelled",
  "retryable": false
}
```

这些结果会作为 tool message 回填给模型，让模型生成自然的最终回复，例如“已取消正式 Review，我可以继续以普通聊天方式帮你分析代码”。

## SSE 事件

新增事件名常量：

```java
public static final String TOOL_PERMISSION_REQUEST = "tool_permission_request";
public static final String TOOL_PERMISSION_DECISION = "tool_permission_decision";
public static final String TOOL_PERMISSION_TIMEOUT = "tool_permission_timeout";
```

### tool_permission_request

```json
{
  "permissionRequestId": "perm_123",
  "runId": "run_abc",
  "stepIndex": 1,
  "toolCallId": "call_1",
  "toolName": "submit_practice_code_review",
  "displayName": "提交代码 Review",
  "reason": "模型请求执行一次正式代码 Review。",
  "preview": {
    "problemSlug": "merge-sorted-array",
    "languageHint": "java",
    "codeLength": 820,
    "codePreview": "class Solution { ... }",
    "effects": ["会生成一条 Review 记录", "Review 结果可能影响完成状态"]
  },
  "expiresAt": "2026-06-26T12:00:00Z"
}
```

### tool_permission_decision

```json
{
  "permissionRequestId": "perm_123",
  "runId": "run_abc",
  "stepIndex": 1,
  "toolCallId": "call_1",
  "toolName": "submit_practice_code_review",
  "decision": "ALLOW",
  "reason": "user_confirmed",
  "decidedAt": "2026-06-26T11:59:30Z"
}
```

### tool_permission_timeout

```json
{
  "permissionRequestId": "perm_123",
  "runId": "run_abc",
  "stepIndex": 1,
  "toolCallId": "call_1",
  "toolName": "submit_practice_code_review",
  "reason": "timeout",
  "expiredAt": "2026-06-26T12:00:00Z"
}
```

前端收到 `tool_permission_timeout` 后关闭确认弹窗并展示“本次未执行”状态。run 仍会继续，后续可能收到模型根据拒绝结果生成的内容和 `agent_run_end`。

## Review Tool 设计

### 工具名与常量

```java
public final class PracticeCodeReviewAgentToolNames {
  public static final String SUBMIT_PRACTICE_CODE_REVIEW = "submit_practice_code_review";
  public static final String USER_INTENT = "userIntent";
  public static final String NOTES = "notes";
}
```

### Tool spec

描述：

```text
当用户明确请求正式代码 Review，或在题目练习中提交完整解法希望判断是否通过时，调用此工具。
不要在用户只是询问概念、语法、局部 bug、异常日志、提示、复杂度分析或普通思路时调用。
该工具会生成正式 Review 记录并可能影响题目完成状态；系统会在执行前请求用户确认。
```

输入 schema：

```json
{
  "type": "object",
  "properties": {
    "userIntent": {
      "type": "string",
      "description": "模型判断的低风险用户意图摘要。"
    },
    "notes": {
      "type": ["string", "null"],
      "description": "可选补充说明，不要包含敏感信息。"
    }
  },
  "required": ["userIntent", "notes"],
  "additionalProperties": false
}
```

模型参数只作为辅助说明。工具实现必须从服务端上下文读取：

- 当前 `userId`，来自受信 `AgentRequest.metadata`。
- `practiceSessionId`。
- `planId`。
- `phaseIndex`。
- `problemSlug`。
- 当前用户消息和最近上下文。
- agent run db id / run uuid。

这些信息来自 `AgentExecutionContext.requestMetadata()`、`PracticeSessionRepository` 和 agent runtime repository，不能信任模型参数。

### 工具执行流程

```text
PracticeCodeReviewAgentTool.execute(arguments, context)
  -> 从 context.requestMetadata 读取 practiceSessionId、userId/runDbId/taskId 等服务端事实
  -> 校验当前 run 属于 practice chat
  -> 校验 session 存在且属于当前用户
  -> 读取本轮用户消息或当前会话最近用户提交
  -> 调用 PracticeCodeReviewService 生成结构化 Review
  -> 保存 Review 记录
  -> 返回摘要 JsonNode 给主模型
```

工具返回示例：

```json
{
  "type": "practice_code_review_submitted",
  "reviewId": 123,
  "versionNo": 2,
  "language": "java",
  "passed": true,
  "totalScore": 8.0,
  "passScore": 6.0,
  "summary": "整体思路正确，边界处理完整。",
  "topIssues": [],
  "improvementSuggestions": ["可以补充复杂度说明。"]
}
```

失败策略：

- 业务前置校验失败抛 `AgentException`，例如缺少 practice session metadata。
- Review 模型调用失败按现有 `PracticeCodeReviewService` 语义返回或抛错。
- 用户拒绝/超时不进入工具实现，不产生 Review 记录。

幂等：

- 继续依赖 Review repository 的同一用户消息唯一约束，防止重复落库。
- 如果相同用户消息已存在 Review，工具可以返回已有 Review 摘要，不重复调用 Review 模型。

### PracticeCodeReviewPermissionHook

职责：

- 只匹配 `submit_practice_code_review`。
- 从受信 metadata 和 repository 构造 preview。
- 返回 `ASK`。
- 不执行 Review，也不等待用户。

伪代码：

```java
public AgentToolPermissionDecisionPlan evaluate(AgentToolPermissionCheck check) {
  if (!SUBMIT_PRACTICE_CODE_REVIEW.equals(check.toolCall().name())) {
    return AgentToolPermissionDecisionPlan.passthrough();
  }
  return AgentToolPermissionDecisionPlan.ask(
      "提交代码 Review",
      "模型请求执行一次正式代码 Review。",
      previewFromTrustedContext(check),
      "practice-code-review-hook");
}
```

## Prompt 调整

练习聊天系统提示词增加工具边界：

```text
如果用户明确要提交完整解法做正式代码 Review，或希望判断本题是否可以通过，应调用 submit_practice_code_review。
该工具会生成正式 Review 记录并可能影响完成状态，系统会在执行前请求用户确认。
如果用户拒绝或确认超时，请继续用普通聊天方式帮助用户，不要声称已经完成正式 Review。
如果用户只是询问概念、语法、局部报错、思路提示或复杂度分析，不要调用该工具。
```

阶段一接受模型可能不调用工具。prompt 优化只提高调用概率，不作为正确性保证。

## 前端交互设计

### 状态模型

在 `PracticeChatWorkbench` 中维护 pending permission：

```ts
interface PendingToolPermission {
  permissionRequestId: string;
  runId: string;
  stepIndex: number;
  toolCallId: string;
  toolName: string;
  displayName: string;
  reason: string;
  preview: Record<string, unknown>;
  expiresAt: string;
  submitting?: boolean;
  error?: string;
}
```

### 弹窗行为

- 收到 `tool_permission_request` 后打开确认弹窗。
- 弹窗显示工具名、原因、题目、语言、代码长度、短代码预览和影响说明。
- 主按钮：允许。
- 次按钮：拒绝。
- 提交后禁用按钮，调用决策 API。
- API 成功后弹窗进入“已提交，等待继续生成”或关闭。
- API 失败时保留弹窗并展示错误，可重试；如果已过期则关闭。
- 收到 `tool_permission_decision` 或 `tool_permission_timeout` 后关闭对应弹窗。

### SSE 流处理

- 决策提交后不能 abort 当前 SSE。
- 当前 practice 流服务端已使用 `SseLlmStreamSubscriber(..., false)`，客户端断开后服务端仍会 drain 上游。阶段一前端应尽量保持连接，便于用户看到后续回复。
- 如果用户关闭页面，服务端最终会因权限超时生成拒绝 tool result，run 不会永久卡住。

### Review 历史刷新

- 当收到 `agent_tool_end` 且 `toolName=submit_practice_code_review`，或收到 `agent_run_end` 后，刷新 session/messages/reviews。
- 如果 result type 是 `practice_code_review_submitted`，可以立即触发 Review 抽屉数据刷新。
- 拒绝或超时不刷新 Review 历史，除非 run end 后统一刷新 session。

## 安全设计

- 权限 hook 只基于服务端受信上下文做决策。
- 决策 API 不接受 `runId`、`toolName`、`userId` 等前端声明作为授权依据。
- pending request 保存 owner user id 和业务定位 metadata。
- `submit_practice_code_review` 工具不信任模型传入的 `userId`、`sessionId`、`problemSlug` 或完整代码。
- 权限 preview 进行长度截断和敏感字段过滤。
- hook 异常默认安全失败，不允许因此直接执行高权限工具。
- 日志记录工具名、request id、run id、decision、timeout，不记录完整代码和 Authorization。
- `tool_permission_request` 只暴露当前用户有权看到的低敏信息。

## 观测与审计

metadata key 建议集中到 `AgentToolPermissionMetadataKeys`：

- `permissionRequestId`
- `permissionBehavior`
- `permissionDecision`
- `permissionDecisionReason`
- `permissionTimeout`
- `permissionLatencyMs`
- `permissionPolicySource`
- `permissionHookName`
- `permissionOwnerUserId`

Micrometer 指标：

- `agent.tool.permission.hook.decisions`：按 `toolName`、`behavior`、`policySource` 计数。
- `agent.tool.permission.requests`：按 `toolName` 计数。
- `agent.tool.permission.decisions`：按 `toolName`、`decision`、`reason` 计数。
- `agent.tool.permission.timeout`：按 `toolName` 计数。
- `agent.tool.permission.latency`：用户决策耗时。
- `agent.tool.execution.high_permission`：高权限工具真实执行次数。
- `practice.code_review.tool.success` / `failure`：Review tool 执行结果。

日志：

- hook 决策：debug，包含 toolName、behavior、policySource。
- 创建权限请求：info，包含 runId、toolName、toolCallId、permissionRequestId、expiresAt。
- 决策完成：info，包含 decision、reason、latency。
- 决策拒绝/越权/过期：warn，不输出敏感内容。
- pending request 清理：debug 或 info。

## 错误与边界场景

### 用户拒绝

后端不执行工具，生成 `tool_permission_denied` tool result，模型继续生成最终回复。

### 用户超时

超时按拒绝处理，生成 `tool_permission_timeout` tool result，发送 `tool_permission_timeout` SSE，run 继续。

### 用户关闭页面

如果 SSE 断开但 run 未取消，pending request 会超时并继续完成 run。前端再次进入页面时通过 active run 或消息刷新看到最终状态。

### 决策 API 比超时晚到

返回 `409`，不改变 run 结果。

### hook 抛异常

默认安全失败：

- 对高权限工具返回 `DENY` synthetic result。
- 对无法判断工具风险的场景，阶段一也按 `DENY` 处理并记录错误。

如果后续需要更细粒度策略，可以引入 hook failure policy，但阶段一不做。

### 工具执行中失败

这是工具失败，不是权限失败。保持现有 `AgentException` 语义，发 `agent_error`。后续如希望工具失败也回填给模型，需要单独设计。

### 模型并行调用多个 ASK 工具

阶段一按现有 runner 顺序逐个处理工具调用。每个 ASK 工具单独创建权限请求并等待。后续如果支持 parallel tool calls，需要重新设计并发弹窗和决策聚合。

### 未知工具

未知工具仍按现有逻辑抛 `UNKNOWN_TOOL`。因为找不到 `AgentTool` 时，没有可执行对象和可信工具元数据，权限 hook 不负责补救未知工具。

## 配置

新增配置建议：

```yaml
algo-mentor:
  agent:
    tool-permission:
      enabled: true
      timeout: 60s
      cleanup-interval: 30s
      default-behavior: ALLOW
      tool-name-policies:
        submit_practice_code_review: ASK
```

阶段一也可以先不暴露完整 `tool-name-policies` map，直接通过 Spring bean 注册 `PracticeCodeReviewPermissionHook`。配置文件至少需要 timeout 和 enabled。

## 测试计划

### agent-core 单元测试

新增或扩展 `AgentLoopRunnerTest`：

- `allowHookExecutesToolNormally`：默认 allow 不改变现有工具调用流程。
- `denyHookDoesNotExecuteToolAndContinuesWithDeniedToolResult`：工具未执行，下一轮 LLM 收到拒绝 result。
- `askHookPublishesRequestAndExecutesAfterAllow`：发出 request SSE，API 决策 allow 后执行工具。
- `askHookDeniedDoesNotExecuteTool`：拒绝后工具未执行，run 正常结束。
- `askHookTimeoutReturnsTimeoutToolResult`：超时后发 timeout 事件，工具未执行。
- `hookPassthroughFallsBackToDefaultAllow`：所有 hook passthrough 时默认 allow。
- `hookFailureFailsClosed`：hook 异常不会执行工具。
- `permissionEventOrder`：事件顺序符合 `tool_call_end -> agent_step_end -> tool_permission_request -> ...`。
- `cancelWhileWaitingPermissionDoesNotHang`：等待期间取消 run 不永久阻塞。
- `unknownToolStillErrorsBeforePermission`：未知工具保持现有错误语义。

`AgentToolPermissionHookChainTest`：

- hook 按 order 执行。
- 第一个非 passthrough 结果生效。
- passthrough 进入下一 hook。
- 默认策略生效。
- hook 异常安全失败。

`InMemoryAgentToolPermissionCoordinatorTest`：

- 创建 pending request。
- 允许/拒绝完成 future。
- 重复决策返回冲突。
- owner user 不匹配拒绝。
- timeout 清理 pending。

### mentor-application 单元测试

`PracticeCodeReviewPermissionHookTest`：

- 非 Review 工具返回 `PASSTHROUGH`。
- Review 工具返回 `ASK`。
- preview 从受信 metadata/session 构造。
- preview 截断代码，不泄露完整历史。
- 缺少上下文时返回安全 fallback preview。

`PracticeCodeReviewAgentToolTest`：

- 从 `AgentExecutionContext.requestMetadata` 读取 session，而不是使用模型参数。
- 缺少 practice session metadata 时失败。
- 用户/session 校验失败时失败。
- 已有同一用户消息 Review 时返回已有摘要。
- 正常调用 `PracticeCodeReviewService` 并映射摘要。

Prompt 测试：

- practice chat prompt 包含 Review tool 使用边界。
- 普通提示、局部 bug、概念咨询说明不应要求调用 Review tool。

### mentor-api 测试

`AgentToolPermissionControllerTest`：

- 登录用户可提交 pending request 决策。
- 未登录返回 401。
- 非 owner 返回 403。
- 不存在返回 404。
- 已过期/已决策返回 409。

`LlmStreamSseMapperTest`：

- `tool_permission_request` 映射字段完整。
- `tool_permission_decision` 映射字段完整。
- `tool_permission_timeout` 映射字段完整。

配置测试：

- `MentorAiConfigurationTest` 验证 hook chain、coordinator 和 `AgentLoopRunner` 装配。
- `AgentConversationApiAutoConfigurationTest` 验证 Review tool 和 Review permission hook 在依赖齐备时注册。

### 前端测试

`PracticeChatWorkbench.test.tsx`：

- 收到 `tool_permission_request` 后显示确认弹窗。
- 点击允许调用决策 API，保持流继续处理。
- 点击拒绝调用决策 API。
- 收到 timeout 关闭弹窗并展示状态。
- `agent_tool_end` 为 Review 成功时刷新 Review 历史。
- API 失败时弹窗保留并可重试。

`api.test.ts`：

- `decideAgentToolPermission` 请求路径、method、body、CSRF header 正确。

## 实施顺序

1. 确认阶段零已完成：旧 post-run Review capability 不再执行，不再向 `agent_run_end.metadata.practiceCapabilities` 写入 Review 结果。
2. 在 `AgentConversationService.toConversationRun(...)` 中写入受信用户 metadata。
3. 在 `agent-core` 新增 permission 包、hook chain、decision plan、authorization、coordinator 和 synthetic result factory。
4. 修改 `AgentStreamEvent`、`AgentStreamEventNames`、`AgentLoopLifecycle`，增加权限事件和 `beforeToolExecution` 门禁点。
5. 修改 `AgentLoopRunner`，在工具执行前调用 `beforeToolExecution`，并支持真实工具执行或 synthetic result 两条路径。
6. 在 `mentor-api` 增加 SSE mapper 支持和权限决策 controller。
7. 增加配置装配，把 hook chain/coordinator 或 permission guard 注入 runner。
8. 在 `mentor-application` 新增 `submit_practice_code_review` tool，复用 `PracticeCodeReviewService`。
9. 新增并注册 `PracticeCodeReviewPermissionHook`，让 Review tool 默认 `ASK`。
10. 调整 practice chat prompt，描述 Review tool 使用边界。
11. 前端增加权限事件类型、决策 API 和确认弹窗。
12. 跑最小相关后端/前端测试，补齐失败用例。

## 验收标准

- 低风险现有工具在默认 `ALLOW` 下行为不变。
- 权限 hook 链是工具执行前不可绕过的路径。
- `submit_practice_code_review` 被模型调用时，真实工具执行前一定先发送 `tool_permission_request`。
- 用户允许前不会产生 Review 记录。
- 用户拒绝后不会产生 Review 记录，run 正常结束并给出最终回复。
- 用户超时后不会产生 Review 记录，run 正常结束并给出最终回复。
- 用户允许后生成 Review 记录，前端可刷新 Review 历史和完成门禁。
- hook 抛异常时不会默认执行高权限工具。
- 权限请求不可被其他用户决策。
- 取消、断线、超时不会让 run 永久卡住。
- 日志和 SSE 不泄露密钥、Authorization 或完整长代码。

## 后续演进

阶段二在本设计之上增加 per-run tool execution options：

- `allowedToolNames`
- `toolChoice=SPECIFIC(submit_practice_code_review)`
- `scope=FIRST_STEP_ONLY`
- `parallelToolCalls=false`
- `maxToolCalls=1`

显式“提交 Review”入口可以选择把用户点击视为本次授权，或仍复用阶段一确认弹窗。无论如何，阶段一的服务端权限 hook 和执行门禁点仍作为最终保护层保留。
