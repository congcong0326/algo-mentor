# 学习计划 AI 草案修订 Redis Stream 解耦第二阶段研发文档

## 1. 文档信息

- 状态：待实施
- 创建日期：2026-08-20
- 实施范围：学习计划草案预览页的“按要求调整计划”AI 修订
- 前置完成项：`docs/learning-plan-ai-generation-redis-stream-phase-one-implementation.md`
- 总体设计：`docs/learning-plan-ai-generation-sse-resilience-design.md`
- 修订业务基线：`docs/learning-plan-revision-plan-compiler-design.md`
- 修订访问控制：`docs/learning-plan-ai-revision-access-design.md`

阶段一已将首次 AI 草案创建切换为“启动 -> Redis Stream 观察 -> PostgreSQL 查询”。本阶段对草案修订使用同一交付模型，并以已经持久化的 `revisionId` 作为异步修订的稳定标识：

~~~text
POST revision generation
  -> 返回 revisionId
  -> Agent 在后台独立执行，提交 revision 和 draft
  -> 尝试写入 revision Redis Stream
GET revision events?after=cursor
  -> 从 Redis Stream 回放最小公开事件
GET revision / GET draft
  -> PostgreSQL 读取 revision 权威状态 / 已提交的完整草案
~~~

## 2. 目标与范围

### 2.1 目标

1. 点击“按要求调整计划”后，客户端立即获得稳定的 `revisionId`，刷新、断线和 SSE 超时不取消后台修订。
2. 修订执行、SSE 观察和结果读取彻底分离；浏览器连接、`SseEmitter` 和前端 AbortSignal 不再拥有 Agent `Flow.Subscription`。
3. `learning_plan_draft_revision` 是修订状态、冻结基线、提议内容和受控错误的唯一事实来源；`learning_plan_draft` 是最终预览草案的唯一事实来源。
4. Redis Stream 仅保存低敏、版本化、可回放的修订进度与终态通知，终态不再携带完整 `LearningPlanDraftResponse`、提案 JSON 或编译产物。
5. 同一用户、同一草案、同一 `Idempotency-Key` 不重复创建 revision 或启动 Agent；不同请求体复用同一键必须稳定失败。
6. 保持既有修订 Plan Compiler、冻结基线、访问策略、proposal group、revisionNo 和“新请求优先”的业务语义。
7. Redis 写入、读取、过期或 SSE 传输失败只能降级观察体验，不能改变修订或草案的业务终态。

### 2.2 本阶段不做

1. 不迁移已保存学习计划的扩展提案首次生成或扩展提案再次修订。
2. 不实现用户主动取消、cancellation handle registry、跨节点取消或应用重启后续跑修订任务。
3. 不改变 `LearningPlanDraftRevision` 的 Plan Compiler、Review Child、工具调用预算、结构化输出或 proposal group 业务规则。
4. 不建设通用 AI 任务中心、消费者组、ACK、任务队列、WebSocket、Redis 可靠投递或跨实例实时协调。
5. 不将完整用户修订要求、冻结计划、提议计划、Compiler/Review artifact、模型输出、工具输入输出、Agent metadata 或异常 cause 写入 Redis/SSE。
6. 不在本阶段删除旧 `POST /drafts/{draftId}/revisions/stream`；旧入口只作为兼容接口保留，不能被新前端使用。

## 3. 固定架构

### 3.1 职责边界

| 组件 | 职责 | 不承担的职责 |
| --- | --- | --- |
| PostgreSQL `learning_plan_draft_revision` | revision 状态、冻结基线、结果、幂等元数据与受控错误 | 实时事件回放 |
| PostgreSQL `learning_plan_draft` | 已完成 revision 写入后的权威完整预览草案 | 运行进度与事件日志 |
| 草案修订生成协调服务 | 参数与访问校验、幂等、revision 创建、Agent 准入、订阅激活 | `SseEmitter` 生命周期 |
| 修订业务 Subscriber | 消费 Agent 事件、执行既有 Compiler 终态收束、提交数据库、投影公开事件 | 直接向浏览器发送 |
| 学习计划 Redis Stream transport | `XADD`、`XREAD`、TTL、游标和连接资源 | 领域状态、载荷白名单和业务事实 |
| revision realtime mapper/store | revision 领域事件到公开事件的唯一安全边界 | 保存完整修订或草案 |
| SSE GET Controller | 鉴权、`after` 校验、回放、EOF 收束 | 启动、取消或订阅 Agent |
| 前端创建页 | 保存 pending revision、回放进度、终态后查询 revision/draft | 根据 SSE 载荷拼装草案 |

