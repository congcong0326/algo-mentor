# 学习计划 AI 首次草案 Redis Stream 解耦第一阶段研发文档

## 1. 文档信息

- 状态：待实施
- 创建日期：2026-08-20
- 实施范围：仅学习计划 AI 首次草案创建
- 总体设计：docs/learning-plan-ai-generation-sse-resilience-design.md
- 实时通道基线：docs/design_for_decoupling_the_agent_real-time_message_channel.md
- Redis 载荷安全基线：docs/practice-chat-sse-completeness-and-payload-design.md

本阶段将 AI 草案创建从接口级 POST SSE 改为三段式协议：

~~~text
POST generation
  -> 返回 draftId
  -> Agent 在后台独立执行并写入 PostgreSQL 和 Redis Stream
GET events?after=cursor
  -> 从 Redis Stream 回放为 SSE
GET draft
  -> 从 PostgreSQL 读取权威状态、完整草案或受控错误
~~~

## 2. 目标与范围

### 2.1 目标

1. 浏览器断开、SSE 超时、发送失败和页面刷新不能取消首次草案生成。
2. SSE 不再传输完整 LearningPlanDraftResponse、完整计划 JSON、模型输出或工具原始结果。
3. 客户端在成功启动后立即得到稳定 draftId，并可使用该 ID 查询结果。
4. Redis Stream 支持按 after 游标的短期进度回放；Redis 故障只能降级实时体验。
5. PostgreSQL 中的草案状态、错误和 draftPlan 是唯一业务事实来源。
6. 同一用户的同一 Idempotency-Key 不重复创建草案或启动 Agent。

### 2.2 本阶段不做

1. 草案 AI 修订、正式计划扩展提案和扩展修订的 Redis Stream 改造。
2. 用户主动取消、cancellation handle registry、跨节点取消或恢复运行中的 Agent。
3. 通用 AI 任务中心、消费组、ACK、工作队列、WebSocket 或 Redis 可靠投递补偿。
4. Redis 中保存完整草案、完整用户输入、完整模型输出、Agent metadata 或工具原始结果。
5. ManagedSseConnection、心跳统一设施和跨业务 SSE 抽象。

## 3. 固定架构

### 3.1 职责边界

| 组件 | 职责 | 不承担的职责 |
| --- | --- | --- |
| PostgreSQL learning_plan_draft | 草案状态、结果、错误和幂等事实 | 实时事件回放 |
| 首次草案生成协调服务 | 启动、幂等、领域状态转换、Agent 订阅生命周期 | SseEmitter 生命周期 |
| 生成业务 Subscriber | 消费 Agent 事件、提交草案终态、尝试写 Redis Stream | 直接向浏览器发送 |
| Redis Stream | 每 draft 短期公开事件日志和 cursor 回放 | 任务可靠投递、业务事实、消费确认 |
| SSE GET Controller | 鉴权、after 校验、XREAD 回放、连接结束 | 启动或取消 Agent |
| 前端创建页 | 保存 pending draft、处理进度、终态后查询草案 | 根据 SSE 载荷拼装计划 |

### 3.2 运行链路

~~~text
Browser
  | POST /drafts/generations, Idempotency-Key
  v
LearningPlanDraftGenerationService
  | 校验、幂等、AI 准入和执行器接收
  | 创建 GENERATING draft
  | 202 { draftId, status, eventsUrl, initialAfter }
  v
Agent Runtime
  v
LearningPlanDraftGenerationSubscriber
  | 事务提交 GENERATED 或 GENERATION_FAILED
  | 尝试 XADD 公开事件
  v
Redis Stream per draft
  ^
  | XREAD / XREAD BLOCK, after cursor
SSE GET /drafts/{draftId}/events
  ^
  | 终态或回放失败后 GET /drafts/{draftId}
Browser
~~~

业务 Subscriber 必须独立于任何浏览器订阅存在。没有 SSE 客户端、SSE 读失败或 Redis 写失败均不得取消 Agent 的 Flow.Subscription。

### 3.3 执行器与准入前置条件

当前实现把草案首次写入延迟到 AgentRunStart，以避免 AI 准入或执行器拒绝时消耗每日草案额度。新协议必须保留这一业务语义：

