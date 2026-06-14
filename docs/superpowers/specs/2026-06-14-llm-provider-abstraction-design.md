# LLM Provider 抽象研发设计

## 背景

`algo-mentor` 后端已经包含 `llm-core`、`llm-openai`、`agent-core`、`mentor-application` 与 `mentor-api` 模块。当前 `llm-core` 只提供最小聊天抽象：同步完成、流式文本回调、消息角色、请求模型和响应文本。这个抽象足以承载早期演示，但无法稳定屏蔽不同模型与 provider 在工具调用、结构化输出、流式事件、多模态输入、错误格式和 token usage 上的差异。

本设计目标是在 `backend/llm-core` 中定义项目内长期稳定的 LLM provider 抽象，对上层提供统一服务，对下层隔离 OpenAI、Anthropic、Qwen、DeepSeek、Gemini 等 provider 的 SDK 与协议差异。

## 目标

- 定义通用 provider 契约，支持聊天补全、流式输出、工具调用、结构化输出、能力发现和多模态输入扩展位。
- 让 `agent-core`、`mentor-application` 和 `mentor-api` 只依赖 `llm-core`，不感知 OpenAI SDK 或 provider 专有字段。
- 将能力校验、默认模型选择、provider 路由和错误归一放在统一 gateway 边界内。
- 为后续算法题解析、学习计划生成、AI 讲解、错题复盘、SSE 流式事件和 agent 工具调用提供稳定基础。
- 保持 `llm-core` 与 Spring MVC、OpenAI SDK、数据库和具体业务 DTO 解耦。

## 非目标

- 不在本阶段实现真实 OpenAI API 调用。
- 不在 `llm-core` 执行业务工具，工具执行由 `agent-core` 或应用层负责。
- 不在 `llm-core` 绑定算法学习领域 DTO。
- 不设计 embedding、rerank、图片生成等完整接口，只保留能力枚举和扩展空间。

## 模块边界

### `llm-core`

`llm-core` 是稳定契约层，定义：

- provider 抽象。
- gateway 统一入口。
- 请求、消息、内容 part、响应、流式事件。
- 工具定义、工具调用和工具结果协议。
- 结构化输出格式。
- provider/model 能力发现。
- token usage、finish reason、错误码和统一异常。
- 少量普通 Java 配置模型，例如超时、重试、默认生成参数。

`llm-core` 可以引入少量通用依赖来简化 JSON Schema、异步流和不可变数据模型，例如 Jackson `JsonNode` 和 JDK `Flow.Publisher`。它不引入 Spring Bean、OpenAI SDK、HTTP client、数据库或业务模块依赖。

### `llm-openai`

`llm-openai` 实现 `llm-core` 中的 provider 契约，负责：

- 读取并绑定 `algo-mentor.ai.openai.*` 配置。
- 将 core 请求映射为 OpenAI SDK 参数。
- 将 OpenAI 响应、流式事件、usage、finish reason 和工具调用映射回 core 模型。
- 将 OpenAI SDK 异常翻译成 `LlmException`。
- 执行 provider 级超时、重试和敏感字段保护。

### 上层模块

- `agent-core`：依赖 `LlmGateway`，负责 agent 编排、工具执行、工具结果回传和多轮调用。
- `mentor-application`：通过 use case 组织算法学习业务，不直接构造 provider 专有请求。
- `mentor-api`：负责 HTTP/SSE 适配，把 `LlmStreamEvent` 转换为稳定 API 事件。

## 核心接口

`llm-core` 采用 provider 与 gateway 两层接口。

```java
public interface LlmProvider {
  LlmProviderId id();
  LlmProviderCapabilities capabilities();
  List<LlmModelDescriptor> models();
  LlmCompletionResult complete(LlmCompletionRequest request);
  Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request);
}
```

`LlmProvider` 是 provider 模块实现的低层契约。它只表达 provider 能做什么，以及如何执行一次统一请求。

```java
public interface LlmGateway {
  LlmCompletionResult complete(LlmCompletionRequest request);
  Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request);
}
```

`LlmGateway` 是上层推荐使用的统一入口，负责：

- 根据 `provider`、`model`、用途或能力选择具体 provider/model。
- 应用默认模型和默认生成参数。
- 在调用前进行能力校验。
- 将 provider 抛出的异常归一为 `LlmException`。

## 请求模型

`LlmCompletionRequest` 表达一次通用 LLM 生成请求：

