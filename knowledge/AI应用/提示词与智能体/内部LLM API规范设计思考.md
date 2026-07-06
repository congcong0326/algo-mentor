# 内部 LLM API 规范设计思考

更新时间：2026-07-06

## 核心结论

设计 LLM 抽象层，本质上是在给自己的系统定义一套内部 API 规范。它不是简单包一层 SDK，也不是把 OpenAI、Claude、Qwen、DeepSeek、Ollama 等接口参数全部求并集，而是要先理解当前大模型的能力谱系：

- 哪些能力是几乎所有聊天模型都有的基础能力。
- 哪些能力是主流模型常见但协议细节不一致的能力。
- 哪些能力是部分 provider 或部分模型才支持的高级能力。
- 哪些能力虽然某个 provider 提供了，但不应该污染项目内部的稳定协议。

`algo-mentor` 的设计思路是：**用统一请求/响应模型承载稳定语义，用能力声明表达差异，用 provider adapter 处理厂商协议，用 gateway 做路由、默认值和能力校验。**

这比“只靠 `base_url + api_key` 兼容 OpenAI-like API”更工程化。OpenAI-compatible 适合快速接入一批兼容厂商，但长期来看，真正稳定的是项目自己的语义协议，而不是某一家厂商当前的 wire format。

## 为什么要先理解模型能力谱系

LLM provider 抽象最容易犯两个错误。

第一种错误是抽象过薄，只定义：

```text
String chat(String prompt)
```

这样早期 demo 很快，但一旦需要流式输出、工具调用、结构化输出、token usage、模型选择、错误治理，就会开始在业务代码里到处透传厂商参数。最后表面上有一层 `LlmClient`，实际上上层仍然被 OpenAI 或某个 provider 绑定。

第二种错误是抽象过厚，把所有 provider 的特有能力都塞进一个请求对象：

```text
openaiReasoningEffort
anthropicThinkingBudget
qwenEnableSearch
geminiSafetySettings
ollamaKeepAlive
...
```

这样短期看灵活，长期会让内部规范变成厂商参数垃圾桶。上层代码不得不理解每个 provider 的细节，抽象层就失去了意义。

所以设计前必须先区分能力层次。只有知道“大家都有的是什么”“多数有但细节不同的是什么”“少数模型才有的是什么”，才能决定哪些进入核心协议，哪些进入能力枚举，哪些留在 provider adapter。

## 能力分层

### 第一层：基础能力

基础能力是内部规范的稳定核心。它们应该直接进入 `LlmCompletionRequest` 和 `LlmCompletionResult`。

典型包括：

- 文本聊天补全：输入多轮消息，输出 assistant 文本。
- 基础消息角色：system、user、assistant。
- 基础生成参数：temperature、topP、maxOutputTokens、stop、timeout。
- 模型选择：provider、model、用途或能力约束。
- 普通完成结果：文本、finish reason、实际 provider/model。
- 基础错误：认证失败、限流、超时、provider 不可用、非法请求。

原因是这些概念已经不是某一家厂商的私有功能，而是现代聊天模型服务的共同语义。即使不同 API 的字段名不同，provider adapter 也能稳定映射。

### 第二层：主流但协议不一致的能力

这些能力应该进入内部规范，但不能假设所有模型都支持。它们适合用统一模型表达，并通过 capability 做调用前校验。

典型包括：

- 流式输出。
- 工具调用。
- JSON object 输出。
- JSON Schema 或 strict structured output。
- token usage。
- 图片输入。
- 文件输入。
- prompt cache 或 cached token usage。

原因是这些能力对 Agent 系统非常关键。比如工具调用和结构化输出不是“锦上添花”，而是学习计划生成、代码评审、题库检索、错题复盘这类功能的基础设施。

但它们的协议差异很大。例如工具调用：

- OpenAI Chat Completions 使用 `assistant.tool_calls` 和 `role=tool` 消息。
- OpenAI Responses 使用 `function_call` 和 `function_call_output` item。
- Claude 使用 assistant 的 `tool_use` block 和 user 消息里的 `tool_result` block。

语义上它们都是“模型发起工具调用、应用执行工具、应用回传工具结果”，但 wire format 完全不同。所以内部规范应该抽象 `LlmToolSpec`、`LlmToolCall`、`tool result message` 这类语义对象，而不是把某个 provider 的消息格式直接泄漏给 `agent-core`。

### 第三层：高级或小众能力

这些能力不应一开始强行变成核心字段，除非项目已经有稳定业务需求。

典型包括：

- reasoning effort、thinking budget、隐藏推理预算。
- 内置 web search。
- provider 托管工具。
- 并行工具调用细节。
- 多模态音频、视频输入输出。
- 图片生成、语音合成。
- embeddings、rerank。
- 批处理、异步任务、长上下文缓存句柄。
- provider 特定安全策略和区域合规参数。

这些能力可以先通过 capability 预留，或在独立子接口里表达。原因是它们差异大、变化快，如果过早进入核心 `complete()` 请求模型，会让所有 provider 都被迫面对自己不支持的字段，也会让上层误以为这些能力是通用能力。