### 3.2 运行链路

~~~text
Browser
  | POST /drafts/{draftId}/revisions/generations, Idempotency-Key
  v
LearningPlanDraftRevisionGenerationService
  | 锁定 draft 与 idempotency key，校验访问策略和可修订性
  | 创建 GENERATING revision，准备并同步受理 Agent
  | 202 { revisionId, eventsUrl, initialAfter }
  v
Agent Runtime + 既有 Plan Compiler / Review Child
  v
LearningPlanDraftRevisionGenerationSubscriber
  | 原子提交 READY + draft，或 FAILED / SUPERSEDED
  | 事务提交后尽力 append 公共事件
  v
Redis Stream: learning-plan:revision:{revisionId}:events
  ^
  | XREAD / XREAD BLOCK, after cursor
GET /drafts/{draftId}/revisions/{revisionId}/events
  ^
  | 终态、EOF、Redis 失败或 cursor gap 后
GET /drafts/{draftId}/revisions/{revisionId}
GET /drafts/{draftId}
Browser
~~~

业务 Subscriber 必须在 Agent 执行器成功受理后独立存在。没有 SSE 客户端、前端卸载、连接超时、回放异常和 Redis append 失败均不得取消 Agent subscription。

### 3.3 Agent 准入与事务边界

`revisionId` 是 Agent invocation、Compiler artifact、Review Child 与 Redis key 的共同稳定标识，因此必须在启动请求的事务中先创建 revision。与此同时，不能因为执行器拒绝留下一个 `GENERATING` revision 或消耗幂等资源。

启动顺序固定如下：

1. 规范化 instruction，校验 `Idempotency-Key`，获得以 `userId + draftId + requestKey` 为范围的事务级互斥。
2. 若存在同键 revision，先比对请求指纹；相同则返回已有资源，不再调用 Agent；不同则返回幂等冲突。
3. 锁定 draft，重新执行 AI 修订访问策略与草案可修订性校验，复用既有 proposal group 和 revisionNo 分配规则，创建 `GENERATING` revision。
4. 以该 revision 构造既有 `AgentInvocation`，调用 `AgentRuntime.prepareStream(...)`；只有执行器同步接收后才注册业务 Subscriber。
5. Subscriber 在当前事务提交后才申请首个 demand；事务未提交则取消已注册 subscription。
6. 请求提交后返回 `202 Accepted`。`AgentRunStart`、Redis 公开事件和任何前端观察均不得发生在 revision 提交之前。

必须沿用阶段一已验证的 `prepareStream` 接收边界。禁止继续使用“调用 `stream(...).subscribe(SseSubscriber)` 后由浏览器连接决定是否受理”的旧模式。

## 4. API 契约

所有路径、请求头、查询参数、事件名、稳定错误码和 JSON 字段名必须定义在 `ApiContractConstants`、revision realtime protocol 常量类或已有领域常量类中；Controller、service、mapper、前端类型和测试不得散落字符串。

### 4.1 启动草案修订

~~~http
POST /api/learning-plans/drafts/{draftId}/revisions/generations
Idempotency-Key: client-generated-uuid
Content-Type: application/json
Accept: application/json

{
  "instruction": "把训练周期调整为 6 周，并增加图论复习"
}
~~~

Agent 已被成功受理时返回 `202 Accepted`，并设置 `Location: /api/learning-plans/drafts/{draftId}/revisions/{revisionId}`：

~~~json
{
  "success": true,
  "data": {
    "revisionId": 203,
    "proposalGroupId": 72,
    "draftId": 102,
    "revisionNo": 3,
    "status": "GENERATING",
    "eventsUrl": "/api/learning-plans/drafts/102/revisions/203/events",
    "initialAfter": "0-0",
    "realtimeProtocolVersion": 1
  }
}
~~~