- `LlmModelSelector modelSelector`：指定 provider、model，或按能力/用途选择。
- `List<LlmMessage> messages`：system、user、assistant、tool 消息。
- `LlmGenerationOptions options`：temperature、topP、maxOutputTokens、stop、seed、timeout、streamOptions 等。
- `List<LlmToolSpec> tools`：可调用工具定义。
- `LlmToolChoice toolChoice`：`auto`、`none`、`required`、`specific`。
- `LlmResponseFormat responseFormat`：文本、JSON object 或 JSON Schema。
- `Map<String, Object> metadata`：traceId、业务场景、幂等键等非 provider 私货元信息。

`metadata` 不作为 provider 专有参数逃生口。provider 不支持的能力必须明确失败或通过 gateway 策略降级，不能默默忽略关键请求约束。

## 消息与内容 part

现有 `LlmMessage(String content)` 升级为多 part 内容模型：

- `role`：`SYSTEM`、`USER`、`ASSISTANT`、`TOOL`。
- `content`：`List<LlmContentPart>`。
- `name`：可选参与者或工具名。
- `toolCallId`：工具结果消息关联的调用 id。
- `metadata`：非敏感追踪信息。

`LlmContentPart` 支持：

- `text`：普通文本。
- `image`：URL、base64/mediaType 或文件引用，用于后续题目截图、白板图等场景。
- `file`：文件引用、mediaType、文件名。
- `toolResult`：工具执行结果。
- `custom`：保留扩展位。provider 不支持时必须明确失败或降级。

## 响应模型

`LlmCompletionResult` 统一表达一次非流式结果：

- `message`：统一 assistant 消息。
- `toolCalls`：模型请求调用的工具列表。
- `structuredOutput`：结构化 JSON 结果，使用 Jackson `JsonNode` 表示。
- `finishReason`：`STOP`、`LENGTH`、`TOOL_CALLS`、`CONTENT_FILTER`、`ERROR`、`UNKNOWN`。
- `usage`：inputTokens、outputTokens、cachedTokens、reasoningTokens、totalTokens。
- `model`：实际使用模型。
- `provider`：实际使用 provider。
- `metadata`：provider request id、耗时等非敏感信息。

业务层如需强类型 DTO，应在业务模块中把 `structuredOutput` 转换为领域对象，`llm-core` 不绑定业务类型。

## 流式事件

流式接口返回 `Flow.Publisher<LlmStreamEvent>`，事件类型包括：

- `MESSAGE_START`：assistant 消息开始。
- `CONTENT_DELTA`：文本或内容片段增量。
- `TOOL_CALL_START`：工具调用开始。
- `TOOL_CALL_DELTA`：工具调用参数增量。
- `TOOL_CALL_END`：工具调用结束。
- `MESSAGE_END`：assistant 消息结束，包含 finish reason。
- `USAGE`：token usage。
- `ERROR`：统一错误事件。
- `HEARTBEAT`：长连接保活。

API 层负责把这些事件映射为 SSE 事件。上层不再依赖简单 `String chunk`，从而可以稳定承载工具调用进度、最终 usage 和错误信息。

## 工具调用协议

工具定义：

```java
public record LlmToolSpec(
    String name,
    String description,
    JsonNode inputSchema,
    boolean strict
) {}
```

工具调用：

```java
public record LlmToolCall(
    String id,
    String name,
    JsonNode arguments
) {}
```

工具结果通过下一轮消息回传：

```java
LlmMessage.toolResult(toolCallId, JsonNode result)
```

provider 只负责协议映射，不执行真实工具。工具是否允许副作用、如何鉴权、如何超时、是否重试、如何落日志，由 `agent-core` 或应用层控制。

## 结构化输出

`LlmResponseFormat` 定义为 sealed interface：

```java
public sealed interface LlmResponseFormat {
  record Text() implements LlmResponseFormat {}
  record JsonObject() implements LlmResponseFormat {}
  record JsonSchema(String name, JsonNode schema, boolean strict) implements LlmResponseFormat {}
}
```

规则：

- `Text` 是默认格式。
- `JsonObject` 要求 provider 尽量启用 JSON mode。
- `JsonSchema` 用于题目解析、学习计划生成、错题分析等需要稳定字段的场景。
- provider 不支持请求格式时，gateway 根据能力策略返回 `UNSUPPORTED_CAPABILITY` 或显式降级。
- 结构化结果统一放在 `structuredOutput`，不要求 provider 返回原始字符串再由业务层手动截取。

## 能力发现

`LlmProviderCapabilities` 描述 provider 和模型能力：

