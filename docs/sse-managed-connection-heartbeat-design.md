# SSE Managed Connection 与 Heartbeat 保活研发设计

## 1. 文档信息

- 设计日期：2026-08-05
- 状态：设计完成，待实施
- 首个接入场景：学习计划 AI 草案生成观察接口
- 后续候选场景：学习计划修订、扩展提案及其他低频业务事件 SSE
- 关联业务设计：`docs/learning-plan-ai-generation-sse-resilience-design.md`
- 关联线程模型：`docs/agent-thread-model-refactoring-design.md`
- 当前部署边界：单实例 `mentor-api`

本设计只负责 SSE 传输连接管理、串行写入、幂等关闭和 heartbeat 保活。它不负责创建、完成、失败或取消任何业务任务。

## 2. 背景

Spring MVC `SseEmitter` 可能同时被以下线程或回调访问：

- Agent 或业务工作线程发送普通业务事件。
- 业务工作线程发送终态事件并完成连接。
- heartbeat 调度线程发送保活 comment。
- Servlet 容器触发 `onCompletion`、`onTimeout` 或 `onError`。

如果这些调用直接散落在 Controller、Subscriber 和调度器中，会产生以下问题：

- heartbeat 与业务事件可能并发写同一个 `SseEmitter`。
- 发送、完成、超时和错误回调之间缺少统一状态机。
- 连接可能重复完成或重复从注册表移除。
- heartbeat 可能与业务写竞争并阻塞调度线程。
- 连接指标和关闭原因难以统一记录。
- 传输断开容易被上层错误解释为业务任务取消。

学习计划业务通过领域 ID、数据库状态和查询恢复保证正确性。heartbeat 的目标只是提高连接可用性和统一传输层生命周期，不能成为业务结果交付的唯一保障。

## 3. 设计目标

1. 每个实际 SSE HTTP 连接由唯一 `ManagedSseConnection` 持有和操作 `SseEmitter`。
2. heartbeat 与业务事件对同一连接的写入线程安全且有序。
3. heartbeat 不阻塞 Agent 线程，也不引入每连接线程或发送队列。
4. 普通业务事件和终态事件不会因为 heartbeat 锁竞争而丢失。
5. Servlet 回调、发送失败和正常完成最终收敛到幂等关闭。
6. 关闭后的连接及时从注册表移除，不再参与 heartbeat 扫描。
7. 连接断开只影响观察连接，不触发业务任务取消。
8. SSE 连接指标与业务结果指标保持分离。
9. 配置、日志和指标支持代理超时调优和故障定位。

## 4. 非目标

1. 本设计不定义学习计划的启动、查询、终态或取消 API。
2. 本设计不持有 Agent、LLM 或业务 publisher 的 cancellation handle。
3. 本设计不把 `onCompletion`、`onTimeout`、`onError` 或写失败映射为业务取消。
4. 本设计不保存和回放 SSE 历史事件。
5. 本设计不为每个连接创建独立线程、`ScheduledFuture`、消费者或发送队列。
6. 本设计不处理高吞吐 token 直出场景的背压、丢弃和缓冲策略。
7. 本设计不建设跨实例连接注册表或分布式 heartbeat 调度。
8. 本设计不通过 heartbeat 延长 Agent 最大运行时间、provider 读取超时或前端最大等待时间。

## 5. 核心边界

### 5.1 连接生命周期不等于业务生命周期

```text
ManagedSseConnection
  -> 只拥有传输连接
  -> 可以关闭观察
  -> 不可以取消 Agent

业务 Generation Coordinator
  -> 拥有业务任务和 cancellation handle
  -> 可以完成、失败或取消业务任务
  -> 不直接操作 SseEmitter
```

以下情况只关闭和注销 SSE 连接：

- `SseEmitter.onCompletion`。
- `SseEmitter.onTimeout`。
- `SseEmitter.onError`。
- heartbeat 写失败。
- 普通业务事件写失败。
- 业务终态发送完成或发送失败。

业务取消必须由所属业务 API 和业务任务控制注册表完成，不属于 `ManagedSseConnection` 的能力。

### 5.2 Heartbeat 是增强能力

heartbeat 用于：

- 减少反向代理或负载均衡器的 idle timeout。
- 在长时间没有业务事件时更早发现失效连接。
- 保持浏览器、代理和服务端之间的活动流量。

即使 heartbeat 被禁用、调度延迟或单次发送被跳过，业务正确性仍必须由领域 ID、数据库状态和查询恢复保证。

### 5.3 不引入发送队列