规则：

1. `Idempotency-Key` 必填，去首尾空白后长度为 `1..128`；前端在一次提交及其网络重试中复用同一 UUID，明确再次点击才生成新 UUID。
2. instruction 继续复用既有空值、长度与输入安全校验；指纹基于服务端规范化后的请求 JSON 计算 SHA-256。
3. 同用户、同 draft、同 key、同指纹返回同一 revision 的控制面响应。无论该 revision 为 `GENERATING`、`READY`、`FAILED` 或 `SUPERSEDED`，都不得重新启动 Agent。
4. 同用户、同 draft、同 key、不同指纹返回 `LEARNING_PLAN_DRAFT_REVISION_IDEMPOTENCY_CONFLICT`。
5. 未通过修订灰度策略、草案不存在/已确认/缺少 draftPlan、输入无效、AI 准入拒绝或执行器拒绝必须同步返回既有受控错误；不创建 revision、不更新 proposal group、不占用成功幂等资源、不写 Redis。
6. 不同 key 的并发修订保持现有“后创建 revision 优先”规则。较早 revision 即使稍后完成，也只能收敛为 `SUPERSEDED` 或既有安全失败，不能覆盖最新草案。

### 4.2 查询 revision 状态

~~~http
GET /api/learning-plans/drafts/{draftId}/revisions/{revisionId}
Accept: application/json
~~~

返回的控制面 DTO 只包含 `revisionId`、`proposalGroupId`、`draftId`、`revisionNo`、`status`、`errorCode`、`errorMessage`、`startedAt`、`completedAt`。`errorCode/errorMessage` 只在 `FAILED` 或 `SUPERSEDED` 终态出现，均为用户安全内容。

该接口按当前用户和 URL 中的 `draftId` 同时过滤 revision，避免仅凭猜测 revisionId 跨草案读取。它不得返回 instruction、base/proposed brief、base/proposed plan、artifact、Agent run ID、内部 metadata 或异常 cause。

状态语义：

| status | 客户端动作 |
| --- | --- |
| `GENERATING` | 从最新 cursor 继续观察；观察持续不可用时退避查询本接口 |
| `READY` | 立即 `GET /drafts/{draftId}`，只以该响应更新预览 |
| `FAILED` | 展示受控错误，保留当前已展示的草案 |
| `SUPERSEDED` | 回读草案；若草案已由更晚 revision 更新则展示最新草案，否则展示“本次调整已被较新的请求取代” |
| `APPLIED`、`DISCARDED`、`EXPIRED` | 非本阶段启动结果；按现有业务语义只读返回，不重启任务 |

`READY` 不在此 DTO 中内嵌完整草案，避免再次形成“状态查询和最终资源交付混合”的协议。

### 4.3 revision 事件订阅

~~~http
GET /api/learning-plans/drafts/{draftId}/revisions/{revisionId}/events?after={cursor}
Accept: text/event-stream
~~~

`after` 缺失等同 `0-0`，只返回严格大于 `after` 的 entry。服务端步骤固定为：

1. 校验当前用户、draft 与 revision 三者归属；不存在或越权不泄漏任何资源信息。
2. 校验 after 只能是 `0-0` 或 `N-0` 的连续公开事件游标。
3. 先进行无阻塞 `XREAD` 回放，再用配置的有限 block 时间进行 `XREAD BLOCK`。
4. 每次空读后读取 revision 权威状态；数据库已进入终态则正常关闭 SSE。前端将无终态 EOF 视为必须查询 revision 的恢复信号。
5. 读取到 Redis 终态事件后立即正常关闭 SSE。

Redis 不可用、Stream 已过期、cursor 非法、envelope 解码失败或 HTTP 传输失败均不得改写 revision/draft 状态。前端先查询 revision；仍为 `GENERATING` 时使用最后成功 cursor 重连，持续不可用时退避轮询。

### 4.4 SSE 公开事件

