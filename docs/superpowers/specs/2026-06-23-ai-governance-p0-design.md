# AI Governance P0 底层能力设计

## 背景

`algo-mentor` 当前已经具备一组 AI 基础设施：

- `llm-core` 提供 provider-neutral LLM 抽象、流式事件、工具调用、结构化输出和统一错误模型。
- `llm-openai` 接入 OpenAI Responses API。
- `agent-core` 提供 Agent loop、工具执行、结构化最终输出、取消、运行锁扩展点和生命周期 observer。
- `agent-persistence-postgres` 提供会话、运行轨迹、请求快照、工具结果和 trace 持久化。
- `mentor-application` 与 `mentor-api` 已承载学习计划 Agent、题目工具、SSE 和业务 API。
- `auth` 已提供登录态、当前用户解析和角色模型。

下一阶段准备面向 5-20 个真实登录用户开放 AI 能力：学习计划生成、题目讲解、受限学习对话。当前缺口不在于再新增一个具体 AI 业务功能，而在于所有 AI 调用缺少统一上线治理层：用户归属、准入、配额、并发、审计、完整内容访问控制、指标和稳定错误映射还容易散落在 controller、application service、agent metadata 或 observer 中。

本设计从已确认的试用需求反推底层平台能力，目标是在不污染 `llm-core`、`agent-core` 和业务模块边界的前提下，新增一个独立 `ai-governance` 模块，作为所有真实用户 AI 调用的统一治理入口。

## 已确认决策

- 首批用户规模为 5-20 人，小范围真实试用。
- 首批开放能力包括学习计划生成、题目讲解、受限学习对话。
- 多轮 AI 对话只允许围绕算法学习、题目、学习计划和错题复盘，不开放泛聊天。
- 每个用户所有 AI 功能共享每日 50 次请求配额。
- 每个用户同一时间最多 1 个 active AI run。
- 完整 prompt/response 可以留存用于排查，但 API key、Authorization、OAuth token、数据库密码等凭据必须强制脱敏。
- 只有管理员可以查看完整 AI 会话和 trace 内容。
- 管理员读取完整 trace 需要写访问审计。
- 每日用量 P0 必须落 PostgreSQL，服务重启不丢计数。
- active run 并发控制 P0 先复用现有本地内存锁，不引入分布式锁或数据库锁。
- 新增一个 `backend/ai-governance` Maven 模块，内部按包分层，不在 P0 拆成多个 governance 子模块。

## 目标

- 为所有真实用户 AI 调用提供统一的强类型运行上下文。
- 支持按 AI 场景解析策略，例如默认模型、输出 token 上限、最大 step、是否允许工具、是否允许流式。
- 在调用 LLM 或 Agent 前完成准入判断：认证、角色、功能开关、配额、并发、请求大小和 purpose 合法性。
- 使用 PostgreSQL 持久化每日用量、run admission 记录和 trace 访问审计。
- 将完整内容留存与完整内容读取分开治理：可以写入脱敏后的完整 trace，但读取完整内容需要管理员权限并写审计。
- 通过 observer 或等价生命周期扩展统一记录指标、usage、失败、取消和 active run 释放。
- 为 API 层提供稳定错误码，避免 provider、agent、配额和权限错误以不一致格式泄露到前端。
- 保持现有 `llm-core`、`agent-core`、`agent-persistence-postgres` 的职责边界清晰。

## 非目标

- 不实现精确美元成本账单。
- 不实现多 provider 自动降级。
- 不实现 prompt 管理后台。
- 不实现用户自助删除或导出 AI 数据。
- 不实现复杂 RBAC；P0 只依赖现有 `USER`、`ADMIN` 角色。
- 不实现 embedding、向量检索或 RAG。
- 不实现离线 eval 平台。
- 不把业务 prompt、学习计划 DTO、题目讲解 DTO 放入 `ai-governance`。

## 模块边界

新增模块：

```text
backend/ai-governance
```

包名使用：

```text
org.congcong.algomentor.ai.governance.*
```

建议内部包：

- `model`：强类型运行上下文、actor、purpose、source、状态和错误码。
- `policy`：场景策略、配置属性、策略解析器。
- `admission`：准入服务、准入结果、失败原因。
- `usage`：每日 AI 用量接口与 PostgreSQL 实现。
- `runlock`：active run 锁适配，P0 复用 `agent-core` 现有本地内存锁实现。
- `audit`：trace 访问策略和访问审计。
- `metrics`：AI run 指标 observer。
- `repository.mybatis`：MyBatis mapper、row model 和 XML。
- `autoconfigure`：Spring Boot 自动装配。

