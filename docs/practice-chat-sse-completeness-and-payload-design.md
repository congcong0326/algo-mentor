# Practice Chat SSE 完整性与下行载荷收敛研发设计

> 状态：已实施（2026-08-16）
>
> 范围：仅 Practice Chat 的 `POST /messages` 后 run 级 Redis Stream SSE 通道。

## 1. 背景与原则

Practice Chat 已将 Agent 事件写入每个 run 独立的 Redis Stream，并由
`GET /api/practice-sessions/{sessionId}/runs/{runUuid}/events?after={cursor}` 回放给浏览器。
浏览器用 `content_delta` 实时渲染 assistant 气泡，但在收到终态后仍读取
`GET /api/practice-sessions/{sessionId}/messages?limit=50`，以 PostgreSQL 消息覆盖内存内容。

这会重复传输已经通过 SSE 得到的 assistant 正文；同时，Redis 自动生成的 ID 不能让浏览器发现
中间漏事件，现有通用 SSE mapper 还会把内部 metadata 带入用户通道。

本设计只解决这三个问题，保持以下简单边界：

- PostgreSQL 是消息、run 和业务状态的唯一事实来源；Redis 只是可丢失的实时日志。
- 正常成功 run 不回读聊天消息正文；刷新页面和实时流不完整时仍读取 PostgreSQL。
- 不为了减少一个请求而停止更新必要的派生状态。代码 Review 成功后可读取专用 `/reviews`
  接口更新完成资格；该接口不返回聊天正文。
- 不新增 ACK、消费组、草稿持久化、WebSocket、跨节点 sequence 分配或客户端遥测上报接口。

## 2. 目标与非目标

### 2.1 目标

1. 收到完整成功 SSE 后，不请求 `/api/practice-sessions/{sessionId}/messages?limit=50`。
2. 每个 run 的**公开事件**使用连续 ID `1-0`、`2-0`、`3-0`；前端能检测漏写、回放漏失和协议错误。
3. Practice SSE 只包含前端实际消费的公开 DTO，不包含原始 metadata、诊断数据或任意工具原始结果。
4. HTTP/SSE 错误、EOF 未见成功终态、ID 缺口、非法载荷、`agent_error`、Redis TTL 到期和页面刷新
   都安全回退到 PostgreSQL。

### 2.2 非目标

- 不把 Redis Stream 升级为业务事实来源，也不以 Redis 写入结果决定 Agent 成功、失败或工具副作用。
- 不迁移其他 Agent 场景、通用调试 SSE 或管理员审计 API。
- 不引入正文 hash。当前 Agent 的最终输出来自最后一个无工具 step，前端按同一规则归并即可。
- 不支持多进程共同写同一 run。单 run 仍只有一个订阅者和一个 Redis 写入出口；重启后的遗留
  run 沿用现有收束策略。

## 3. 关键决策

| 决策 | 结论 |
| --- | --- |
| 成功条件 | 从 `1-0` 开始连续处理所有公开事件，且收到当前 `runUuid` 的 `agent_run_end`，未收到 `agent_error`。 |
| 成功后的读取 | 不读取 `/messages`、`/active-run` 或完整 session；只有本轮实际保存 Review 时读取一次 `/reviews`。 |
| Redis ID | 使用显式的 `N-0`；序号只为实际写入的公开事件分配。 |
| 载荷边界 | `PracticeRealtimeEventPayloadMapper` 先投影，未知事件或未知工具结果默认不公开。 |
| 终态回放 | 事件接口按 session 所属用户和 task/run 归属授权，不以 run 是否仍 active 作为访问条件。 |
| 回退 | 仅在实时不完整、失败或刷新时读取 PostgreSQL 消息历史。 |

## 4. 公开 SSE 协议

### 4.1 事件白名单

`AgentStreamEvent.metadata` 是内部运行上下文，绝不是浏览器 DTO。Practice 专用 mapper 位于
`mentor-api` 的 realtime 边界，不改变 `agent-core` 事件模型，也不影响 PostgreSQL trace 和管理员审计。

