# AI 提供商配置与模型路由研发设计

> 设计日期：2026-07-27
> 状态：已实施，待评审
> 适用范围：`backend/llm-core`、`backend/llm-openai`、`backend/ai-governance`、`backend/mentor-application`、`backend/mentor-api`、`backend/policy`、`frontend`
> 技术基线：Java 17、Spring MVC、PostgreSQL、MyBatis、Flyway、通用策略底座、openai-java、React + TypeScript

---

## 0. 已定决策

1. AI 提供商连接配置从 Spring 配置文件迁移到 PostgreSQL，并由管理员页面维护。
2. 提供商连接配置使用明文存储，不建设字段加密、独立密钥表、密钥版本、密钥轮换和字段级变更审计。
3. 管理员详情接口直接返回完整提供商配置，包括完整 API Key；编辑时全量覆盖配置。
4. API Key、完整提供商 `config` 和 Authorization 信息不得写入日志、异常 metadata、Agent trace、调用台账或 SSE 事件。
5. 代码只注册提供商适配器类型。第一版仅注册 `OPENAI`，不接入 DeepSeek、千问或其他新提供商。
6. 管理员不能创建新的提供商类型，只能创建已注册类型的提供商实例。
7. 同一种提供商类型可以创建多个实例。每个实例拥有独立名称、连接配置、启停状态和 SDK Client；提供商类型只在创建时选择，创建后不可切换。
8. 一个提供商实例可以配置多个模型。API Key、Base URL、超时和重试配置只保存在实例上，不在模型记录中重复保存。
9. 模型是管理员显式维护的数据库资源。业务路由引用 `ai_model.id`，不在规则中保存自由文本 provider/model 组合。
10. 模型记录只保存展示名称、上游真实 `model_id`、所属实例和启停状态；第一版不配置模型参数、模型能力、上下文窗口或服务等级。
11. 第一版所有 OpenAI 模型继承 OpenAI 适配器已有能力声明，不建设模型级 capability 编辑能力。
12. 提供商实例和模型第一版不支持物理删除，只支持创建、编辑、启用和停用。
13. 业务场景由代码注册，管理员不能创建、删除或重命名业务场景，前端不能维护独立的场景白名单。
14. 模型路由与系统提示词共用同一份稳定业务场景目录。系统提示词版本升级不得改变模型路由场景标识。
15. 第一批业务场景不包含 `AI_DEBUG`。现有 AI Debug 相关代码由后续独立改动删除。
16. 主题讲解作为独立的 `TOPIC_EXPLANATION` 场景，并补充当前缺失的受管理系统提示词定义。
17. 每个业务场景注册一个内部通用策略类型，策略内容只有 `modelId`。
18. 模型路由复用通用策略已有的用户、用户组、全部用户、启停、逻辑删除、优先级、缓存和单条命中语义。
19. 所有路由规则共享显式优先级，数字越小越优先；不增加“用户高于用户组、用户组高于全部用户”的隐式权重。
20. 不要求每个场景配置全部用户规则，不设置全局默认模型，也不实施任何模型兜底。
21. 当前用户在当前场景没有命中模型路由时，调用失败并返回 `AI_MODEL_ROUTE_NOT_CONFIGURED`。
22. 管理页面只提示每个场景是否存在至少一条已启用规则，不分析规则是否覆盖所有用户。
23. 一次 AI 业务执行开始时只解析一次模型路由和提供商版本。多 step Agent run 在整个 run 中固定使用同一模型。
24. 独立的代码 Review、画像更新等子调用使用自己的业务场景，并在自身开始时单独解析一次模型。
25. 数据库中的提供商和模型记录在每次业务执行开始时读取，不建设业务配置缓存。
26. 每个提供商实例版本复用一个 SDK Client。Client 以 `providerInstanceId + updatedAt` 识别版本，配置更新后新执行使用新 Client，运行中的执行继续使用旧 Client。
27. OpenAI 同步和流式请求共享同一个 `OpenAIClient` 和同一套 OkHttp 连接池，实例配置只保留一个 `timeoutSeconds`。
28. OkHttp 连接池和现有 Agent `20/100 + SynchronousQueue` 线程池保持当前配置，不新增 HTTP 连接参数、provider semaphore、等待队列或 provider 并发限制。
29. 第一版不自动测试提供商连接，不调用远程模型列表 API，不自动发现或同步模型。
30. 第一版不自动导入现有文件配置，也不在数据库未配置时回退 `OPENAI_*` 或 `AI_GATEWAY_*` 环境变量。
31. 调用台账增加 `provider_instance_id` 和 `ai_model_id`，同时保留 provider 类型和上游模型名文本快照。
32. 现有模型价格第一版继续按 `provider type + upstream model_id` 管理，同一提供商类型下同名模型共享价格；实例级差异价格不在本期范围。

## 1. 背景

当前项目已经完成第一版 OpenAI 接入和 LLM 抽象，但运行时配置仍然是单实例、单模型的启动配置：

```yaml
algo-mentor:
  ai:
    gateway:
      default-provider: openai
      default-model: gpt-5.2
    openai:
      enabled: false
      api-key: ...
      base-url: https://api.openai.com/v1
      model: gpt-5.2
      timeout: 5m
      stream-timeout: 5m
      max-retries: 2
```

现有结构存在以下限制：

- `OpenAiLlmProperties` 只能描述一个 OpenAI 连接和一个默认模型；
- `OpenAiLlmProvider.PROVIDER_ID` 固定为 `openai`，无法区分多个 OpenAI 实例；
- `DefaultLlmGateway` 以 `LlmProviderId` 构造唯一 provider Map，同一个 provider id 不能出现多次；
- OpenAI provider 只向 gateway 声明配置文件中的一个模型，无法在同一实例下路由多个模型；
- `LlmGatewayProperties` 在应用启动时固定全局默认 provider/model，无法由管理员实时调整；
- 当前 `AgentModelSelectorResolver` 已预留按可信 userId、场景等信息路由的扩展点，但尚无数据库模型路由实现；
- AI 治理已经记录 `purpose`、`source`、provider、model 和 Token，但当前 provider/model 只是文本，无法稳定关联到配置实例；
- 项目已经具备用户组和通用策略底座，如果模型路由重新实现用户、用户组和优先级，会产生重复语义。

与此同时，系统提示词管理已经形成了适合复用的业务场景注册模式：

- 业务场景由代码注册；
- 管理员只能管理注册场景对应的策略，不能创建代码不认识的类型；
- 管理页面从后端注册目录自动发现类型；
- 用户、用户组、全部用户和优先级继续由通用策略负责；
- 一次业务执行在固定时点解析一次配置快照。

模型路由应沿用这套场景目录和管理语义，但与系统提示词存在一个关键差异：系统提示词有代码默认正文，模型连接和模型资源完全来自数据库，因此模型路由没有代码默认值和运行时兜底。

## 2. 目标与非目标

### 2.1 目标

- 管理员可以在页面创建和维护多个 OpenAI 提供商实例。
- 每个提供商实例可以维护多个显式模型资源。
- 提供商配置修改后无需重启应用，并从下一次 AI 业务执行开始生效。
- 未来增加新提供商时只新增代码适配器和对应配置 DTO，不修改提供商实例、模型和路由表结构。
- 业务模型选择可以按稳定业务场景、指定用户、用户组或全部用户配置。
- Pro 用户组、普通用户、后台任务等可以在不同场景命中不同模型。
- 模型路由与系统提示词使用同一业务场景目录，新增业务场景后自动出现在管理页面。
- 多 step Agent run 在执行期间保持模型稳定，不因管理员中途修改配置发生 step 间切换。
- 直接 completion、Agent step 和后台任务都经过同一模型路由和动态 provider 调用链。
- 保持现有 AI 治理开关、额度、调用台账、成本估算和 SSE 契约可继续工作。
- 管理页面明确展示哪些业务场景尚未配置任何启用规则。