依赖方向：

- `ai-governance` 可以依赖 `llm-core`，用于 usage、provider/model、LLM 错误语义。
- `ai-governance` 可以依赖 `agent-core`，用于 Agent 生命周期 observer 和 run metadata 接入。
- `ai-governance` 可以依赖 `auth` 的用户身份与角色契约。
- `ai-governance` 可以依赖 Spring、MyBatis、Micrometer、Jackson 和 PostgreSQL 驱动。
- `ai-governance` 不依赖 `llm-openai`。
- `ai-governance` 不依赖 `mentor-api` controller。
- `ai-governance` 不依赖 `mentor-application` 具体业务模型。

现有模块职责保持：

- `llm-core`：provider-neutral LLM 抽象，不感知用户、配额和管理员权限。
- `llm-openai`：OpenAI provider 适配，不感知业务治理。
- `agent-core`：Agent loop、工具执行、结构化输出和生命周期，不持有真实用户授权逻辑。
- `agent-persistence-postgres`：会话、运行轨迹、请求快照、工具结果和上下文快照。
- `mentor-api`：HTTP/SSE 适配、认证上下文解析、业务入口接入 governance。
- `mentor-application`：学习计划、题目讲解、学习对话等业务编排。

## 核心模型

### `AiPurpose`

P0 支持三个 purpose：

```text
LEARNING_PLAN          学习计划生成
PROBLEM_EXPLANATION    题目讲解
LEARNING_CHAT          受限学习对话
```

purpose 是治理层的稳定契约，用于策略解析、配额记录、指标标签、审计和错误排查。它不等同于业务 intent。例如学习计划内部的 `INTERVIEW_SPRINT`、`TOPIC_BREAKTHROUGH` 仍属于 `LEARNING_PLAN` purpose。

### `AiRunSource`

P0 建议支持：

```text
LEARNING_PLAN_DRAFT
PROBLEM_DETAIL
LEARNING_CHAT
AI_DEBUG
```

source 描述触发入口，粒度比 purpose 更接近产品入口。它主要用于观测、审计、排查、默认文案和后续细粒度策略，不用于替代业务路由。

例如 `PROBLEM_EXPLANATION` purpose 可能同时来自题目详情页、错题复盘页或学习计划阶段页。purpose 决定治理大类，source 帮助回答“这次调用从哪里触发”。P0 不需要为每个 source 配独立配额，但指标和 admission 记录应保留 source，方便后续判断哪个入口消耗最多或错误最多。

`AI_DEBUG` 只能本地或管理员使用，不面向普通用户开放。

### `AiActor`

表达当前调用者：

- `userId`
- `roles`
- `authenticated`
- `admin`

`AiActor` 必须由后端认证上下文构造，禁止从客户端请求体读取。controller 可以接收业务参数，但不能接收 AI 调用使用的 `userId`。

### `AiRunContext`

每次 AI run 的强类型上下文：

- `runId`：全局唯一 run 标识，建议 UUID。
- `actor`：当前用户身份。
- `purpose`：AI 场景。
- `source`：触发入口。
- `idempotencyKey`：可选幂等键。
- `requestSize`：治理层可见的请求大小估算。
- `streaming`：是否为流式调用。
- `metadata`：非敏感扩展字段。
- `createdAt`。

`AiRunContext` 是 `ai-governance` 的主输入，后续会被转换为 Agent/LLM metadata，但业务代码不应直接拼散落的 governance metadata。

### `AiRunStatus`

P0 状态：

```text
ADMITTED
REJECTED_QUOTA
REJECTED_CONCURRENT
REJECTED_DISABLED
REJECTED_UNAUTHENTICATED
REJECTED_FORBIDDEN
REJECTED_REQUEST_TOO_LARGE
RUNNING
COMPLETED
FAILED
CANCELLED
EXPIRED
```

`ADMITTED` 表示通过准入检查。`RUNNING` 表示已占用 active run 锁并进入真实执行。`EXPIRED` 用于 TTL 兜底释放后的状态。

## Purpose 策略

`AiPurposePolicy` 建议包含：

- `enabled`
- `dailyRequestLimit`
- `maxConcurrentRunsPerUser`
- `maxRequestBytes`
- `maxOutputTokens`
- `maxSteps`
- `streamingAllowed`
- `toolsAllowed`
- `structuredOutputRequired`
- `adminOnly`
- `defaultProvider`
- `defaultModel`
- `systemPolicyVersion`

