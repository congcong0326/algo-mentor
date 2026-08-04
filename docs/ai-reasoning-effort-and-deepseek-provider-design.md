# AI Reasoning Effort 与 DeepSeek Responses 适配研发设计

> 设计日期：2026-08-03
>
> 状态：待评审
>
> 前置设计：[AI 提供商配置与模型路由研发设计](ai-provider-and-model-routing-design.md)
>
> 适用范围：`backend/llm-core`、`backend/llm-openai-compatible`、`backend/llm-openai`、`backend/llm-deepseek`、`backend/ai-governance`、`backend/agent-core`、`backend/mentor-api`、`frontend`
>
> 技术基线：Java 17、Spring MVC、PostgreSQL、MyBatis、Flyway、`openai-java 4.39.1`、React + TypeScript

---

## 0. 建议确认的核心决策

1. 本需求分两个可独立发布的阶段实施：第一阶段打通统一 Reasoning Effort；第二阶段增加 DeepSeek Responses provider。
2. Reasoning Effort 是 LLM 通用生成参数，不放入 `OpenAiProviderConfig` 或 `DeepSeekProviderConfig`。
3. 管理员配置入口放在模型路由规则中，而不是 provider 实例或模型记录中。同一个模型在不同业务场景、用户或用户组下可以使用不同 effort。
4. `reasoningEffort = null` 表示不发送该参数，继续使用上游模型默认值；`none` 表示显式关闭推理。两者语义不可合并。
5. 当前生效优先级固定为“请求显式值 > 路由规则值 > 不发送参数”。未来若增加模型级默认值，应插入路由规则与 provider 默认值之间。
6. 项目统一 effort 枚举使用 `none`、`minimal`、`low`、`medium`、`high`、`xhigh`、`max` 七个协议值；具体 provider 和模型可以只支持其中子集。
7. `LlmCapability.REASONING_EFFORT` 已经存在，第一阶段只打通请求、路由、能力校验和 provider 映射，不重复增加 capability。
8. OpenAI 映射到 Responses API 的 `reasoning.effort`。使用 `ReasoningEffort.of(wireValue)`，不依赖 SDK 静态常量是否及时覆盖 `max` 等新值。
9. 旧路由规则缺少 `reasoningEffort` 时自动按 `null` 读取，因此路由配置本身不需要数据库迁移。
10. 建议在调用级台账增加可空的 `reasoning_effort` 字段，用于关联延迟、成本、输出质量和 reasoning token；该观测增强需要一条向前兼容的 Flyway 迁移。
11. DeepSeek 虽可使用 OpenAI SDK 发起 Responses 请求，但必须注册为独立 provider type `deepseek`，不能伪装为 `openai`。
12. 第二阶段不复制现有 OpenAI mapper。新增纯 Java 模块 `llm-openai-compatible`，承载两家共用的 Responses SDK 传输、映射、流式消费和异常归一。
13. `llm-openai` 和 `llm-deepseek` 都是薄 adapter 模块，各自保留 provider type、展示信息、严格配置解析、能力 profile 和 Spring 自动配置。
14. DeepSeek provider 实例的建议 Base URL 为 `https://api.deepseek.com`；Base URL 仍显式保存在实例配置中，不在后端隐藏回退。
15. 截至 2026-08-03，DeepSeek Responses API 官方声明仅支持 `deepseek-v4-flash`。系统仍保持管理员显式维护模型，不硬编码远端模型白名单。
16. DeepSeek Responses 的 effort 首期只接受 `none`、`low`、`high`、`max`；未配置时不发送参数，并接受 DeepSeek 当前默认开启 thinking、默认 `high` 的上游语义。
17. DeepSeek 对部分不支持参数会静默忽略。adapter 必须在本地只发送已确认支持的字段，并对会被静默忽略但影响业务语义的组合显式拒绝。
18. DeepSeek thinking 模式与工具调用组合时，需要把本 step 的 reasoning 上下文带入后续工具结果轮次。该状态必须作为 provider opaque continuation 在内存中传递。
19. 原始 reasoning/chain-of-thought 不向用户展示，不进入 SSE、日志、调用台账、Agent trace、普通消息持久化或可检索 metadata。
20. DeepSeek 的工具调用场景只有在 opaque continuation 的同步、流式和多 step E2E 均通过后才能灰度；在此之前只允许无工具场景验收。
21. 不增加 OpenAI 与 DeepSeek 之间的自动 fallback。切换和回滚继续通过现有显式模型路由完成。

## 1. 背景与现状

项目已完成动态 provider 实例、显式模型资源和按业务场景解析的模型路由：

```text
业务场景 + 用户范围
  -> Generic Policy
  -> AiModelRoutePolicyContent
  -> ResolvedAiModelSnapshot
  -> LlmInvocationTarget
  -> DynamicLlmGateway
  -> LlmProviderClient
```

当前实现已经具备扩展基础，但 Reasoning Effort 尚未真正接入：