首期接入场景只发送低频工作状态，不是 token 级高吞吐流。本阶段不增加事件队列、消费者线程或专用发送线程，原因包括：

- 队列只能转移网络写阻塞，不能消除慢连接。
- 队列需要额外定义容量、丢弃、终态优先级和关闭语义。
- 每连接队列和线程会增加内存及线程治理成本。
- 当前单实例、低并发规模不足以证明这层复杂度必要。

## 6. 组件设计

### 6.1 ManagedSseConnection

每个实际 SSE HTTP 连接对应一个 `ManagedSseConnection`。只有该对象可以持有和操作 `SseEmitter`，Controller、业务 Subscriber 和 heartbeat 调度器都不能直接调用 `emitter.send(...)`、`complete()` 或 `completeWithError(...)`。

建议持有：

```text
connectionId
streamType
ownerId
resourceType
resourceId
SseEmitter
ReentrantLock writeLock
AtomicReference<SseConnectionState>
AtomicLong lastWriteNanos
SseConnectionRegistry
SseOpsRecorder
```

公开能力建议保持最小：

```text
sendEvent(eventName, data)
sendTerminalEvent(eventName, data)
tryHeartbeat(nowNanos)
closeFromCallback(reason, error)
state()
```

### 6.2 SseConnectionState

连接状态固定为：

```text
OPEN
CLOSING
CLOSED
```

| 状态 | 语义 |
| --- | --- |
| `OPEN` | 可以发送业务事件或 heartbeat |
| `CLOSING` | 正在串行发送终态并完成连接，不接受新写入 |
| `CLOSED` | 已关闭并从注册表注销 |

不引入可逆状态流转。任何异常关闭都可以从 `OPEN` 或 `CLOSING` 原子进入 `CLOSED`。

### 6.3 SseCloseReason

关闭原因使用枚举统一管理：

```text
TERMINAL_COMPLETED
CLIENT_DISCONNECTED
SERVLET_COMPLETED
TIMEOUT
SEND_FAILED
HEARTBEAT_FAILED
APPLICATION_STOPPING
```

关闭原因只描述连接发生了什么，不描述业务任务成功、失败或取消。

### 6.4 SseConnectionRegistry

API 层维护单实例连接注册表：

```text
connectionId -> ManagedSseConnection
```

可选维护受控业务索引，便于业务层按资源通知当前观察者：

```text
streamType + resourceId -> connectionId set
```

注册表职责：

- 注册新连接。
- 按连接 ID 幂等移除。
- 按受信资源标识查找当前观察连接。
- 为 heartbeat 调度器提供当前连接快照。
- 应用停止时关闭所有连接。
- 维护 `active_connections` gauge。

注册表不是业务事实来源。业务状态查询必须读取所属领域仓储。

### 6.5 SseHeartbeatScheduler

使用 Spring 管理的专用 `TaskScheduler` 周期扫描连接注册表：

```text
heartbeat tick
  -> 获取当前连接快照
  -> 遍历 OPEN 连接
  -> connection.tryHeartbeat(nowNanos)
```

不为每个连接创建独立 `ScheduledFuture`。连接关闭并从注册表移除后，后续扫描自然不会再访问该连接。

调度器要求：

- 不使用 Agent executor。
- 不使用 JVM common pool。
- 调度线程数量保持较小，首期默认为 1。
- 单条连接拿不到写锁时立即跳过，不阻塞整个扫描。
- 单条连接发送失败只关闭该连接，不终止调度器。
- tick 级异常必须被捕获并记录，后续 tick 继续运行。

## 7. 写入与并发模型

### 7.1 写锁策略

连接使用一个 `ReentrantLock` 保护 `SseEmitter.send(...)` 和正常终态关闭。

| 写入类型 | 获取锁策略 | 拿不到锁时的行为 |
| --- | --- | --- |
| heartbeat | `tryLock()` | 立即跳过本次 heartbeat |
| 普通业务事件 | `lockInterruptibly()` | 等待当前写入结束；线程中断时停止等待 |
| 业务终态事件 | `lockInterruptibly()` | 串行发送终态并完成连接 |
| Servlet 异常回调 | 不等待写锁 | 原子关闭状态并注销连接 |

heartbeat 拿不到锁说明连接正在发送业务数据，此时连接已经活跃，没有必要补发保活帧。普通业务事件不能因为与 heartbeat 短暂竞争而丢失，因此等待写锁；获得锁后必须再次检查连接状态。

### 7.2 普通业务事件

```text
lockInterruptibly
  -> 确认状态为 OPEN
  -> emitter.send(event)
  -> 更新 lastWriteNanos
  -> 记录发送指标
  -> unlock
```