P0 默认策略：

| Purpose | 每日共享配额 | 并发 | 流式 | 工具 | 结构化输出 |
| --- | --- | --- | --- | --- | --- |
| `LEARNING_PLAN` | 共享 50 | 每用户 1 | 可选 | 允许题库工具 | 需要 |
| `PROBLEM_EXPLANATION` | 共享 50 | 每用户 1 | 允许 | 允许题库工具 | 不强制 |
| `LEARNING_CHAT` | 共享 50 | 每用户 1 | 允许 | 可按入口限制 | 不强制 |

每日 50 次是用户维度所有 AI 功能共享总量，不按 purpose 各给 50 次。表结构预留 purpose 字段，后续可以切换为按 purpose 或混合策略。

## 准入流程

业务入口接入治理层的标准流程：

```text
Controller/Application 解析认证上下文
  -> 构造 AiActor
  -> 构造 AiRunContext
  -> AiRunAdmissionService.admit(context)
  -> 准入失败：返回稳定 API 错误
  -> 准入成功：获得 AiRunAdmission
  -> 将 admission metadata 注入 AgentRequest 或 LlmCompletionRequest
  -> 调用 AgentLoopRunner 或 LlmGateway
  -> lifecycle observer 记录 usage、释放 active run、更新状态、上报指标
```

`AiRunAdmissionService` 应按固定顺序检查：

1. purpose 是否存在。
2. purpose 是否启用。
3. actor 是否已认证。
4. actor 是否满足管理员或普通用户权限。
5. 请求大小是否超过 purpose 策略。
6. 每日共享请求配额是否可用。
7. active run 锁是否可获得。
8. 记录 admission 成功，返回可用于后续释放和结算的 token。

配额扣减采用“准入成功即计一次请求”的语义。原因是模型调用可能在流式阶段被用户取消，也已经消耗系统资源和外部 provider 机会。后续如果要区分 provider 未触达的失败，可以增加 refund 规则，但 P0 不做。

## PostgreSQL 持久化

迁移脚本放在：

```text
backend/ai-governance/src/main/resources/db/migration/ai
```

项目 Flyway 递归扫描 `classpath:db/migration`，新增版本号需要与所有模块全局唯一。

### `ai_run_admissions`

记录每次 AI run 的治理状态。

建议字段：

- `id`
- `run_id`
- `user_id`
- `purpose`
- `source`
- `status`
- `idempotency_key`
- `request_size`
- `rejection_code`
- `error_code`
- `provider`
- `model`
- `input_tokens`
- `output_tokens`
- `cached_tokens`
- `reasoning_tokens`
- `total_tokens`
- `started_at`
- `completed_at`
- `created_at`
- `updated_at`

唯一约束：

- `run_id`

索引：

- `(user_id, created_at)`
- `(purpose, created_at)`
- `(status, created_at)`

### `ai_daily_usage`

记录每日共享 AI 用量。P0 先按请求次数限制，后续应能扩展为 token 维度限制。

按请求次数限制的优点是实现简单、用户容易理解，也足以支撑 5-20 人试用。token 限制更接近真实成本，但需要稳定拿到 usage、处理流式取消、provider 未返回 usage、tool 多轮调用和跨 provider token 口径差异。P0 表结构应预留 token 字段，治理策略可以先只启用 `request_count` 上限。

建议字段：

- `id`
- `user_id`
- `quota_date`
- `scope`
- `request_count`
- `input_tokens`
- `output_tokens`
- `cached_tokens`
- `reasoning_tokens`
- `total_tokens`
- `limit_count`
- `token_limit`
- `created_at`
- `updated_at`

P0 `scope` 固定为 `ALL`。后续可扩展为 purpose 名称。

唯一约束：

- `(user_id, quota_date, scope)`

准入时在事务内原子递增 `request_count`。超过 `limit_count` 时拒绝，不创建 active run 锁。run 完成后再把 LLM usage 累加到 token 字段。后续如果要改为 token limit，可以使用 `total_tokens` 和 `token_limit` 实施软硬限制。

### Active run 锁

P0 不新增 `ai_active_run_locks` 表，不引入分布式锁。当前代码库已有 `agent-core` 的 `AgentRunLockManager` 和 `InMemoryAgentRunLockManager`，`mentor-api` 默认也在使用本地内存锁。AI Governance 的 active run 并发控制应先复用这套能力。

建议锁 key：

- `user:{userId}:ai:all`

语义：