| SSE event | 公开字段 | 处理规则 |
| --- | --- | --- |
| `content_delta` | `content` | 仅当前 step 的正文增量。 |
| `agent_step_start` | `runId`, `stepIndex` | 用于维护 step 缓冲。 |
| `agent_step_end` | `runId`, `stepIndex`, `finishReason`, `toolCallCount` | `toolCallCount=0` 时确认该 step 正文。 |
| `agent_tool_start` | `runId`, `stepIndex`, `toolCallId`, `toolName` | 仅为 4.2 列出的工具发送，用于现有状态展示。 |
| `agent_tool_end` | `runId`, `stepIndex`, `toolCallId`, `toolName`, `result` | 仅为 4.2 列出的工具发送。 |
| `agent_run_end` | `runId`, `steps`, `finishReason` | 成功终态。 |
| `agent_error` | `runId`, `code`, `message`, `retryable` | 失败终态，见 4.3。 |

`agent_run_start`、`message_start`、`message_end`、`usage`、`heartbeat`、`tool_call_*`、未列出的事件，
以及 4.2 之外工具的 start/end 均不写入 Practice Stream。被过滤的事件不占 sequence。

### 4.2 工具结果白名单

`agent_tool_end.result` 不得直接复用 Agent 工具原始 `JsonNode`。仅下列三种工具允许携带结果：

| 工具 | 公开 `result` 字段 |
| --- | --- |
| `submit_practice_code_review` | `type`, `status`, `totalScore`, `passed`, `failureCode?` |
| `propose_current_problem_coach_summary` | `type`, `status`, `proposalId`, `summaryMarkdown`, `operation` |
| `update_learner_declared_profile` | `type`, `status` |

其余工具的 start/end 和 result 都不公开；`agent_step_end.toolCallCount` 足以维持 step 归并。新增工具
必须先在 mapper 增加公开 DTO 和前端消费测试，否则默认完全过滤。

下列字段一律禁止出现在 SSE、Redis realtime envelope 或上述工具 DTO 中：

- `aiAdmission`、lock token、准入/配额/路由/provider 配置；
- `userId`、`runDbId`、`turnId`、`sessionId`、review ID 等内部关联 ID；
- prompt snapshot/hash、scope reference、原始 metadata；
- 历史提交源码、笔记正文、学习者记忆正文及任意未明确列出的工具字段。

### 4.3 失败载荷

`agent_error.code` 只能是前端已识别的稳定错误码；未知错误投影为
`PRACTICE_RUN_FAILED`。`message` 使用固定、可本地化的用户文案，不能使用异常原文；`retryable`
保留布尔值。异常 metadata、provider 返回体和 cause 均不公开。

## 5. 连续 Redis Stream ID

### 5.1 单一写入算法

sequence、公开投影和 `XADD` 必须在同一个 `PracticeRealtimeEventStore` 内完成。应用层的
`PracticeChatRunEventSubscriber` 继续只调用 `append(runUuid, rawEvent)`，不感知哪些事件公开，也不维护
sequence。

```text
append(runUuid, rawEvent):
  publicEvent = payloadMapper.map(rawEvent)
  若 publicEvent 为空：直接返回，不分配 sequence

  sequence = 本 run 的 nextSequence
  nextSequence += 1
  尝试 XADD stream {sequence}-0 serializedPublicEvent

  XADD 成功：事件可回放
  投影后的序列化或 XADD 失败：记录 append failure，绝不回收或复用 sequence
```

因此，过滤事件不会制造 gap；真正应该公开但未能写入的事件会让后续事件留下 gap，或在终态也未
写入时以 EOF 未见终态触发回退。Redis 写入仍异步提交，不等待 Redis 响应，也不取消 Agent。

每个新 run 从 `1-0` 开始，`0-0` 仅表示首次读取 cursor。v2 stream 只接受 `0-0` 或 `N-0`
作为 `after`；服务端和前端都必须拒绝其他格式。该约束不应用到 legacy v1 stream。

### 5.2 终态流访问

