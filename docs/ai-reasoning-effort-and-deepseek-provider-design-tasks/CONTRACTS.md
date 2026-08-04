# AI Reasoning Effort 与 DeepSeek Provider 固定契约

本文件是 `RDP-00` 至 `RDP-13` 的实现期固定契约摘要。任务实施时优先遵守这里的约束，不在单个任务中重新发散产品方向。设计原文第 0 节和第 14 节列出的七项核心建议在本任务包中视为已确认。

## 1. 阶段与发布边界

- 第一阶段只打通统一 Reasoning Effort，可在 `RDP-06` 后独立发布。
- 第二阶段依次完成共享 Responses 传输层、opaque continuation 和 DeepSeek provider；顺序不可颠倒。
- 不增加 OpenAI 与 DeepSeek 自动 fallback、自动重试切换或负载均衡。
- provider、模型、价格和路由均由管理员显式维护；不调用远端模型目录自动同步。
- DeepSeek 工具场景只有在真实 continuation E2E 为 `PASS` 后才能灰度。
- 研发任务完成与外部发布资格分开：缺少真实 API Key 不阻断仓库代码完成，但发布门禁保持 `NOT_RUN`。

## 2. Reasoning Effort 协议

统一 Java 枚举固定为：

| Java | wire / JSON / DB |
| --- | --- |
| `NONE` | `none` |
| `MINIMAL` | `minimal` |
| `LOW` | `low` |
| `MEDIUM` | `medium` |
| `HIGH` | `high` |
| `XHIGH` | `xhigh` |
| `MAX` | `max` |

固定语义：

- `null` 表示不发送 `reasoning.effort`，继续使用上游默认值。
- `none` 表示显式关闭 reasoning；不得与 `null` 合并。
- 未知值在 JSON 反序列化、策略写入或请求构造边界拒绝，不提供自由文本逃生口。
- effort 是通用生成参数，不进入 OpenAI/DeepSeek provider config，也不放入 request metadata。
- 管理员配置位置固定在模型路由规则；同一模型可以在不同场景或用户范围使用不同 effort。

## 3. 生效值与核心模型

唯一优先级：

```text
request.options.reasoningEffort
  > invocationTarget.routeReasoningEffort
  > null
```

- `llm-core` 必须提供唯一、无副作用的生效值解析入口；gateway、台账和指标复用同一实现。
- `LlmGenerationOptions.defaults().reasoningEffort()` 固定为 `null`。
- `LlmGenerationOptions` 保留现有六参数构造器并委托到新构造器；新增 `withReasoningEffort(...)` 或等价不可变复制方法。
- `LlmCompletionRequest` 增加 `withOptions(...)`，不得通过修改 metadata 传递生效 effort。
- `LlmInvocationTarget` 携带可空 `routeReasoningEffort`，与 provider client、模型和 provider 版本一起按 run 固定。
- 合并发生在 required capability 计算之前；生效 effort 非空时必须要求 `LlmCapability.REASONING_EFFORT`。
- provider 缺少 capability 时，在远程调用前返回 `UNSUPPORTED_CAPABILITY`。
- `LlmProviderAdapter.acceptedReasoningEfforts()` 默认返回空集合；它表示协议层可映射子集，不承诺该 provider 下每个模型都支持。

## 4. 路由、快照与管理 API

路由内容固定为：

```json
{
  "modelId": 101,
  "reasoningEffort": "high"
}
```

- 旧 JSON 缺少 `reasoningEffort` 时读取为 `null`，不需要迁移 `generic_policy.content`。
- 保存路由时校验模型、provider 实例、provider type、capability 和 adapter 接受值；模型/实例可以停用，但引用和协议必须合法。
- `ResolvedAiModelSnapshot` 和其生成的 `LlmInvocationTarget` 固定携带路由 effort；run 中途策略变更不影响当前 run。
- trusted metadata 可记录路由配置值，但最终生效值以调用级解析为准；metadata 只使用七个固定值或 `provider_default`。
- provider type 目录响应固定增量字段：`reasoningEfforts` 和 `defaultConfig`。
- `reasoningEfforts` 与 `defaultConfig` 来源于代码注册的 adapter 描述，不在 controller 或前端按 provider code 硬编码。
- `defaultConfig` 只能包含安全模板；API Key 固定为空字符串，不返回任何已保存实例的 secret。
- 有效路由模拟响应增加可空 `reasoningEffort`，表示命中规则配置值，不冒充请求覆盖后的最终值。