- P0 `purpose_scope` 固定为 `ALL`，表示每用户所有 AI 功能最多一个 active run。
- 准入时通过 `AgentRunLockManager.tryAcquire(...)` 获取锁。
- 准入返回的 lock token 写入 AgentRequest metadata。
- 终态 observer 在 `COMPLETED`、`FAILED`、`CANCELLED` 时释放锁。
- 本地内存锁只保证单实例并发控制；如果后续部署多实例，再设计 PostgreSQL 或 Redis 分布式锁。

`InMemoryAgentRunLockManager` 当前不会主动清理过期锁，因此 P0 要么在使用时避免依赖 TTL 自动清理，要么先增强现有内存锁的过期清理语义。这个改动属于 run lock 基础能力增强，不需要引入数据库锁。

### `ai_trace_access_audits`

记录管理员读取完整 trace 的访问审计。

这张表不是 AI run 执行路径的必需表，它只服务一个管理能力：当管理员通过后台、调试接口或运维工具查看完整 prompt/response、request snapshot、tool result 等敏感内容时，系统必须留下“谁在什么时候看了哪个 run，为什么看”的记录。

如果 P0 暂时没有管理后台或完整 trace 读取接口，可以先不落这张表；但一旦提供完整内容读取能力，就必须同步提供访问权限校验和审计写入。否则“完整内容可留存”会变成无访问记录的敏感数据暴露面。

建议字段：

- `id`
- `admin_user_id`
- `target_user_id`
- `run_id`
- `purpose`
- `reason`
- `created_at`

P0 `reason` 可以固定为 `DEBUG`，接口仍保留字段，便于后续要求管理员填写排查原因。

## Trace 与内容留存

现有 `agent-persistence-postgres` 继续负责 request snapshot、tool result、conversation message 和 run trace。`ai-governance` 不重复保存完整 prompt/response，但负责定义读取规则：

- 写入 trace 前必须执行脱敏，至少覆盖 API key、Authorization、OAuth token、Session/Cookie、数据库密码、OpenAI key、JWT、常见 secret 字段。
- 脱敏策略必须有版本号，写入 trace 时记录。
- 管理员读取完整 trace 前必须调用 `AiTraceAccessPolicy`。
- 非管理员不能读取完整 trace，即使读取自己的 trace 也不在 P0 开放。
- 每次读取完整 trace 必须写 `ai_trace_access_audits`。

如果现有 `AgentTraceRedactor` 只覆盖部分字段，P0 实施时应扩充或在 `ai-governance` 中提供统一 redaction policy，供 trace observer 复用。

## Metrics 与生命周期事件

`ai-governance` 应提供 `AiRunObserver` 或复用 `AgentLoopObserver` 适配，统一处理：

- run admitted
- run rejected
- run started
- llm usage received
- run completed
- run failed
- run cancelled
- active lock released

Micrometer 指标建议：

- `ai.run.requests`：按 `purpose`、`source`、`status`、`provider`、`model` 标记。
- `ai.run.duration`：run 总耗时。
- `ai.run.errors`：按 `error_code`、`purpose` 标记。
- `ai.run.tokens`：input、output、cached、reasoning、total token。
- `ai.run.active`：当前 active run 数。
- `ai.run.rejections`：按 rejection code 标记。
- `ai.tool.calls`：工具调用次数。
- `ai.tool.errors`：工具失败次数。
- `ai.sse.cancelled`：SSE 取消次数。

日志只记录 runId、userId、purpose、source、provider、model、错误码、耗时和 token usage，不直接输出完整 prompt、完整 response、Authorization 或密钥。

## API 错误映射

治理层提供稳定错误码，供 `mentor-api` 映射为统一 `ApiResponse`。

建议错误码：

```text
AI_PROVIDER_DISABLED
AI_PURPOSE_DISABLED
AI_UNAUTHENTICATED
AI_FORBIDDEN
AI_QUOTA_EXCEEDED
AI_CONCURRENT_RUN_CONFLICT
AI_REQUEST_TOO_LARGE
AI_TIMEOUT
AI_RATE_LIMITED
AI_PROVIDER_UNAVAILABLE
AI_STRUCTURED_OUTPUT_INVALID
AI_CANCELLED
AI_UNKNOWN
```

P0 用户可见文案应简短明确，例如：

- 配额耗尽：`今日 AI 使用次数已达上限，请明天再试。`
- 并发冲突：`已有一个 AI 任务正在运行，请等待完成后再试。`
- provider 未配置：`AI 服务暂不可用，请稍后再试。`
- 超时：`AI 响应超时，请稍后重试。`