```java
public record LlmProviderCapabilities(
    Set<LlmCapability> capabilities,
    Map<String, LlmModelDescriptor> models
) {}
```

`LlmCapability` 初始枚举：

- `CHAT_COMPLETION`
- `STREAMING`
- `TOOL_CALLING`
- `STRUCTURED_OUTPUT`
- `JSON_SCHEMA_OUTPUT`
- `VISION_INPUT`
- `FILE_INPUT`
- `REASONING_EFFORT`
- `TOKEN_USAGE`
- `CACHED_TOKEN_USAGE`
- `EMBEDDING`

`LlmModelDescriptor` 包含：

- providerId、modelId、displayName。
- supportedCapabilities。
- contextWindowTokens。
- maxOutputTokens。
- defaultGenerationOptions。
- metadata。

`LlmGateway` 在执行前校验能力。例如请求包含图片但模型不支持 `VISION_INPUT`，或请求 `JsonSchema` 但模型不支持 `JSON_SCHEMA_OUTPUT`，应返回统一 `LlmException`。

## 错误模型

统一异常：

```java
public class LlmException extends RuntimeException {
  private final LlmErrorCode code;
  private final String provider;
  private final String model;
  private final boolean retryable;
  private final Map<String, Object> metadata;
}
```

`LlmErrorCode` 初始枚举：

- `INVALID_REQUEST`
- `UNSUPPORTED_CAPABILITY`
- `AUTHENTICATION_FAILED`
- `PERMISSION_DENIED`
- `RATE_LIMITED`
- `TIMEOUT`
- `PROVIDER_UNAVAILABLE`
- `CONTENT_FILTERED`
- `TOOL_CALL_INVALID`
- `RESPONSE_PARSE_FAILED`
- `CANCELLED`
- `UNKNOWN`

provider 实现必须把底层 SDK 异常翻译成 `LlmException`，不得把 OpenAI SDK 异常泄漏到上层。日志只记录 provider、model、错误码、请求 id、耗时和 token usage，不记录 API key、完整 prompt、Authorization 或用户隐私内容。

## 配置边界

- `llm-core` 不读取环境变量，不定义 Spring `@ConfigurationProperties`。
- `llm-core` 可以定义 `LlmProviderOptions`、`LlmRetryPolicy`、`LlmTimeoutOptions` 等普通 Java 配置模型。
- `llm-openai` 负责 Spring 配置绑定，再转换为 provider 实现参数。
- 默认 provider、默认模型和业务用途到模型的映射由应用启动配置装配到 `LlmGateway`。
- 业务代码不硬编码 OpenAI 模型名。

## 兼容迁移

- 保留旧 `LlmClient` 一个短周期，标记 `@Deprecated`。
- 新增 `LlmCompletionRequest`、`LlmCompletionResult` 等模型，避免一次性破坏已有测试。
- 旧 `LlmClient` 可内部适配到新的 `LlmGateway`，或由 `agent-core` 先迁移到新 gateway 后再删除。
- `AgentRunner` 从依赖 `LlmClient` 迁移到 `LlmGateway`。
- `AiExplanationService` 后续消费 `LlmStreamEvent` 并转换为 SSE。

## 测试策略

`llm-core` 重点测试：

- 请求构造、字段校验和不可变性。
- provider/model 能力校验。
- 工具 schema、工具调用和工具结果模型。
- 结构化输出格式模型。
- 流式事件字段和基本顺序。
- `LlmException` 与错误码。

`llm-openai` 重点测试：

- core request 到 OpenAI SDK 参数的映射。
- OpenAI response 到 core result 的映射。
- OpenAI 流式事件到 `LlmStreamEvent` 的映射。
- OpenAI SDK 异常到 `LlmException` 的翻译。
- 配置默认值和敏感字段不泄漏。

`agent-core` 重点测试：

- 使用 fake `LlmGateway` 验证普通文本生成。
- 验证工具调用、工具结果回传和结构化输出分支。

`mentor-api` 重点测试：

- `LlmStreamEvent` 到 SSE 事件的转换。
- content delta、usage、error、complete 等事件的稳定输出。

## 实施建议

第一阶段只在 `llm-core` 定义新契约和单元测试，不接真实 provider。

第二阶段在 `llm-openai` 增加映射层和配置装配，用 fake 或 mock SDK 响应验证映射逻辑。

第三阶段迁移 `agent-core` 和 `mentor-api` 到 `LlmGateway`，保留旧接口兼容一段时间。

第四阶段根据算法学习业务增加工具注册、工具执行和结构化 DTO 转换。
