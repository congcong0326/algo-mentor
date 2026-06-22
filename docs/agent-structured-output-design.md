# Agent 结构化输出与最终结果捕获设计

## 背景

当前 `agent-core` 的 `AgentLoopRunner` 已经具备流式 LLM 调用、工具调用、run step、生命周期 observer、trace snapshot 和 run 内上下文压缩能力。它适合“边生成边通过 SSE 输出”的聊天和讲解场景，但对“收集用户基础信息、追问缺失信息、最终生成学习计划”这类业务流程还缺少两个关键底座：

- 每次 run 需要能声明执行配置，例如温度、最大输出 token、结构化响应格式和后续可扩展的结构化输出策略。
- Agent loop 需要把最终 assistant 输出作为正式结果暴露给应用层，而不是只通过 SSE token 和持久化 observer 拼接文本。

学习计划生成属于结构化业务产物。业务层需要拿到稳定 JSON，做 schema 校验、业务校验、落库和后续确认。如果只依赖 prompt 约束模型“请输出 JSON”，前端渲染、幂等重试、断线恢复、审计回放和错误处理都会变脆。

成熟框架的共同方向可以概括为：

- 结构化输出格式是 run/agent 的一等执行配置，不是藏在 prompt 里的自然语言约定。
- 最终输出是 run result 或 agent state 的正式字段，不是流式 token 的副产品。
- 对支持原生结构化输出的模型优先使用 provider-native JSON schema；tool-call 方式更多作为弱模型兼容和自动修复兜底。

## 设计目标

- `AgentRequest` 能携带通用执行配置，并由 `AgentLlmRequestFactory` 原样映射到 `LlmCompletionRequest`。
- 支持 provider-native structured output，第一阶段复用 `llm-core` 已有的 `LlmResponseFormat.JsonSchema`。
- `AgentLoopRunner` 在流式转发 token 的同时，聚合最终 assistant 输出，并在 run 成功时暴露 `AgentOutput`。
- 应用层可以从 `AgentRunResult` 或 observer 生命周期中拿到最终文本和结构化 JSON。
- 保持 `agent-core` 业务无关，不引入 `LearningPlan`、用户画像、学习目标等领域模型。
- 保持现有 SSE 事件兼容；新增结果捕获能力不要求客户端改变现有 token 消费方式。
- 为后续 `AUTO` 或 `TOOL_CALL` 结构化输出策略预留接口，但第一阶段不实现 ToolStrategy。

## 非目标

- 第一阶段不实现 LangChain 式 ToolStrategy。
- 第一阶段不实现结构化输出自动纠错 loop，例如 schema 校验失败后把错误作为 tool result 回填给模型。
- 第一阶段不引入复杂 artifact 存储模型；学习计划业务产物由 `mentor-application` 和业务表处理。
- 第一阶段不改变 provider SDK 的具体适配方式，只使用 `llm-core` 已有的 `LlmResponseFormat` 抽象。
- 不把结构化输出 schema 放入 `metadata` 作为隐式约定。

## 核心原则

### 执行配置显式化

模型执行参数和输出格式属于调用契约，应放在强类型对象中。`metadata` 只保存观测、路由、审计和业务关联信息，不承载影响 provider 请求语义的核心配置。

### Provider-native 作为主路径

当前主要目标是强模型和稳定学习计划生成。对支持原生 JSON Schema 的 provider，应直接使用 provider-native structured output：

```text
AgentRequest.executionOptions.responseFormat(JsonSchema)
  -> LlmCompletionRequest.responseFormat
  -> provider 原生结构化输出
  -> AgentOutput.structured
  -> mentor-application 校验并保存业务对象
```

相比 ToolStrategy，这条路径更短、更符合 provider 能力演进方向，也减少了把最终结果伪装成工具调用带来的 trace 噪音和边界复杂度。

### 最终输出是正式结果

`AgentRunResult` 应携带最终输出，或至少通过 `AgentLoopObserver` 暴露明确的 final output 生命周期事件。持久化 observer、业务应用层和测试不应再从零散 token 中推断 run 的最终产物。

### 中间 step 与最终 step 分离

一次 run 可能包含多次 LLM step：前面 step 可能产生 tool call，最后 step 才给出最终回答。最终输出应来自“无需继续工具调用的最后一个 assistant message”，而不是把所有 step 的 `ContentDelta` 简单拼接。

这点也会修正当前持久化 observer 的隐含风险：如果模型在工具调用 step 中输出了少量解释文本，不能把这些中间文本和最终回答合并成一个 assistant message。

## 核心模型

### AgentExecutionOptions

建议在 `agent-core` 增加执行配置模型：

