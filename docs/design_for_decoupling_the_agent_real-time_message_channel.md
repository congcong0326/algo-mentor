# Agent 实时消息通道解耦研发设计

> 状态：方案已收敛（第一阶段）
>
> 本阶段优先保证「Agent 不依赖浏览器连接、刷新后能拿到最终结果」。Redis Stream 是增强实时体验的临时通道，不承担业务可靠投递职责；不能为它引入队列语义、分布式协调或复杂的失败补偿。

## 1. 目标与第一阶段范围

本设计将 Agent run 的执行生命周期与浏览器 SSE 连接生命周期解耦，使 Practice Chat 在短暂断线、网络切换和页面刷新后能够恢复正在生成的回复。

第一阶段只改造 Practice Chat。Redis Stream 是**单次 run 的短期、尽力而为的实时事件日志**，用于正常情况下的实时投递和断线回放；PostgreSQL 中的会话消息和 run 状态仍是业务事实来源与终态读取来源。

不在第一阶段实施的内容：

- 将所有 Agent 场景统一迁移至该通道；
- WebSocket 协议实现；
- 运行中 assistant 草稿的 PostgreSQL 快照；
- Redis Stream 消费组、ACK 或按浏览器消费删除事件；
- 显式停止 run、run-control registry、多节点取消协调；
- Redis 写入重试、去重、补偿或“恰好一次”投递；
- `MAXLEN`/按年龄裁剪、游标缺口检测与 `EVENT_REPLAY_GAP` 协议；
- 多节点运行与节点故障恢复。第一阶段固定单应用节点；进程重启时只收束遗留运行，不继续执行。

第一阶段已拍板的简化原则：

- Redis 的 `XADD`、`EXPIRE` 或读取失败只记录结构化日志和指标；不得取消、失败或阻塞 Agent run。
- 正常 Redis 路径提供按 cursor 的断线补发；Redis 降级时前端退回查询 PostgreSQL 最终结果，不保证运行中草稿完整。
- 不新增持久化的 `ACCEPTED`、`REJECTED` 状态。`ACCEPTED` 只是 HTTP 启动响应的控制面标签；数据库沿用 `running`、`succeeded`、`failed`、`cancelled`。
- 刷新后一律从当前 run 的开头回放；不使用 `sessionStorage` 保存草稿或 cursor。页面未刷新时才在内存中保存 cursor 以减少短暂断线的重复回放。

### 1.1 Practice Chat 工具授权策略

Practice Chat 第一阶段不保留逐次人工工具确认弹窗。该交互会使 Agent worker 在等待用户决策期间被占用，也会为刷新恢复引入未决弹窗状态的额外协议和持久化问题。

本场景绑定的工具统一使用自动允许（`ALLOW`）策略：Agent 可调用的是服务端注册的、与 Practice Chat Definition 绑定的**受限工具集**，而不是拥有任意后端操作权限。自动允许不替代以下服务端边界：工具白名单、参数校验、当前用户与 session 归属校验、业务幂等、审计日志及既有风险控制。通用 Agent 的人工确认机制仍保留，供存在高风险副作用的其他场景使用。

实现上新增优先级高于 `PracticeCodeReviewPermissionHook` 的 Practice Chat 专用 permission hook：仅当受信 metadata 中 `agentKey=PRACTICE_CHAT` 时返回 `ALLOW`。既有 `AgentLoopExecution` 的 `allowedToolNames` 仍是不可绕过的工具白名单；其他 Agent 场景仍可命中 Review 的 `ASK` 策略。

因此 Practice Chat 不产生、不存储也不消费 `tool_permission_request`、`tool_permission_decision`、`tool_permission_timeout` 事件；Practice Chat 工作台移除对应弹窗、等待状态和权限决策调用。通用权限 API、类型和其他场景代码不在本次删除范围。

## 2. 身份与游标模型

三个标识属于不同层级，不能互相替代：

| 标识 | 含义 | 生命周期 | 用途 |
| --- | --- | --- | --- |
| `taskId` | Practice Chat 会话复用的长期 Agent task | 随 session 长期存在 | 页面刷新后定位当前运行的稳定锚点 |
| `runUuid` | 一条用户消息触发的一次 Agent run | 一次运行 | 事件流、终态和鉴权的最小单位 |
| `cursor` | 客户端最后**成功处理**的 Redis Stream entry ID | 当前 run 的客户端消费进度 | 断线后补发事件 |