`agent_run_end` 在当前运行时会在 assistant 消息和 run 成功状态持久化之后发出。事件接口因此必须允许
浏览器读取仍在 Redis 保留期内的终态 stream，避免“run 太快结束，首次 SSE GET 已返回 404”的竞态。

控制器按以下顺序授权：

1. 验证当前用户拥有 `sessionId`；
2. 取得该 session 的 `agentTaskId`；
3. 通过一个最小的 repository 查询确认 `runUuid` 属于该 task；
4. 允许读取 active 或已结束 run 的 Redis Stream。

Stream 已过期或不存在时返回现有实时不可用/不存在结果，前端进入 PostgreSQL 回退；不得把任意
`runUuid` 仅凭 session 归属开放给用户。

## 6. 前端状态机

### 6.1 成功路径

`PracticeRunStreamState` 保存 `nextExpectedSequence=1`、`lastEventId`、是否收到错误以及本轮是否保存
Review。每个 SSE block 必须先严格解析 JSON 和 ID，再应用事件：

```text
event.id 必须为 nextExpectedSequence + "-0"
  -> 处理事件；lastEventId = event.id；nextExpectedSequence += 1
否则
  -> realtimeIncomplete = true；停止把本流当作最终消息
```

`agent_run_end.runId` 必须等于当前 `runUuid`。`content_delta` 放入当前 step 缓冲；`agent_step_end`
的 `toolCallCount=0` 才确认该 step 正文，工具 step 的正文丢弃。这样前端的 assistant 正文和
`AgentOutput.text()` 的最终无工具 step 语义一致。

完整成功后：

- 保留当前内存中的用户消息、最终 assistant 正文和已投影的工具状态；
- 本地清除 `activeRun`，恢复输入框；
- 不请求 `/messages`、`/active-run` 或完整 session；
- 若本轮 `submit_practice_code_review` 的公开结果 `status=SAVED`，请求一次 `/reviews`，用返回的
  `latestReview` 和 `completionGate` 更新本地状态；这不影响 assistant 正文确认。

`/reviews` 请求失败只展示状态刷新错误，不把已完整确认的消息降级为不完整；后续标记完成仍由服务端
重新校验。

### 6.2 回退路径

下列任一情况标记为 realtime incomplete：HTTP 非 2xx、网络错误、严格 JSON 解析失败、非法/重复/
倒退 ID、EOF 未见匹配的 `agent_run_end`、`agent_error`、页面切换或主动 abort。

回退步骤保持现有简单逻辑：先查询 active run；仍在运行则保持“生成中”并从最后一个成功 ID 重连；
已结束则读取 `/messages`，以 PostgreSQL 消息替换临时气泡。页面刷新始终通过既有 session 创建/读取流程
恢复消息，不依赖旧页面内存。

## 7. 实施位置

| 模块 | 主要改动 |
| --- | --- |
| `backend/mentor-api` | 新增 `PracticeRealtimeEventPayloadMapper`；Redis store 在投影后分配 sequence 并使用显式 `XADD` ID；events 接口改为 task/run 归属校验，允许保留期内终态回放。 |
| `backend/agent-core`、`mentor-application` | 不改 Agent 事件模型和 `PracticeChatRunEventSubscriber` 的职责。仅为 task/run 归属查询增加最小读取端口。 |
| `frontend/src/services` | SSE parser 对 Practice v2 暴露 JSON 解析错误，不能把非法 JSON 当普通字符串继续处理。 |
| `frontend/src/learning-plans/PracticeChatWorkbench.tsx` | 实现 sequence 校验、step 缓冲和成功免 `/messages`；仅在保存 Review 时刷新 `/reviews`。 |
| `backend/ops-observability` | 在服务端记录公开事件 append 成功/失败和按事件类型的 payload bytes；不新增客户端 telemetry 通道。 |

## 8. 测试与验收

### 8.1 后端

1. 被过滤的 LLM 生命周期事件不占 sequence；首个实际公开事件为 `1-0`。
2. 公开事件 sequence 严格递增；XADD 失败后不复用 ID，下一次成功写入留下 gap。
3. 所有公开 payload 均不含 metadata、内部 ID、prompt/route/admission 字段；未知工具和历史详情工具
   完全不写入 Stream。