## 业务接入约束

所有 AI 入口必须遵守：

- controller 不接收用于 AI 治理的 `userId`。
- `userId` 只能来自认证上下文。
- 每个入口必须明确 `AiPurpose` 和 `AiRunSource`。
- 每个入口必须先调用准入服务，再启动 Agent/LLM。
- 准入返回的 runId、admissionId、quotaScope、lockToken 必须进入 AgentRequest metadata。
- Agent 终态 observer 必须能根据 metadata 更新 admission 状态和释放 active run。
- `AI_DEBUG` 入口默认只允许管理员或本地开发 profile。

## 受限学习对话底层能力

受限学习对话不在 governance 层实现完整业务逻辑，但 governance 需要为其提供底层约束：

- purpose 为 `LEARNING_CHAT`。
- policy 指定系统策略版本 `systemPolicyVersion`。
- policy 指定是否允许工具和允许工具集合。
- admission metadata 带上 purpose/source，便于 prompt 组装层选择对应 system prompt。
- 业务 prompt 必须要求非算法学习、题目、计划、错题复盘相关问题拒答并引导回学习场景。

`ai-governance` 只负责保存和传递这些策略，不直接拼接具体 prompt 文案。

## 测试策略

后端测试重点：

- `AiRunAdmissionService`：认证失败、管理员限定、purpose disabled、配额耗尽、并发冲突、请求过大、准入成功。
- `PostgresAiDailyUsageStore`：同一天共享 50 次、跨天重置、并发递增不越限、run 结束后累加 token usage。
- active run 锁：复用 `InMemoryAgentRunLockManager`，覆盖同用户 active run 冲突、终态释放；如增强 TTL，再覆盖过期清理。
- `AiTraceAccessPolicy`：非管理员拒绝、管理员允许。
- `AiTraceAccessAuditLogger`：管理员读取完整 trace 时写审计。
- `AiRunMetricsObserver`：成功、失败、取消、usage 指标被记录。
- API controller 接入测试：请求体传 `userId` 的旧入口迁移后不再信任客户端用户 ID。

验证命令通过根目录 `Makefile` 暴露，P0 至少运行：

```bash
make backend-test
```

必要时可运行相关模块 Maven 测试：

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance,mentor-api -am test
```

## 推进顺序

建议后续实施按以下顺序拆分：

1. 新建 `ai-governance` 模块与核心模型，不接入业务。
2. 落 PostgreSQL migration、daily usage store、admission repository。
3. 实现 `AiRunAdmissionService` 和策略配置。
4. 接入现有 `AgentRunLockManager`，实现用户维度 active run 准入和终态释放。
5. 接入 Agent lifecycle observer，完成状态更新、usage 记录和锁释放。
6. 增加 metrics observer。
7. 增加 trace access policy；如果提供完整 trace 读取接口，同步落访问审计。
8. 迁移 AI 调试会话接口，不再接收客户端 `userId`，并限制管理员访问。
9. 将学习计划、题目讲解、受限学习对话接入 governance。

## 风险与约束

- 准入成功即扣减请求次数，可能让用户取消流式任务后仍消耗一次配额；P0 接受该语义，用简单规则换取一致性。
- active run 锁 P0 是单实例内存锁，只适合当前小范围试用和单实例部署；多实例部署前必须升级为 PostgreSQL 或 Redis 分布式锁。
- 完整内容留存提高排查能力，也提高隐私风险；P0 必须先保证凭据脱敏、管理员访问控制和访问审计。
- Flyway 版本号在多模块共享同一版本空间，新增迁移前必须检查现有最大版本号。
- 指标标签不能包含 prompt、response、用户输入或高基数字段，避免泄露和指标爆炸。

## 验收标准

- 所有真实用户 AI 入口都能构造 `AiRunContext` 并通过 `AiRunAdmissionService` 准入。
- 普通用户无法通过请求体伪造其他用户身份发起 AI run。
- 单用户每日所有 AI 功能共享 50 次请求上限。
- 单用户同一时间最多 1 个 active AI run。
- 成功、失败、取消都会更新 admission 状态并释放 active run 锁。
- 管理员读取完整 trace 会写访问审计，普通用户不能读取完整 trace。
- 日志和 trace 脱敏策略不会输出 API key、Authorization、OAuth token、数据库密码等凭据。
- AI run 关键指标可通过 Micrometer 暴露。
- `llm-core` 和 `agent-core` 不新增用户、配额、管理员权限等业务治理语义。