| 事件 | data 字段 | 语义 |
| --- | --- | --- |
| `work_start` | `draftId`, `revisionId`, `message` | 修订开始 |
| `work_progress` | `draftId`, `revisionId`, `message` | 低频、固定文案的修订阶段进度 |
| `revision_completed` | `draftId`, `revisionId` | revision 与最新 draft 已经提交到 PostgreSQL |
| `revision_failed` | `draftId`, `revisionId`, `code` | revision 失败已提交到 PostgreSQL |
| `revision_superseded` | `draftId`, `revisionId`, `code` | 本次结果被更晚请求取代，未覆盖 draft |

Redis entry 继续使用阶段一的版本化 envelope：

~~~json
{
  "version": 1,
  "eventName": "revision_completed",
  "data": {
    "draftId": 102,
    "revisionId": 203
  }
}
~~~

仅实际公开事件分配连续显式 Redis ID：`1-0`、`2-0`、`3-0`。过滤事件不占 sequence；某次 `XADD` 或 `EXPIRE` 失败后不复用 sequence。前端检测到 ID gap、未知事件、额外字段或非法 envelope 时不得猜测结果，必须回读 revision。

修订不公开 `work_tool_start/work_tool_end`。Plan Compiler、Review Child 和工具拓扑不属于前端实时协议；页面只展示固定、低敏的工作进度。

### 4.5 旧 SSE 入口

`POST /api/learning-plans/drafts/{draftId}/revisions/stream` 在兼容窗口内保留原始完整草案 SSE 契约，供未升级前端使用。该入口不得调用阶段二新的启动服务，也不得产生第二条 revision。

前后端切换完成后，旧入口标记为废弃并在独立清理事项中删除。不能让旧入口和新入口共享无 `Idempotency-Key` 的启动实现，否则网络重试会再次调用模型。

## 5. 数据、状态与并发

### 5.1 revision 状态流转

~~~text
成功受理：GENERATING -> READY
                     -> FAILED
                     -> SUPERSEDED

READY -> SUPERSEDED  （更晚 revision 成功并替换当前 draft 时）
~~~

`READY`、`FAILED` 和 `SUPERSEDED` 是本阶段观察终态。`APPLIED`、`DISCARDED`、`EXPIRED` 沿用既有提案语义，不是本阶段 Agent 的直接落点。终态一经提交，不得被迟到的 Agent、Compiler 或 Redis 回放改变。

完成事务固定执行：锁定 draft 与 proposal group -> 验证当前 revision 仍可完成且不是过期请求 -> 保存 `READY` revision -> 将同 group 的其他 `READY` revision 标为 `SUPERSEDED` -> 更新 group latest proposal -> 更新 draft 完整预览 -> 提交事务 -> append `revision_completed`。只有这条路径可以更新 draft。

失败或过期事务固定执行：条件地将当前 `GENERATING` revision 改为 `FAILED` 或 `SUPERSEDED` -> 提交事务 -> append 对应终态。任何 Redis 成功都不能替代条件更新成功。

### 5.2 数据库迁移

新增一条 Flyway migration，向 `learning_plan_draft_revision` 增加以下字段：

| 字段 | 类型 | 用途 |
| --- | --- | --- |
| `generation_request_key` | `VARCHAR(128)` | 修订启动幂等键 |
| `generation_request_fingerprint` | `CHAR(64)` | 规范化修订请求 SHA-256 |
| `generation_run_id` | `VARCHAR(128)` | 受控 Agent run 关联标识 |
| `generation_started_at` | `TIMESTAMPTZ` | Agent 成功受理后的业务开始时间 |
| `generation_completed_at` | `TIMESTAMPTZ` | `READY`、`FAILED` 或 `SUPERSEDED` 提交时间 |

约束与索引：

~~~sql
CREATE UNIQUE INDEX uk_learning_plan_draft_revision_user_draft_generation_request_key
  ON learning_plan_draft_revision(user_id, draft_id, generation_request_key)
  WHERE generation_request_key IS NOT NULL;

CREATE INDEX idx_learning_plan_draft_revision_generation_status
  ON learning_plan_draft_revision(user_id, draft_id, status, generation_started_at)
  WHERE generation_request_key IS NOT NULL;
~~~

旧 revision 的新增字段均保持 `NULL`，不回填也不伪造历史幂等关系。Mapper row、领域模型、repository、JSON 映射与测试 fixture 必须显式承载新字段，避免 update 时丢失已有的 error、brief 或 plan snapshot。