- `LlmCapability` 已预留 `REASONING_EFFORT`；
- `LlmGenerationOptions` 只有 temperature、topP、maxOutputTokens、stop、seed 和 timeout；
- `AiModelRoutePolicyContent` 只保存 `modelId`；
- `ResolvedAiModelSnapshot` 和 `LlmInvocationTarget` 不携带路由级生成参数；
- `DynamicLlmGateway` 只负责能力校验和动态分发，不合并路由参数；
- `OpenAiResponsesMapper` 未构造 `reasoning.effort`；
- `OpenAiProviderAdapter` 未声明 Reasoning Effort 能力；
- 管理后台路由编辑器只能选择模型，不能配置或查看 effort。

当前 OpenAI Responses 实现同时包含两类职责：

- OpenAI provider 身份、配置和 Spring 注册；
- 可被 OpenAI-compatible provider 复用的 SDK Client、请求映射、响应映射、SSE publisher 和异常映射。

如果 DeepSeek adapter 直接依赖 `llm-openai`，会产生以下问题：

- DeepSeek 的响应 provider id、日志和错误信息容易被标记为 `openai`；
- DeepSeek 特有的 effort 集合、thinking 约束和兼容差异没有清晰归属；
- 后续修复共享 mapper 时容易在两个 provider 之间产生行为漂移；
- `llm-openai` 将同时承担 provider 实现和通用传输层职责，模块边界继续恶化。

因此本设计先完成 provider 无关的 effort 契约，再将 Responses 兼容传输抽成共享模块，最后注册 DeepSeek 的独立 profile 和 adapter。

## 2. 目标与非目标

### 2.1 第一阶段目标

- 管理员可以为每条模型路由规则选择 Reasoning Effort。
- 未配置 effort 的既有路由和请求保持当前行为，OpenAI 请求中不出现 `reasoning` 字段。
- 业务代码可以在单次请求中显式指定 effort，并覆盖路由规则值。
- 同一个 Agent run 继续固定使用一次解析得到的模型、provider 版本和路由 effort。
- OpenAI 同步与流式请求使用完全一致的 effort 映射。
- provider 不支持 Reasoning Effort 时，在远程调用前得到稳定的 `UNSUPPORTED_CAPABILITY` 错误。
- 管理后台只展示目标 provider 接受的 effort 协议值，不使用自由文本输入。
- 调用观测可以区分最终使用的 effort，并继续记录 reasoning token。

### 2.2 第二阶段目标

- 管理员可以创建 `deepseek` provider 实例并显式维护 DeepSeek 模型。
- DeepSeek 复用 `openai-java` 的 Responses API SDK 传输能力，但保留独立 provider 身份。
- 同步文本、流式文本、函数工具调用、JSON Object、JSON Schema、usage、错误和取消语义映射到现有 `llm-core` 契约。
- DeepSeek 的 effort 范围和 thinking 参数组合由 DeepSeek profile 校验。
- thinking 模式下的工具调用可以正确保留 provider continuation，并在下一轮请求中回传。
- OpenAI 现有行为在抽取共享模块后保持不变。
- AI 调用台账、模型价格、指标和管理查询能够按 `deepseek` 正确归类。

### 2.3 非目标

- 不接入 DeepSeek Chat Completions API。
- 不建设任意“填写 Base URL 即成为新 provider”的通用代理类型。
- 不自动调用 DeepSeek 模型列表 API，不自动创建或同步模型。
- 不自动在 OpenAI 和 DeepSeek 之间降级、重试或负载均衡。
- 不在本期建设模型级 capability、上下文窗口和 effort 子集配置。
- 不向用户展示 reasoning text、reasoning summary 或完整思维链。
- 不持久化原始 provider continuation。
- 不接入 DeepSeek 的 web search、custom tool、Codex apply_patch 或其他 hosted tool。
- 不增加 image、file、audio 等多模态输入。
- 不因 DeepSeek 当前只支持 `deepseek-v4-flash` 而在数据库或代码中建立永久模型白名单。

## 3. 上游协议基线

### 3.1 OpenAI Responses Reasoning Effort

OpenAI Responses API 使用以下结构：

```json
{
  "reasoning": {
    "effort": "high"
  }
}
```

官方协议当前可能使用以下值：

| 值 | 项目统一枚举 | 说明 |
| --- | --- | --- |
| `none` | `NONE` | 显式不进行 reasoning |
| `minimal` | `MINIMAL` | 最小推理 |
| `low` | `LOW` | 低推理 |
| `medium` | `MEDIUM` | 中等推理 |
| `high` | `HIGH` | 高推理 |
| `xhigh` | `XHIGH` | 更高推理 |
| `max` | `MAX` | 最大推理 |

具体模型只支持其中子集，默认值也由模型决定。项目首期不维护模型级 effort 矩阵，因此 provider profile 只代表“协议层可以映射这些值”，不承诺目标模型一定接受。

### 3.2 DeepSeek Responses API

截至设计日期，DeepSeek 官方协议有以下约束：