OpenAI 默认模板：

```json
{
  "apiKey": "",
  "baseUrl": "https://api.openai.com/v1",
  "timeoutSeconds": 300,
  "maxRetries": 2
}
```

DeepSeek 默认模板：

```json
{
  "apiKey": "",
  "baseUrl": "https://api.deepseek.com",
  "timeoutSeconds": 300,
  "maxRetries": 2
}
```

## 5. OpenAI 映射

- OpenAI adapter 和仍保留的 legacy OpenAI facade 都声明 `REASONING_EFFORT`，避免两条入口能力漂移。
- OpenAI 接受七个统一协议值。
- effort 为空时不创建 `reasoning` 对象，请求 JSON 不出现 `reasoning` 字段。
- effort 非空时映射到 Responses API `reasoning.effort`。
- 使用 SDK `ReasoningEffort.of(wireValue)`，不得依赖 SDK 静态常量是否包含 `max`。
- 同步和流式调用复用同一个 `toParams(...)` 或等价唯一请求映射入口。
- 文本、函数工具、JSON Object、JSON Schema、usage、取消和异常语义在第一阶段不得改变。

## 6. 调用台账与指标

- `ai_llm_call_usage` 增加可空 `reasoning_effort VARCHAR(16)`。
- 数据库约束只允许七个 wire value 或 `NULL`；历史数据不回填，保持 `NULL`。
- 调用开始时使用共享生效值解析器生成快照；同步成功、流式成功、失败和取消都保留该开始值。
- 终态更新不得重新读取路由或根据上游响应猜测 effort。
- reasoning token 继续写既有字段，不重复计费。
- provider 调用指标增加固定 tag：`reasoning_effort=provider_default|none|minimal|low|medium|high|xhigh|max`。
- 指标不得增加 user ID、provider instance ID、model ID、route ID 或其他高基数 label。

## 7. OpenAI-compatible 共享模块

固定模块依赖：

```text
llm-core
   ^
   |
llm-openai-compatible
   ^                 ^
   |                 |
llm-openai       llm-deepseek
```

共享模块固定职责：

- `openai-java` SDK client 和 connection config。
- Responses 请求/响应映射。
- stream publisher、资源关闭和单 subscriber 语义。
- SDK/HTTP/SSE 异常归一。
- provider profile 驱动的差异校验和 provider 身份注入。

共享模块禁止：

- 注册 Spring bean 或自动配置。
- 读取数据库、环境变量或业务路由。
- 硬编码 `openai` provider id、OpenAI Base URL、OpenAI effort 集合或 DeepSeek thinking 规则。
- 依赖 `mentor-api`、`ai-governance`、`agent-core` 或任一业务模块。

稳定共享类型使用设计中的命名：`OpenAiCompatibleResponsesClient`、`SdkOpenAiCompatibleResponsesClient`、`OpenAiCompatibleResponsesMapper`、`OpenAiCompatibleStreamPublisher`、`OpenAiCompatibleExceptionMapper`、`OpenAiCompatibleProviderClient`、`OpenAiCompatibleConnectionConfig`、`OpenAiCompatibleProviderProfile`。

## 8. Provider Profile 与配置

`OpenAiCompatibleProviderProfile` 至少提供：

- provider type 和 display name。
- supported capabilities 和 accepted reasoning efforts。
- `validateRequest(modelId, request)`。
- `requiresReasoningContinuationForToolCalls()`。

OpenAI 和 DeepSeek adapter 都保留自己的严格 `fromJson(...)` 配置边界；共享 connection config 只承载已经验证的通用值。