### 2.2 非目标

- 本期不接入 DeepSeek、千问或其他新模型提供商。
- 不允许管理员上传 SDK、脚本、类名、SpEL 或任意可执行 provider 实现。
- 不加密数据库中的 API Key，不集成 Vault、KMS 或外部密钥管理服务。
- 不建设 API Key 轮换、过期提醒、版本历史、审批和恢复能力。
- 不自动测试 API Key 或 Base URL 是否有效。
- 不自动调用上游模型列表接口，不自动创建、更新或删除模型记录。
- 不提供模型参数、temperature、reasoning effort、service tier、上下文窗口或 capability 管理。
- 不实施模型级自动降级、provider 故障转移、重试到备用实例或负载均衡。
- 不设置全局默认模型、场景默认模型或文件配置兜底。
- 不分析一组用户/用户组规则是否覆盖所有用户。
- 不建设百分比实验、随机分桶、动态标签表达式或基于成本的自动选模。
- 不调整 Agent 线程池、Tomcat、OkHttp dispatcher、OkHttp 连接池和操作系统连接参数。
- 不新增 provider 级并发 semaphore、等待队列或优先级调度器。
- 不在本期完成 AI Debug 功能删除；本设计只保证新场景目录中不注册该场景。
- 不把模型价格改造成实例级价格，也不保存调用发生时的价格版本。

## 3. 核心术语

### 3.1 提供商类型

提供商类型表示代码中的协议和 SDK 适配能力，例如：

```text
OPENAI
```

未来可能增加：

```text
DEEPSEEK
QWEN
```

提供商类型由代码注册，不能由管理员创建。数据库建议保存稳定的小写 code，例如 `openai`，Java 枚举或常量可使用 `OPENAI`。

### 3.2 提供商实例

提供商实例表示管理员配置的一套真实连接，例如：

```text
OpenAI 主实例
OpenAI 备用实例
OpenAI 代理实例
```

同一个提供商类型可以有多个实例，每个实例拥有独立的 Base URL、API Key、超时、重试和启停状态。

### 3.3 配置模型

配置模型表示某个提供商实例下可被业务路由选择的上游模型。它包含：

- 数据库主键 `ai_model.id`；
- 管理端展示名称；
- 上游真实 `model_id`；
- 所属提供商实例；
- 启停状态。

配置模型不是代码中的模型枚举，也不是上游模型目录的自动镜像。

### 3.4 AI 业务场景

AI 业务场景表示一次独立的 AI 执行目标，例如题目训练聊天、学习计划草案或画像批量更新。业务场景决定：

- 使用哪一类系统提示词；
- 使用哪一类模型路由策略；
- 调用台账记录哪个业务来源；
- 管理页面如何展示和模拟有效配置。

业务场景 code 稳定且不带提示词 schema 版本。

### 3.5 模型路由规则

模型路由规则是一个通用策略记录：

```text
业务场景 + 用户范围 + 显式优先级 -> ai_model.id
```

规则本身不保存 provider type、Base URL、API Key 或上游模型名。

### 3.6 执行模型快照

执行模型快照是一次业务执行开始时解析出的不可变结果，包含命中规则、配置模型、提供商实例版本和实际上游模型名。快照不包含 API Key 等连接密钥。

## 4. AI 业务场景目录

### 4.1 统一场景契约

新增统一代码契约，替代“系统提示词一套场景、治理 `AiRunSource` 一套场景、模型路由再建一套场景”的分散方式。

建议定义：

```java
public enum AiBusinessScenario {
  MENTOR_CONVERSATION(
      "mentor-conversation", "CONVERSATION", "普通导师会话", "Mentor conversation"),
  TOPIC_EXPLANATION(
      "topic-explanation", "CONVERSATION", "主题讲解", "Topic explanation"),
  PRACTICE_CHAT(
      "practice-chat", "PRACTICE", "题目训练聊天", "Practice chat"),
  LEARNING_PLAN_DRAFT(
      "learning-plan-draft", "LEARNING_PLAN", "学习计划草案", "Learning plan draft"),
  LEARNING_PLAN_REVISION(
      "learning-plan-revision", "LEARNING_PLAN", "学习计划修订", "Learning plan revision"),
  LEARNING_PLAN_EXTENSION(
      "learning-plan-extension", "LEARNING_PLAN", "学习计划扩展", "Learning plan extension"),
  PRACTICE_CODE_REVIEW(
      "practice-code-review", "PRACTICE", "练习代码 Review", "Practice code review"),
  LEARNER_DECLARED_PROFILE_UPDATE(
      "learner-declared-profile-update", "LEARNER_PROFILE", "学习者自述画像更新",
      "Declared learner profile update"),
  CODE_REVIEW_PROFILE_UPDATE(
      "code-review-profile-update", "LEARNER_PROFILE", "Code Review 画像更新",
      "Code review profile update");
}
```

实际实现应集中维护以下字段：

```text
code
categoryCode
displayNameZh
displayNameEn
descriptionZh
descriptionEn
```

跨模块使用的场景 code、模型路由 typeCode 前缀和 metadata key 必须集中到常量类或枚举中，不能在 controller、service 和前端散落字符串。

### 4.2 第一批场景与系统提示词映射

| 业务场景 | 稳定 code | 系统提示词定义 |
| --- | --- | --- |
| 普通导师会话 | `mentor-conversation` | `MENTOR_CONVERSATION` |
| 主题讲解 | `topic-explanation` | 新增 `TOPIC_EXPLANATION` |
| 题目训练聊天 | `practice-chat` | `PRACTICE_CHAT` |
| 学习计划草案 | `learning-plan-draft` | `LEARNING_PLAN_DRAFT` |
| 学习计划修订 | `learning-plan-revision` | `LEARNING_PLAN_REVISION` |
| 学习计划扩展 | `learning-plan-extension` | `LEARNING_PLAN_EXTENSION` |
| 练习代码 Review | `practice-code-review` | `PRACTICE_CODE_REVIEW` |
| 学习者自述画像更新 | `learner-declared-profile-update` | `DECLARED_PROFILE_UPDATE` |
| Code Review 画像更新 | `code-review-profile-update` | `CODE_REVIEW_PROFILE_UPDATE` |

`AI_DEBUG` 不注册为 `AiBusinessScenario`，不出现在系统提示词目录或模型路由目录中。

### 4.3 系统提示词版本与场景版本

系统提示词 `typeCode` 继续带 schema 主版本：

```text
ai.system-prompt.practice-chat.v1
ai.system-prompt.practice-chat.v2
```

业务场景 code 不带版本：

```text
practice-chat
```

`ManagedSystemPromptDefinition` 增加场景引用：

```java
public interface ManagedSystemPromptDefinition {
  AiBusinessScenario scenario();
  String typeCode();
  String sourceRevision();
  // ...
}
```

同一个业务场景可以在提示词升级期间同时注册 `.v1` 和 `.v2` definition，但模型路由始终使用同一个场景 code，不复制或迁移模型规则。

### 4.4 主题讲解缺口

当前 `ExplainTopicUseCase` 只发送一条用户消息：

```text
Explain the learning topic for an algorithm student: {topic}
```

该调用没有受管理系统提示词。实施时新增 `TOPIC_EXPLANATION` definition，并让 `ExplainTopicUseCase` 通过 `ManagedSystemPromptResolver` 解析系统提示词快照。旧 `AiRunSource.PROBLEM_DETAIL` 语义收敛为 `AiBusinessScenario.TOPIC_EXPLANATION`。

## 5. 总体架构