| 能力或参数 | 当前协议表现 | 本项目处理 |
| --- | --- | --- |
| Base URL | `https://api.deepseek.com` | provider 实例显式配置 |
| Responses 模型 | 当前仅 `deepseek-v4-flash` | 管理员显式维护，真实 E2E 使用该模型 |
| stream | 支持语义化 SSE | 复用共享 stream publisher |
| function tool | 支持 | 映射到现有函数工具契约 |
| JSON format | 支持 | 复用现有结构化输出映射 |
| reasoning effort | `none/low/high/max` | DeepSeek profile 只接受这四个值 |
| 默认 thinking | 开启，当前默认 effort 为 `high` | effort 为空时不发送，接受上游默认 |
| temperature/top_p | thinking 模式下无效且不报错 | adapter 本地显式校验，避免静默失效 |
| previous_response_id | 不支持 | 项目继续使用 stateless input items |
| store/conversation | 不支持 | 项目不发送 |
| metadata | 不支持 | 项目 metadata 只在本地使用，不发送 |
| image/file input | 不支持且可能被替换为占位文本 | capability 校验前置拒绝 |
| cached tokens | usage 返回 | 映射到 `cachedTokens` |
| reasoning tokens | usage 返回 | 映射到 `reasoningTokens` |

DeepSeek 对部分不支持参数采用“静默忽略”而不是返回错误。本项目不能把“请求成功”误认为“参数生效”，因此共享 mapper 必须采用明确 allowlist，DeepSeek profile 还要校验有业务含义的参数组合。

### 3.3 Thinking 与工具调用

DeepSeek 官方说明：在两条用户消息之间，如果模型执行过工具调用，则中间 assistant 的 reasoning 上下文必须随工具调用一起进入后续请求。

当前 Agent loop 只保留：

- assistant 可见文本；
- tool call id、名称和参数；
- tool result。

当前 mapper 会丢弃 Responses output 中的 reasoning item，因此不能直接宣称 DeepSeek thinking + tool calling 已兼容。该缺口必须作为第二阶段的发布门禁处理。

## 4. 总体架构

### 4.1 运行时调用链

```text
AiModelRoutePolicyContent
  modelId
  reasoningEffort?
       |
       v
DefaultAiModelRouteResolver
       |
       v
ResolvedAiModelSnapshot
       |
       v
LlmInvocationTarget
  providerType
  upstreamModelId
  routeReasoningEffort?
  providerClient
       |
       v
DynamicLlmGateway
  合并请求值与路由值
  校验 REASONING_EFFORT capability
       |
       v
OpenAiCompatibleProviderClient
       |
       +--> OpenAI profile --> OpenAI Responses API
       |
       +--> DeepSeek profile --> DeepSeek Responses API
```

### 4.2 模块依赖

```text
llm-core
   ^
   |
llm-openai-compatible
   ^                 ^
   |                 |
llm-openai       llm-deepseek
   ^                 ^
   +--------+--------+
            |
        mentor-api
```

模块职责如下：

| 模块 | 职责 |
| --- | --- |
| `llm-core` | 统一请求、effort 枚举、能力、调用目标、opaque continuation 契约 |
| `llm-openai-compatible` | `openai-java` Client、Responses 请求/响应映射、SSE、异常归一 |
| `llm-openai` | `openai` adapter、OpenAI 配置、OpenAI profile、自动配置 |
| `llm-deepseek` | `deepseek` adapter、DeepSeek 配置、DeepSeek profile、自动配置 |
| `ai-governance` | 路由内容、解析快照、规则校验、调用台账和管理查询 |
| `agent-core` | 在工具调用 step 之间传递 opaque continuation |
| `mentor-api` | 组合根、管理员 API；不感知 provider SDK |
| `frontend` | provider 配置模板、effort 下拉框、路由展示与模拟 |

## 5. 第一阶段：打通 Reasoning Effort

### 5.1 统一枚举

在 `llm-core` 增加：

```java
public enum LlmReasoningEffort {
  NONE("none"),
  MINIMAL("minimal"),
  LOW("low"),
  MEDIUM("medium"),
  HIGH("high"),
  XHIGH("xhigh"),
  MAX("max");

  private final String wireValue;
}
```

要求：

- Java 内部使用大写枚举；
- JSON、数据库和上游协议统一使用小写 `wireValue`；
- 通过 Jackson `@JsonValue` 和 `@JsonCreator` 固定 JSON 契约；
- 未知值在策略写入或 API 反序列化阶段拒绝；
- 不使用自由文本逃生口。

### 5.2 扩展生成参数

`LlmGenerationOptions` 增加可空字段：

```java
public record LlmGenerationOptions(
    Double temperature,
    Double topP,
    Integer maxOutputTokens,
    List<String> stop,
    Long seed,
    Duration timeout,
    LlmReasoningEffort reasoningEffort
) {
}
```

兼容要求：

- `defaults()` 的 `reasoningEffort` 为 `null`；
- 暂时保留现有六参数构造器，并委托到新构造器，减少全仓一次性调用点改动；
- `LlmCompletionRequest` 增加 `withOptions(...)`；
- 不把 effort 放入 request metadata。

### 5.3 扩展模型路由内容

路由规则调整为：

```java
public record AiModelRoutePolicyContent(
    long modelId,
    LlmReasoningEffort reasoningEffort
) {
}
```

未配置时的 JSON：

```json
{
  "modelId": 101
}
```

显式高推理：

```json
{
  "modelId": 101,
  "reasoningEffort": "high"
}
```

显式关闭推理：

```json
{
  "modelId": 101,
  "reasoningEffort": "none"
}
```

旧 JSON 缺少新字段时，Jackson 将其解析为 `null`。`generic_policy.content` 已经是 JSONB，不需要为路由内容新增列。