如果线程在等待锁时被中断：

- 恢复线程中断标记。
- 不继续发送该事件。
- 由业务调用方按自身生命周期决定是否继续运行。
- 不在连接层取消业务任务。

如果 `send` 失败：

- 将连接原子关闭。
- 从注册表幂等移除。
- 记录 `SEND_FAILED`。
- 不调用业务 cancellation handle。

### 7.3 业务终态事件

正常业务终态路径：

```text
业务事务已经提交
  -> lockInterruptibly
  -> OPEN 改为 CLOSING
  -> 从注册表移除
  -> 尝试发送 terminal event
  -> emitter.complete()
  -> CLOSED
  -> unlock
```

终态发送失败不能改变已经提交的业务结果。连接仍进入 `CLOSED`，前端通过普通查询接口读取权威状态。

### 7.4 Servlet 回调

Servlet 回调不能等待正在阻塞网络写的线程释放锁，否则可能扩大容器回调阻塞。回调路径采用原子关闭：

```text
OPEN/CLOSING -> CLOSED
  -> 从注册表移除
  -> 记录关闭原因
  -> 不等待 writeLock
  -> 不再次调用 emitter.send
  -> 不取消业务任务
```

已经持有写锁的发送线程返回后必须再次看到 `CLOSED`，并停止后续写入或完成动作。

### 7.5 避免锁重入关闭

写失败路径可能发生在已经持有 `writeLock` 的代码中。内部关闭方法必须区分已持锁的发送失败关闭和未持锁的 Servlet 回调关闭。不能从持锁路径调用一个再次尝试获取写锁的公共关闭方法。

## 8. Heartbeat 设计

### 8.1 Heartbeat 帧

heartbeat 使用 SSE comment，不分发前端业务事件：

```text
: keepalive

```

不使用 `event: heartbeat`，避免前端业务事件解析器增加无意义分支。

### 8.2 空闲判断

连接记录单调时钟 `lastWriteNanos`。业务事件和 heartbeat 成功写入后都更新时间。

```text
nowNanos - lastWriteNanos < heartbeatInterval
  -> 跳过

连接最近无成功写入且 tryLock 成功
  -> 再次确认状态和空闲时间
  -> 发送 keepalive comment
```

使用单调时钟而不是墙上时钟计算间隔，避免系统时间调整影响空闲判断。

### 8.3 忙连接跳过

heartbeat 使用 `tryLock()`，拿不到锁时立即增加 `heartbeat_skipped_busy` 并返回。不能使用阻塞锁，也不能在调度器中重试同一连接。业务事件正在写入已经证明连接活跃，跳过此次 heartbeat 不降低连接保活效果。

### 8.4 Heartbeat 失败

heartbeat 写失败时：

1. 将连接关闭原因记录为 `HEARTBEAT_FAILED`。
2. 从连接注册表移除。
3. 更新连接指标。
4. 不向业务层传播取消。
5. 不把所属任务标记为失败。

## 9. 注册与关闭竞争

### 9.1 新连接注册

推荐顺序：

```text
Controller 校验用户和资源归属
  -> 创建 SseEmitter
  -> 创建 ManagedSseConnection
  -> 绑定 onCompletion/onTimeout/onError
  -> 注册连接
  -> 再次查询业务状态
  -> 已终态则立即发送终态并关闭
```

再次查询业务状态属于业务适配器职责，用于避免“业务已经完成，但新连接注册后永久等待”的竞争。连接基础设施不自行读取业务数据库。

### 9.2 回调重复触发

`onCompletion`、`onTimeout`、`onError` 和发送失败可能重复或交错触发。所有路径必须通过状态 CAS 和注册表幂等移除收敛，保证：

- 只记录一次最终关闭原因。
- `active_connections` 只减少一次。
- 不重复执行连接清理回调。
- 不调用业务取消。

### 9.3 应用停止

```text
停止接受新连接
  -> 停止 heartbeat 调度
  -> 遍历注册表快照
  -> 以 APPLICATION_STOPPING 关闭连接
  -> 清空注册表
```

业务任务如何取消或收敛由各业务协调器负责，不能通过关闭连接间接完成。

## 10. 超时边界

heartbeat 可以防止部分代理 idle timeout，但通常不会自动延长 `SseEmitter` 的总异步超时。以下超时必须分别配置和理解：

- SSE 总异步超时。
- 反向代理或负载均衡器 idle timeout。
- provider 读取超时。
- Agent 最大运行时间。
- 前端轮询最大等待时间。

约束：