### 5.1 管理配置链路

```text
管理员
  -> AI 提供商页面
      -> 创建/编辑 ai_provider_instance
      -> 在实例下创建/编辑 ai_model

管理员
  -> 模型路由页面
      -> 读取 AiBusinessScenario 代码目录
      -> 查看每个场景已启用规则数量
      -> 通过通用策略 CRUD 管理用户/用户组/全部用户规则
      -> 规则内容只保存 modelId
```

### 5.2 运行时调用链路

```text
业务入口
  -> 确定 AiBusinessScenario 和可信 userId
  -> AiModelRouteResolver
      -> GenericPolicyQueryService 按场景 typeCode 解析单条命中规则
      -> 加载 ai_model
      -> 加载 ai_provider_instance
      -> 校验模型和实例已启用
      -> ProviderClientRegistry 获取当前实例版本 Client
      -> 生成 ResolvedAiModelSnapshot
  -> 将快照绑定到本次 AI 业务执行
  -> Agent step / direct completion / background completion
  -> DynamicLlmGateway
      -> 按 providerType 找到代码适配器
      -> 使用快照绑定的 Client 和 upstreamModelId 发起请求
  -> 调用台账记录 providerInstanceId、aiModelId、providerType 和 upstreamModelId
```

### 5.3 运行时无兜底语义

```text
场景没有任何命中规则
  -> AI_MODEL_ROUTE_NOT_CONFIGURED

规则引用不存在模型
  -> AI_MODEL_UNAVAILABLE

模型已停用
  -> AI_MODEL_UNAVAILABLE

提供商实例已停用
  -> AI_MODEL_UNAVAILABLE

providerType 没有代码适配器
  -> AI_PROVIDER_TYPE_NOT_SUPPORTED

provider config 无法解析
  -> AI_PROVIDER_CONFIG_INVALID
```

以上错误不得回退到文件配置、其他场景、其他模型或其他提供商实例。

## 6. 模块边界

### 6.1 `llm-core`

`llm-core` 继续定义 provider 无关的请求、响应、流和工具协议，并补充动态 provider 运行时所需的窄契约：

```text
provider/
  LlmProviderAdapter
  LlmProviderAdapterRegistry
  LlmProviderClient
  LlmProviderInstanceSpec
  LlmProviderType

model/
  LlmInvocationTarget
```

建议契约方向：

```java
public interface LlmProviderAdapter {
  String providerType();

  void validate(JsonNode config);

  LlmProviderClient createClient(LlmProviderInstanceSpec instance);
}

public interface LlmProviderClient {
  LlmCompletionResult complete(String upstreamModelId, LlmCompletionRequest request);

  Flow.Publisher<LlmStreamEvent> stream(String upstreamModelId, LlmCompletionRequest request);
}
```

`llm-core` 不依赖 PostgreSQL、MyBatis、通用策略、用户组或管理 API。

### 6.2 `llm-openai`

`llm-openai` 负责：

- 注册唯一的 `OpenAiProviderAdapter`；
- 定义 `OpenAiProviderConfig`；
- 将通用配置 JSON 转换为 OpenAI 强类型配置；
- 使用 openai-java 创建共享 `OpenAIClient`；
- 将通用 LLM 请求映射到 Responses API；
- 映射同步、流式、Token 和异常结果；
- 声明 OpenAI 适配器支持的能力集合。

现有 `OpenAiLlmProperties` 和基于 `@ConditionalOnProperty` 的单例 provider 不再作为主运行链路。完成切换后删除或仅在过渡提交中保留，最终不得形成文件配置兜底。

### 6.3 `ai-governance`

`ai-governance` 继续拥有 AI 动态配置、治理、计费和调用台账，并新增：

```text
provider/
  model/                 提供商实例与配置模型领域对象
  repository/            MyBatis mapper、repository 和 row
  service/               管理 CRUD、启停和强类型配置校验
  runtime/               ProviderClientRegistry

routing/
  AiBusinessScenario
  AiModelRoutePolicyContent
  AiModelRoutePolicyTypeContributor
  AiModelRouteResolver
  ResolvedAiModelSnapshot
```

`ai-governance` 可以依赖 `policy` 的强类型查询接口，但不能复制其用户组、范围、优先级和缓存实现。

### 6.4 `mentor-application`

`mentor-application` 已依赖 `ai-governance`，负责：

- 让每个系统提示词 definition 引用 `AiBusinessScenario`；
- 在业务入口选择正确场景；
- 在创建 Agent request 或直接 completion context 时携带已解析模型快照；
- 保持业务 prompt、工具和领域逻辑不理解 provider config JSON。

业务代码不得直接写 `model_id`、provider type 或 provider instance id。

### 6.5 `policy`

`policy` 保持通用底座职责：

- `GenericPolicyType<T>` 注册；
- 用户、用户组和全部用户范围；
- 全局显式优先级；
- 启停、逻辑删除和乐观锁；
- 类型级策略缓存；
- 单条有效策略解析；
- 管理 CRUD 和排序。

`policy` 不理解模型、provider、业务场景含义或模型启停状态。

### 6.6 `mentor-api`

`mentor-api` 负责：

- 提供商实例与模型管理员 API；
- 模型路由场景目录和有效命中模拟 API；
- Spring Bean 装配；
- 将动态 gateway 接入现有 Agent 和 completion 链路；
- 将现有 `LlmGatewayProperties` 启动默认值从主链路移除；
- 复用现有 `/api/admin/ai` 管理员权限边界。

### 6.7 `frontend`

前端在现有 `/admin/ai` 工作区增加：

```text
提供商与模型
模型路由
```

前端从后端读取 provider type 和业务场景目录，不硬编码未来 provider 或业务场景清单。

## 7. 数据模型

### 7.1 Flyway 迁移

当前全仓最大迁移版本为 `V40`。建议迁移位置：

```text
backend/ai-governance/src/main/resources/db/migration/ai/V41__ai_provider_instance_and_model.sql
```

`V41` 为当前设计时建议值。实施前必须再次扫描所有模块 Flyway 版本并顺延冲突版本。

### 7.2 `ai_provider_instance`

```sql
CREATE TABLE ai_provider_instance (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  provider_type VARCHAR(32) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  config JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_ai_provider_instance_name UNIQUE (name),
  CONSTRAINT ck_ai_provider_instance_name_not_blank CHECK (btrim(name) <> ''),
  CONSTRAINT ck_ai_provider_instance_type_not_blank CHECK (btrim(provider_type) <> ''),
  CONSTRAINT ck_ai_provider_instance_config_object CHECK (jsonb_typeof(config) = 'object')
);

CREATE INDEX idx_ai_provider_instance_type_enabled
  ON ai_provider_instance(provider_type, enabled, id);
```

字段语义：

| 字段 | 说明 |
| --- | --- |
| `id` | 提供商实例稳定主键 |
| `name` | 管理员可读实例名称，全局唯一 |
| `provider_type` | 代码适配器稳定 code，第一版仅 `openai` |
| `enabled` | 实例是否允许新执行使用 |
| `config` | provider 专属明文配置 JSON |
| `updated_at` | 配置版本标识，同时用于 SDK Client 刷新 |

不增加 `deleted_at`、密钥密文、密钥掩码、密钥 revision、独立 endpoint 字段或 provider 专属数据库列。

### 7.3 `ai_model`

```sql
CREATE TABLE ai_model (
  id BIGSERIAL PRIMARY KEY,
  provider_instance_id BIGINT NOT NULL REFERENCES ai_provider_instance(id) ON DELETE RESTRICT,
  display_name VARCHAR(120) NOT NULL,
  model_id VARCHAR(160) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_ai_model_instance_model UNIQUE (provider_instance_id, model_id),
  CONSTRAINT ck_ai_model_display_name_not_blank CHECK (btrim(display_name) <> ''),
  CONSTRAINT ck_ai_model_model_id_not_blank CHECK (btrim(model_id) <> '')
);

CREATE INDEX idx_ai_model_instance_enabled
  ON ai_model(provider_instance_id, enabled, id);
```