### 5.4 快照与调用目标

以下对象增加可空的路由 effort：

- `ResolvedAiModelSnapshot.routeReasoningEffort`；
- `LlmInvocationTarget.routeReasoningEffort`。

一次路由解析后，该值和模型、provider Client 一起固定在 run 快照中。管理员在 Agent run 中途修改路由，不影响当前 run 的后续 step。

`ResolvedAiModelSnapshot.trustedMetadata()` 可以记录路由规则配置的 effort，但必须使用低敏、低基数值。最终生效 effort 仍以调用级解析结果为准。

### 5.5 生效值合并

在 `llm-core` 提供唯一的合并函数，`DynamicLlmGateway`、调用台账和指标都复用它：

```text
request.options.reasoningEffort != null
  -> 使用请求显式值

否则 target.routeReasoningEffort != null
  -> 使用路由规则值

否则
  -> null，不发送 reasoning.effort
```

伪代码：

```java
LlmReasoningEffort effectiveEffort =
    request.options().reasoningEffort() != null
        ? request.options().reasoningEffort()
        : target.routeReasoningEffort();

LlmCompletionRequest effectiveRequest =
    request.withOptions(request.options().withReasoningEffort(effectiveEffort));
```

合并必须发生在 capability 计算之前。如果 `effectiveEffort != null`，`DynamicLlmGateway` 将 `REASONING_EFFORT` 加入必需能力。

### 5.6 Provider 接受值与路由校验

`LlmProviderAdapter` 增加默认方法：

```java
default Set<LlmReasoningEffort> acceptedReasoningEfforts() {
  return Set.of();
}
```

语义是“adapter 能够按协议映射的值”，不是“该 provider 下所有模型都支持”。

第一阶段 OpenAI adapter：

- `supportedCapabilities()` 增加 `REASONING_EFFORT`；
- `acceptedReasoningEfforts()` 返回七个统一协议值。

`AiProviderManagementService.validateModelReference(...)` 调整为同时校验模型引用和路由 effort：

```java
validateModelRoute(long modelId, LlmReasoningEffort reasoningEffort)
```

校验规则：

1. 模型存在；
2. provider 实例存在；
3. provider type 已注册；
4. effort 为空时通过；
5. effort 非空但 adapter 未声明 `REASONING_EFFORT` 时拒绝；
6. effort 不在 adapter 接受集合中时拒绝。

模型级差异不在首期硬编码。若上游模型拒绝某个 provider 协议值，统一映射为 `INVALID_REQUEST`，并由管理员调整路由。

### 5.7 OpenAI Responses 映射

`OpenAiResponsesMapper.toParams(...)` 增加：

```java
if (request.options().reasoningEffort() != null) {
  builder.reasoning(Reasoning.builder()
      .effort(ReasoningEffort.of(
          request.options().reasoningEffort().wireValue()))
      .build());
}
```

必须满足：

- effort 为空时不创建 `reasoning` 对象；
- 同步与流式都复用 `toParams(...)`；
- 不用 `ReasoningEffort.MAX` 等静态常量做完整性判断；
- mapper 单元测试覆盖七个值和空值；
- OpenAI 报错仍由统一异常 mapper 转换。

采用 `ReasoningEffort.of(...)` 的原因是：当前 `openai-java 4.39.1` 已支持自定义字符串值，但其静态常量集合可能落后于最新 API 文档。例如当前 SDK 静态字段未包含 `MAX`，直接依赖静态常量会让项目无必要地等待 SDK 升级。

### 5.8 管理 API 与前端

Provider type 目录响应建议扩展为：

```json
{
  "code": "openai",
  "displayName": "OpenAI",
  "reasoningEfforts": [
    "none",
    "minimal",
    "low",
    "medium",
    "high",
    "xhigh",
    "max"
  ],
  "defaultConfig": {
    "apiKey": "",
    "baseUrl": "https://api.openai.com/v1",
    "timeoutSeconds": 300,
    "maxRetries": 2
  }
}
```

路由编辑器调整：

- 先选择目标模型；
- 根据模型所属 provider type 读取 effort 选项；
- effort 使用 `select`，首项为“使用 Provider 默认值”；
- 选项值为空时写入 `null` 或省略 JSON 字段；
- 路由表增加 Effort 列；
- 有效路由模拟结果增加 `reasoningEffort`；
- 切换启停、编辑优先级时必须完整保留原 content 中的 effort。

前端类型：

```ts
export type LlmReasoningEffort =
  | 'none'
  | 'minimal'
  | 'low'
  | 'medium'
  | 'high'
  | 'xhigh'
  | 'max';

export interface AiModelRoutePolicyContent {
  modelId: number;
  reasoningEffort?: LlmReasoningEffort | null;
}
```

不在前端硬编码 OpenAI 与 DeepSeek 的 effort 集合，选项由 provider type 目录返回。

### 5.9 调用台账与指标

建议为 `ai_llm_call_usage` 增加：

```sql
reasoning_effort VARCHAR(16) NULL
```

并增加约束，只允许七个协议值或 `NULL`。

写入规则：