`taskId` 保留为恢复锚点，但不作为单次实时事件流的身份。每次 run 使用独立 Stream：

```text
agent:realtime:run:{runUuid}:events
```

这可避免多轮聊天事件互相混入，也使 TTL、终态和回放范围严格对应一次运行。页面刷新时并不缺少 `runUuid`：客户端以 session / `taskId` 查询当前 active run，由服务端返回 `runUuid` 后再订阅。

`cursor` 的语义固定为 `after`：服务端只发送 Redis ID **严格大于**该 cursor 的事件。首次回放使用 `0-0`。

## 3. 当前实现的衔接边界

现有 Agent Loop 已经向 `AgentStreamEventSink` 发布 `AgentStreamEvent`；运行时通过单订阅 `Flow.Publisher` 启动 Agent worker。当前 Practice Chat 的 `SseLlmStreamSubscriber` 是该 Publisher 的唯一订阅者，并直接写入当前 HTTP SSE 连接。

第一阶段不把 Redis 写入实现为全局 `AgentLoopObserver`：Observer 是旁路生命周期监听，回调失败会被隔离，且其调用顺序不等价于最终事件投递顺序。正常路径的 Redis 写入位于**本次 run 的唯一事件出口**，从而保持事件顺序：

```text
Agent Loop
  ↓ AgentStreamEventSink
Single-subscriber Flow Publisher
  ↓（run 级唯一订阅者；尝试 XADD 后立即申请下一条）
RunEventStoreSubscriber
  ↓
Redis Stream
  ↓
SSE Subscriber / 后续 WebSocket Subscriber
```

因此保留既有 `AgentStreamEvent` 和事件名；仅将 Practice Chat 当前的 `SseLlmStreamSubscriber` 替换为 run 级 `RunEventStoreSubscriber`。`RunEventStoreSubscriber` 对每个事件顺序尝试 `XADD`；写入失败时记录低敏结构化日志和指标，然后继续请求下一条事件。Redis 仅承担临时实时体验，写入失败不能终止、取消或改变 run 的 PostgreSQL 终态。

`RunEventStoreSubscriber` 是原 SSE subscriber 在本场景中的职责替换，而非新的 Agent 生命周期或第二套事件发布机制。它订阅既有 `Flow.Publisher<AgentStreamEvent>`，将事件写入 Redis；浏览器不订阅该 Publisher，而是通过独立的 SSE GET 从 Redis 读取。名称可在实现阶段确定为 `RedisRunEventSubscriber` 等更贴近职责的名称。

## 4. 启动响应与事件数据面的职责分离

启动请求的 HTTP 响应属于控制面，由 Tomcat 请求线程负责；Redis 事件写入与浏览器 SSE 消费属于异步数据面。两者的职责和时序固定如下：

```text
Tomcat 请求线程
  ├─ 校验、幂等、task 互斥并创建/复用数据库 `running` run，取得 taskId + runUuid
  ├─ 将 RunEventStoreSubscriber 订阅到现有 Agent Publisher
  ├─ 由该订阅触发 Agent worker 提交至 SynchronousQueue 执行器
  ├─ 执行器已接收任务：立即返回 202 + 订阅信息
  └─ 执行器拒绝：同步将刚创建的 run/turn 标记 failed 并释放 task lock，再返回容量错误

Agent 工作线程
  └─ Agent Loop → 既有 AgentStreamEvent → RunEventStoreSubscriber → XADD Redis Stream

浏览器
  └─ 收到 202 后新开 GET SSE → XREAD / XREAD BLOCK Redis Stream
```

Tomcat 线程不等待 Agent Loop 真正开始或生成第一个 token；它只需确认 run 身份已确定，并且无队列执行器已经接收工作。`SynchronousQueue` 没有排队容量：执行器的 `execute(...)` 同步成功返回，表示任务已交给 worker 且已取得执行组许可；同步抛出拒绝异常，表示未被接收。