字段语义：

| 字段 | 说明 |
| --- | --- |
| `provider_instance_id` | 使用哪一套连接配置 |
| `display_name` | 管理页面展示，例如 `5.6 Sol` |
| `model_id` | 上游真实模型标识，例如 `gpt-5.6-sol` |
| `enabled` | 是否允许新路由执行使用 |

模型不增加 `config`、`is_default`、capability、价格、参数或上下文窗口字段。

### 7.4 调用台账扩展

在 `ai_llm_call_usage` 增加：

```sql
ALTER TABLE ai_llm_call_usage
  ADD COLUMN provider_instance_id BIGINT NULL REFERENCES ai_provider_instance(id) ON DELETE RESTRICT,
  ADD COLUMN ai_model_id BIGINT NULL REFERENCES ai_model(id) ON DELETE RESTRICT;

CREATE INDEX idx_ai_llm_call_usage_configured_model_started
  ON ai_llm_call_usage(ai_model_id, started_at DESC);

CREATE INDEX idx_ai_llm_call_usage_provider_instance_started
  ON ai_llm_call_usage(provider_instance_id, started_at DESC);
```

历史记录不回填，新增字段允许为 `NULL`。已有 `provider` 和 `model` 字段继续保存调用时的 provider type 和上游模型名文本快照，避免配置名称变更影响历史展示。

### 7.5 模型价格兼容

第一版不修改 `ai_model_price` 的主语义：

```text
provider = openai
model = gpt-5.6-sol
```

成本查询继续使用调用台账中的 provider type 和 upstream model id 关联价格。因此：

- 同一 `provider_type` 下同名模型共享一份价格；
- 不支持同名模型按 provider instance 配置不同价格；
- `ai_model.id` 只用于路由、配置关联和诊断，不作为本期价格主键。

如果后续出现代理实例、企业合同或区域实例价格不同，再单独设计实例级或配置模型级价格迁移。

## 8. OpenAI 配置契约

### 8.1 JSON 结构

第一版 OpenAI `config` 固定为：

```json
{
  "apiKey": "sk-xxx",
  "baseUrl": "https://api.openai.com/v1",
  "timeoutSeconds": 300,
  "maxRetries": 2
}
```

建议强类型 DTO：

```java
public record OpenAiProviderConfig(
    String apiKey,
    URI baseUrl,
    int timeoutSeconds,
    int maxRetries
) {
}
```

### 8.2 校验规则

- `apiKey` 非空；
- `baseUrl` 是绝对 `http` 或 `https` URI；
- `timeoutSeconds > 0`；
- `maxRetries >= 0`；
- 不允许未知顶层字段静默生效，Jackson 配置应明确失败或由适配器显式忽略策略统一处理；
- 校验只验证结构和本地语义，不发送网络请求。

### 8.3 明文读写边界

按照已定极简方案：

- PostgreSQL `config` 保存完整明文 API Key；
- `GET` 管理员详情返回完整 `config`；
- `PUT` 使用完整 `config` 覆盖旧值；
- 不返回 `credentialConfigured`、掩码或密钥摘要；
- 不建设“留空表示不修改”语义。

唯一保留的输出约束是：日志、异常、指标 tag、审计 metadata、调用台账、Agent metadata、SSE 和普通用户 API 不得包含完整 `config` 或 API Key。

## 9. 提供商和模型生命周期

### 9.1 提供商实例

支持：

- 创建；
- 查询列表和详情；
- 全量编辑名称、状态和配置；
- 启用；
- 停用。

不支持：

- 物理删除；
- 逻辑删除；
- 克隆；
- 自动测试；
- 自动同步模型。

停用提供商实例不会修改其模型的 `enabled`，但这些模型在运行时全部视为不可用。重新启用实例后，模型按自身状态恢复可用性。

### 9.2 配置模型

支持：

- 在指定实例下创建；
- 查询实例下模型列表；
- 编辑展示名称、上游模型名和状态；
- 启用；
- 停用。

不支持物理或逻辑删除。

模型停用后，已有路由策略仍保留并在管理页面显示不可用目标；运行时命中该规则时返回 `AI_MODEL_UNAVAILABLE`，不跳过该规则继续匹配低优先级规则。

### 9.3 配置更新与运行中任务

- 提供商、模型或路由规则更新只影响更新后新开始的业务执行；
- 运行中的 Agent run 继续使用启动时解析出的模型和 SDK Client；
- 停用实例或模型不强制中断运行中的请求；
- 本期不提供管理员强制取消 provider 请求能力。

## 10. 模型路由策略

### 10.1 类型编码

每个业务场景注册一个内部策略类型：

```text
ai.model-route.mentor-conversation.v1
ai.model-route.topic-explanation.v1
ai.model-route.practice-chat.v1
ai.model-route.learning-plan-draft.v1
ai.model-route.learning-plan-revision.v1
ai.model-route.learning-plan-extension.v1
ai.model-route.practice-code-review.v1
ai.model-route.learner-declared-profile-update.v1
ai.model-route.code-review-profile-update.v1
```

`.v1` 表示模型路由策略内容 schema 版本。业务场景 code 仍然不带版本。

### 10.2 策略内容

所有场景共用同一个强类型内容：

```java
public record AiModelRoutePolicyContent(long modelId) {
  public AiModelRoutePolicyContent {
    if (modelId < 1) {
      throw new IllegalArgumentException("modelId must be positive");
    }
  }
}
```

JSON 示例：

```json
{
  "modelId": 101
}
```

策略内容不允许出现 provider id、provider type、模型字符串、生成参数或 fallback 列表。

### 10.3 类型贡献

`AiModelRoutePolicyTypeContributor` 遍历 `AiBusinessScenario`，为每个场景贡献一个 `GenericPolicyType<AiModelRoutePolicyContent>`：

```java
GenericPolicyType.of(
    routeTypeCode(scenario),
    AiModelRoutePolicyContent.class,
    routeContentValidator,
    GenericPolicyTypeExposure.INTERNAL_ONLY);
```

策略写入时校验：

- `modelId` 为正数；
- 模型记录存在；
- provider type 已注册适配器。

允许为当前已停用的模型保存规则，便于管理员提前编排或暂时停用；运行时仍要求模型和实例都已启用。

### 10.4 匹配与优先级

完全沿用通用策略语义：

```text
按 typeCode 加载已启用策略
  -> 按 priority 从小到大
  -> 第一条命中用户、用户组或全部用户范围的策略胜出
  -> 不再继续匹配
```

不增加范围隐式权重。例如：

| 优先级 | 范围 | 模型 |
| ---: | --- | --- |
| 10 | Pro 用户组 | 5.6 Sol |
| 20 | 指定测试用户 | 5.5 |
| 100 | 全部用户 | 5.6 Terra |

如果测试用户同时属于 Pro 组，优先级 10 的 Pro 规则胜出。管理员需要把单用户规则调到更高优先级，才能覆盖用户组规则。

### 10.5 无兜底和未覆盖用户

以下配置合法：

```text
PRACTICE_CHAT
  -> 只有 Pro 用户组规则
```

该场景在管理页面显示“已配置 1 条”，但不属于 Pro 用户组的用户没有模型可用。运行时返回：

```text
AI_MODEL_ROUTE_NOT_CONFIGURED
```

管理端不计算用户全集、不分析组交集，也不阻止这种配置。

### 10.6 场景配置状态

场景目录状态按已启用策略数量计算：