- 调用开始时使用与 gateway 相同的合并函数计算最终 effort；
- effort 为空写 `NULL`；
- 同步成功、流式成功、失败和取消均保留开始时的 effort 快照；
- 历史数据不回填，保持 `NULL`；
- reasoning token 继续使用现有字段，不重复计费。

指标建议在现有 provider 调用计数上增加低基数 tag：

```text
reasoning_effort=provider_default|none|minimal|low|medium|high|xhigh|max
```

不增加用户、provider instance id、模型 id 或路由 id 等高基数 tag。

## 6. 第二阶段：DeepSeek Adapter

### 6.1 先抽取共享 Responses 传输层

新增 Maven 模块：

```text
backend/llm-openai-compatible
```

建议迁移和重命名如下：

| 当前 `llm-openai` 类 | 共享模块目标职责 |
| --- | --- |
| `OpenAiResponsesClient` | `OpenAiCompatibleResponsesClient` |
| `SdkOpenAiResponsesClient` | SDK 实现 |
| `OpenAiResponsesMapper` | `OpenAiCompatibleResponsesMapper` |
| `OpenAiStreamPublisher` | `OpenAiCompatibleStreamPublisher` |
| `OpenAiLlmExceptionMapper` | `OpenAiCompatibleExceptionMapper` |
| `OpenAiProviderClient` | `OpenAiCompatibleProviderClient` |

共享模块不注册 Spring Bean，不声明具体 provider type，也不读取数据库。它只依赖：

- `llm-core`；
- `openai-java`；
- Jackson；
- SLF4J。

抽取步骤必须先完成 OpenAI 行为等价验证，再新增 DeepSeek。不要在同一个提交中同时重写共享 mapper 和引入大量 DeepSeek 差异。

### 6.2 Provider Profile

共享模块通过 profile 接收 provider 差异：

```java
public interface OpenAiCompatibleProviderProfile {

  LlmProviderType providerType();

  String displayName();

  Set<LlmCapability> supportedCapabilities();

  Set<LlmReasoningEffort> acceptedReasoningEfforts();

  void validateRequest(
      LlmModelId modelId,
      LlmCompletionRequest request);

  boolean requiresReasoningContinuationForToolCalls();
}
```

共享代码不得出现以下硬编码：

- 固定 provider id `openai`；
- 固定日志文案“OpenAI provider”；
- OpenAI 专属 Base URL；
- OpenAI 专属 effort 集合；
- DeepSeek 专属 thinking 规则。

异常底层类名仍可能是 `OpenAIServiceException`，但对上层只输出当前 profile 的 provider id、统一错误码、HTTP 状态和安全文案。

### 6.3 连接配置

OpenAI 与 DeepSeek 首期使用相同的严格 JSON 形状：

```json
{
  "apiKey": "...",
  "baseUrl": "https://api.deepseek.com",
  "timeoutSeconds": 300,
  "maxRetries": 2
}
```

共享模块可以定义 `OpenAiCompatibleConnectionConfig` 完成通用校验，但每个 adapter 仍保留自己的 `fromJson(...)` 边界，以便：

- 错误消息明确属于 OpenAI 或 DeepSeek；
- 后续某一家增加配置字段时不改变另一家的契约；
- 配置 `toString()` 始终脱敏 API Key 和 Base URL。

DeepSeek 不从环境变量读取配置，不在数据库缺失时回退文件配置。

### 6.4 DeepSeek Adapter

新增模块：

```text
backend/llm-deepseek
```

主要类型：

```text
DeepSeekProviderAdapter
DeepSeekProviderConfig
DeepSeekProviderProfile
DeepSeekLlmAutoConfiguration
```

稳定 provider type：

```java
public static final LlmProviderType PROVIDER_TYPE =
    LlmProviderType.of("deepseek");
```

建议声明能力：

```text
CHAT_COMPLETION
STREAMING
TOOL_CALLING
STRUCTURED_OUTPUT
JSON_SCHEMA_OUTPUT
REASONING_EFFORT
TOKEN_USAGE
CACHED_TOKEN_USAGE
```

首期不声明：

```text
VISION_INPUT
FILE_INPUT
EMBEDDING
```

DeepSeek 接受的 effort：

```text
none
low
high
max
```

`minimal`、`medium` 和 `xhigh` 在路由保存阶段拒绝，不依赖 DeepSeek 当前可能进行的隐式映射。

### 6.5 DeepSeek 请求约束

DeepSeek profile 在远程调用前执行以下校验：

1. effort 必须为空或属于 `none/low/high/max`；
2. effort 为 `low/high/max` 时，temperature 和 topP 必须为空；
3. effort 为空时，按 DeepSeek 当前默认 thinking 开启处理，temperature 和 topP 也必须为空；
4. effort 为 `none` 时可以发送 temperature 或 topP，但沿用项目现有参数范围校验；
5. image、file 和自定义 content 在 gateway capability 校验阶段拒绝；
6. 只发送函数工具，不发送项目未抽象的 hosted tools；
7. 不发送 `store`、`previous_response_id`、`conversation`、`metadata`、`include`、`service_tier` 等 DeepSeek 不支持字段。

第 2、3 条选择“显式失败”而不是“调用成功但参数无效”，避免管理员误以为 temperature/topP 已生效。