### 5.3 Repository 原子操作

在 `LearningPlanProposalRepository` 与 MyBatis 实现增加以下端口，名称可按现有风格调整，但语义不能省略：

1. `lockDraftRevisionGenerationRequest(userId, draftId, requestKey)`：PostgreSQL 使用事务级 advisory lock，哈希输入必须同时包含 user、draft 与 key。
2. `findDraftRevisionByGenerationRequestKey(userId, draftId, requestKey)`：仅用于幂等复用与指纹冲突判断。
3. 按 `revisionId + userId + draftId` 查询和 `FOR UPDATE` 查询：用于控制面查询与终态事务。
4. `complete...IfGenerating`、`fail...IfGenerating`、`supersede...IfGenerating`：SQL `WHERE status = 'GENERATING'` 或在已持有行锁时等价保证，返回实际更新的 revision。
5. 启动恢复查询：查找超过最小年龄仍为 `GENERATING` 且拥有本阶段 `generation_request_key` 的 revision。

不得使用普通 `saveDraftRevision` 覆盖终态，也不得只通过内存 `AtomicBoolean` 判断竞争结果。内存标记只用于单个 Subscriber 的重复回调抑制，跨线程、跨请求与重启后的正确性必须由数据库状态和约束保证。

### 5.4 幂等与并发规则

1. 幂等范围是“同一用户对同一草案的一次修订启动”，不是全局 key，也不是 proposal group。
2. 同键并发请求由 advisory lock 串行化；唯一索引用于进程崩溃、实现遗漏或未来多节点场景下的最终约束。
3. 不同 key 可创建不同 revision，并维持既有顺序语义：revisionNo 更大的有效请求优先。先完成但已落后于更晚 revision 的请求不得修改 draft。
4. 已提交的 `READY` revision 在更晚 revision 成功时可被标为 `SUPERSEDED`；对应旧 Stream 不补发 retroactive 事件，客户端的 revision 查询结果是权威状态。
5. 同一 key 在 `FAILED`、`SUPERSEDED` 后重试仍返回原 revision；用户点击“再次调整”必须使用新 key 才创建新的 Agent run。

### 5.5 应用重启收敛

阶段一的启动恢复只处理 `learning_plan_draft.status = GENERATING`，本阶段必须新增独立 revision recovery，或将其扩展为按资源类型收敛。应用启动后，超过配置最小年龄且仍为 `GENERATING` 的 revision 必须条件更新为安全 `FAILED`，错误码使用 `LEARNING_PLAN_DRAFT_REVISION_INTERRUPTED`，然后尽力写 `revision_failed`。

该恢复不续跑 Agent、不重建 Compiler artifact、不覆盖 draft，也不依赖 Redis 可用。恢复与迟到回调竞争时，只有数据库条件更新胜出的一方可以发布终态事件。

## 6. Redis Stream 设计

### 6.1 共享 transport 与配置迁移

阶段一已验证学习计划独立 Streams Redis 连接。本阶段不得为 revision 再复制一套 Lettuce client、阻塞读取连接池和 metrics 实现，也不得复用业务缓存 Redis 或 Practice Chat Stream key。

实施时将阶段一的连接与 `XADD/XREAD` 通用能力提取为学习计划 realtime transport，generation 与 revision 各自保留独立的 protocol、payload mapper、event store 和严格 decode 校验。transport 只处理 key、envelope、显式 sequence、TTL、连接和低基数 I/O 指标；领域 store 负责允许的事件、字段和值校验。

配置规范化为：

~~~yaml
algo-mentor:
  learning-plan:
    realtime-stream:
      enabled: true
      host: localhost
      port: 6380
      database: 0
      command-timeout: 3s
      connect-timeout: 2s
      shutdown-timeout: 2s
      read-block: 2s
      max-read-connections: 32
      active-retention: 2h
      completed-retention: 24h
~~~