当前 `AgentRuntime.stream()` 为惰性 Publisher，只有发生 `subscribe` 才会执行提交，因此必须先完成 `RunEventStoreSubscriber` 的内部订阅，再将该次提交是否被执行器接收作为返回 `202 Accepted` 的前提。不得先返回订阅地址、之后才尝试启动 run。

现有 `SingleSubscriberAgentStreamPublisher.startWorker()` 会捕获 `executor.execute(...)` 的拒绝并转换为下游 subscriber 的 `onError(...)`；这样 `subscribe(...)` 会正常返回，Tomcat 无法同步区分“已接收”和“被拒绝”。Practice Chat 改造时，执行器拒绝不得在 Publisher 层吞获并异步化：应保留/抛出该异常至启动服务，由 Controller 返回既有容量错误。

run 在提交前可能已由 `prepare(...)` 创建了持久化记录。因此拒绝路径还必须同步收束：沿用现有 `failed` run / failed turn 终态写入，并释放 task lock；不得新增 `REJECTED` 数据库状态，也不得遗留 `running`/active 的幽灵 run，阻塞后续消息。只有这一收束完成后才返回容量错误。

启动服务建议返回一个独立控制面 DTO，例如：

```java
record PracticeChatRunSubscription(
    long taskId,
    String runUuid,
    String status, // 固定为控制面值 ACCEPTED，非数据库 run status
    String eventsUrl,
    String initialAfter
) {}
```

该 DTO 由 Practice Chat 启动服务组装并交给 Controller 返回；`RunEventStoreSubscriber` 不持有 HTTP response，也不负责写回订阅信息。

## 5. 启动与订阅 API

原 `POST /api/practice-sessions/{sessionId}/messages/stream` 调整为显式启动命令，不再保持该请求的 SSE 响应：

```http
POST /api/practice-sessions/{sessionId}/messages
Idempotency-Key: {key}
Content-Type: application/json
```

服务端执行用户、session、输入、幂等、同 task 互斥与 AI 治理准入校验；创建/复用本次 run，并提交到现有无队列 `SynchronousQueue` 执行器。只有执行器成功接收后才返回 `202 Accepted`。响应中的 `ACCEPTED` 只说明本次 POST 已成功提交；run 在响应送达前完成或失败是允许的，权威终态仍以 PostgreSQL 为准：

```json
{
  "type": "accepted",
  "taskId": 123,
  "runUuid": "run-abc",
  "status": "ACCEPTED",
  "eventsUrl": "/api/practice-sessions/42/runs/run-abc/events",
  "initialAfter": "0-0"
}
```

无可用执行资源时直接返回现有容量拒绝错误，不进入队列：若提交前已创建 run，必须同步标记为 `failed` 并释放 task lock。此失败 run 不是 active run，订阅接口对它返回资源不存在。幂等重试只复用已成功提交或已经完成的同一 run；提交被拒绝的请求允许使用新的 Idempotency-Key 重试，不复用已失败 run。

`Idempotency-Key` 的作用域为一次 Practice Chat 消息提交。相同 key 命中既有 run 时不再校验或重新执行模型；客户端不得把同一 key 用于不同 session 或不同消息内容。服务端不为这个异常用法额外保存请求体指纹，首期以客户端正确生成每次提交的新 UUID 为前提。

事件订阅接口：

```http
GET /api/practice-sessions/{sessionId}/runs/{runUuid}/events?after={cursor}
Accept: text/event-stream
```

订阅端必须验证当前用户拥有该 session，并验证 `runUuid` 隶属于其 `taskId`。不得向浏览器暴露 Redis key 或允许客户端指定任意 topic。

服务端先使用 `XREAD` 回放 `after` 之后已有事件，再使用有限时长的 `XREAD BLOCK` 持续等待新事件；每次超时后重新检查 PostgreSQL run 状态。每一条 Redis entry 的 ID 原样写为 SSE 的 `id:`；`event:` 使用现有稳定事件名，`data:` 使用既有 SSE DTO 映射的 JSON。读到 `agent_run_end` 或 `agent_error` 后正常关闭 SSE；若 Redis 中没有终态事件但 PostgreSQL 已终态，也直接关闭 SSE，由前端查询会话消息刷新最终展示。