```java
public record AgentExecutionOptions(
    LlmGenerationOptions generationOptions,
    LlmResponseFormat responseFormat,
    AgentStructuredOutputOptions structuredOutput
) {

  public AgentExecutionOptions {
    generationOptions = generationOptions == null ? LlmGenerationOptions.defaults() : generationOptions;
    responseFormat = responseFormat == null ? new LlmResponseFormat.Text() : responseFormat;
    structuredOutput = structuredOutput == null
        ? AgentStructuredOutputOptions.none()
        : structuredOutput;
  }

  public static AgentExecutionOptions defaults() {
    return new AgentExecutionOptions(
        LlmGenerationOptions.defaults(),
        new LlmResponseFormat.Text(),
        AgentStructuredOutputOptions.none());
  }
}
```

### AgentStructuredOutputOptions

`responseFormat` 描述 provider 请求格式，`structuredOutput` 描述 agent 层如何理解和校验最终输出。第一阶段只实现 `PROVIDER_NATIVE`：

```java
public record AgentStructuredOutputOptions(
    StructuredOutputStrategy strategy,
    String schemaName,
    String schemaVersion,
    boolean required
) {

  public static AgentStructuredOutputOptions none() {
    return new AgentStructuredOutputOptions(
        StructuredOutputStrategy.NONE,
        null,
        null,
        false);
  }
}
```

```java
public enum StructuredOutputStrategy {
  NONE,
  PROVIDER_NATIVE,
  AUTO,
  TOOL_CALL
}
```

第一阶段约束：

- `NONE`：普通文本输出。
- `PROVIDER_NATIVE`：要求 `responseFormat` 是 `LlmResponseFormat.JsonObject` 或 `LlmResponseFormat.JsonSchema`。
- `AUTO`：先保留枚举，不启用。
- `TOOL_CALL`：先保留枚举，不启用。

### AgentRequest

扩展 `AgentRequest`，保留兼容构造函数：

```java
public record AgentRequest(
    String runId,
    String requestId,
    List<LlmMessage> messages,
    Map<String, Object> metadata,
    AgentExecutionOptions executionOptions
) {

  public AgentRequest(String runId, String requestId, List<LlmMessage> messages, Map<String, Object> metadata) {
    this(runId, requestId, messages, metadata, AgentExecutionOptions.defaults());
  }
}
```

`AgentLlmRequestFactory.build(...)` 应把配置映射进 `LlmCompletionRequest`：

```java
return LlmCompletionRequest.builder()
    .modelSelector(validatedSelector(modelSelector))
    .messages(messages)
    .options(request.executionOptions().generationOptions())
    .tools(tools)
    .toolChoice(...)
    .responseFormat(request.executionOptions().responseFormat())
    .metadata(metadata)
    .build();
```

### AgentOutput

新增通用最终输出模型：

```java
public record AgentOutput(
    String text,
    JsonNode structured,
    String schemaName,
    String schemaVersion,
    Map<String, Object> metadata
) {

  public boolean hasStructuredOutput() {
    return structured != null && !structured.isNull();
  }
}
```

语义：

- `text`：最终 assistant message 的完整文本。普通文本输出一定有值；结构化输出时也可以保存原始 JSON 文本。
- `structured`：解析后的 JSON。仅当 response format 是 JSON 且解析成功时有值。
- `schemaName` / `schemaVersion`：来自 `AgentStructuredOutputOptions`，用于业务校验和审计。
- `metadata`：保存解析策略、输出字符数、schema 校验状态等通用信息。

### AgentRunResult

扩展 run result：

```java
public record AgentRunResult(
    int steps,
    LlmFinishReason finishReason,
    AgentOutput output,
    Map<String, Object> metadata
) {
}
```

兼容方式：

- 可以保留旧构造函数，把 `output` 设为 `null` 或空输出。
- 新代码应使用带 `AgentOutput` 的构造函数。

## 生命周期扩展

建议在 `AgentLoopObserver` 增加一个明确的最终输出事件：

```java
default void onFinalOutput(AgentLoopContext context, AgentOutput output) {
}
```

触发时机：

```text
final step LLM stream complete
  -> StepCollector.result() 判定不需要工具
  -> AgentLoopRunner 构造 AgentOutput
  -> lifecycle.finalOutput(context, output)
  -> lifecycle.runEnded(context, AgentRunResult(..., output, ...))
```

顺序建议：

1. `onStepEnd`
2. `onFinalOutput`
3. `onRunEnd`

原因：

- `onStepEnd` 表达单次 LLM 调用完成。
- `onFinalOutput` 表达 agent 已经确定最终产物。
- `onRunEnd` 表达整个 run 终态完成，持久化 observer 可以在这里更新 run 状态。