### 落到代码里的能力枚举

上面三层不是纯概念，`llm-core` 用一个 `LlmCapability` 枚举把它们统一表达，模型描述符声明自己支持哪些，gateway 在调用前据此校验：

```java
public enum LlmCapability {
  CHAT_COMPLETION,      // 第一层：基础聊天补全
  STREAMING,            // 第二层：流式输出
  TOOL_CALLING,         // 第二层：工具调用
  STRUCTURED_OUTPUT,    // 第二层：JSON object 输出
  JSON_SCHEMA_OUTPUT,   // 第二层：JSON Schema / strict 结构化输出
  VISION_INPUT,         // 第二层：图片输入
  FILE_INPUT,           // 第二层：文件输入
  TOKEN_USAGE,          // 第二层：token 用量
  CACHED_TOKEN_USAGE,   // 第二层：缓存 token 用量
  REASONING_EFFORT,     // 第三层：推理预算
  EMBEDDING             // 第三层：向量化
}
```

关键点是：能力分层最终收敛成一个**有限、可枚举、跨 provider 复用的集合**，而不是每接一家厂商就新增一批私有开关。第三层能力即使还没有稳定业务，也可以先在枚举里占位，让模型声明和 gateway 校验有统一的表达方式。

## `algo-mentor` 的内部规范边界

当前项目把边界分成三层：

```text
agent-core
  只负责 Agent 编排、工具执行、多轮循环、权限和上下文

llm-core
  定义内部 LLM API 规范：请求、响应、事件、provider 契约、能力、错误

llm-openai / 未来 llm-qwen / llm-deepseek / llm-anthropic
  把内部规范映射到具体 provider API
```

这个边界的关键点是：`agent-core` 不直接依赖 OpenAI SDK，也不关心 DeepSeek、Qwen、Claude 的请求格式。它只构造内部的 `LlmCompletionRequest`，交给 `LlmGateway`。

`LlmProvider` 是 provider 适配器契约，表达一个 provider 至少要告诉系统：

- 我是谁：`id()`。
- 我有哪些模型：`models()`。
- 每个模型支持什么能力：`capabilities()`。
- 如何完成一次非流式生成：`complete()`。
- 如何完成一次流式生成：`stream()`。

`LlmGateway` 是上层真正使用的入口，负责：

- 根据默认配置、请求级 selector、用途和能力选择 provider/model。
- 在调用前检查模型是否支持请求所需能力。
- 把未知 provider、未知模型、不支持能力变成统一异常。
- 避免上层到处写 provider 判断。

## 为什么要有能力声明，而不是只靠运行时失败

能力声明是这个设计里很重要的一环。

如果没有 `LlmCapability` 和 `LlmModelDescriptor`，系统只能把请求发出去，然后等 provider 报错。这样会带来几个问题：

- 错误发生太晚，用户已经进入一次失败的 Agent 运行。
- provider 原始错误格式不同，业务层很难统一处理。
- 有些 provider 会静默降级，例如忽略 JSON Schema 或工具约束，这比显式失败更危险。
- 上层无法根据能力选择模型，只能硬编码模型名。

所以 `DefaultLlmGateway` 在调用前根据请求推导需要的能力：

- 任何调用都以 `CHAT_COMPLETION` 为基线，再并入 selector 里显式声明的 `requiredCapabilities`。
- 有 tools，或 tool choice 为 required/specific，就需要 `TOOL_CALLING`。
- 请求 `JsonObject`，就需要 `STRUCTURED_OUTPUT`。
- 请求 `JsonSchema`，就需要 `JSON_SCHEMA_OUTPUT`。
- 消息里包含 image part，就需要 `VISION_INPUT`。
- 消息里包含 file part，就需要 `FILE_INPUT`。
- 流式调用（`stream()`）额外需要 `STREAMING`。

然后再和模型声明的能力做校验，只要有一项不被支持，就直接抛出 `unsupportedCapability` 异常，而不是把请求发出去等 provider 报错。这样做的原因是：**内部 API 规范不能只描述“怎么调用”，还要描述“什么时候不应该调用”。**

## 为什么不能把 metadata 当 provider 参数逃生口

很多抽象层最后会失控，是因为设计了一个万能字段：

```text
Map<String, Object> metadata
```

然后所有 provider 专有参数都从这里塞进去。这样看起来扩展性很好，实际上会破坏规范边界。

`metadata` 应该主要承载非协议语义的信息，例如：

- traceId。
- 业务场景。
- 幂等键。
- schema version。
- 日志和观测需要的非敏感信息。

它不应该成为 provider 私有参数逃生口。原因是：如果上层通过 metadata 传 `openai_reasoning_effort`、`anthropic_thinking_budget` 之类字段，那么上层仍然在依赖具体 provider。后续换模型时，失败点会隐藏在运行时，代码评审也很难发现。

如果某个 provider 特性确实成为项目稳定需求，应该把它提升为内部规范中的明确概念，或者设计为独立 capability 和 options；如果只是临时实验，就应该限制在对应 provider adapter 或应用配置中。

