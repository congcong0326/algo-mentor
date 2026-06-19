# Agent Run 锁研发设计

## 背景

当前 Agent 对话流式接口在用户提交问题后会启动一次 `AgentLoopRunner` 执行。前端后续会把发送按钮置灰，避免用户在同一会话中连续提交多次请求。但前端状态只能改善交互，不能保证后端正确性：用户可能刷新页面、多开标签页、网络重试，或者直接调用 HTTP API。

因此后端需要在启动 run 前建立互斥机制，保证同一个学习会话同一时间只有一个 Agent run 在执行。第一版不引入 Redis 或数据库锁，先使用进程内内存锁；但接口和语义需要按后续分布式实现设计，避免未来切换 Redis、PostgreSQL 锁表或 worker 架构时改动业务层。

## 设计目标

- 同一个 `taskId` 同一时间只允许一个 Agent run 执行。
- 第一版使用进程内内存锁，降低实现复杂度。
- 前端置灰只作为用户体验，后端锁作为正确性边界。
- 后端提供 task 级运行状态查询接口，支持前端刷新后恢复按钮状态。
- 锁接口不暴露具体实现，后续可替换为 Redis 或数据库实现。
- 锁接口预留 TTL 能力，但第一阶段内存锁可以不启用 TTL，先通过 run 终态主动释放。
- 释放和续期必须校验本次加锁令牌，避免旧请求误释放新请求锁。
- 锁失败时返回明确的业务错误，方便前端展示和保持禁用状态。

## 非目标

- 第一版不实现跨实例互斥。如果同时部署多个 API 实例，内存锁不能保证全局唯一执行。
- 第一版不实现 SSE 断线重连、事件回放或运行中订阅。
- 第一版不实现后台队列和 worker 调度。
- 第一版不把锁作为 run 状态的唯一事实来源。`agent_run.status` 仍然记录业务执行状态。

## 核心原则

### 锁粒度按 task 控制

锁 key 使用会话或学习任务维度：

```text
agent-run:task:{taskId}
```

这表示同一个 task 下只能有一个回答生成中。不要使用 user 级别锁，否则用户在两个不同学习会话中也无法并行提问，限制过大。

### lockKey、ownerId 和 tokenId 分离

锁语义拆成三层：

- `lockKey`：互斥粒度，决定“谁和谁不能并发”。
- `ownerId`：当前服务实例身份。
- `tokenId`：本次成功获取锁生成的唯一令牌。

第一版锁粒度是 `taskId`，因此 `lockKey` 使用 `agent-run:task:{taskId}`。这表示同一个 task 内不能并发生成多个回答。

`tokenId` 不是锁粒度，它只表示“本次成功持有锁的令牌”。第一版 `ownerId` 可以由接口固定返回，例如 `local-agent-api`。但释放锁不能只校验固定 `ownerId`，因为同一进程内不同请求会共享这个 owner。每次成功加锁都必须生成新的 `tokenId`，释放和续期时校验完整 token。

推荐锁值语义：

```text
lockKey = agent-run:task:123
ownerId = local-agent-api
tokenId = 550e8400-e29b-41d4-a716-446655440000
```

释放条件：

```text
lockKey 匹配，并且 ownerId/tokenId 都匹配
```

### tokenId 不等同于 Idempotency-Key

`Idempotency-Key` 和 `tokenId` 解决的问题不同：

- `Idempotency-Key`：客户端提交请求的幂等键，用来表达“这是不是同一次用户提交”。
- `tokenId`：服务端成功获取锁后生成的持锁令牌，用来防止误释放或误续期。

第一阶段可以把 `Idempotency-Key` 写入锁 metadata，方便排查冲突来源，但不建议直接把它当作 `tokenId`。原因是同一个幂等键可能被重复请求携带，重复请求不应该天然获得释放当前锁的能力；释放锁应只允许真正成功 acquire 的那次执行持有的 `tokenId` 完成。

推荐关系：

```text
lockKey        = agent-run:task:{taskId}
metadata       = { idempotencyKey, runId, taskId }
tokenId        = server-generated UUID per successful acquire
Idempotency-Key = request-level deduplication key
```

### TTL 是后续兜底能力

第一阶段内存锁可以不启用 TTL，正常路径中，run 成功、失败、取消或超时结束时应主动释放锁。这样实现更简单，也避免长回答过程中因为 TTL 过短导致锁提前释放。

后续切换 Redis 或数据库锁时再启用 TTL 和 refresh。TTL 用于防止进程异常、回调未触发或未知 bug 导致锁永久存在，不应替代正常释放路径。

如果第一阶段不启用 TTL，需要接受一个取舍：如果 run 执行线程异常退出且没有触发终态回调，同一进程内该 task 可能被锁住，直到服务重启或提供人工清理能力。

## 接口设计

建议在 agent runtime 或 application 边界新增锁抽象。

```java
public interface AgentRunLockManager {

  AgentRunLockAcquireResult tryAcquire(AgentRunLockRequest request);

  boolean refresh(AgentRunLockToken token);

  void release(AgentRunLockToken token);
}
```

锁 owner 由独立 provider 提供：