```text
enabledPolicyCount > 0 -> CONFIGURED
enabledPolicyCount = 0 -> UNCONFIGURED
```

已停用但未删除的策略不计入 `enabledPolicyCount`，但详情页继续展示。

## 11. 模型路由解析与执行快照

### 11.1 解析接口

建议契约：

```java
public interface AiModelRouteResolver {
  ResolvedAiModelSnapshot resolve(AiBusinessScenario scenario, long userId);
}
```

### 11.2 快照内容

```java
public record ResolvedAiModelSnapshot(
    AiBusinessScenario scenario,
    long routePolicyId,
    long routePolicyVersion,
    PolicyMatchSource matchSource,
    Long matchedSubjectId,
    long aiModelId,
    String upstreamModelId,
    long providerInstanceId,
    String providerType,
    Instant providerUpdatedAt,
    LlmProviderClient client
) {
}
```

实际实现可以把 `LlmProviderClient` 封装为内部 handle，避免业务模块直接感知 client 类型。持久化 metadata 只记录以下低敏字段：

```text
scenarioCode
routePolicyId
routePolicyVersion
matchSource
matchedSubjectId
aiModelId
upstreamModelId
providerInstanceId
providerType
providerUpdatedAt
```

不得把 `config`、API Key 或 OpenAI SDK Client 序列化到 metadata。

### 11.3 解析顺序

```text
1. 校验 scenario 已注册
2. 根据场景 route typeCode 和 userId 解析通用策略
3. 未命中 -> AI_MODEL_ROUTE_NOT_CONFIGURED
4. 加载 ai_model
5. 模型不存在或 disabled -> AI_MODEL_UNAVAILABLE
6. 加载 ai_provider_instance
7. 实例不存在或 disabled -> AI_MODEL_UNAVAILABLE
8. 查找 provider adapter
9. adapter 不存在 -> AI_PROVIDER_TYPE_NOT_SUPPORTED
10. 解析并校验 config
11. 获取 provider instance 当前版本 Client
12. 返回不可变执行快照
```

### 11.4 与 AI 准入顺序

用户入口建议在消耗每日请求额度和获取用户运行锁之前解析模型路由：

```text
认证和静态功能开关校验
  -> 动态 AI 开关校验
  -> 模型路由解析
  -> 请求额度消耗
  -> 用户运行锁
  -> 写入 admitted 记录
  -> 执行模型调用
```

这样未配置模型、模型停用或 provider config 无效不会消耗用户当日额度，也不会占用长时间运行锁。

拒绝记录仍应保存业务场景和稳定错误码，但不得保存 provider config。

### 11.5 Agent run

主 Agent run 在创建 `AgentRequest` 或准入结果时绑定一个 `ResolvedAiModelSnapshot`：

```text
PRACTICE_CHAT run 开始
  -> 解析 5.6 Sol
  -> step 1 使用 5.6 Sol
  -> 执行工具
  -> step 2 仍使用 5.6 Sol
  -> run 结束
```

`AgentModelSelectorResolver` 不再允许普通业务请求通过显式 provider/model 文本绕过管理员路由。请求级 selector 只保留 required capabilities 和 purpose/scenario 等提示，最终目标模型来自执行快照。

### 11.6 独立子调用

以下调用虽然可能发生在父 Agent run 内，但属于独立业务场景：

- `PRACTICE_CODE_REVIEW`；
- `LEARNER_DECLARED_PROFILE_UPDATE`；
- `CODE_REVIEW_PROFILE_UPDATE`。

它们在自身开始时使用相同 userId 和自己的场景重新解析模型，不继承父 `PRACTICE_CHAT` 模型。

### 11.7 后台任务

后台画像任务继续携带它所处理的可信 userId：

```text
CODE_REVIEW_PROFILE_UPDATE + userId
  -> 解析该后台场景针对该用户的模型路由
```

管理员可以只配置全部用户规则，也可以为指定用户或用户组配置后台任务模型。没有命中规则时当前消息失败，不回退其他场景模型。

## 12. Provider Adapter 与 SDK Client

### 12.1 Adapter 注册表

应用启动时收集 `LlmProviderAdapter` Bean：

```text
Map<providerType, LlmProviderAdapter>
```

约束：

- provider type 统一标准化；
- 同一个 provider type 只能注册一个 adapter；
- 重复注册阻止应用启动；
- 数据库出现未注册 provider type 时不阻止应用启动，但对应实例不可执行；
- 管理创建接口只能选择已注册 provider type。

### 12.2 OpenAI Client 创建

每个启用实例当前版本创建一个共享 `OpenAIClient`：

```java
OpenAIOkHttpClient.builder()
    .apiKey(config.apiKey())
    .baseUrl(config.baseUrl().toString())
    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
    .maxRetries(config.maxRetries())
    .build();
```

同步和流式调用使用同一个 Client：

```text
client.responses().create(...)
client.responses().createStreaming(...)
```

不再为普通调用和流式调用创建两个独立 OkHttp Client。

### 12.3 Client 复用

`ProviderClientRegistry` 按实例维护当前 holder：

```text
providerInstanceId -> ClientHolder(updatedAt, client)
```

获取逻辑：

```text
holder 不存在
  -> 创建 Client

holder.updatedAt == row.updatedAt
  -> 复用 Client

holder.updatedAt != row.updatedAt
  -> 创建新 Client 并原子替换 holder
```

运行中的执行持有旧 Client 引用直到结束。Map 中只保留实例的最新 holder，不保留无界历史版本列表。

### 12.4 数据库读取与 Client 缓存边界

每次业务执行开始仍查询：

- 命中策略；
- `ai_model`；
- `ai_provider_instance`。

Client holder 只复用 SDK Client 和底层 HTTP 连接池，不缓存业务路由或数据库配置记录。因此管理员更新 `updated_at` 后，下一次执行能够立即识别新版本。

### 12.5 线程与连接池语义

当前 Agent loop 使用阻塞式 OpenAI 流：

- 一条活跃 Agent run 占用一条 `agent-loop-*` 平台线程；
- 当前 Agent 线程池 `core=20`、`max=100`、`SynchronousQueue`、`AbortPolicy`；
- 达到 100 条 Agent 执行后，新 Agent 任务立即拒绝，不进入等待队列；
- Tomcat 请求线程在返回 `SseEmitter` 后释放，不在整个模型流期间持续占用。

OkHttp 连接池与数据库 Hikari 池语义不同：

- OkHttp pool 负责复用 TCP/TLS 连接；
- 默认保留最多 5 条空闲连接，空闲保留 5 分钟；
- 5 不是活动请求硬上限；
- 并发请求需要时可以创建更多活动连接；
- 线程不独占固定连接；
- HTTP/2 可能复用单连接承载多个流，但系统不依赖该能力保证并发。

第一版不暴露 `maxIdleConnections`、keep-alive、dispatcher 或活动连接数配置。

### 12.6 不新增 provider 并发限制

Agent 线程池只直接约束 Agent 路径，direct completion 和后台 worker 不一定受其 100 线程上限控制。尽管如此，本期明确不新增：

- provider instance semaphore；
- `maxConcurrentRequests` 配置；
- provider 等待队列；
- 用户级或场景级 provider 许可；
- 后台与用户请求优先级调度。

后续只有在实际观察到 provider 过载、后台任务挤占或上游限流问题时，再基于调用台账和 in-flight 指标单独设计容量控制。

## 13. Dynamic LLM Gateway 改造

### 13.1 当前问题

当前 `DefaultLlmGateway` 在启动时接收 `List<LlmProvider>`，并按静态 `LlmProviderId` 构造不可变 Map。这一模型无法支持数据库实例新增、编辑和停用。

### 13.2 目标语义