- heartbeat interval 必须明显小于代理 idle timeout。
- SSE 总超时必须大于常规业务观察时长，但不能代替业务最大运行时间。
- SSE 总超时先到达时只关闭观察连接，业务任务是否继续由业务设计决定。
- 前端不能因为持续收到 heartbeat 就无限等待；仍需有业务级最大等待和查询恢复策略。

## 11. 配置设计

在 `ApiSseProperties` 中增加通用或按 stream type 覆盖的配置。首期可以先提供学习计划配置：

```yaml
mentor:
  api:
    sse:
      learning-plan-draft-timeout: 6m
      learning-plan-heartbeat-enabled: true
      learning-plan-heartbeat-interval: 20s
      heartbeat-scheduler-pool-size: 1
```

校验规则：

- interval 必须为正数。
- heartbeat 启用时必须配置 interval。
- timeout 必须为正数且明显大于 heartbeat interval。
- scheduler pool size 必须大于 0，并设置保守上限。
- 配置 key 必须在 `MentorConfigurationKeys` 中集中维护。

如果后续多个 SSE 场景接入，应优先演进为默认配置加按 `streamType` 覆盖，避免为每个业务复制调度器和属性类。

## 12. 可观测性

### 12.1 连接指标

- `sse_connection_opened_total`
- `sse_connection_closed_total{reason}`
- `sse_send_failed_total{stream_type}`
- `sse_heartbeat_sent_total{stream_type}`
- `sse_heartbeat_skipped_busy_total{stream_type}`
- `sse_heartbeat_failed_total{stream_type}`
- `sse_active_connections{stream_type}`
- `sse_connection_duration`

指标标签只使用低基数值，例如 `stream_type` 和受控 `reason`。禁止把 `connectionId`、用户 ID 或业务资源 ID 放入指标标签。

### 12.2 与业务指标分离

以下映射明确禁止：

```text
SSE timeout       != business failure
SSE send failed   != business failure
client disconnect != business cancellation
heartbeat failed  != business cancellation
```

业务完成、失败和取消只能由业务记录的终态产生对应指标。

### 12.3 结构化日志

允许记录 `connectionId`、`streamType`、低敏资源标识、关闭原因、连接持续时间、heartbeat 发送或跳过结果以及异常类型。禁止记录 API Key、Authorization、Cookie、访问令牌、SSE 完整 data payload、完整用户输入、完整模型输出和 reasoning 内容。

正常 heartbeat 成功不应逐条输出 info 日志，避免日志噪声；以指标为主，必要时使用 debug。

## 13. 实施计划

### 阶段一：连接抽象与幂等关闭

1. 新增 `SseConnectionState` 和 `SseCloseReason`。
2. 新增 `ManagedSseConnection`，收敛所有 `SseEmitter` 操作。
3. 新增 `SseConnectionRegistry`。
4. 接入现有 `SseOpsRecorder` 和结构化日志。
5. 为普通事件、终态和 Servlet 回调补齐并发测试。

### 阶段二：学习计划观察接口接入

1. 将学习计划观察 Controller 的 `SseEmitter` 包装为 `ManagedSseConnection`。
2. 业务层通过连接接口发送事件，不再直接操作 emitter。
3. 验证观察连接关闭不会调用学习计划 cancellation handle。
4. 验证终态先提交数据库，再尝试发送并关闭连接。

阶段二依赖学习计划业务设计至少完成“启动与观察分离”。如果业务设计尚处于兼容 `/drafts/stream` 阶段，可以先完成基础类和单元测试，不强行接入旧的启动即观察接口。

### 阶段三：Heartbeat 调度

1. 增加 heartbeat 配置和校验。
2. 增加专用 `TaskScheduler`。
3. 实现注册表周期扫描和空闲判断。
4. 实现 `tryLock` 忙时跳过。
5. 增加 heartbeat 指标、失败关闭和调度器容错测试。

### 阶段四：后续场景评估

根据学习计划场景的连接数、发送耗时、busy skip 和失败率，逐个评估修订、扩展、Practice Chat 等 SSE 场景是否接入。不得仅为统一形式一次性迁移全部 SSE 入口。

## 14. 测试设计

### 14.1 ManagedSseConnection 单元测试

- 普通业务事件成功发送后更新 `lastWriteNanos`。
- 已关闭连接拒绝后续业务写和 heartbeat。
- 普通业务事件等待 heartbeat 写入结束后按顺序发送。
- 业务线程中断时停止等待写锁并恢复中断标记。
- 正常终态串行发送并完成连接。
- 终态发送失败后仍关闭和注销连接。
- 写失败路径不会重复获取关闭锁。
- `onCompletion/onTimeout/onError` 重复触发时只注销一次。
- Servlet 回调不等待正在进行的网络写。
- 任意关闭路径都不会调用业务取消回调。

