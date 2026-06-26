# Agent 工具权限与自主 Review Tool Call 设计

## 当前结论

本文早期曾把阶段二方向描述为“显式入口 + forced tool calling”。该方向已经废弃。

当前阶段二方向是：不做 forced tool calling，不做后端代码提交规则识别，也不恢复旧的 post-run capability 自动 Review。后续 Review 触发长期依赖主模型自主判断；当主模型认为当前用户消息像是在粘贴当前题目的完整 LeetCode 解法时，应主动调用 `submit_practice_code_review`。阶段一已经落地的权限人在回路机制仍作为最终安全边界：用户允许后才执行正式 Review 并写库，拒绝或超时不生成正式 Review 记录。

## 背景

当前 `agent-core` 已经支持模型自主 tool calling：`AgentLoopRunner` 每个 step 把工具列表传给 LLM，模型返回 tool calls 后由 runner 执行工具，再把 tool result 追加回上下文进入下一步。

题目练习代码 Review 适合迁移为 Agent 工具，但它不是低风险只读工具：

- 会触发额外 AI 调用和成本。
- 会生成正式 Review 记录并落库。
- Review 结果会影响练习完成门禁。
- 如果模型误判用户意图，自动执行 Review 会打扰用户。

因此不追求“模型一定调用 Review 工具”，也不做后端代码意图识别或 forced tool calling。第一阶段先把最小闭环做出来：

```text
模型自主决定是否调用 submit_practice_code_review
-> Agent loop 在执行工具前检查权限
-> 高权限工具发起用户确认
-> 用户允许后执行工具
-> 用户拒绝或超时则不执行工具，并把拒绝结果回填给模型
-> 模型继续生成最终回复
```

后续不再把 forced tool calling 作为计划方向；触发率通过 prompt 和工具描述优化提升。

## 阶段策略

### 阶段零：历史自动 Review 链路清理

在引入工具权限和 Review tool 前，先清理当前 post-run 自动 Review 触发链路，避免新旧两套机制同时生成正式 Review。

清理范围：

- `PracticeTurnOrchestrator` 不再在普通聊天 run 的 `agent_run_end` 后执行练习 capability。
- 下线 `PracticeTurnClassifier -> CodeReviewTurnCapability -> PracticeCodeReviewService` 的自动触发路径。
- 不再向 `agent_run_end.metadata.practiceCapabilities` 追加代码 Review 结果。
- 保留 `PracticeCodeReviewService`、Review repository、数据库表、历史查询 API、完成门禁和前端 Review 抽屉，后续由 `submit_practice_code_review` 工具复用。

阶段零完成后，用户粘贴完整代码只会进入普通聊天 run，不会自动生成正式 Review 记录。正式 Review 的写库入口后续只来自 `submit_practice_code_review` 工具。

### 阶段一：自主 Tool Call + 人在回路权限

第一阶段目标是验证工具权限机制，而不是验证模型意图识别准确率。

接受的限制：

- 模型可能不调用 Review 工具。
- 用户粘贴完整代码时，不做后端预检弹窗。
- 不做 `tool_choice=SPECIFIC` 强制调用。
- 不做 `PracticeTurnClassifier` 驱动的 Review 自动触发。

必须保证：

- 如果模型调用高权限工具，后端必须在执行前请求用户确认。
- 用户拒绝时工具绝不能执行。
- 拒绝、超时、取消都要能回填给模型，run 不能卡死。
- Review 工具执行成功后，结果可被模型用于本轮回复，前端可刷新 Review 历史。

### 阶段二：自主 Tool Call Prompt 强化

第二阶段支持：

```text
用户在题目聊天中粘贴疑似完整解法
-> 主模型根据系统 prompt 和工具描述自主判断是否调用 submit_practice_code_review
-> 如果调用，阶段一权限机制发起用户确认
-> 用户允许后执行 Review tool 并写入正式 Review 记录
-> 用户拒绝或超时则不执行工具，模型可继续普通点评但不能给正式分数
```

阶段二不解决确定性调用问题，也不承诺 100% 触发。阶段一解决工具执行安全问题，阶段二只提升模型自主调用意愿和契约清晰度。

## 设计目标

- 在 Agent 框架层引入工具权限管线，所有工具执行前统一检查。
- 支持高权限工具的人在回路确认。
- 支持 SSE 流中暂停等待用户决策，并通过独立 API 回传决策。
- 让拒绝或超时以 tool result 形式回填给模型，而不是直接让 run 崩溃。
- 保持权限和业务事实由服务端上下文决定，不能信任模型传入的敏感参数。
- 为后续自主 tool call 的 prompt 和工具描述优化预留测试与文档边界。

## 非目标