Dynamic gateway 不再通过静态 provider Bean 列表选择实例，而是消费执行快照中的调用目标：

```text
providerType
providerInstanceId
providerUpdatedAt
aiModelId
upstreamModelId
client handle
```

调用前继续执行通用能力检查，但第一版模型能力来自 provider adapter 的统一声明。

### 13.3 身份字段

需要区分：

| 字段 | 用途 |
| --- | --- |
| `providerType` | 选择 adapter、价格关联和聚合展示，例如 `openai` |
| `providerInstanceId` | 选择连接配置和诊断具体实例 |
| `aiModelId` | 关联管理员配置模型 |
| `upstreamModelId` | 发送给上游和价格关联，例如 `gpt-5.6-sol` |

现有 `LlmCompletionResult.provider` 继续返回 provider type，`model` 继续返回 upstream model id。实例和配置模型 ID 通过受信调用上下文与调用台账保存，不把 `LlmProviderId` 强行改成实例名称。

### 13.4 显式模型覆盖边界

普通业务代码和前端不得传入任意 provider/model 覆盖管理员路由。以下内容仍可由调用方表达：

- required capabilities；
- 是否流式；
- response format；
- tool specs 和 tool choice；
- generation options 中现有允许项。

真正的 provider instance 和 upstream model 必须来自 `ResolvedAiModelSnapshot`。

测试代码可以通过 fake resolver 或 fake snapshot 注入测试模型，不需要连接数据库或真实 OpenAI。

## 14. 管理员 API

### 14.1 API 常量

新增 API 路径、provider type、场景 code、策略 typeCode 前缀、错误码和 JSON 字段必须集中在所属模块常量类或枚举中。

### 14.2 提供商类型目录

```http
GET /api/admin/ai/provider-types
```

响应：

```json
{
  "items": [
    {
      "code": "openai",
      "displayName": "OpenAI"
    }
  ]
}
```

目录来自 adapter registry，不从数据库读取。

### 14.3 提供商实例

```http
GET  /api/admin/ai/providers
POST /api/admin/ai/providers
GET  /api/admin/ai/providers/{providerInstanceId}
PUT  /api/admin/ai/providers/{providerInstanceId}
```

创建请求使用全量字段：

```json
{
  "name": "OpenAI 主实例",
  "providerType": "openai",
  "enabled": true,
  "config": {
    "apiKey": "sk-xxx",
    "baseUrl": "https://api.openai.com/v1",
    "timeoutSeconds": 300,
    "maxRetries": 2
  }
}
```

编辑请求不允许切换 `providerType`，只全量提交可编辑字段：

```json
{
  "name": "OpenAI 主实例",
  "enabled": true,
  "config": {
    "apiKey": "sk-xxx",
    "baseUrl": "https://api.openai.com/v1",
    "timeoutSeconds": 300,
    "maxRetries": 2
  }
}
```

`providerType` 决定配置 DTO、Adapter 和既有模型的协议归属，创建后保持不可变。如需更换类型，管理员应创建新的提供商实例并重新配置模型和路由。

详情响应直接返回完整 `config`。列表响应可以返回完整配置或只返回连接摘要；为减少列表载荷，建议列表不返回 `config`，详情返回完整配置。这不是密钥保护语义，只是列表 DTO 边界。

不提供 `DELETE` 接口。启停通过 `PUT` 全量编辑完成，不额外增加 action endpoint。

### 14.4 配置模型

```http
GET  /api/admin/ai/providers/{providerInstanceId}/models
POST /api/admin/ai/providers/{providerInstanceId}/models
GET  /api/admin/ai/models/{modelId}
PUT  /api/admin/ai/models/{modelId}
```

请求：

```json
{
  "displayName": "5.6 Sol",
  "modelId": "gpt-5.6-sol",
  "enabled": true
}
```

不提供模型 `DELETE` 接口。

### 14.5 模型路由场景目录

```http
GET /api/admin/ai/model-routing/scenarios
```

响应示例：

```json
{
  "items": [
    {
      "scenarioCode": "practice-chat",
      "categoryCode": "PRACTICE",
      "displayName": "题目训练聊天",
      "description": "题目训练聊天 Agent 使用的模型路由。",
      "policyTypeCode": "ai.model-route.practice-chat.v1",
      "configured": true,
      "enabledPolicyCount": 2,
      "totalPolicyCount": 3
    },
    {
      "scenarioCode": "topic-explanation",
      "categoryCode": "CONVERSATION",
      "displayName": "主题讲解",
      "description": "主题讲解使用的模型路由。",
      "policyTypeCode": "ai.model-route.topic-explanation.v1",
      "configured": false,
      "enabledPolicyCount": 0,
      "totalPolicyCount": 0
    }
  ]
}
```

### 14.6 路由规则 CRUD

路由规则底层继续使用现有通用策略管理 API 和 `GenericPolicyManagementService`：

- 创建；
- 编辑；
- 启用、禁用；
- 逻辑删除；
- 排序；
- 用户、用户组和全部用户范围。

模型路由页面只负责将场景 code 映射为注册的 policy typeCode，并使用模型选择器生成：

```json
{
  "modelId": 101
}
```

后端不得信任前端提交未注册的模型路由 typeCode。

### 14.7 有效命中模拟

```http
GET /api/admin/ai/model-routing/scenarios/{scenarioCode}/effective?userId=123
```

命中响应：

```json
{
  "scenarioCode": "practice-chat",
  "configured": true,
  "matched": true,
  "policyId": 10,
  "policyVersion": 3,
  "priority": 10,
  "matchSource": "GROUP",
  "matchedSubjectId": 8,
  "model": {
    "id": 101,
    "displayName": "5.6 Sol",
    "modelId": "gpt-5.6-sol",
    "enabled": true,
    "providerInstanceId": 1,
    "providerInstanceName": "OpenAI 主实例",
    "providerType": "openai",
    "providerEnabled": true
  }
}
```

未命中时返回成功的诊断响应：

```json
{
  "scenarioCode": "practice-chat",
  "configured": true,
  "matched": false,
  "reason": "AI_MODEL_ROUTE_NOT_CONFIGURED"
}
```

模拟接口不消耗额度、不获取运行锁、不创建 Client、不发送模型请求。

## 15. 管理端页面

### 15.1 信息架构

复用现有 `/admin/ai` 工作区，增加局部页签：

```text
运行设置
提供商与模型
模型路由
模型价格
用量
```

不为每个 provider type 或业务场景创建单独前端路由。

### 15.2 提供商与模型

提供商列表展示：

- 名称；
- provider 类型；
- Base URL；
- 启停状态；
- 模型数量；
- 更新时间。

提供商详情使用紧凑表单编辑完整配置，并在同一工作区下方展示模型表格：

```text
模型展示名称
上游 model_id
状态
更新时间
```

API Key 输入框显示完整当前值并允许直接编辑，符合已定明文管理语义。

模型新增和编辑不提供 capability、参数、价格或测试连接按钮。

### 15.3 模型路由

左侧或主表展示全部代码注册场景：

```text
普通导师会话                已配置 2 条
主题讲解                    未配置
题目训练聊天                已配置 2 条
学习计划草案                已配置 1 条
学习计划修订                未配置
学习计划扩展                未配置
练习代码 Review             已配置 1 条
学习者自述画像更新           已配置 1 条
Code Review 画像更新         已配置 1 条
```

场景详情复用通用策略规则表，展示：

- 规则名称；
- 优先级；
- 范围类型和主体摘要；
- 目标模型；
- provider 实例；
- 规则状态；
- 目标模型和实例可用状态；
- 更新时间。

新建和编辑规则使用：

- 模型下拉选择器；
- 用户、用户组和全部用户范围选择器；
- 数字优先级输入；
- 启用状态。