```java
public interface AgentRunLockOwnerProvider {

  String ownerId();
}
```

请求模型：

```java
public record AgentRunLockRequest(
    String lockKey,
    String ownerId,
    Duration ttl,
    Map<String, Object> metadata
) {
}
```

`ttl` 第一阶段允许为空，表示内存锁不启用自动过期。后续 Redis 或数据库锁实现应要求配置明确 TTL。

成功获取锁后返回 token：

```java
public record AgentRunLockToken(
    String lockKey,
    String ownerId,
    String tokenId,
    Instant expiresAt
) {
}
```

当第一阶段内存锁不启用 TTL 时，`expiresAt` 可以为空。后续启用 TTL 后再返回明确过期时间。

获取结果：

```java
public record AgentRunLockAcquireResult(
    boolean acquired,
    AgentRunLockToken token,
    AgentRunLockConflict conflict
) {
}
```

冲突信息：

```java
public record AgentRunLockConflict(
    String lockKey,
    String ownerId,
    Instant expiresAt,
    Map<String, Object> metadata
) {
}
```

## 第一版内存锁实现

内存实现可以基于 `ConcurrentHashMap<String, LockEntry>`。

建议内部结构：

```java
record LockEntry(
    String ownerId,
    String tokenId,
    Instant expiresAt,
    Map<String, Object> metadata
) {
}
```

`tryAcquire` 语义：

```text
1. 当前 lockKey 不存在：写入新 LockEntry，返回 acquired=true。
2. 当前 lockKey 已存在：返回 acquired=false，并带上 conflict 信息。
```

第一阶段不启用 TTL 时，不做过期替换。这样语义更直接：只要同一个 task 已经有执行中的 run，就拒绝新提交，直到 run 终态释放锁。

`release` 语义：

```text
1. lockKey 不存在：直接返回。
2. lockKey 存在且 ownerId/tokenId 匹配：删除。
3. lockKey 存在但 ownerId/tokenId 不匹配：不删除。
```

`refresh` 语义：

```text
1. 第一阶段可以直接返回 false 或 no-op，因为内存锁不启用 TTL。
2. 后续启用 TTL 后，lockKey 存在且 ownerId/tokenId 匹配时延长 expiresAt，返回 true。
3. lockKey 不存在或 token 不匹配：返回 false。
```

内存锁只保证单进程内互斥。后续多实例部署前，需要切换到 Redis 或数据库锁实现。

## 后端接入流程

推荐流程：

```text
AgentConversationController
  -> AgentConversationService.prepareRun(...)
  -> AgentRunLockManager.tryAcquire(lockKey=agent-run:task:{taskId})
  -> 获取成功：启动 AgentLoopRunner.stream(...)
  -> 获取失败：返回 409 AGENT_RUN_IN_PROGRESS
```

释放锁推荐通过 Agent 生命周期完成：

```text
AgentLoopObserver.onRunEnd  -> release(token)
AgentLoopObserver.onError   -> release(token)
```

如果第一版采用“客户端断开即取消执行”，SSE timeout/cancel 也可以触发 release。但如果后续改为“客户端断开后后端继续执行”，就不能在 SSE 断开时释放锁，应等 run 进入终态后释放。

锁 token 需要进入本次 run 的 metadata，便于 observer 在终态回调中释放：

```text
metadata.agentRunLockToken = ...
```

实际落地时应避免把完整 token 直接返回给前端；前端只需要知道当前 task 正在生成。

## 前端交互

第一版前端行为：

- 用户提交问题后，当前 task 的发送按钮置灰。
- 流式回答结束、失败或取消后恢复发送按钮。
- 如果后端返回 `409 AGENT_RUN_IN_PROGRESS`，保持按钮置灰或提示当前会话正在生成回答。
- 页面刷新后调用 task 运行状态查询接口，判断按钮应保持置灰还是恢复可发送。

后端 409 响应建议：

```json
{
  "code": "AGENT_RUN_IN_PROGRESS",
  "message": "当前会话正在生成回答",
  "metadata": {
    "taskId": 123
  }
}
```

后续启用 TTL 后，可以在 metadata 中补充 `expiresAt`，让前端展示预计可重试时间。

## Task 运行状态查询接口

为了支持页面刷新、前端状态丢失和多标签页场景，后端需要提供按 `taskId` 查询当前运行状态的接口。第一版不要求支持订阅运行中的 SSE，也不要求回放已发送 token；这个接口只负责告诉前端当前 task 是否还能提交新消息。

建议接口：

```http
GET /api/agent/conversations/tasks/{taskId}/run-state
```

建议响应：

```json
{
  "taskId": 123,
  "sendEnabled": false,
  "activeRun": {
    "runId": 456,
    "runUuid": "7b96fd4d-7f4d-4e49-a2a1-c73f8463a16a",
    "status": "running",
    "startedAt": "2026-06-19T10:00:00Z"
  }
}
```

无运行中 run 时：

```json
{
  "taskId": 123,
  "sendEnabled": true,
  "activeRun": null
}
```

字段语义：