本接口只支持显式 query 参数 `after`；首期前端继续使用 `fetch` 解析 SSE，不接入原生 `EventSource` 和 `Last-Event-ID`。`after` 缺失时等同 `0-0`；格式非法时返回参数错误；Redis 不可用时关闭/拒绝 SSE 连接，前端回读 PostgreSQL，不提供 Redis 级错误协议。

## 6. 断线、重连与页面刷新

客户端只在成功处理一个 SSE 事件后，在**当前页面内存**更新该 run 的 `lastEventId`。短暂断线或网络切换时，可直接重连，不需要先查 active run：

```text
内存中的 { sessionId, runUuid, lastEventId }
  ↓
GET events?after=lastEventId
```

页面刷新后的服务端状态为准；第一阶段不使用 `sessionStorage` 持久化草稿、cursor 或 run 信息，避免登录切换和草稿合并的额外状态机。

刷新恢复分流如下：

| active run 查询结果 | 当前页内存 | 行为 |
| --- | --- | --- |
| 存在，且当前页面内存的 `runUuid` 一致 | 有有效 `lastEventId` | 从 `after=lastEventId` 补发 |
| 存在，但刷新后或无有效内存 cursor | 无有效 cursor | 从 `after=0-0` 回放本次 run |
| 不存在（run 已终态） | 任意 | 不订阅 SSE；读取 PostgreSQL 会话消息/最终结果，并清理临时缓存 |

运行中而浏览器没有缓存时，不能从任意“中间位置”消费：当前 assistant 正文和工具执行状态在 run 终态前尚未完整持久化。因此第一阶段从该 `runUuid` 的开头回放；这不是重放整个聊天会话，而是只重放当前一条未完成回复。未来如需减少回放量，可再引入运行中快照及 `snapshotCursor`。

第一阶段继续使用 `fetch` 解析 SSE，并显式传递 `after`。同一 cursor 的重试可能使客户端重复处理一小段事件；前端以“从当前 run 开头重建临时 assistant 气泡”保证展示正确，不承诺端到端恰好一次。

SSE 正常结束但本页尚未收到终态事件时，前端必须查询一次 `active-run` 与会话消息：active run 已消失则按 PostgreSQL 刷新最终展示；仍存在则保留“正在生成”状态并允许用户稍后刷新或重新连接。Redis 不可用导致订阅请求直接失败时采用相同分流，不能把它误报为 Agent run 失败。

## 7. 事件封装与回放一致性

Redis entry 存储版本化的协议 envelope，而不是 Java 序列化对象：

```json
{
  "version": 1,
  "eventName": "content_delta",
  "data": { "content": "..." }
}
```

`eventName` 与 `data` 沿用当前前端已消费的 Agent/SSE 事件契约。Redis ID 是唯一的回放位置，不需要另造业务 sequence。写入顺序以 run 级唯一事件出口为准。

第一阶段不提供主动取消 API。浏览器关闭、刷新、SSE 读失败都不影响 Agent run；服务端或应用正常停止导致的既有协作式取消仍使用终态 `agent_error` 且 `code=CANCELLED` 表达，不新增含义重叠的领域事件。

## 8. 运行生命周期与连接断开

逻辑生命周期固定为：

```text
POST /messages 成功提交（控制面 ACCEPTED）
  ↓
数据库 run: running
  ├── succeeded
  ├── failed
  └── cancelled（仅现有服务端/应用停止协作式取消路径）
```

不存在 `QUEUED`、持久化 `ACCEPTED`、`REJECTED` 或前端“停止生成”操作。`agent_run_start` 表示 Agent Loop 已真正开始；它可能早于或晚于 202 响应被浏览器收到。

关闭 SSE 连接绝不等同于停止 run。首期没有 run-control registry，因而也不处理“已接受未开始”的显式取消竞态；后续真的需要停止按钮或多节点部署时再与取消路由、节点恢复一并设计。

应用进程启动时执行一次简单收束：将遗留的 `running` run 标记为 `failed`；不尝试恢复或续跑。task lock 是当前节点内存状态，进程重启后自然消失；正常运行中的 lock 仍由既有 Agent lifecycle 终态 observer 释放。

刷新时发现 run 已终态但没有 assistant 最终消息，代表该次运行失败或被服务端中断；首期不增加单 run 状态详情页或错误恢复 UI，工作台移除临时气泡并允许用户重新发送。成功 run 仍由既有会话消息查询返回最终 assistant 消息。