`AgentLoopLifecycle` 也应增加对应方法，并保证 observer 异常不会中断主流程，保持现有生命周期语义。

## AgentLoopRunner 行为

### StepCollector 聚合内容

`StepCollector` 当前只保留 tool calls 和 finish reason。建议增加当前 step 的内容聚合：

```java
private final StringBuilder content = new StringBuilder();
```

在 `onNext` 中：

```java
if (item instanceof LlmStreamEvent.ContentDelta delta) {
  content.append(delta.content());
}
```

`AgentStepResult` 可扩展为：

```java
public record AgentStepResult(
    List<LlmToolCall> toolCalls,
    LlmFinishReason finishReason,
    String content
) {
}
```

只有当 `!stepResult.requiresTools()` 时，`stepResult.content()` 才能作为最终输出候选。

### 构造 AgentOutput

`AgentLoopRunner` 在最终 step 后根据 `AgentRequest.executionOptions()` 构造输出：

```text
Text response format
  -> AgentOutput.text = finalContent
  -> AgentOutput.structured = null

JsonObject / JsonSchema response format
  -> parse finalContent as JsonNode
  -> AgentOutput.text = finalContent
  -> AgentOutput.structured = parsed JsonNode
```

解析失败策略：

- 如果 `structuredOutput.required == true`，抛出 `AgentException`，错误码建议新增 `STRUCTURED_OUTPUT_INVALID`。
- 如果 `required == false`，保留 `text`，`structured` 为空，并在 metadata 记录解析失败原因。

第一阶段可以先不在 core 做完整 JSON Schema 校验，因为 provider-native strict schema 已经在 provider 侧约束。业务层仍需要做业务校验，例如学习计划天数、阶段数量、每日任务为空等。

### 与工具调用 step 的关系

当某个 step `requiresTools()`：

- 继续向 SSE 转发该 step 的 token 和 tool call 事件。
- 可以在 trace 中记录该 step 的 content。
- 不把该 step 的 content 作为最终 assistant message。
- 执行工具并进入下一 step。

最终输出只来自第一个“不需要继续工具调用”的 step。

## 持久化影响

### PersistentAgentRunObserver

当前 `PersistentAgentRunObserver` 在 `onLlmEvent` 中把所有 `ContentDelta` 拼到一个 buffer，并在 `onRunEnd` 插入 assistant message。引入 final output 后，建议调整为：

- `onFinalOutput` 接收 `AgentOutput`，保存最终 assistant message。
- `onRunEnd` 只更新 run 成功状态、usage、finishReason，不再决定 assistant message 内容。

这样可以避免多 step run 中中间 content 污染最终 assistant message。

### PersistentAgentTraceObserver

`PersistentAgentTraceObserver` 已经保存 final request snapshot，其中包括 `responseFormat`。扩展 `AgentRequest.executionOptions` 后，需要确认 snapshot 中能看到：

- generation options
- response format
- schema name/version metadata

如果 schema 很大，第一阶段可以 inline 保存；后续再考虑 schema hash 或 artifact 引用。

### 业务产物存储

学习计划 JSON 不建议只存在 agent trace 中。推荐链路：

```text
AgentOutput.structured
  -> mentor-application 反序列化为 LearningPlanDraft
  -> 业务校验
  -> 保存 learning_plan / learning_plan_item 等业务表
  -> agent run metadata 或业务表记录 runId/sourceRunId
```

agent trace 用于审计和回放，业务表用于产品功能。

## API 与应用层使用方式

学习计划生成场景建议由 `mentor-application` 组装请求：

```java
AgentRequest request = new AgentRequest(
    runUuid,
    requestId,
    context.messages(),
    metadata,
    new AgentExecutionOptions(
        new LlmGenerationOptions(0.2, null, 3000, List.of(), null, timeout),
        new LlmResponseFormat.JsonSchema("learning_plan_draft", schema, true),
        new AgentStructuredOutputOptions(
            StructuredOutputStrategy.PROVIDER_NATIVE,
            "learning_plan_draft",
            "v1",
            true)));
```

应用层拿到 `AgentOutput` 后：

```text
AgentOutput.structured
  -> LearningPlanDraft DTO
  -> 业务校验
  -> 保存计划草稿
  -> 返回前端 planId / draftId
```

如果当前 API 仍是 SSE 流式接口，可以先通过 observer 落库，再让前端在 run end 后查询生成的计划草稿。后续也可以在 SSE 中新增 `final_output` 或 `artifact_created` 事件，但第一阶段不是必须。