共同配置字段固定为：`apiKey`、`baseUrl`、`timeoutSeconds`、`maxRetries`。未知字段拒绝；`toString()` 对 API Key 和 Base URL 脱敏。

provider 实例只从数据库显式配置创建 client。DeepSeek 不读取环境变量，不在数据库缺失时回退文件配置。真实 E2E 测试使用的环境变量只属于测试入口，不属于生产 provider 配置。

## 9. DeepSeek Provider

- 稳定 provider type 为 `deepseek`，不得伪装成 `openai` 或通用 compatible 类型。
- 当前官方 Responses 模型只作为运行手册和真实 E2E 默认值记录；代码、数据库和校验不建立永久模型白名单。
- 建议能力固定为：`CHAT_COMPLETION`、`STREAMING`、`TOOL_CALLING`、`STRUCTURED_OUTPUT`、`JSON_SCHEMA_OUTPUT`、`REASONING_EFFORT`、`TOKEN_USAGE`、`CACHED_TOKEN_USAGE`。
- 首期不声明 `VISION_INPUT`、`FILE_INPUT`、`EMBEDDING`。
- DeepSeek 接受 effort：`none`、`low`、`high`、`max`；`minimal`、`medium`、`xhigh` 在路由保存或请求校验阶段拒绝。
- effort 为 `low/high/max` 时，`temperature` 和 `topP` 必须为空。
- effort 为 `null` 时按 DeepSeek 默认 thinking 开启处理，`temperature` 和 `topP` 也必须为空。
- effort 为 `none` 时可以使用项目既有范围内的 `temperature` 或 `topP`。
- 只发送函数工具；不发送 hosted tool、`store`、`previous_response_id`、`conversation`、`metadata`、`include`、`service_tier` 或项目未抽象的参数。
- image/file 输入由 gateway capability 校验前置拒绝，不允许被 DeepSeek 静默替换或忽略。
- provider id、结果、异常、指标、台账和管理端显示必须始终为 `deepseek`。

## 10. Opaque Reasoning Continuation

核心模型固定为等价于：

```java
public record LlmProviderContinuation(
    LlmProviderType providerType,
    JsonNode payload
) {
}
```

固定约束：

- payload 是 provider 私有敏感状态，不是 metadata、消息正文、reasoning summary 或用户可见内容。
- `toString()` 只返回固定 REDACTED 摘要；异常和日志不得输出 payload、hash 前缀或可关联正文。
- 普通 Jackson 消息序列化必须忽略 continuation。
- 只允许匹配相同 provider type 的共享 mapper 消费；不匹配时在远程调用前拒绝。
- 只在当前业务 run 的工具调用 step 之间传递，不跨 run、replay、conversation 或进程持久化。
- `LlmCompletionResult`、`LlmStreamEvent.MessageEnd`、`AgentStepResult` 和 assistant tool-call message 可以携带可空 continuation；现有构造器/工厂保持兼容并默认 `null`。
- 只有当前 step 确实产生工具调用时才保留 continuation；最终答案、无工具结果、失败和取消立即释放引用。
- run message compaction 必须完整保留尚未结束的最近 tool interaction group 及其 continuation；不得把 continuation 复制到 snip marker、tool result blob 或压缩 metadata。
- SSE DTO、Agent 外部事件 DTO、调用台账、Agent run trace、最终请求诊断快照和普通消息持久化都不得包含 continuation。

同步链路：

```text
Response.output.reasoning
  -> LlmProviderContinuation
  -> AgentStepResult
  -> assistantToolCalls(..., continuation)
  -> 下一轮 Responses input reasoning item
```

流式链路：

```text
response.output_item.done(reasoning)
  -> publisher 内部收集完整 reasoning item
  -> MessageEnd.continuation
  -> Agent StepCollector
  -> 下一轮 Responses input reasoning item
```

`response.reasoning_text.delta/done` 和 reasoning summary 事件都不得转换为 `ContentDelta`。

## 11. 管理前端

