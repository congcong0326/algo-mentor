# Agent Loop 生命周期扩展设计

## 背景

`AgentLoopRunner` 当前负责组织一次 Agent 流式执行：初始化消息、调用 LLM stream、收集工具调用、执行工具、追加工具结果并继续下一轮循环。循环中的关键阶段已经通过 `AgentStreamEvent` 对外发布，例如 run start、step start/end、tool start/end、run end 和 error。

这些 `publisher.submit(...)` 调用本质上已经形成了 agent-core 内部的生命周期事件发布机制。API 层的 SSE 只是这些事件的下游传输形式，不应反向决定 agent-core 的核心抽象。

后续需要在 loop 重要阶段加入扩展点，用于日志、指标、审计、学习过程沉淀，以及请求改写、工具调用拦截等能力。为避免职责混淆，生命周期扩展需要区分只读事件通知和可修改执行输入的机制。

## 设计目标

- 将 `AgentLoopRunner` 中分散的生命周期事件发布收敛到统一组件。
- 区分只读观察和可改变行为的扩展点，保持调用语义清晰。
- 保持 `agent-core` 不依赖 HTTP、SSE、Spring Web 等传输层概念。
- 让 SSE、日志、metrics、trace、审计等能力作为事件消费者接入。
- 允许后续在明确边界内改写 LLM 请求、工具调用参数或工具结果。

## 非目标

- 不把 SSE 连接生命周期直接放进 `agent-core`。
- 不把所有扩展点塞进一个泛化的 `Hook` 接口。
- 不在第一阶段支持任意修改 loop 控制流，例如任意跳步、重试策略替换或动态修改 `maxSteps`。
- 不让 observer 失败默认影响主流程，除非后续明确引入强一致审计类场景。

## 核心概念

### AgentLoopObserver

`AgentLoopObserver` 是只读事件通知机制，职责是知道发生了什么，不改变主流程输入和输出。

适用场景：

- 发布 `AgentStreamEvent` 给下游订阅者。
- 输出结构化日志。
- 记录 metrics 和 tracing。
- 写入审计或学习过程记录。
- 采集调试信息。

示例接口：

```java
public interface AgentLoopObserver {
  default void onRunStart(AgentLoopContext context) {}

  default void onStepStart(AgentLoopContext context, int stepIndex) {}

  default void onLlmEvent(AgentLoopContext context, int stepIndex, LlmStreamEvent event) {}

  default void onStepEnd(AgentLoopContext context, int stepIndex, AgentStepResult result) {}

  default void onToolStart(AgentLoopContext context, int stepIndex, LlmToolCall toolCall) {}

  default void onToolEnd(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      JsonNode result
  ) {}

  default void onRunEnd(AgentLoopContext context, AgentRunResult result) {}

  default void onError(AgentLoopContext context, AgentException error) {}
}
```

现有的 `publisher.submit(new AgentStreamEvent...)` 可以收敛为一个内置 observer，或者由生命周期调度器在调用 observer 的同时统一发布事件。

### AgentLoopInterceptor

`AgentLoopInterceptor` 是可修改执行输入或输出的扩展机制，职责是参与执行决策或改写明确的执行对象。

适用场景：

- 修改 `LlmCompletionRequest`，例如注入系统提示词、裁剪历史消息、附加 metadata。
- 检查或改写 tool call 参数。
- 阻止不允许的工具调用。
- 改写工具执行结果。
- 做额度、安全、权限检查。

接口应使用返回值表达修改结果，避免依赖原地修改：

```java
public interface AgentLoopInterceptor {
  default LlmCompletionRequest beforeLlmRequest(
      AgentLoopContext context,
      int stepIndex,
      LlmCompletionRequest request
  ) {
    return request;
  }

  default LlmToolCall beforeToolCall(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall
  ) {
    return toolCall;
  }

  default JsonNode afterToolCall(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      JsonNode result
  ) {
    return result;
  }
}
```

如果 interceptor 需要阻断流程，应抛出 `AgentException`，由 loop 统一转换为 `agent_error` 生命周期事件。

### AgentLoopLifecycle

`AgentLoopLifecycle` 是内部调度组件，不直接代表业务扩展点。它负责把 `AgentLoopRunner` 中分散的生命周期动作收敛起来：

- 调用 observer。
- 调用 interceptor。
- 统一发布 `AgentStreamEvent`。
- 管理 observer/interceptor 的调用顺序和异常语义。