1. 启动服务在返回 202 前确认本次 Agent 已通过准入且执行器已接收。
2. 只有成功接收的请求创建 GENERATING 草案并消耗草案额度。
3. 准入拒绝或执行器拒绝必须同步返回既有错误，不创建草案、不占用幂等键的成功资源、不写 Redis Stream。
4. 实现前先完成一个最小技术验证，确认 AgentRuntime 的 prepare 或 start 接口能够在请求线程观察到接收或拒绝；不能将拒绝吞成异步 Subscriber 的 onError。

若现有 AgentRuntime 无法提供该边界，先扩展其准备接口或在学习计划协调服务中引入明确的提交回执。不得为了尽快返回 draftId 而改变每日草案额度和容量拒绝语义。

## 4. API 契约

所有新路径、请求头、查询参数、SSE 事件名、错误码和 metadata key 必须统一定义在所属模块常量类或枚举中。

### 4.1 启动首次草案生成

~~~http
POST /api/learning-plans/drafts/generations
Idempotency-Key: client-generated-uuid
Content-Type: application/json
Accept: application/json
~~~

输入完整且 Agent 已成功接收时返回 202：

~~~json
{
  "success": true,
  "data": {
    "draftId": 102,
    "status": "GENERATING",
    "eventsUrl": "/api/learning-plans/drafts/102/events",
    "initialAfter": "0-0",
    "realtimeProtocolVersion": 1
  }
}
~~~

同时设置 Location 为草案查询 URL。输入缺失时不启动 Agent，直接创建 COLLECTING 草案并返回 201 和完整草案响应；该分支不创建 Redis Stream。

幂等规则：

1. 同用户、相同 Idempotency-Key、相同规范化请求体返回相同 draftId，不重新启动 Agent。
2. 同用户、相同 Idempotency-Key、不同请求体返回稳定冲突错误。
3. 已有草案为 GENERATING 时返回同一订阅信息；已终态时返回同一草案资源信息。
4. 幂等键的作用域是首次草案创建，前端明确点击重新生成才生成新 UUID。

### 4.2 查询草案

~~~http
GET /api/learning-plans/drafts/{draftId}
Accept: application/json
~~~

接口按当前用户过滤。GENERATING 状态不包含 draftPlan；GENERATED 状态返回完整 LearningPlanDraftResponse；GENERATION_FAILED 返回受控错误码和用户安全文案。不得将 provider 响应、异常 cause、完整模型输出或完整用户输入放入响应。

### 4.3 订阅事件

~~~http
GET /api/learning-plans/drafts/{draftId}/events?after={cursor}
Accept: text/event-stream
~~~

after 缺失等同 0-0，语义为严格返回大于 after 的事件。服务端步骤固定为：

1. 验证当前用户和 draftId 归属。
2. 校验 after 只能为 0-0 或 N-0 的连续公开事件游标。
3. 先 XREAD 回放已有 entry，再以配置的有限 block 时间执行 XREAD BLOCK。
4. 每次空读后查询草案状态；数据库已终态则发送缺失的最小终态，或正常关闭。
5. 读取到终态事件后正常关闭 SSE。

Redis 不可用、Stream 已过期、cursor 非法或发生解码错误时不得改写草案状态。前端据 HTTP 错误或无终态 EOF 回读草案。

### 4.4 SSE 公开事件

| 事件 | data 字段 | 语义 |
| --- | --- | --- |
| work_start | draftId, message | 开始生成 |
| work_progress | draftId, message | 低频阶段进度 |
| work_tool_start | draftId, toolName | 已白名单工具开始 |
| work_tool_end | draftId, toolName | 已白名单工具结束，不含工具结果 |
| draft_completed | draftId | 草案已提交到 PostgreSQL |
| draft_failed | draftId, code | 草案失败已提交到 PostgreSQL |

Redis entry 使用版本化 envelope：

~~~json
{
  "version": 1,
  "eventName": "draft_completed",
  "data": { "draftId": 102 }
}
~~~

只对实际公开事件分配连续显式 ID：1-0、2-0、3-0。被过滤的 Agent 事件不占 sequence；一次公开事件写入失败不复用 sequence，客户端检测到 ID gap 时必须回读 PostgreSQL。

## 5. 数据与状态

### 5.1 草案状态

第一阶段状态流转：

~~~text
输入缺失：COLLECTING
输入完整：GENERATING -> GENERATED
                         -> GENERATION_FAILED
~~~

GENERATION_CANCELLED 和显式取消接口不属于本阶段。已有 GENERATION_FAILED 状态继续使用；新增 GENERATING 表示 Agent 已被成功提交且正在执行。