原 `algo-mentor.learning-plan.generation.realtime-stream` 及 `LEARNING_PLAN_GENERATION_REALTIME_STREAM_*` 环境变量在一个兼容发布周期内作为 deprecated fallback；新配置和旧配置同时存在时以新配置为准并在启动时输出不含敏感信息的迁移警告。`application.yml`、`application-preprod.yml`、本地配置和部署文档必须在同一变更中更新。配置关闭时 generation/revision 都降级为查询与轮询，不能只关闭其中一条链路。

### 6.2 Key、保留与事件边界

每个 revision 使用独立 key：

~~~text
learning-plan:revision:{revisionId}:events
~~~

保留期复用阶段一的 `activeRetention` 与 `completedRetention`。`revision_completed`、`revision_failed`、`revision_superseded` 写入后续接 completed retention；工作事件续 active retention。每次阻塞 `XREAD` 仍使用独立连接并受 `maxReadConnections` 限制，不能阻塞 XADD 写连接。

revision realtime mapper 是 Agent runtime 到浏览器之间的唯一公开投影边界，禁止写入或返回：

1. instruction、用户 ID、完整草案/计划、冻结或提议快照、历史消息与 metadata。
2. `generationRunId`、Agent run DB id、Agent 准入、锁 token、provider、模型、prompt、usage、reasoning、trace 与 Review/Compiler artifact。
3. 工具名、工具输入、工具结果、child Agent 生命周期，以及未经 `PUBLIC_FAILURE_CODES` 允许的错误码。
4. Throwable message、cause、stack trace 或数据库/Redis 连接信息。

## 7. 后端实施任务

### 7.1 数据与领域模型

1. 新增 Flyway migration、`LearningPlanDraftRevisionRow`、领域 record、mapper result map、insert/update SQL 和 repository 映射字段。
2. 为 revision 增加 `withGenerationStarted`、条件终态转换所需的不可变 copy 方法及 DTO mapper；局部错误消息可保留在 service，稳定错误码必须集中定义。
3. 增加 idempotency lookup/lock、按 draft 的 revision 查询、条件终态更新和启动恢复扫描端口。
4. 将现有完成事务改为明确校验 `GENERATING` 与 revision 顺序，保证迟到成功、失败或启动恢复均无法覆盖终态或最新 draft。

### 7.2 生成协调与业务 Subscriber

1. 从 `LearningPlanDraftRevisionStreamService` 中抽取控制面 `LearningPlanDraftRevisionGenerationService`。Controller 不再获得 `Flow.Publisher<LearningPlanProposalStreamEvent>`。
2. 启动服务负责访问策略、幂等、草案/分组锁定、冻结基线、revision 创建、`prepareStream` 同步准入、事务提交后激活与启动 DTO。
3. 独立 Subscriber 复用既有 Agent work projector、Plan Compiler、Review Child 和最终 draft 更新逻辑；它仅向 revision realtime store append 公开投影。
4. `READY` 必须在 revision、proposal group 和 draft 的事务提交之后 append `revision_completed`；`FAILED/SUPERSEDED` 同理先持久化后通知。
5. Redis append 由 Subscriber 捕获并记录，不得向 Agent 传播异常、取消 subscription 或改写终态。
6. 旧 `LearningPlanDraftRevisionStreamService` 在兼容期保留原行为；新启动服务不可通过 `SseLearningPlanProposalStreamSubscriber` 实现。

### 7.3 Realtime transport 与 SSE 读取

1. 将阶段一 generation 专用 Lettuce 连接、sequence 管理、envelope 编解码、TTL 与读取连接限制提取为共享 transport；保持 phase one generation 协议、key 与客户端行为不变。
2. 新增 revision protocol、cursor、payload mapper、event store、metrics 和 unavailable 实现。未知事件、额外字段、非白名单 code、draft/revision 不匹配的 entry 必须按 realtime decode 失败处理。
3. 新增 revision events GET Controller。它只能读取 Redis、写 `SseEmitter` 并检查 revision 数据库终态，不能创建 Agent 或使用业务 Subscriber。
4. revision 已终态时在 Stream TTL 内允许回放；已过期或缺失的 Stream 通过正常 EOF 让前端回读数据库收束。

### 7.4 API、配置与装配