### 6.6 Opaque Reasoning Continuation

在 `llm-core` 增加专用模型：

```java
public record LlmProviderContinuation(
    LlmProviderType providerType,
    JsonNode payload
) {
  // toString() 只返回 REDACTED 摘要
}
```

该模型不是 metadata，也不是用户可见内容。约束如下：

- 只允许 provider mapper 创建；
- 只允许匹配相同 provider type 的 mapper 消费；
- 不允许写入 `LlmMessage.metadata`；
- Jackson 普通消息序列化必须忽略；
- `toString()`、日志和异常不得输出 payload；
- Agent run 结束、取消或失败后立即释放引用；
- 当前只在工具调用 step 之间传递，不跨独立业务 run 持久化。

建议扩展：

- `LlmCompletionResult`：增加可空 continuation；
- `LlmStreamEvent.MessageEnd`：增加可空 continuation，但 SSE DTO 不映射该字段；
- `AgentStepResult`：增加可空 continuation；
- `LlmMessage.assistantToolCalls(...)`：允许附带 continuation；
- Responses mapper：在 assistant tool call 前恢复 reasoning input item。

同步链路：

```text
Response.output.reasoning
  -> LlmProviderContinuation
  -> AgentStepResult
  -> assistantToolCalls(..., continuation)
  -> 下一轮 Responses input.reasoning
```

流式链路：

```text
response.output_item.done(reasoning)
  -> stream publisher 内部收集完整 reasoning item
  -> MessageEnd.continuation
  -> Agent StepCollector
  -> 下一轮 Responses input.reasoning
```

`response.reasoning_text.delta` 和 `response.reasoning_text.done` 不转换为 `ContentDelta`，否则会把 chain-of-thought 暴露到现有 SSE。

只有当前 step 产生工具调用时才保留 continuation；没有工具调用的最终回答立即丢弃 reasoning state。

### 6.7 管理端集成

后端调整：

- `backend/pom.xml` 注册 `llm-openai-compatible` 和 `llm-deepseek`；
- `llm-openai`、`llm-deepseek` 依赖共享模块；
- `mentor-api` 依赖两个 adapter 模块；
- Spring 自动配置注册两个 `LlmProviderAdapter`；
- provider type 目录自动出现 `deepseek`；
- provider type 默认配置模板返回 DeepSeek Base URL；
- 有效路由模拟返回 provider type 和 effort。

前端调整：

- 新建 provider 时切换 provider type，同步切换 JSON 配置模板；
- 编辑已有 provider 时不重置配置；
- DeepSeek 路由 effort 下拉框只展示“Provider 默认、none、low、high、max”；
- 模型 ID 仍由管理员输入；
- provider 列表和路由表明确显示 `deepseek`，不显示为 OpenAI compatible。

### 6.8 数据库影响

新增 DeepSeek provider 本身不需要数据库迁移：

- `ai_provider_instance.provider_type` 是字符串；
- `ai_model.model_id` 是字符串；
- 调用台账 provider 快照是字符串；
- 模型价格按 provider type + upstream model id 管理。

管理员创建 DeepSeek provider、模型、价格和路由后，现有表结构即可承载。

## 7. API 与数据契约汇总

| 契约 | 变更 | 兼容性 |
| --- | --- | --- |
| `LlmGenerationOptions` | 增加可空 `reasoningEffort` | 保留旧构造器 |
| `AiModelRoutePolicyContent` | 增加可空 `reasoningEffort` | 旧 JSON 自动为 `null` |
| `ResolvedAiModelSnapshot` | 增加路由 effort | 进程内契约 |
| `LlmInvocationTarget` | 增加路由 effort | 进程内契约 |
| `LlmProviderAdapter` | 增加 accepted effort 目录 | 默认空集合 |
| provider type API | 增加 effort 目录和默认配置模板 | 响应增量字段 |
| effective route API | 增加 `reasoningEffort` | 响应增量字段 |
| `ai_llm_call_usage` | 建议增加 `reasoning_effort` | 可空列，历史不回填 |
| `LlmProviderContinuation` | 新增内部敏感状态 | 不进入外部 API |
| provider type | 新增 `deepseek` | 字符串契约，无表迁移 |

## 8. 兼容与迁移策略

### 8.1 第一阶段

- 发布后所有既有路由的 effort 都是 `null`；
- OpenAI mapper 在 `null` 时不发送 `reasoning`，线上行为保持不变；
- 管理员可以逐条路由启用，不需要批量迁移；
- 新台账列为可空，代码回滚后不会影响旧版本读写；
- 前后端需要同批发布，避免旧前端编辑规则时意外丢失新字段。

### 8.2 第二阶段

- 先发布共享模块抽取和 OpenAI 回归，不创建 DeepSeek 配置；
- 再发布 `llm-deepseek`，此时系统只多注册一个 provider type；
- 管理员先创建 disabled DeepSeek provider 和模型；
- 连接验收通过后再启用 provider；
- 最后创建小范围路由规则；
- 不修改既有 OpenAI 路由，不做自动迁移。

### 8.3 回滚

第一阶段回滚：

- 将路由 effort 改回“Provider 默认值”即可恢复不发送参数；
- 若需要代码回滚，新增 JSON 字段和可空台账列可保留；
- 不回滚 Flyway 迁移。