### 5.2 生成元数据迁移

在 learning_plan_draft 增加并由领域模型显式承载：

| 字段 | 用途 |
| --- | --- |
| generation_request_key | 用户幂等键 |
| generation_request_fingerprint | 规范化请求 JSON 的 SHA-256 |
| generation_run_id | 本次 Agent run 的受控关联标识 |
| generation_error_code | 持久化稳定错误码 |
| generation_error_message | 用户安全错误文案 |
| generation_started_at | 业务开始时间 |
| generation_completed_at | 业务终态时间 |

为 user_id 和 generation_request_key 建立部分唯一索引，仅约束非空幂等键。创建、成功和失败转换必须使用条件更新，防止迟到事件覆盖已提交终态。

## 6. Redis Stream 设计

### 6.1 Key、配置和保留

每个草案使用独立 key：

~~~text
learning-plan:generation:{draftId}:events
~~~

新增独立配置前缀 algo-mentor.learning-plan.generation.realtime-stream。它沿用 Practice Chat 已验证的独立 Streams Redis 连接模型和参数类型：

1. enabled、host、port、database、username、password、sslEnabled。
2. commandTimeout、connectTimeout、shutdownTimeout、readBlock、maxReadConnections。
3. activeRetention、completedRetention。

默认值与 Practice Chat 的保守设置保持一致，实际值通过环境配置登记。学习计划不得复用缓存 Redis client，也不与 Practice Stream 共用 key 前缀。每个阻塞 XREAD 使用独立连接并受 maxReadConnections 限制，不能阻塞 XADD 写连接。

### 6.2 写入与失败边界

LearningPlanGenerationRealtimeEventStore 负责公开投影、序列号、序列化、XADD、TTL 和低基数指标。业务 Subscriber 只调用 append(draftId, domainEvent)：

1. 先映射为公开事件；未知事件和未知工具结果直接丢弃。
2. 仅对公开事件分配下一个 sequence。
3. 尝试使用 sequence-0 作为显式 Redis ID 写入版本化 envelope。
4. XADD 或 EXPIRE 失败时记录日志和指标，继续向 Agent 请求下一条事件。

终态必须先提交 PostgreSQL，再尝试写 draft_completed 或 draft_failed。Redis 成功不能作为草案成功条件，Redis 失败也不能让草案从 GENERATED 回退。

### 6.3 载荷安全

公开 mapper 是 Agent 运行态与浏览器之间唯一边界。禁止写入或返回：

1. Agent metadata、AI 准入、锁 token、provider、模型路由、prompt、trace 和 usage。
2. 用户 ID、完整请求、完整计划 JSON、完整模型输出、reasoning 和异常 cause。
3. 工具输入、工具原始结果以及未显式白名单的工具名。

工具名称是固定小集合；若当前计划生成工具只需要展示通用进度，则优先只发送 work_progress，避免向浏览器暴露工具拓扑。

## 7. 后端实施任务

### 7.1 领域与持久化

1. 新增 Flyway migration、行模型和 MyBatis 映射，补齐生成元数据与唯一索引。
2. 扩展 LearningPlanDraftStatus、LearningPlanDraft、repository 和 response mapper。
3. 新增按用户查询 draftId、幂等键查询、条件终态更新和启动失败收束端口。
4. 增加 GET draft Controller，并覆盖越权、过期和失败状态。

### 7.2 生成协调与业务 Subscriber

1. 从 LearningPlanDraftStreamService 提取首次生成协调服务，Controller 不再直接订阅到 SseEmitter。
2. 启动服务负责幂等、容量和准入边界、创建 GENERATING 草案、提交 Agent 与返回订阅 DTO。
3. 业务 Subscriber 负责保存最终草案或安全失败状态，并通过 realtime store 发布公开事件。
4. 删除学习计划首次创建中 clientDisconnected、timeout 或 emitter send failure 对 Agent subscription 的取消语义。
5. 旧 POST /drafts/stream 在迁移期保留，但不能复用新前端路径；切换完成后单独删除。

### 7.3 Redis 与 SSE 读取

1. 在 mentor-api 的 learningplan/realtime 包增加 protocol、cursor、event、payload mapper、event store 和 unavailable 实现。
2. 新增独立 properties、configuration、健康降级日志和对应 observability recorder。
3. Controller 的 events GET 只读取 Redis、写 SseEmitter 和检查数据库终态，不创建 Agent Subscriber。
4. 对终态草案允许在 Stream TTL 内访问 events，不能仅因草案不再 GENERATING 返回不存在。