1. 在 `ApiContractConstants` 定义启动、查询、事件路径和 `after` 参数；新增 start/status DTO 与 response mapper。
2. 通过 `Location`、HTTP `202`、受控错误与阶段一保持一致；旧 stream Controller 保持独立。
3. 更新 `LearningPlanConfiguration`，将新 generation coordinator、revision realtime store、共享 transport 和启动恢复正确装配；disabled realtime 下须装配 unavailable store。
4. 迁移配置 prefix 时保留一周期的旧配置兼容，并为 credentials 延续现有“只经环境变量注入、不写日志”的约束。

## 8. 前端实施任务

1. 在 `frontend/src/services/api.ts` 与 `frontend/src/types/api.ts` 增加 revision 启动、revision 查询和 cursor SSE 订阅类型；新路径不再调用 `streamLearningPlanDraftRevision`。
2. 点击“按要求调整计划”后生成 UUID，调用启动 API；`202` 后在 `sessionStorage` 保存 `{ draftId, revisionId, idempotencyKey, lastEventId, protocolVersion }` 的 pending revision。
3. `revision_completed` 后立刻查询 revision，再查询 draft；只有 revision 仍为 `READY` 时才用 `GET draft` 完整响应更新预览。
4. `revision_failed` 与 `revision_superseded` 后查询 revision，展示其安全错误或回读最新 draft；不得信任 SSE 中的任何最终内容。
5. SSE 异常、EOF 无终态、cursor gap、未知/非法 envelope、Redis HTTP 错误与 Stream 过期时均先查询 revision。仍为 `GENERATING` 时从最近 cursor 重连，持续失败时退避轮询。
6. 刷新页面发现 pending revision 时恢复观察；revision 已终态时清理 pending。页面卸载、返回列表和 AbortSignal 不调用取消接口。
7. 开始新修订时禁用当前“按要求调整计划”提交按钮；若页面存在来自其他标签页的并发 revision，按 revision 查询和草案回读结果收束，不在前端推断 proposal group 顺序。
8. 保留旧 stream reader 仅服务兼容前端或测试，切换完成后从创建页的生产调用链移除。

## 9. 测试门禁

### 9.1 后端单元与集成

1. 合法启动创建一条 `GENERATING` revision，返回 revisionId、eventsUrl 和 `0-0`，且 Agent Subscriber 与任何 SSE 客户端无关。
2. 空 instruction、无权用户、草案不存在、已确认、缺少 draftPlan、访问策略拒绝、Agent 准入拒绝和执行器拒绝均不创建 revision、不占用幂等键、不写 Redis。
3. 相同 key 的并发请求只创建一条 revision 和一个 Agent run；相同 key + 不同 instruction 返回稳定冲突。
4. 同 key 的 `GENERATING`、`READY`、`FAILED`、`SUPERSEDED` 重放都不重新启动 Agent；新 key 才创建下一条 revision。
5. `READY` 事务先写 revision、group 与 draft，再写最小 `revision_completed`；Redis append 失败后 draft 仍可读取。
6. Agent/Compiler/结构化输出失败先将 revision 收敛为 `FAILED`，再写最小 `revision_failed`；Throwable cause 与完整 instruction 不进入 Redis。
7. 两个不同 key 并发时，后创建 revision 优先；旧 revision 的迟到完成不能覆盖 draft，并正确收敛 `SUPERSEDED` 或原始失败。
8. 已 `READY` 的 revision 被更新 revision 替换时标记 `SUPERSEDED`；revision 查询是权威状态，旧 Stream 不要求追补事件。
9. revision 事件 XREAD 严格遵守 after；SSE id 与 Redis entry ID 一致；TTL 内终态可读；越权 draft/revision 组合、非法 cursor、过期 Stream 与 malformed envelope 走受控响应或数据库回读。
10. Redis 写、读、TTL、连接容量失败与 SSE 写失败不取消 Agent，最终 revision/draft 仍可查询。
11. 应用启动将遗留 `GENERATING` revision 条件收敛为 `FAILED`；与迟到回调竞争时只产生一个终态。
12. phase one generation 的启动、回放、配置兼容和低敏 payload 回归测试必须继续通过。

### 9.2 前端