### 14.2 Heartbeat 单元测试

- 连接空闲超过 interval 时发送 comment。
- 最近有业务写时跳过 heartbeat。
- heartbeat 拿不到锁时立即跳过，不阻塞调度线程。
- heartbeat 成功后更新 `lastWriteNanos`。
- heartbeat 写失败后连接从注册表移除。
- 连接关闭后全局扫描不再访问该连接。
- 单条连接异常不会停止其他连接扫描和后续 tick。

### 14.3 注册表与生命周期测试

- 同一连接重复移除只影响 active gauge 一次。
- 连接注册后能够按受信资源索引查找。
- 终态发送和 Servlet 回调竞争时只保留一个关闭结果。
- 应用停止时禁止新注册并关闭当前连接。
- 调度器停止后不再发起新 heartbeat。

### 14.4 集成测试

- 启动真实 HTTP SSE，在无业务事件期间观察到 comment heartbeat。
- 主动断开客户端后连接从注册表移除，后台业务任务继续执行。
- 模拟 heartbeat 与业务事件并发写入，验证没有交叉写或重复关闭。
- 模拟终态提交后 `SseEmitter.send` 失败，验证业务查询仍返回成功终态。
- 模拟 SSE 总超时，验证只关闭观察连接，不改变业务状态。

## 15. 风险与取舍

### 15.1 慢连接仍可能反压业务线程

业务事件仍由调用线程同步写 SSE。`lockInterruptibly()` 和 `SseEmitter.send(...)` 都可能让单条慢连接短暂阻塞对应业务线程。

风险控制：

- 首期只接入低频业务事件场景。
- heartbeat 忙时跳过，不与业务事件争用。
- 写失败后立即注销观察连接。
- 业务结果先落库，连接失败不影响恢复。

如果线上指标证明慢客户端长期占用业务线程，再单独设计共享投递池或有界单写者模型，不在本设计中预设。

### 15.2 Servlet 回调与阻塞发送交错

回调不等待写锁可以避免容器线程被网络写拖住，但意味着持锁发送线程可能稍后才观察到 `CLOSED`。状态 CAS、发送后检查和幂等注册表移除必须共同保证收敛。

### 15.3 单实例注册表

连接和 heartbeat 调度只存在当前实例。当前单实例部署可接受。未来多实例部署需要粘性路由或跨节点事件投递，本设计不提前实现。

### 15.4 Heartbeat 不能解决总超时

heartbeat 只能处理部分 idle timeout，不能替代 Servlet 异步总超时、业务最大运行时间或前端查询恢复。上线前必须结合实际代理配置验证。

### 15.5 通用化范围过大

不同 SSE 场景的事件频率、取消语义和背压要求不同。基础设施只统一连接操作和 heartbeat，不统一业务取消或业务事件状态机。

## 16. 主要实施位置

预计涉及：

- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/ApiSseProperties.java`
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorConfigurationKeys.java`
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanController.java`
- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/service/SseLearningPlanDraftStreamSubscriber.java`
- 新增 `backend/mentor-api/src/main/java/org/congcong/algomentor/api/sse/connection/`
- `backend/ops-observability` 中现有 SSE recorder 或其扩展
- `backend/mentor-api/src/main/resources/application.yml`
- 对应后端单元测试与 HTTP 集成测试

建议新增类型：

- `ManagedSseConnection`
- `SseConnectionRegistry`
- `SseHeartbeatScheduler`
- `SseConnectionState`
- `SseCloseReason`

## 17. 验收标准

1. Controller、业务 Subscriber 和 heartbeat 调度器不再直接持有并操作同一个 `SseEmitter`。
2. 同一连接的业务事件、heartbeat 和正常终态写入保持串行。
3. heartbeat 拿不到写锁时立即跳过。
4. 普通业务事件不会因为 heartbeat 锁竞争被静默丢弃。
5. Servlet 回调不等待正在进行的网络写。
6. 所有关闭路径幂等注销连接，active gauge 不重复变化。
7. 连接关闭后不再参与 heartbeat 扫描。
8. heartbeat 或业务发送失败不会取消 Agent，也不会修改业务终态。
9. heartbeat 不依赖 Agent 线程主动产生事件。
10. 连接指标和业务结果指标能够明确区分。
11. 配置校验、并发单元测试和真实 HTTP SSE 集成测试全部通过。