## 为什么第一阶段不做 ToolStrategy

ToolStrategy 的做法是把“最终结构化输出”包装成一个特殊工具，例如 `submit_learning_plan`，模型通过 tool call arguments 提交结果。它的优势是：

- 兼容不支持 provider-native JSON schema 的模型。
- 校验失败后可以把错误作为 tool result 回填给模型，让模型再次提交。
- 可以复用 tool call 的 trace、参数和 step 机制。

但在当前项目的第一阶段，它的代价更明显：

- 把最终业务产物伪装成工具调用，语义上比 provider-native 更绕。
- 增加 step、延迟、费用和 trace 噪音。
- 需要处理模型同时调用普通工具和 final output tool 的复杂边界。
- 当前工具系统主要表达外部能力调用，过早混入 final output tool 会模糊职责。

因此推荐：

```text
第一阶段：只实现 PROVIDER_NATIVE
后续需要兼容弱模型或自动修复时：再实现 TOOL_CALL
AUTO：作为未来策略，由模型/provider capability 决定 provider-native 或 tool-call
```

## 错误处理

建议新增或复用错误码：

- `STRUCTURED_OUTPUT_INVALID`：模型最终输出不是合法 JSON，或不满足 required 结构化输出要求。
- `STRUCTURED_OUTPUT_UNSUPPORTED`：请求了 `PROVIDER_NATIVE`，但当前 provider/model 不支持对应 response format。
- `STRUCTURED_OUTPUT_SCHEMA_INVALID`：业务传入的 schema 自身不合法。

第一阶段推荐失败策略：

- 学习计划生成请求使用 `required=true`。
- 解析失败直接让 run failed，不保存业务计划。
- API 返回可重试错误；后续可以补自动重试或降级文本解释。

## 测试建议

### agent-core 单元测试

- `AgentRequest` 默认执行配置兼容旧构造方式。
- `AgentLlmRequestFactory` 正确透传 `generationOptions` 和 `responseFormat`。
- 文本输出 run 产生 `AgentOutput.text`。
- JSON schema 输出 run 产生 `AgentOutput.structured`。
- 结构化输出 required 且 JSON 非法时抛出 `AgentException`。
- 工具调用 step 的 content 不会成为最终 output。
- `onFinalOutput` 在 `onRunEnd` 前触发。

### persistence 测试

- `PersistentAgentRunObserver` 使用 `onFinalOutput` 插入 assistant message。
- 多 step run 中只保存最终 step 内容。
- trace snapshot 包含 response format 和 generation options。

### application 测试

- 学习计划 schema 请求能生成结构化 `AgentOutput`。
- schema 输出转换为业务 DTO 后能保存计划草稿。
- 业务校验失败不落计划表，并能记录 run error 或业务错误。

## 分阶段落地

### 阶段 1：执行配置入 core

- 新增 `AgentExecutionOptions`、`AgentStructuredOutputOptions`、`StructuredOutputStrategy`。
- 扩展 `AgentRequest` 并保留兼容构造函数。
- 修改 `AgentLlmRequestFactory` 透传 options 和 response format。
- 补充 agent-core 测试。

### 阶段 2：最终输出捕获

- 扩展 `AgentStepResult`，聚合 step content。
- 新增 `AgentOutput`。
- 扩展 `AgentRunResult`。
- 新增 `AgentLoopObserver.onFinalOutput` 和 lifecycle 方法。
- 修改 `AgentLoopRunner` 构造最终输出。
- 补充生命周期顺序和多 step 测试。

### 阶段 3：持久化 observer 调整

- `PersistentAgentRunObserver` 改用 `onFinalOutput` 保存 assistant message。
- 保持 `onRunEnd` 只负责 run/turn 成功状态。
- 补充多 step 持久化回归测试。

### 阶段 4：学习计划业务接入

- 在 `mentor-application` 定义 `learning_plan_draft` schema。
- 学习计划生成 run 使用 `PROVIDER_NATIVE`。
- 将 `AgentOutput.structured` 转为业务 DTO 并保存计划草稿。
- 前端在 run end 后展示计划草稿或通过后续接口查询。

## 后续演进

- 根据 provider capabilities 自动选择 `PROVIDER_NATIVE` 或 `TOOL_CALL`。
- 增加 schema hash、schema registry 或 artifact 引用，避免大 schema 重复写入 snapshot。
- 增加结构化输出自动修复策略，例如 JSON 解析失败后用一次轻量 repair prompt。
- 增加 `final_output` SSE 事件，让前端在流末端直接收到结构化产物摘要。
- 如果引入弱模型或多 provider 混跑，再实现 ToolStrategy 作为兼容路径。