建议结构：

```text
AgentLoopRunner
  -> AgentLoopLifecycle
       -> AgentLoopInterceptor 列表
       -> AgentLoopObserver 列表
       -> AgentStreamEvent 发布
```

`AgentLoopRunner` 应更聚焦于 loop 编排本身，例如：

```java
lifecycle.runStarted(...);

for (int stepIndex = 1; stepIndex <= maxSteps; stepIndex++) {
  lifecycle.stepStarted(...);
  LlmCompletionRequest request = lifecycle.beforeLlmRequest(...);
  StepResult result = runStep(...);

  for (LlmToolCall toolCall : result.toolCalls()) {
    LlmToolCall effectiveToolCall = lifecycle.beforeToolCall(...);
    lifecycle.toolStarted(...);
    JsonNode result = executeTool(...);
    JsonNode effectiveResult = lifecycle.afterToolCall(...);
    lifecycle.toolEnded(...);
  }
}
```

## 推荐执行顺序

一次正常执行的顺序建议如下：

```text
run start
  observer.onRunStart
  publish agent_run_start

step start
  observer.onStepStart
  publish agent_step_start

build llm request
  interceptor.beforeLlmRequest
  call llm stream

llm stream event
  observer.onLlmEvent
  publish LLM mapped AgentStreamEvent

step end
  observer.onStepEnd
  publish agent_step_end

tool call
  interceptor.beforeToolCall
  observer.onToolStart
  publish agent_tool_start
  execute tool
  interceptor.afterToolCall
  observer.onToolEnd
  publish agent_tool_end

run end
  observer.onRunEnd
  publish agent_run_end
```

异常路径：

```text
exception
  convert to AgentException
  observer.onError
  publish agent_error
  close publisher
```

## SSE 生命周期边界

SSE 生命周期可以纳入整体观测，但不应直接成为 `agent-core` 的 hook。原因是 `agent-core` 当前只暴露 `Flow.Publisher<AgentStreamEvent>`，不应该感知 HTTP 连接、客户端断开、SSE emitter 超时等传输细节。

建议在 `mentor-api` 层单独建模 SSE 生命周期，例如：

```java
public interface AgentStreamObserver {
  default void onSubscribe(String runId) {}

  default void onEvent(String runId, AgentStreamEvent event) {}

  default void onClientDisconnect(String runId) {}

  default void onStreamError(String runId, Throwable error) {}

  default void onStreamComplete(String runId) {}
}
```

`agent-core` 与 SSE 层通过 `runId`、trace id 或 request id 关联。这样即使后续把传输方式换成 WebSocket、后台任务、消息队列或批处理，也不会影响 agent loop 的核心生命周期抽象。

## 异常语义

- interceptor 属于执行链的一部分，失败应转换为 `AgentException` 并终止当前 run。
- observer 默认不应影响主流程；observer 失败应记录日志并继续。
- 如果存在必须强一致的 observer，例如审计写入失败必须中断执行，应作为单独的强约束 interceptor 或明确配置的 mandatory observer 处理。

## 命名建议

- `AgentLoopObserver`：只读生命周期通知。
- `AgentLoopInterceptor`：可改写执行输入或输出。
- `AgentLoopLifecycle`：内部生命周期调度器。
- `AgentLoopContext`：单次 run 的上下文，包含 `runId`、topic、maxSteps、必要 metadata。
- `AgentStepResult`：step 结束的稳定结果视图。
- `AgentRunResult`：run 结束的稳定结果视图。

## 分阶段落地建议

第一阶段：

- 新增 `AgentLoopObserver` 和 `AgentLoopLifecycle`。
- 将 `publisher.submit(...)` 从 `AgentLoopRunner` 中收敛到 lifecycle。
- 保持现有 `AgentStreamEvent` 协议不变。
- 增加测试覆盖事件顺序和 observer 调用顺序。

第二阶段：

- 新增 `AgentLoopInterceptor`。
- 支持 `beforeLlmRequest`、`beforeToolCall`、`afterToolCall`。
- 明确多个 interceptor 的顺序规则。
- 增加测试覆盖请求改写、工具调用改写和异常阻断。

第三阶段：

- 在 `mentor-api` 层补充 SSE/stream observer。
- 用 `runId` 或 trace id 串联 agent loop 与 SSE 连接生命周期。
- 接入 metrics、日志、审计或学习过程持久化。