在 `algo-mentor` 里，这些字段名统一收敛在 `LlmMetadataKeys` 常量类，承载的都是 provider/model、responseId、status、错误码和 token 用量这类可观测信息，而不是某家厂商的调参开关。这样也保证了 metadata 的键是受控集合，不会退化成任意字符串垃圾场。

## 如何判断一个字段是否进入内部规范

可以用几条规则判断。

第一，是否具有跨 provider 的稳定语义。

例如 `temperature`、`maxOutputTokens`、`toolChoice`、`responseFormat` 有稳定语义，适合进入 core。即使不同 provider 支持程度不同，也可以通过能力和 adapter 处理。

第二，上层业务是否需要基于它做决策。

例如 `finishReason`、`usage`、`provider/model`、`toolCalls` 对日志、计费、重试、Agent 下一步动作都重要，应该进入统一响应模型。

第三，缺失时是否需要显式失败。

例如代码评审要求严格 JSON Schema，如果模型不支持，不应该悄悄让模型“尽量输出 JSON”。这种能力必须被规范化，并在 gateway 层校验。

第四，是否只是某个 provider 的调参细节。

如果只是某家厂商的实验参数，且上层不应该依赖它，就不要放进核心请求。它可以留在 provider 配置、adapter 内部策略或后续专门扩展里。

第五，是否会污染其他 provider 的实现。

如果一个字段进入 core 后，绝大多数 provider 都只能忽略它或抛错，说明它可能还不够通用。此时更适合先用 capability 或独立模块表达。

## 为什么请求、响应、流式事件和错误都要统一

只统一请求是不够的。真正让上层脱离 provider 的，是完整生命周期都统一。

请求统一解决“怎么调用”。

响应统一解决“怎么消费结果”。例如文本、结构化输出、工具调用、finish reason、usage 都应该有统一位置。

流式事件统一解决“怎么做 SSE 和 Agent 过程展示”。Agent 不应该只拿到字符串 chunk，因为工具调用、usage、错误、message start/end 都可能是流式过程的一部分。

错误统一解决“怎么治理”。认证失败、限流、超时、内容过滤、provider 不可用、能力不支持，都应该映射成内部 `LlmException` 和 `LlmErrorCode`，这样上层才能统一重试、降级、记录和展示。

这也是为什么内部规范看起来像在定义一套 API 标准：它要覆盖一次模型调用的完整生命周期，而不只是封装 HTTP 请求。

## 和 hello-agents 思路的差异

hello-agents 第 7.2.1 的思路是通过 `provider` 处理不同服务商的配置差异，例如不同环境变量、默认 base URL、默认模型。它的基础假设是：很多模型服务都兼容 OpenAI API，所以用统一的 `api_key + base_url + model + provider` 可以快速切换。

这个设计适合教学和轻量项目，优点是简单、直观、接入快。

`algo-mentor` 的设计更偏长期工程：

- 不把 OpenAI-compatible API 当成唯一底层协议。
- 不让 `agent-core` 直接接触 provider 配置。
- 不只处理 base URL 和 API key，还处理能力、工具调用、结构化输出、流式事件、错误和 usage。
- provider 差异集中在 `llm-*` adapter 中，上层只依赖 `llm-core`。

所以我们的目标不是“让所有 provider 看起来都像 OpenAI”，而是“让所有 provider 映射到我们自己的内部 LLM 语义规范”。

## 面试表达

如果面试中被问到“你们为什么要自己设计 LLM provider 抽象”，可以这样回答：

> 我们没有简单封装某一家 SDK，而是先分析了当前大模型服务的能力谱系。文本聊天、消息、基础生成参数是通用能力，直接进入核心请求模型；工具调用、流式输出、结构化输出、多模态输入是主流但支持不均的能力，所以进入统一协议但通过 capability 校验；reasoning effort、内置搜索、音视频、多 provider 特殊参数这类能力差异更大，先不污染核心协议。  
>
> 在实现上，`agent-core` 只依赖 `LlmGateway`，不感知 OpenAI 或其他 provider。`llm-core` 定义内部请求、响应、流式事件、错误和 provider 契约；具体 provider adapter 负责把内部协议映射到 OpenAI、Qwen、DeepSeek、Claude 等 API。Gateway 在调用前根据请求推导所需能力，并和模型声明能力做校验，避免静默降级。  
>
> 这样做的核心原因是：我们定义的是项目内部长期稳定的 LLM API 规范，而不是把某个厂商当前 API 直接扩散到业务层。

## 继续深入方向

- OpenAI、Claude、Gemini、Qwen、DeepSeek 的工具调用协议差异。
- JSON mode、JSON Schema、strict structured output 的可靠性差异。
- 多 provider gateway 的模型选择策略：按用途、成本、能力、延迟、可用性路由。
- LLM 抽象层中的降级策略：显式失败、能力降级、provider fallback 的边界。
- Agent 系统中 provider 抽象与工具权限、审计、SSE 事件的关系。