- 不做 forced tool calling。
- 第一阶段不做后端代码 Review 意图识别。
- 第一阶段不保证模型一定调用 Review 工具。
- 不把 code review 业务规则写入 `agent-core`。
- 不让模型绕过服务端权限检查执行副作用工具。

## 核心抽象

### AgentToolPermissionBehavior

工具权限行为建议抽象为四种：

```java
public enum AgentToolPermissionBehavior {
  ALLOW,
  DENY,
  ASK,
  PASSTHROUGH
}
```

语义：

- `ALLOW`：无需询问，直接执行。
- `DENY`：直接拒绝执行。
- `ASK`：执行前请求用户确认。
- `PASSTHROUGH`：当前策略不决策，交给下一条规则或默认策略。

`submit_practice_code_review` 第一阶段默认应为 `ASK`。

### AgentToolPermissionRequest

当工具命中 `ASK` 时，Agent loop 创建权限请求并发给前端。

建议字段：

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
    Instant expiresAt
) {
}
```

`preview` 用于给用户展示低敏摘要，例如：

- 题目名称或 slug。
- 工具说明。
- 语言提示。
- 代码长度。
- 代码前若干行预览。
- “会生成一次代码 Review 记录”。

不要在权限请求里暴露 API key、完整 Authorization、隐私数据或过长代码。

### AgentToolPermissionDecision

前端通过独立 API 回传决策。

建议字段：

```java
public record AgentToolPermissionDecision(
    String permissionRequestId,
    AgentToolPermissionDecisionType decision,
    String reason
) {
}
```

决策类型：

```java
public enum AgentToolPermissionDecisionType {
  ALLOW,
  DENY
}
```

第一阶段只支持本次允许或拒绝。暂不做“本会话总是允许”“永久允许”等记忆策略。

### AgentToolPermissionService

权限服务负责创建请求、等待决策、处理超时。

建议接口：

```java
public interface AgentToolPermissionService {
  AgentToolPermissionCheckResult check(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      AgentTool tool
  );

  AgentToolPermissionDecision awaitDecision(
      AgentToolPermissionRequest request,
      Duration timeout
  );

  void decide(AgentToolPermissionDecision decision);
}
```

第一阶段可以先实现内存版 pending request 存储：

- key：`permissionRequestId`
- value：`CompletableFuture<AgentToolPermissionDecision>`
- 超时：默认 60 秒
- 超时行为：按 `DENY` 处理

后续如需多实例部署，再迁移到 Redis/PostgreSQL。

## Agent Loop 执行流程

### 当前流程

```text
1. LLM 返回 tool calls
2. AgentLoopRunner 查找工具
3. 直接 executeTool
4. 追加 tool result
5. 进入下一 step
```

### 阶段一目标流程

```text
1. LLM 返回 tool calls
2. AgentLoopRunner 查找工具
3. 执行 permission check
4. 如果 ALLOW：执行工具
5. 如果 DENY：不执行工具，构造拒绝 tool result
6. 如果 ASK：
   - 发出 tool_permission_request 事件
   - 等待前端 decision API
   - ALLOW 后执行工具
   - DENY/超时后构造拒绝 tool result