## 9. Redis Stream 保留与降级

SSE 浏览器是广播/回放读取者，不是工作队列消费者。因此：

- 不使用 `XREADGROUP`、ACK 或“消费一条就删除一条”；
- 不因某个浏览器收到事件而删除该事件；
- 多标签页、SSE 重连可独立读取同一 run Stream；
- 事件按 run 终态后的短期 TTL 整体回收，而非按单条消费回收。

每个 Stream 在首次成功写入后设置安全 TTL，并在正常写入时按配置续期；run 终态后设置最终保留期，例如 24 小时。TTL/续期失败只记录日志和指标。首期不启用 `MAXLEN`、按年龄裁剪或 `EVENT_REPLAY_GAP`：TTL 到期、Redis 重启、写入失败和读取失败都统一视为“实时日志不可用”。

实时日志不可用时：run 已终态则前端读取 PostgreSQL 会话消息；run 仍运行则前端显示简短的“正在生成，刷新后会显示最终结果”状态，不尝试伪造或恢复中间草稿。Stream 不可用不会影响 Agent 的模型调用、工具执行、最终消息和 run 状态持久化。

Redis Stream 使用独立的 Streams Redis 实例和独立配置，不复用缓存 Redis client（当前缓存 client 禁止连接 Streams 端口）。首期使用 Lettuce 异步命令或每个阻塞读取独立连接；不得在唯一的同步共享连接上执行 `XREAD BLOCK`，以免一个慢订阅读取阻塞 `XADD`。连接上限、`XREAD BLOCK` 时长和命令超时使用保守配置即可，不建设消费者池、连接复用调度或健康检查编排。

## 10. 最终架构

```text
Browser
  │ POST /messages
  ▼
Practice Chat 启动服务 ── 准入 / 幂等 / task 互斥 ── SynchronousQueue
  │                                                   │
  │ 202 { taskId, runUuid, initialAfter }             ├─ 提交失败：收束为 failed 后 HTTP 错误
  ▼                                                   ▼
Browser                                      Agent Loop（单次 run）
  │                                                      │
  │ GET .../runs/{runUuid}/events?after=cursor          ▼
  │                                              RunEventStoreSubscriber
  │                                                      │ XADD
  ▼                                                      ▼
SSE Subscriber ◀──── XREAD / XREAD BLOCK ─── Redis Stream（per run）
  │
  └── 记录 lastEventId，断线后按 cursor 重连

刷新：session / active-run → taskId 定位 → 获得 runUuid → 按状态订阅或直读持久化结果
```

## 11. 验收标准

完成第一阶段后，Practice Chat 应满足：

1. 浏览器断开、刷新或 SSE 读失败都不取消 Agent run，也不会因浏览器写出速度拖慢 Agent Loop。
2. Redis 正常时，当前页面短暂断线可使用内存 `lastEventId` 补发 `after` 之后的事件；刷新后一律只回放当前 run。
3. 已终态时只读 PostgreSQL 最终结果；Redis 不可用、事件缺失或 TTL 到期时也能退回 PostgreSQL，不阻塞 Agent。
4. `taskId` 仅作为恢复锚点；实时流和 cursor 始终绑定 `runUuid`。首期不提供主动停止接口。
5. 执行器无资源时同步返回容量错误；提交前已创建的 run 会同步收束为 `failed` 并释放 task lock，不残留 active run。
6. 正常 Redis 路径的每个 SSE 事件携带 Redis ID，事件 payload 继续兼容现有前端事件名和数据结构。
7. Stream 不按浏览器消费删除，终态后按 TTL 统一清理；不实现 Redis 的重试、去重、缺口检测或补偿。
8. Redis 写入、读取、订阅连接、重连和 Stream 清理具有最小的成功/失败计数、耗时和低敏结构化日志；Redis 故障不记为 Agent run 失败。
9. Practice Chat 工具使用受限工具集的自动允许策略，不出现逐次人工确认弹窗；工具调用仍受到服务端白名单、参数、归属、幂等和审计约束。
10. 覆盖启动拒绝及 run 收束、幂等重试、断线重连、刷新回放、终态直读、未授权访问、Redis 不可用和 Practice Chat 自动授权的后端与前端测试。