模型下拉展示全部模型，并同时展示模型及所属实例的启停状态。停用目标可以用于提前编排，但必须标记为不可用；已启用规则指向不可用目标时，页面明确提示该规则运行时会返回 `AI_MODEL_UNAVAILABLE`。

### 15.4 用户命中模拟

场景详情提供 userId 或用户搜索选择器，调用有效命中模拟接口，展示：

- 是否命中；
- 命中规则；
- 命中来源；
- 目标模型；
- provider 实例；
- 不可用原因。

该能力只用于管理员诊断，不进入普通用户页面。

## 16. 错误码与 HTTP 语义

建议新增稳定错误码：

| 错误码 | 场景 | 建议 HTTP 状态 |
| --- | --- | ---: |
| `AI_MODEL_ROUTE_NOT_CONFIGURED` | 当前用户在场景中未命中规则 | `503` |
| `AI_MODEL_UNAVAILABLE` | 模型或实例不存在、已停用 | `503` |
| `AI_PROVIDER_TYPE_NOT_SUPPORTED` | 数据库 provider type 没有代码适配器 | `503` |
| `AI_PROVIDER_CONFIG_INVALID` | config 结构或字段非法 | `503`；管理员写接口使用 `400` |
| `AI_PROVIDER_NAME_ALREADY_EXISTS` | 实例名称冲突 | `409` |
| `AI_MODEL_ALREADY_EXISTS` | 同实例 `model_id` 冲突 | `409` |
| `AI_PROVIDER_NOT_FOUND` | 管理查询实例不存在 | `404` |
| `AI_MODEL_NOT_FOUND` | 管理查询模型不存在 | `404` |

面向普通用户的消息保持简洁，不暴露 provider config、实例名称或内部规则信息。管理员 API 可以返回低敏实例和模型标识用于排查。

## 17. 可观测性与调用台账

### 17.1 受信 metadata

新增受信 metadata key：

```text
aiScenarioCode
aiModelRoutePolicyId
aiModelRoutePolicyVersion
aiModelRouteMatchSource
aiModelRouteMatchedSubjectId
aiConfiguredModelId
aiProviderInstanceId
aiProviderType
aiUpstreamModelId
aiProviderConfigRevision
```

这些 key 应集中定义，并在 Agent、直接 completion、调用台账和 trace snapshot 中使用一致名称。

### 17.2 日志

允许记录：

- provider type；
- provider instance id；
- configured model id；
- upstream model id；
- business scenario；
- route policy id/version；
- 耗时、Token、错误码和 retryable。

禁止记录：

- provider `config`；
- API Key；
- Authorization；
- 完整请求头；
- 通过异常 cause message 间接暴露的密钥或连接参数。

现有 `SdkOpenAiResponsesClient` 日志中的 exception message 需要复核，避免 SDK 异常回显敏感连接信息。

### 17.3 指标

建议增加低基数指标：

```text
ai_model_route_resolutions_total{scenario,result}
ai_model_route_resolution_seconds{scenario}
ai_provider_client_creations_total{provider_type,result}
ai_provider_calls_active{provider_type}
ai_provider_calls_total{provider_type,status}
```

不得把 provider instance name、model id、userId、policyId 或 Base URL 作为 Micrometer tag，避免高基数和敏感信息扩散。具体实例和模型维度通过调用台账查询。

### 17.4 调用台账开始时点

调用台账在真实 provider dispatch 前写入：

- `provider_instance_id`；
- `ai_model_id`；
- `provider`；
- `model`；
- `scenario/source`；
- `routePolicyId` 可保存在受信 metadata 或后续扩展列。

路由解析失败不会写真实 provider call usage，但可以继续通过 admission rejection 或专用 route resolution metric 观测。

## 18. 配置迁移与切换

### 18.1 不自动迁移

Flyway 无法安全读取运行环境中的 `OPENAI_API_KEY` 并写入数据库。本期也不建设启动时自动导入器。因此：

- 不从 `application.yml` 或环境变量自动创建实例；
- 不自动创建模型；
- 不自动创建路由策略；
- 数据库初始为空是合法状态；
- 管理页面会把全部业务场景显示为未配置；
- AI 调用在配置完成前返回明确错误。

### 18.2 人工配置顺序

发布后管理员按以下顺序配置：

```text
1. 创建 OpenAI 提供商实例
2. 在实例下创建所需模型
3. 为业务场景创建模型路由规则
4. 使用 userId 模拟接口验证 Pro、普通用户和后台场景命中
5. 发起最小真实业务调用验证
```

### 18.3 文件配置退役

完成动态链路切换后：

- `algo-mentor.ai.openai.*` 不再参与主运行链路；
- `algo-mentor.ai.gateway.default-provider/default-model` 不再参与主运行链路；
- `.env.example` 移除或标记对应变量已废弃；
- `OpenAiLlmProperties`、`LlmGatewayProperties` 和相关自动配置按实际依赖分批删除；
- 不保留“数据库失败时回退文件配置”的隐藏分支。

### 18.4 AI Debug

`AI_DEBUG` 和现有 debug 页面、API、source 枚举值由后续独立任务删除。本设计实施时：

- 不注册 debug 业务场景；
- 不提供 debug 模型路由；
- 不把 debug 代码迁入新 provider 管理链路；
- 若旧代码暂时仍编译存在，必须继续走旧隔离路径或同步删除，不能影响新场景目录完整性。

## 19. 安全边界

本设计明确接受 API Key 明文存储和管理员直接读取，不建设额外密钥保护流程。但仍保留以下系统边界：

- 所有 provider 管理 API 继续受 `/api/admin/**` 和管理员权限保护；
- 普通用户 API 不返回 provider instance、config 或 API Key；
- 日志和可观测数据不包含 API Key；
- Agent prompt、tool result、trace 和 SSE 不包含 provider config；
- config JSON 只能由注册 adapter 转换为固定 Java DTO，JSON 不能指定类名；
- provider type 必须来自代码 adapter registry；
- Base URL 不作为普通用户可控输入；
- 管理员修改配置不进入用户业务事务，也不在数据库事务中调用外部模型。

数据库账号、备份、管理员 Session 和基础设施访问控制仍承担明文密钥保护责任，但不属于本期新增实现范围。

## 20. 测试设计

### 20.1 数据模型和 repository

- 创建多个同类型 provider 实例；
- provider name 唯一约束；
- provider type 创建后不可修改；
- 同一实例下 `model_id` 唯一；
- 不同实例允许相同 `model_id`；
- config 必须是 JSON object；
- provider/model 启停读取；
- provider 不提供删除路径；
- usage 新增 ID 字段读写和历史 `NULL` 兼容。

### 20.2 Adapter 和 Client Registry

- adapter type 重复注册启动失败；
- 未知 provider type 返回稳定错误；
- OpenAI config 强类型转换和边界校验；
- 相同 `providerInstanceId + updatedAt` 复用 Client；
- `updatedAt` 改变创建新 Client；
- 运行中 holder 继续可用；
- 同步和流式请求使用同一个 fake Client；
- Client 创建失败不污染 registry 当前 holder。

### 20.3 业务场景注册

- 场景 code 唯一；
- 场景 descriptor 完整；
- 所有 active 系统提示词 definition 引用已注册场景；
- 同一场景允许多个提示词主版本；
- 模型路由类型与场景一一对应；
- 场景目录不包含 `AI_DEBUG`；
- `TOPIC_EXPLANATION` 存在受管理系统提示词定义。

### 20.4 模型路由