第二阶段回滚：

- 停用或删除 DeepSeek 路由规则；
- 停用 DeepSeek provider 实例；
- 通过既有优先级规则重新命中 OpenAI；
- provider 和模型记录保留，不做物理删除；
- 不启用自动 fallback。

## 9. 测试设计

### 9.1 第一阶段自动化测试

`llm-core`：

- 七个 effort 的 wire value；
- 未知 JSON 值拒绝；
- `null`、请求覆盖路由、路由默认三种合并路径；
- effective effort 非空时要求 `REASONING_EFFORT` capability；
- provider 不支持时返回 `UNSUPPORTED_CAPABILITY`；
- 旧 `LlmGenerationOptions` 构造器兼容。

`ai-governance`：

- 旧路由 JSON 缺少 effort 可读取；
- `none` 与 `null` 不混淆；
- provider effort 子集校验；
- 快照固定 effort；
- 路由模拟返回 effort；
- 调用台账写入最终 effort；
- 历史台账 `NULL` 查询兼容。

`llm-openai`：

- effort 为空时请求 JSON 不包含 `reasoning`；
- 七个值映射到准确 wire value；
- 同步与流式共用同一请求参数；
- `max` 在 SDK 没有静态常量时仍可序列化；
- OpenAI 原有文本、工具、结构化输出和 usage 测试全部回归。

前端：

- 目标模型切换后 effort 选项跟随 provider；
- Provider 默认值保存为空；
- 编辑、启停和重排不丢失 effort；
- 路由表和模拟结果展示 effort；
- API 返回未知 effort 时显示稳定错误而不是自由文本兜底。

### 9.2 第二阶段共享模块测试

- OpenAI 抽取前后的 request fixture 完全一致；
- OpenAI 抽取前后的同步 result、stream event 和错误映射一致；
- provider id 由 profile 注入；
- 异常安全文案不硬编码 OpenAI；
- 不支持字段不会进入请求 JSON；
- stream 取消会关闭 SDK resource；
- 同一 stream 仍只允许一个 subscriber。

### 9.3 DeepSeek mock/fixture 测试

- `deepseek` provider id 贯穿结果、错误和指标；
- Base URL、API Key、timeout 和 retry 正确传入 SDK；
- effort 空值、none、low、high、max 映射；
- minimal、medium、xhigh 在路由或请求校验阶段拒绝；
- thinking 开启时 temperature/topP 拒绝；
- 文本、流式文本、JSON Object 和 JSON Schema 映射；
- cached token 和 reasoning token 映射；
- 401、429、5xx、超时和 SSE error 映射；
- reasoning delta 不进入 `ContentDelta`；
- reasoning item 只作为 opaque continuation；
- 工具调用后下一轮 input 包含 reasoning item、function call 和 function result；
- provider state 不出现在 SSE DTO、日志和持久化 trace。

### 9.4 DeepSeek 真实 E2E 门禁

真实 API Key 只用于人工或受控环境，不进入默认 CI。至少验证：

| 场景 | effort | 预期 |
| --- | --- | --- |
| 同步短文本 | `none` | 返回文本，reasoning token 可为 0 |
| 同步推理 | `high` | 返回文本和 reasoning usage |
| 流式文本 | `low` | SSE 顺序和终态正确 |
| JSON Schema | `high` | 结构化结果可解析 |
| 单工具调用 | `high` | continuation 回传后完成最终回答 |
| 连续两次工具调用 | `high` | 每个 step reasoning 上下文完整 |
| 工具调用关闭推理 | `none` | 无 continuation 也可完成 |
| 取消 | 任意 | 资源关闭、台账 CANCELLED |
| 无效 Key | 任意 | `AUTHENTICATION_FAILED` |
| 限流 | 任意 | `RATE_LIMITED` 且 retryable |

工具调用 E2E 未通过时，不允许把 DeepSeek 路由到 Practice Chat、学习计划 Agent、画像更新 Agent 等工具场景。

## 10. 灰度发布

### 10.1 第一阶段

1. 先发布可空字段、OpenAI mapper 和管理端。
2. 保持所有存量路由为 Provider 默认值。
3. 选择一个内部用户和低风险场景配置 `low`。
4. 对比调用耗时、output tokens、reasoning tokens、错误率和人工质量。
5. 再按场景扩大范围，禁止一次性全局切换到 `high/xhigh/max`。

### 10.2 第二阶段

1. 发布共享模块抽取，保持线上仍只有 OpenAI。
2. 完成 OpenAI 全量回归。
3. 发布 DeepSeek adapter，创建 disabled provider 和 `deepseek-v4-flash` 模型。
4. 用独立测试用户灰度无工具、非关键场景。
5. 验证结构化输出场景。
6. 完成 opaque continuation E2E 后，再灰度工具调用场景。
7. 配置 DeepSeek 模型价格并核对成本统计。
8. 逐步扩大路由范围，保留 OpenAI 显式回滚规则。

停止条件：

- 错误率明显高于 OpenAI 基线；
- 结构化输出解析失败；
- reasoning state 出现在任何用户可见或持久化位置；
- 工具调用出现上下文丢失、重复调用或 call id 不匹配；
- usage 与 DeepSeek 控制台差异超出可接受范围；
- provider/model 被错误归类为 `openai`。