- `sendEnabled`：前端发送按钮是否可用。第一版可以由后端根据内存锁或当前 active run 判断。
- `activeRun`：当前阻止发送的 run。没有运行中 run 时为 `null`。
- `activeRun.status`：建议复用 `agent_run.status`，第一版主要关注 `running`。

第一版状态来源可以采用：

```text
优先检查 AgentRunLockManager 当前 task lock
  -> 有锁：sendEnabled=false
  -> 无锁：sendEnabled=true
```

如果需要返回 `runId`、`runUuid`、`startedAt` 等信息，锁 metadata 中应保存当前 run 的必要标识：

```text
metadata = { taskId, runId, runUuid, idempotencyKey }
```

注意：第一版内存锁不启用 TTL，服务重启后锁会丢失。此时 run-state 接口如果只查内存锁，可能返回可发送，但数据库中仍有旧的 `running` run。为了降低这种不一致，后续可以让 run-state 同时查询 `agent_run.status`，或者在服务启动时清理本实例遗留的 running run。

## 前端状态恢复

前端建议维护两个来源：

```text
本地即时状态：提交后立即置灰，SSE 结束后恢复。
后端权威状态：页面加载或刷新后调用 run-state 接口确认。
```

推荐流程：

```text
进入 task 页面
  -> GET run-state
  -> sendEnabled=false：按钮置灰，显示正在生成
  -> sendEnabled=true：按钮可用

提交消息
  -> 前端立即置灰
  -> POST stream
  -> 409 AGENT_RUN_IN_PROGRESS：保持置灰并重新查询 run-state
  -> SSE run end/error：恢复按钮，或再次查询 run-state 后恢复
```

如果前端没有收到 SSE 终态事件，例如网络中断或页面切后台，可以在页面重新可见、用户点击输入框或固定间隔内调用 run-state 重新确认。第一版可以先在页面加载和 SSE 终态时查询，后续再增加轻量轮询。

## 后续 Redis 实现映射

Redis 锁可以直接复用当前接口。

推荐 key/value：

```text
key   = agent-run:task:{taskId}
value = {ownerId}:{tokenId}
ttl   = request.ttl
```

获取锁：

```text
SET key value NX PX ttlMillis
```

释放锁需要使用 Lua 脚本做 compare-and-delete，避免误删其他实例的新锁：

```text
if redis.call("GET", key) == value then
  return redis.call("DEL", key)
else
  return 0
end
```

续期同理使用 compare-and-expire：

```text
if redis.call("GET", key) == value then
  return redis.call("PEXPIRE", key, ttlMillis)
else
  return 0
end
```

## 后续数据库实现映射

如果希望锁和业务状态在同一个数据库中，可以新增锁表：

```sql
CREATE TABLE agent_run_lock (
  lock_key VARCHAR(255) PRIMARY KEY,
  owner_id VARCHAR(128) NOT NULL,
  token_id VARCHAR(128) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
```

获取锁语义：

```text
1. INSERT 新 lock_key 成功：获取成功。
2. lock_key 已存在但 expires_at < now：条件 UPDATE owner/token/expires_at，获取成功。
3. lock_key 已存在且未过期：获取失败。
```

释放锁：

```sql
DELETE FROM agent_run_lock
WHERE lock_key = ?
  AND owner_id = ?
  AND token_id = ?;
```

续期：

```sql
UPDATE agent_run_lock
SET expires_at = ?, updated_at = NOW()
WHERE lock_key = ?
  AND owner_id = ?
  AND token_id = ?;
```

## 风险与取舍

- 内存锁不能跨实例生效。多实例部署前必须切 Redis 或数据库实现。
- 服务进程重启会丢失内存锁。如果旧 run 实际也随进程中断，这个行为可以接受；如果后续 run 由独立 worker 执行，则不能继续使用纯内存锁。
- 第一阶段不启用 TTL 时，长回答不会因为锁过期而被误放行，但如果终态释放没有触发，当前进程内该 task 会一直被锁住。
- 后续启用 TTL 后，TTL 过短可能导致长回答过程中锁提前过期，TTL 过长会让异常场景下用户等待更久，因此需要配套 refresh 或任务级取消能力。
- 锁只解决并发提交，不解决幂等重放、断线恢复和结果回放。这些能力应在后续 run 状态机和事件日志中补齐。

## 分阶段落地

第一阶段：

- 新增 `AgentRunLockManager`、`AgentRunLockOwnerProvider` 和锁模型。
- 实现 `InMemoryAgentRunLockManager`。
- 提交流式 run 前按 `taskId` 获取锁。
- 获取失败返回 `409 AGENT_RUN_IN_PROGRESS`。
- run 终态通过 observer 释放锁。
- 前端提交后置灰发送按钮。


第二阶段：

- 增加锁续期能力，避免长 run 被 TTL 误释放。
- 增加运行中状态查询接口，前端刷新后可以恢复按钮状态。
- 将锁冲突信息和 `agent_run.status` 对齐。

第三阶段：

- 根据部署方式切换 Redis 或数据库锁实现。
- 保留相同接口和业务接入点。
- 如引入后台 worker，再补充 worker owner、lease、取消和恢复语义。