- 全部用户规则命中；
- 指定用户规则命中；
- 用户组规则命中；
- 用户同时命中多条规则时按显式优先级选择；
- 不实施用户范围隐式权重；
- 无规则返回 `AI_MODEL_ROUTE_NOT_CONFIGURED`；
- 场景只有 Pro 规则时普通用户未命中；
- 规则引用停用模型返回 `AI_MODEL_UNAVAILABLE`，不继续低优先级匹配；
- provider 停用返回 `AI_MODEL_UNAVAILABLE`；
- 模拟接口不创建 Client、不扣额度。

### 20.5 执行快照

- Agent 多 step 使用同一个模型快照；
- 运行中修改路由不改变当前 run；
- 运行中修改 provider config 不改变当前 Client；
- 下一次执行使用新模型或新 provider Client；
- Practice Chat 内的 Code Review 子调用按独立场景重新解析；
- 后台画像任务按自身场景解析；
- 快照 metadata 不含 config 和 API Key。

### 20.6 AI 治理和台账

- 路由失败不消耗每日额度；
- 路由失败不遗留用户运行锁；
- provider dispatch 台账写入 providerInstanceId 和 aiModelId；
- 成功、失败、取消和流式终态均保留 provider/model 文本快照；
- 现有价格查询继续按 provider type/upstream model 计算；
- 老台账 `NULL` 配置 ID 能正常展示。

### 20.7 前端

- provider type 目录加载；
- provider 列表、创建、全量编辑和启停；
- 详情页显示完整 API Key；
- 实例下多模型 CRUD 和启停；
- 模型路由场景目录显示已配置/未配置；
- 新增后端场景无需修改前端白名单；
- 规则编辑支持用户、用户组、全部用户和优先级；
- 停用模型目标显示不可用；
- userId 模拟展示命中与未命中结果；
- 页面不出现 AI Debug 场景。

### 20.8 测试外部依赖边界

自动化测试不使用真实 OpenAI API Key。通过 fake adapter、fake client 和 PostgreSQL Testcontainers 验证完整路由与台账链路。真实 OpenAI 只作为人工发布验收，不进入常规 Maven 测试。

## 21. 实施阶段

### 阶段一：业务场景与数据模型

1. 新增 `AiBusinessScenario` 和集中 code/descriptor。
2. 让系统提示词 definition 引用统一场景。
3. 新增 `TOPIC_EXPLANATION` 受管理系统提示词。
4. 新增 provider/model Flyway 迁移、领域模型、MyBatis mapper 和 repository。
5. 扩展调用台账 ID 字段。

### 阶段二：Provider Adapter 与动态 Gateway

1. 增加 adapter/client/instance spec 核心契约。
2. 将 OpenAI 单例 properties/provider 改造成 `OpenAiProviderAdapter`。
3. 实现 `ProviderClientRegistry` 和单 Client 复用。
4. 改造 gateway 以执行快照目标分发。
5. 保持 provider type/upstream model 的现有响应和价格语义。

### 阶段三：模型路由

1. 注册每个业务场景的模型路由通用策略类型。
2. 实现 `AiModelRouteResolver` 和不可变执行快照。
3. 将路由解析接入 AI admission、Agent run、direct completion 和 background completion。
4. 固化无兜底、停用和错误语义。
5. 增加 metadata、指标和台账关联。

### 阶段四：管理员 API 与前端

1. 提供 provider type、provider instance 和 model 管理 API。
2. 提供场景目录和 userId 有效命中模拟 API。
3. 复用通用策略 CRUD 管理路由规则。
4. 在 `/admin/ai` 增加“提供商与模型”和“模型路由”页签。
5. 展示未配置场景和不可用路由目标。

### 阶段五：切换与清理

1. 管理员创建 OpenAI 实例、模型和路由。
2. 执行 Pro、普通用户、后台任务命中验证。
3. 切换全部业务调用到动态 gateway。
4. 移除文件配置主链路和全局默认模型逻辑。
5. 更新 `.env.example`、`docs/code-index.md` 和运维说明。
6. AI Debug 删除继续由独立任务完成。

## 22. 风险与应对

### 22.1 明文 API Key 暴露

风险由产品决策接受。实现仍需保证不进入日志、trace、SSE 和普通用户 API。管理员权限、数据库权限和备份安全由现有基础设施承担。

### 22.2 无兜底导致业务不可用

这是已定语义。通过场景目录的未配置提示、userId 模拟接口和发布前检查降低误配概率，不增加运行时 fallback。

### 22.3 通用策略 JSON 引用悬空模型

路由内容无法建立数据库外键，因此 provider/model 不提供删除；停用记录继续保留，运行时返回稳定不可用错误。

### 22.4 provider 配置更新导致 Client 资源增长

Registry 每个实例只保留最新 holder，旧 holder 仅被运行中快照引用。配置更新频率低，旧连接在请求结束和 OkHttp idle 清理后释放，不维护无界版本列表。

### 22.5 Agent 线程池不覆盖所有调用

本期接受 direct/background 调用可能不受 Agent 100 线程上限约束，不新增 provider semaphore。通过现有调用级台账和 active 指标观察真实负载，达到实际瓶颈后再设计容量准入。

### 22.6 价格粒度不足

同一 provider type 和 upstream model 在多个实例间共享价格。如果未来不同实例价格不同，新增独立设计迁移到实例级或 `ai_model.id` 级价格，不在本期提前扩展。

### 22.7 系统提示词与场景目录不一致

通过启动校验和架构测试保证所有 active AI 场景都存在系统提示词 definition，所有 definition 都引用注册场景。`AI_DEBUG` 作为明确删除目标不进入校验集合。

## 23. 回滚

### 23.1 代码回滚

回滚到旧版本后，新 provider/model 表和 usage nullable 字段可以保留，不影响旧代码读取。旧版本继续使用原文件配置。

### 23.2 配置回滚

动态版本内的配置回滚通过管理员重新填写旧 provider config、重新启用旧模型或调整路由优先级完成。不建设配置版本恢复按钮。

### 23.3 数据库回滚

发布后不建议删除 provider/model 表或 usage 新字段。若必须数据库回滚，应先确认新版本调用台账和路由已停止写入，再通过单独人工脚本处理，不在应用自动执行 down migration。

## 24. 验收标准

- 管理员可以创建两个以上 `OPENAI` 实例，并为每个实例配置多个模型。
- 同一实例可以配置 5.6 Sol、5.6 Terra、5.5、5.4 等多个上游模型标识。
- provider config 明文保存在 JSONB，管理员详情可以读取和全量编辑完整 API Key。
- provider type 只在实例创建时选择，创建后不可切换。
- 管理员可以为业务场景配置用户、用户组和全部用户模型规则，并通过显式优先级控制命中顺序。
- 模型路由场景目录与系统提示词共用统一业务场景定义，前端不维护独立枚举。
- 场景目录不包含 `AI_DEBUG`，并包含新增的 `TOPIC_EXPLANATION`。
- 未配置场景在管理页面明确显示；运行时无匹配规则返回 `AI_MODEL_ROUTE_NOT_CONFIGURED`。
- 不存在全局默认模型、场景兜底模型、文件配置兜底和自动 provider 故障转移。
- 多 step Agent run 在执行期间固定使用启动时模型快照。
- 独立代码 Review 和画像任务按自己的业务场景单独解析模型。
- OpenAI 实例版本复用单个共享 `OpenAIClient`，同步和流式调用共享 OkHttp 连接池。
- 管理员修改 provider config 后无需重启，下一次执行使用新 Client；运行中执行不受影响。
- provider/model 停用后新执行返回稳定错误，已有执行继续完成。
- 调用台账记录 `provider_instance_id`、`ai_model_id`、provider type 和 upstream model id。
- 现有模型价格和成本查询继续工作。
- 自动化测试不需要真实 OpenAI Key，后端最小相关测试和前端核心交互测试全部通过。