7. 追加 tool result
8. 进入下一 step，让模型基于结果继续回复
```

拒绝时不建议直接抛 `AgentException` 终止 run。更好的方式是把拒绝包装成 tool result：

```json
{
  "type": "tool_permission_denied",
  "toolName": "submit_practice_code_review",
  "message": "用户拒绝执行代码 Review 工具。",
  "retryable": false
}
```

这样模型可以自然回复：

```text
已取消代码 Review。我可以继续以普通聊天方式帮你分析这段代码。
```

## SSE 与决策 API

### SSE 事件

新增事件名：

```text
tool_permission_request
tool_permission_decision
tool_permission_timeout
```

`tool_permission_request` 示例：

```json
{
  "permissionRequestId": "perm_123",
  "runId": "run_abc",
  "stepIndex": 1,
  "toolCallId": "call_1",
  "toolName": "submit_practice_code_review",
  "displayName": "提交代码 Review",
  "reason": "模型请求对本轮代码执行正式 Review。",
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

前端收到后弹确认框。用户选择后调用决策 API。

### 决策 API

建议路径：

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

权限要求：

- 必须登录。
- 当前用户必须是该 run 的拥有者。
- request 未过期。
- request 未被决策过。

## Code Review 工具接入

### 工具定义

建议新增 Agent 工具：

```text
submit_practice_code_review
```

工具职责：

- 基于当前练习会话、题目、用户消息和服务端上下文执行正式 Review。
- 内部复用 `PracticeCodeReviewService`。
- 工具内部可以再发起一次短模型 structured-output 调用。
- Review 结果落库。
- 返回简短可见结果给主模型。

建议工具返回：

```json
{
  "reviewId": 123,
  "versionNo": 2,
  "passed": true,
  "totalScore": 8.0,
  "passScore": 6.0,
  "summary": "整体思路正确，边界处理完整。",
  "topIssues": [],
  "improvementSuggestions": ["可以补充复杂度说明。"]
}
```

### 权限策略

第一阶段：

```text
submit_practice_code_review -> ASK
```

确认文案建议：

```text
模型请求执行一次正式代码 Review。
该操作会调用 AI Review、生成 Review 记录，并可能影响本题完成状态。是否允许？
```

展示内容：

- 工具名：提交代码 Review。
- 当前题目。
- 语言提示。
- 代码预览。
- 可能影响。

### 参数安全

工具参数不应承载敏感事实：

- 不信任模型传入的 `userId`。
- 不信任模型传入的 `sessionId`。
- 不信任模型传入的 `problemSlug`。
- 不信任模型传入的完整代码作为唯一事实来源。

这些信息应从 `AgentExecutionContext.metadata()`、练习 session、持久化消息和服务端上下文中获取。

模型参数可以只包含低风险字段：

```json
{
  "userIntent": "submit_solution_for_review",
  "notes": "用户希望检查这份 Java 解法。"
}
```

## Prompt 与工具描述

不做 forced tool calling，因此工具描述要尽量清晰，让模型在适合时自主调用。

`submit_practice_code_review` 描述建议：

```text
Record the current practice user message as a formal code submission. Use when the current user message looks like a complete solution submission for the active practice problem, even if the user did not explicitly ask for a formal review. Do not use for snippets, pseudocode, error logs, local bug questions, syntax questions, or conceptual discussion. The system asks the user for confirmation before execution.
```

练习聊天系统提示词可补充：

```text
只要当前用户消息看起来像是在粘贴当前题目的完整 LeetCode 解法，应优先调用 submit_practice_code_review。
即使用户没有明确要求正式 Review，也应调用该工具，让用户通过确认弹窗决定是否生成正式记录。
如果用户拒绝工具执行，可以继续普通点评代码，但不要给出正式分数，不要声称已生成 Review 记录。
```

## 与当前后置 Capability 的关系

历史实现中，`PracticeTurnOrchestrator` 会在普通聊天 run 的 `agent_run_end` 上执行 `CodeReviewTurnCapability`，属于 run 后同步后处理。它能保证 Review 落库，但主模型本轮回复并不知道正式 Review 结果，也会与新的 Review tool 形成重复触发风险。

阶段零已先清理这条 post-run 自动 Review 链路：

- `PracticeTurnOrchestrator` 只负责校验练习会话、组装 practice chat metadata、启动普通 agent run。
- 不再执行 `PracticeTurnClassifier` 和 `CodeReviewTurnCapability`。
- 不再输出 `practiceCapabilities.codeReview` 这类后处理 metadata。
- Review service、落库、历史记录和完成门禁继续保留，作为后续工具实现的业务能力。

阶段一目标是让 Review 进入 tool calling 链路：

- Review 作为模型可调用工具。
- 工具执行前有权限确认。
- 工具结果进入下一步模型上下文。
- Assistant 本轮最终回复可以直接引用 Review 结果。

因此阶段一不再兼容旧自动触发路径，也不使用 `PracticeTurnClassifier` 驱动 Review 自动触发。

## 历史 Forced Tool Calling 方案

以下方案是历史设计草案，当前不再作为计划方向执行。保留在本文中仅用于解释为什么阶段一的权限门禁仍然独立于具体触发策略。

建议抽象：

```java
public record AgentToolExecutionOptions(
    Set<String> allowedToolNames,
    LlmToolChoice toolChoice,
    ToolChoiceScope scope,
    boolean parallelToolCalls,
    Integer maxToolCalls,
    UnsupportedToolChoiceStrategy unsupportedStrategy
) {
}
```

语义：

- `allowedToolNames`：本轮允许暴露给模型的工具白名单。
- `toolChoice`：复用 `LlmToolChoice.AUTO/NONE/REQUIRED/SPECIFIC`。
- `scope`：默认 `FIRST_STEP_ONLY`，避免工具结果回填后继续强制调用。
- `maxToolCalls`：Review 场景建议为 `1`。

显式 Review 入口可使用：

```text
allowedToolNames = ["submit_practice_code_review"]
toolChoice = SPECIFIC("submit_practice_code_review")
scope = FIRST_STEP_ONLY
parallelToolCalls = false
maxToolCalls = 1
```

第一阶段的权限机制仍然适用。后续可以选择：

- 显式点击“提交 Review”视为本次已授权，不再弹二次确认。
- 或仍保留确认弹窗，让用户看到模型即将执行的工具和影响。

## 代码改造点

### agent-core

第一阶段新增或修改：

- 新增工具权限相关模型：
  - `AgentToolPermissionBehavior`
  - `AgentToolPermissionRequest`
  - `AgentToolPermissionDecision`
  - `AgentToolPermissionDecisionType`
  - `AgentToolPermissionService`
- `AgentLoopRunner` 在 `executeTool(...)` 前调用 permission service。
- `AgentLoopLifecycle` 增加权限相关事件发布。
- `AgentStreamEvent` 增加权限事件。
- 拒绝或超时时生成标准 tool result，而不是直接终止 run。
- 工具执行结果压缩逻辑继续复用现有 `ToolResultCompactor`。

### mentor-api

新增：

- 权限决策 API：

```text
POST /api/agent/tool-permissions/{permissionRequestId}/decision
```

- SSE mapper 支持：
  - `tool_permission_request`
  - `tool_permission_decision`
  - `tool_permission_timeout`

需要校验：

- 当前用户登录。
- 当前用户拥有该 run。
- request 未过期且未决策。

### mentor-application

新增：

- `submit_practice_code_review` 工具。
- 工具权限策略配置为 `ASK`。
- 工具复用 `PracticeCodeReviewService`。
- 工具从服务端上下文读取 session、user、problem 和消息，不信任模型传参。
- 练习聊天 prompt 暴露 Review 工具使用说明。

### frontend

新增：

- 监听 `tool_permission_request` SSE 事件。
- 弹出确认对话框。
- 用户允许/拒绝后调用决策 API。
- 决策发送后保持当前 SSE 流等待后续事件。
- 超时或请求失败时展示可恢复提示。

弹窗信息不应过长，代码预览需要限制长度。

## 观测与审计

需要记录以下 metadata：

- `permissionRequestId`
- `toolName`
- `toolCallId`
- `permissionBehavior`
- `permissionDecision`
- `permissionDecisionReason`
- `permissionTimeout=true/false`
- `permissionLatencyMs`
- `toolPolicySource`

建议指标：

- 权限请求次数。
- 允许率、拒绝率、超时率。
- 高权限工具执行次数。
- Review 工具执行成功率和耗时。
- 用户确认后工具执行失败次数。

## 风险与应对

### 前端关闭或网络断开

风险：Agent loop 等待用户决策时无人响应。

应对：

- 设置 60 秒超时。
- 超时按 `DENY` 处理。
- 发出 `tool_permission_timeout` 事件。
- 将超时作为 tool result 回填给模型。

### 多实例部署下 pending decision 丢失

风险：内存 pending request 只在单实例有效。

应对：

- 第一阶段本地单实例可先用内存实现。
- 多实例部署前迁移到 Redis 或 PostgreSQL。

### 模型误调用 Review 工具

风险：模型把普通代码问题误判为正式 Review。

应对：

- Review 工具默认 `ASK`。
- 用户拒绝后不执行工具。
- 工具描述强调“正式 Review”边界。

### 模型不调用 Review 工具

风险：用户希望 Review，但模型没有调用工具。

应对：

- 接受该限制，后续不追求 100% 触发。
- 通过工具描述和 prompt 优化提升调用率。
- 不通过 forced tool calling 解决确定性。

### 工具参数被 prompt injection 污染

应对：

- 工具实现只信任服务端上下文。
- 对模型参数做 schema 校验。
- 敏感操作必须重新校验用户、session、权限和题目归属。

## 分阶段实施

### 阶段零：清理旧自动 Review 触发链路

- 简化 `PracticeTurnOrchestrator`，移除 run_end 包装、turn message lookup、classification 和 capability 执行。
- 移除旧 post-run capability 相关 bean wiring，避免 Review infrastructure 存在时自动注册 `CodeReviewTurnCapability`。
- 删除旧自动触发专用类和测试，保留 Review service、repository、completion gate 和历史查询能力。
- 调整回归测试，明确完整代码进入普通聊天后不会自动落 Review，也不会追加 `practiceCapabilities` metadata。

### 阶段一：权限人在回路闭环

- 新增工具权限模型和内存权限服务。
- `AgentLoopRunner` 执行工具前接入权限检查。
- 新增权限 SSE 事件和决策 API。
- 新增 `submit_practice_code_review` 工具，并设置为 `ASK`。
- 前端支持确认弹窗和决策提交。
- 用户允许后执行 Review 工具并回填结果。
- 用户拒绝或超时后回填拒绝结果，run 正常结束。
- 暂不做 forced tool calling。
- 暂不做后端代码 Review 意图识别。

### 阶段二：自主 Tool Call Prompt 强化

- 强化 practice chat prompt，要求模型在疑似完整解法提交时主动调用 `submit_practice_code_review`。
- 强化 `submit_practice_code_review` 工具描述，明确它是正式代码提交记录和 Review 子流程入口。
- 保持阶段一权限确认链路不变。
- 不新增 forced tool calling、后端规则识别或显式 Review run。