## 11. 风险与应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| effort 支持是模型级而非 provider 级 | 路由保存成功但上游 400 | 首期明确限制；错误归一；后续建设模型 capability |
| OpenAI SDK 静态常量落后 | 新值无法编译或发送 | 使用 `ReasoningEffort.of(wireValue)` |
| DeepSeek 静默忽略参数 | 配置看似生效实际无效 | 共享 mapper allowlist + profile 本地校验 |
| DeepSeek Responses 兼容性变化 | SDK 反序列化或 SSE 失败 | 固定 fixture + 真实 E2E + 独立 profile |
| reasoning 内容泄漏 | 隐私和安全风险 | dedicated opaque state、SSE 丢弃、日志脱敏、禁止持久化 |
| 工具调用丢 reasoning 上下文 | Agent 重复调用或失败 | continuation 契约和多 step E2E 发布门禁 |
| 抽取共享模块导致 OpenAI 回归 | 现有业务受影响 | 先行为等价抽取，再增加 DeepSeek |
| DeepSeek 当前模型支持范围窄 | 新模型尚不可用或旧模型下线 | 模型由管理员维护，不硬编码永久白名单 |
| 默认 thinking 导致延迟上升 | 用户体验或成本恶化 | route 显式 `none/low` 灰度，记录 effort 与 usage |
| Base URL 路径差异 | 404 或鉴权失败 | 配置显式；以官方 `https://api.deepseek.com` 做 E2E |

## 12. 实施拆分与工作量

### 12.1 第一阶段

| 任务 | 主要模块 | 估算 |
| --- | --- | --- |
| P1-1 effort 枚举、生成参数和合并器 | `llm-core` | 0.5-1 人日 |
| P1-2 路由内容、快照、校验和模拟 API | `ai-governance`、`mentor-api` | 1-1.5 人日 |
| P1-3 OpenAI mapper 和能力声明 | `llm-openai` | 0.5-1 人日 |
| P1-4 台账字段、指标和查询兼容 | `ai-governance` | 0.5-1 人日 |
| P1-5 管理端编辑器和测试 | `frontend` | 1-1.5 人日 |
| P1-6 回归与灰度验证 | 全链路 | 0.5-1 人日 |

第一阶段合计约 4-7 人日。

### 12.2 第二阶段

| 任务 | 主要模块 | 估算 |
| --- | --- | --- |
| P2-1 抽取 Responses 共享模块 | `llm-openai-compatible`、`llm-openai` | 1.5-2.5 人日 |
| P2-2 opaque continuation 契约与 Agent loop | `llm-core`、`agent-core` | 1.5-3 人日 |
| P2-3 DeepSeek adapter/profile/config | `llm-deepseek` | 1-2 人日 |
| P2-4 管理 API、前端模板和 effort 目录 | `mentor-api`、`frontend` | 0.5-1.5 人日 |
| P2-5 fixture、异常、SSE 和安全回归 | 多模块 | 1-2 人日 |
| P2-6 真实 E2E 与灰度 | 受控环境 | 1-2 人日 |

完整第二阶段约 6.5-13 人日。

如果只要求 DeepSeek 无工具文本调用，可以暂时缩小到约 3-5 人日；但这不满足当前项目大量 Agent 场景的完整接入标准，不建议作为“DeepSeek 已适配完成”的验收口径。

## 13. 建议实施顺序

1. P1-1 至 P1-3：先打通后端 effort 主链路。
2. P1-4 至 P1-5：补齐观测、管理端和兼容测试。
3. P1-6：以 OpenAI 小范围灰度验证 effort。
4. P2-1：只抽取共享模块并完成 OpenAI 等价回归。
5. P2-2：实现 opaque continuation 和 Agent loop 传递。
6. P2-3 至 P2-4：注册 DeepSeek adapter 和管理入口。
7. P2-5：完成 mock、fixture 和安全测试。
8. P2-6：按“文本 -> 结构化输出 -> 工具调用”顺序进行真实 E2E 和灰度。

## 14. 待评审确认

1. 是否确认 effort 主要配置在模型路由规则，而不是模型记录或 provider 实例？
2. 是否确认 `null` 与 `none` 保持不同语义，并且旧路由默认 `null`？
3. 是否将调用台账 `reasoning_effort` 列纳入第一阶段，而不是延后只做临时指标？
4. 是否确认第二阶段必须抽取 `llm-openai-compatible`，不接受复制一套 DeepSeek mapper？
5. 是否确认 DeepSeek 工具场景必须完成 opaque continuation 后才能灰度？
6. 是否确认原始 reasoning state 只在单次 Agent run 内存中存在，禁止持久化和用户展示？
7. 是否继续坚持无自动 fallback，所有切换与回滚由显式模型路由完成？

## 15. 官方资料

- [OpenAI Reasoning models: Reasoning effort](https://developers.openai.com/api/docs/guides/reasoning#reasoning-effort)
- [DeepSeek: Using the Responses API](https://api-docs.deepseek.com/guides/responses_api)
- [DeepSeek: Thinking Mode](https://api-docs.deepseek.com/guides/thinking_mode)