- effort 使用 `<select>`，首项为“使用 Provider 默认值”，保存为 `null` 或省略字段。
- 选项完全来自 provider type 目录，前端不硬编码 OpenAI/DeepSeek 子集。
- 选择模型后按其 provider type 计算选项；切换模型导致当前 effort 不合法时重置为 Provider 默认值并给出稳定提示。
- 编辑、启停、重排和优先级更新必须完整保留 policy content 中的 effort。
- 路由表和有效路由模拟显示 effort；`null` 显示 Provider default，不与 `none` 混淆。
- API 返回未知 effort 时进入明确错误态并阻止保存，不允许自由文本兜底。
- 新建 provider 时切换 provider type 会切换安全默认配置模板；编辑已有 provider 时 provider type 只读且不得重置 config。
- provider 列表、模型和路由必须明确显示 `deepseek`，不得显示为 OpenAI compatible。
- 不主动启动 Vite。

## 12. 安全与可观测性

- 不向用户展示原始 reasoning、reasoning summary、encrypted content 或 continuation。
- 不在 SSE、日志、异常、调用台账、Agent trace、普通消息、tool result、metadata 或管理 API 中返回这些内容。
- 日志只记录 provider type、model、固定错误码、HTTP status、计数、耗时和资源关闭结果；不记录 API Key、Authorization、Base URL、请求/响应 body 或 continuation。
- config 和 continuation 的 `toString()` 都必须脱敏。
- continuation 安全测试必须覆盖 ObjectMapper 序列化、SSE 映射、request snapshot、run trace、异常和日志捕获。
- provider 调用与 accounting 指标只使用固定低基数值；DeepSeek 不得被统计为 OpenAI。

## 13. 数据迁移、兼容与回滚

- 本需求唯一必需 Flyway 变更是调用台账可空 `reasoning_effort` 列；实施时重新扫描全仓版本后选择唯一版本。
- 不修改已应用 migration，不回填历史 effort，不回滚 Flyway。
- DeepSeek provider、模型、价格和路由复用现有字符串/JSONB 表结构，不新增迁移。
- 第一阶段前后端同批发布，避免旧前端编辑规则时丢失新 content 字段。
- effort 回滚通过把路由改为 Provider 默认值；代码回滚时新增 JSON 字段和可空列可以保留。
- DeepSeek 回滚通过停用/删除路由、停用 provider 实例并恢复显式 OpenAI 路由；provider/model 记录可以保留，不物理删除。

## 14. 测试与真实 E2E

默认 CI 和本地门禁必须使用 fake client、fixture 或本地 mock，不依赖外部 AI 服务。

真实 E2E 使用显式测试环境变量：

- `DEEPSEEK_API_KEY`：必需；缺失时 opt-in IT 跳过。
- `DEEPSEEK_BASE_URL`：可选，仅测试入口默认 `https://api.deepseek.com`。
- `DEEPSEEK_MODEL`：可选，仅测试入口使用设计时官方模型作为默认值；生产代码不读取。

真实门禁至少记录以下稳定场景结果：

- 同步短文本 `none`。
- 同步推理 `high`。
- 流式文本 `low`。
- JSON Schema `high`。
- 单工具调用 `high`。
- 连续两次工具调用 `high`。
- 工具调用 `none`。
- 取消。
- 无效 Key。
- 受控 429/限流场景；若受控环境无法稳定制造，门禁保持未完整通过，不能用 mock 结果替代真实发布证据。

每次真实运行只记录 PASS/FAIL、耗时、token 计数摘要、错误码和控制台对账结论，不保存请求、响应或 reasoning 内容。

## 15. 非目标

- 不接入 DeepSeek Chat Completions API。
- 不建设任意 Base URL 的通用 provider 类型。
- 不建设模型级 capability、上下文窗口或 effort 子集配置。
- 不建设 reasoning 展示、summary 展示或 chain-of-thought 持久化。
- 不接 web search、custom tool、Codex apply_patch、image、file、audio 或 hosted tool。
- 不增加模型自动发现、自动价格同步或跨 provider 自动 fallback。