4. 三种允许携带 result 的工具严格只包含 4.2 所列字段；未知异常只产生安全 `agent_error`。
5. run 已落库结束但 Redis 仍保留时，首次 events GET 能完整回放至 `agent_run_end`；其他 task 的 runUuid
   被拒绝。
6. v2 cursor 仅接受 `0-0` 或 `N-0`，Redis/投影故障不取消 Agent，最终消息仍正确落库。

### 8.2 前端

1. 连续 `1-0` 至匹配 `agent_run_end` 时不调用 messages、active-run 或完整 session。
2. 保存 Review 的完整成功 run 只调用一次 `/reviews`，并更新 `completionGate` 使完成按钮可用。
3. `1-0` 后收到 `3-0`、重复/倒退 ID、非法 ID、非法 JSON、EOF 无成功终态和 `agent_error` 都进入回退。
4. 多 step run 只保留最后一个无工具 step 正文；刷新仍从持久化 session 恢复。

### 8.3 验收标准

1. 正常无 Review 的 run 在 `agent_run_end` 后没有 `/messages`、`/active-run` 或完整 session 请求。
2. Review 成功 run 在终态后最多增加一次不含消息正文的 `/reviews` 请求。
3. 注入一次中间 XADD 失败后，浏览器检测 gap，run 结束后从 PostgreSQL 恢复正确消息。
4. 任意 Practice SSE payload 不包含 admission、prompt snapshot、内部 ID 或原始 metadata。
5. Redis 不可用、流连接中断和快速结束的 run 都不改变 PostgreSQL 业务终态。

## 9. 发布与回滚

`POST /messages` 的订阅响应新增 `realtimeProtocolVersion`：

- 新建 v2 run 返回 `2`；前端仅在值为 `2` 时启用连续 ID 和成功免消息回读。
- 缺失或 `1` 使用现有回读逻辑。idempotency replay 的既有 stream 视为 v1，不尝试迁移其 Redis ID。
- 后端先支持 v2 写入与 v1 响应；前端发布后再将新建 run 切到 v2。
- 回滚只需让新建 run 返回 `1` 并使用旧写入策略；已存在 v2 stream 由前端 v1 分支回读 PostgreSQL
  收束，无需修改已落库业务数据。

## 10. 实施记录与验证

本设计已按既定边界实施：Practice realtime store 先投影公开 DTO 后分配连续 sequence，使用
Lettuce `XAddArgs.id(N-0)` 显式写入 Redis Stream；run metadata 记录 realtime protocol v2，events
接口按 session、task 和 run 归属授权已结束 run 的终态回放。前端仅对 v2 启用严格 event/step 校验和
免消息回读收束，实时不完整时继续使用 PostgreSQL 回退。

已执行的验证命令：

```bash
npm --cache ./.npm --prefix frontend test -- --run \
  src/learning-plans/PracticeChatWorkbench.test.tsx src/services/api.test.ts

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest=PracticeRealtimeEventPayloadMapperTest,PracticeRealtimeCursorTest,PracticeSessionControllerTest,LettucePracticeRealtimeEventStoreIT \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application,ops-observability -am \
  -Dtest=AgentConversationServiceTest,PracticeMessageStreamServiceTest,MicrometerOpsRecordersTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application,agent-persistence-postgres -am \
  -Dtest=PracticeChatRunEventSubscriberTest,PostgresAgentConversationRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest=LettucePracticeRealtimeEventStoreLocalRedisIT \
  -Dpractice.realtime.local-redis.it=true \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

最后一条命令连接本机独立 Streams Redis（默认 `127.0.0.1:6380`），已实际验证正常回放和一次
中间 XADD 失败后不复用 sequence、后续终态以 `2-0` 写入。Testcontainers 用例保留用于 Docker 环境；
当前环境无 Docker socket，因此该套件按 `disabledWithoutDocker` 自动跳过。