1. “按要求调整计划”启动后不再请求旧 `POST /revisions/stream`，而是保存 revision pending 状态并订阅 events URL。
2. 正常收到 `revision_completed` 后必须先查询 revision，再以 `GET draft` 的完整响应刷新预览。
3. 断线、无终态 EOF、ID gap、非法 event、Redis 失败和刷新后均能通过 revision 查询恢复；`GENERATING` 时使用最后成功 cursor 重连。
4. `FAILED` 保留原草案和输入错误状态；`SUPERSEDED` 不将较早 revision 的结果渲染为当前草案。
5. 同一次网络重试复用 Idempotency-Key；明确再次提交使用新 key；终态后清理 pending。
6. 首次草案创建、模板创建、追问、确认和已保存计划的扩展提案仍使用原有协议。

### 9.3 最小验证命令

~~~bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application,mentor-api -am \
  -Dtest='*LearningPlanDraftRevision*Test,*LearningPlan*Realtime*Test,*LearningPlanControllerTest' test

npm --cache ./.npm --prefix frontend test -- --run \
  src/learning-plans/LearningPlanCreatePage.test.tsx

make backend-test
make frontend-test
git diff --check
~~~

实施中优先运行相关模块和指定测试；交付时记录实际命令、结果与未覆盖的本地 Redis/PostgreSQL 集成条件。

## 10. 可观测性与安全

新增或扩展低基数指标：

1. `learning_plan_revision_generation_started`、`completed`、`failed`、`superseded`、`idempotency_reused`、`duration`。
2. Redis transport 的 append/read/expire/connect 成功与失败计数，tag 仅 `resourceType=generation|revision`、`operation`、`outcome`。
3. revision SSE 的 opened/completed/timeout/send-failed/active 继续作为连接指标，不映射为 revision 失败。
4. 前端数据库回读恢复、cursor gap、EOF recovery 与轮询降级次数，用于观察 realtime 可用性而非业务成败。

日志仅可记录受控的 `draftId`、`revisionId`、`proposalGroupId`、安全用户标识、revision 状态、稳定错误码、幂等复用与耗时。禁止记录 instruction、完整草案/计划、Agent/LLM 原始内容、artifact、prompt、token、Authorization、密码或 Redis URI。

## 11. 发布与回滚

1. 先发布 Flyway migration、后端 DTO/查询/启动接口与共享 realtime transport，保留阶段一 generation 和旧 revision stream。
2. 先在预发布环境配置新的 canonical realtime prefix，并验证旧 generation Stream 与新 revision Stream 可同时 append/read；确认旧环境变量 fallback 生效后再切换生产配置。
3. 发布前端，将创建页“按要求调整计划”切换到 revision start/events/query；观察 revision 启动成功率、幂等复用率、Redis append/read 失败率、EOF 回读恢复率、`FAILED/SUPERSEDED` 比例和完成耗时。
4. Redis 异常时关闭 learning-plan realtime 开关：启动、revision 查询和 draft 查询仍完成，前端退避轮询；不回滚 revision 状态机或数据库 migration。
5. 新前端异常时回退到仍保留的旧前端及 `/revisions/stream`；不得让新前端回退后继续调用新启动接口。数据库 migration 仅向前演进，不做破坏性回滚。
6. 兼容窗口结束前，检查旧 stream API 的访问量为零、旧配置 fallback 未被使用、阶段一 generation 无回归，再创建独立清理事项删除旧入口和旧配置别名。

## 12. 完成标准

1. 浏览器断开、刷新或 SSE 失败后，草案修订继续执行，用户可通过 revisionId 查询状态并通过 draftId 获取最终草案。
2. 新 revision SSE 终态只携带 `draftId`、`revisionId` 和必要的稳定错误码；前端不从 SSE 接收或拼装完整草案。
3. Redis Stream 支持按 `0-0` 或最近 cursor 回放公开修订进度，且 Redis 不成为修订成功条件。
4. 修订的访问策略、Plan Compiler、冻结基线、proposal group、revisionNo 与新请求优先语义不弱于改造前。
5. 幂等、执行器拒绝、终态竞争、应用重启收敛与跨草案越权读取均有自动化覆盖。
6. 首次草案 generation 保持阶段一协议和行为；正式计划扩展提案及用户显式取消仍未迁移。