## 8. 前端实施任务

1. 在 api.ts 和 types/api.ts 增加启动、草案查询和 cursor SSE 订阅类型，不再用 POST stream 读取首次草案。
2. LearningPlanCreatePage 在启动成功后保存 draftId、Idempotency-Key、lastEventId 到 sessionStorage 的 pending generation 条目。
3. 收到 draft_completed 或 draft_failed 后立刻 GET draftId；成功只以查询结果渲染预览。
4. SSE 连接异常、EOF 无终态、ID gap、非法 envelope 或 Redis 不可用时，先查询草案；仍为 GENERATING 时用最新 cursor 重连，持续不可用时退避轮询。
5. 刷新页面时发现 pending draftId，恢复观察或查询；终态后删除 pending 条目。
6. 浏览器卸载、返回列表、SSE abort 都不能调用取消 API，因为本阶段没有取消语义。

## 9. 测试门禁

### 9.1 后端单元与集成

1. 输入缺失继续同步创建 COLLECTING 草案，不触发 Agent 或 Redis。
2. 启动成功创建一个 GENERATING 草案，并返回 draftId、eventsUrl 和 initialAfter。
3. 执行器或准入拒绝不创建草案、不消耗额度、不写 Redis。
4. 相同幂等键并发请求只创建一个草案和一个 Agent run；请求体不同返回冲突。
5. 生成成功先提交 GENERATED 草案，再写最小 draft_completed。
6. 生成失败持久化 GENERATION_FAILED，再写最小 draft_failed。
7. Redis 写入、TTL 或读取失败不取消 Agent；最终草案仍可查询。
8. XREAD 严格遵守 after，SSE id 与 Redis entry ID 一致，终态 Stream 在保留期内可读。
9. 未公开 Agent event、metadata、完整计划、原始工具结果和异常 cause 不进入 Redis envelope。
10. 他人 draftId、非法 cursor、过期 Stream 和终态缺失 entry 均走受控响应或数据库回读。
11. 应用重启时遗留 GENERATING 草案按总体设计收敛，不尝试续跑。

### 9.2 前端

1. 启动后不再请求 POST /drafts/stream。
2. 正常收到 draft_completed 后只以 GET draftId 的完整草案渲染预览。
3. 断线后用最后成功 cursor 重连；刷新后从 pending draft 恢复。
4. 无终态 EOF、事件 ID gap、非法 JSON、Redis HTTP 错误和过期回放均回读数据库。
5. 生成仍在运行时保留生成状态；GENERATED 或 GENERATION_FAILED 时清理 pending 条目。
6. 模板创建、追问、确认和非首次修订交互保持原有协议。

### 9.3 最小验证命令

~~~bash
make backend-test
make frontend-test
~~~

实施过程中应优先执行相关 Maven 模块和指定 Vitest 测试；交付前记录实际命令、结果及未覆盖的 Redis 本地集成测试条件。

## 10. 发布与回滚

1. 先发布数据库迁移和后端新增接口，保留旧 POST /drafts/stream。
2. 配置独立 Streams Redis 后启用 learning-plan generation realtime 开关。
3. 发布前端，观察启动成功率、Redis append/read 失败率、SSE 终态完整率、数据库回读恢复次数和 GENERATION_FAILED 比例。
4. Redis 故障时关闭 realtime 开关：启动和数据库查询仍可完成，前端退避轮询；不回滚数据库状态机。
5. 若前端需要回退，旧 POST /drafts/stream 在兼容窗口内可继续使用；新 migration 只向前演进，不做破坏性回滚。

## 11. 完成标准

1. 浏览器断开后，Agent 继续执行，草案最终可通过 GET draftId 获取。
2. 正常 SSE 终态不含完整草案，前端查询 PostgreSQL 结果后展示预览。
3. Redis Stream 能从 0-0 或最近 cursor 回放公开进度，且 Redis 不成为业务成功条件。
4. 首次创建的幂等、额度、准入和执行器拒绝语义不弱于改造前。
5. Redis 载荷不泄漏完整计划、原始 Agent 事件或内部 metadata。
6. 本阶段不改变草案 AI 修订、正式计划扩展和用户取消行为。
