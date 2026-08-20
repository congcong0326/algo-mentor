# 学习计划 AI 生成 Redis Stream 回放、启动观察分离与显式取消研发设计

## 1. 文档信息

- 原始设计日期：2026-08-04
- Redis Stream 方案更新：2026-08-20
- 状态：设计更新完成，第一阶段待实施
- 当前实施范围：学习计划 AI 草案首次生成；草案 AI 修订列为第二阶段
- 关联接口：`/api/learning-plans/drafts/*`
- 关联线程模型：`docs/agent-thread-model-refactoring-design.md`
- 关联 SSE 基础设施设计：`docs/sse-managed-connection-heartbeat-design.md`
- 当前部署边界：单实例 `mentor-api`

本设计的 Redis Stream 改造按以下顺序推进：

1. 第一阶段：首次 AI 草案创建改为启动、Redis Stream 回放、数据库查询三段式链路。
2. 第二阶段：草案 AI 修订以 revisionId 复用同一链路。
3. 后续阶段：增加基于领域 ID 的显式取消，并正确处理取消与完成竞争。

`ManagedSseConnection`、连接写锁、连接注册表和 heartbeat 调度器已经拆分到独立设计，不是本设计的发布前置条件。

## 2. 背景

学习计划当前存在两类草案创建方式：

1. 用户选择模板，后端通过确定性业务逻辑同步创建 `LearningPlanDraft`。
2. 用户填写目标和约束，后端调用 Agent/LLM 异步生成 `LearningPlanDraft`。

模板入口使用普通 HTTP JSON：

```text
POST /api/learning-plans/drafts/from-template
  -> 同步创建 GENERATED 草案
  -> 返回完整 LearningPlanDraftResponse
```

当前 AI 入口把任务启动、进度推送和最终草案交付放在同一个 POST SSE 响应中：

```text
POST /api/learning-plans/drafts/stream
  -> 完成 AI 准入并启动 Agent
  -> 在 AgentRunStart 后创建初始草案
  -> SSE 推送 work_* 事件
  -> 最终通过 draft_ready 返回完整草案
```

后端在 AI 准入成功、Agent 发出启动事件后创建草案记录，并在发送 `draft_ready` 前将生成结果写入数据库。这样并发锁拒绝的请求不会创建草案或消耗每日草案额度。但是前端只有收到最终 SSE 事件后才能获得草案。如果连接在终态前中断，可能出现：

- 后端继续完成 Agent 调用并成功落库。
- 前端没有收到最终草案，也没有稳定的恢复入口。
- 用户重试后再次调用模型，增加延迟和费用。
- SSE 连接失败被错误地等同于学习计划生成失败。

根因不是 SSE 能否承载 JSON，而是当前把一次不可恢复的传输连接作为最终业务结果的唯一交付通道。

## 3. 当前实现问题

### 3.1 SSE 承担了过多职责

当前 AI 草案 SSE 同时承担启动任务、展示进度和返回完整结构化草案。最终草案属于可持久化业务资源，必须能够通过普通查询接口重新获取。

### 3.2 传输断开与业务取消语义混淆

TCP 断开可能来自移动网络切换、代理超时、浏览器暂时失联或服务端写失败，并不代表用户希望停止生成。必须区分：

- 传输连接断开：只停止当前观察，后台生成继续。
- 用户显式取消：取消对应 Agent/LLM subscription，并写入取消终态。

### 3.3 草案生成状态不完整

当前 `LearningPlanDraftStatus` 已有 `GENERATION_FAILED`，但没有 `GENERATING` 和生成取消状态。有效请求在 Agent 准入成功后创建的初始草案暂时使用 `COLLECTING`，无法准确表达“输入完整，正在生成”。

### 3.4 取消链路没有成为正式契约

当前 SSE Subscriber 取消的是学习计划业务事件 publisher。学习计划内部 Agent Subscriber 仍可能继续请求上游事件并完成落库。这种行为属于取消链路未显式建模后的副作用，不能作为正式契约依赖。

### 3.5 前端没有验证 SSE 业务终态

前端事件读取器在流正常 EOF 时返回。首次草案创建没有在 `await stream...` 返回后验证是否收到 `draft_ready` 或 `draft_error`，可能在没有业务终态的情况下继续停留在生成状态，或者只能让用户重新提交。

## 4. 设计目标

1. 模板草案和 AI 草案继续共用统一的 Draft 生命周期。
2. 模板创建保持同步，不引入 SSE 或异步任务概念。
3. AI 生成在启动后立即向客户端返回稳定的 `draftId`。
4. 数据库中的草案状态和结果是唯一事实来源。
5. SSE 只负责进度和终态通知，不承载权威完整结果。
6. SSE 断开后，前端能够按 `draftId` 查询并恢复最终结果。
7. 网络断开不自动取消 Agent。
8. 用户显式取消时能够取消 Agent 和 LLM subscription。
9. 使用幂等键避免网络重试重复创建草案或重复调用模型。
10. SSE 生命周期、业务任务生命周期和指标语义明确分离。

## 5. 非目标

1. 本设计不建设通用 AI 任务中心，不引入公共 `operationId`。
2. 本设计不建设持久化 Workflow、DAG、任务队列或跨节点调度器。
3. 本设计不保存原始 Agent 事件、完整草案或完整错误信息到 Redis；仅保存版本化、低敏、白名单的短期回放事件。
4. 本设计不实现 `ManagedSseConnection`、heartbeat、连接写锁或通用连接注册表。
5. 本设计不改变 Practice Chat、通用 Agent 会话等其他 SSE 场景的取消语义。
6. 本设计不解决应用进程重启后继续执行尚未完成的 Agent 任务；重启恢复只负责识别并收敛遗留状态。
7. 本设计不支持多实例之间共享 Agent cancellation handle 或实时 SSE 事件。
8. 第一阶段不迁移草案修订、正式计划扩展和扩展修订；草案修订在第二阶段以 revisionId 复用本设计验证完成的查询与 Redis Stream 观察模式，正式计划扩展继续后置。

## 6. 核心设计决策

### 6.1 Draft 是业务资源，生成任务不是新的公共资源

模板创建和 AI 创建后都需要支持预览、补充信息、修订和确认，因此都创建 `LearningPlanDraft`。首次生成已经有稳定的 `draftId` 承载状态，不额外引入 `operationId`。

| 场景 | 领域标识 | 是否异步 | 是否使用 SSE |
| --- | --- | --- | --- |
| 从模板创建草案 | `draftId` | 否 | 否 |
| AI 创建草案 | `draftId` | 是 | 是，可选观察通道 |
| 确认草案 | `draftId -> planId` | 否 | 否 |

### 6.2 数据库是最终结果的唯一事实来源

AI 生成的最终结构化结果必须先提交数据库，再尝试发送 SSE 终态通知：

```text
生成业务结果
  -> 校验结果
  -> 事务写入 Draft
  -> 提交事务
  -> 尝试发送 draft_completed
```

SSE 发送失败不能回滚已经提交的业务结果，也不能把成功生成重新标记为失败。

### 6.3 Redis Stream 是可选、可回放的观察通道

2026-08-20 更新：业务 Agent Subscriber 依次将公开事件写入每个 draft 独立的 Redis Stream；SSE GET 接口只负责按 after 游标执行回放。Redis Stream 是短期、尽力而为的实时日志，不是任务队列、业务事实来源或跨节点协调机制。

公开事件只包含白名单工作状态和终态资源引用。完成事件只携带 draftId；失败事件只携带 draftId 和稳定错误码。完整草案、原始模型输出、工具原始结果、完整用户输入、metadata、路由与准入信息均不得写入 Redis 或 SSE。前端收到终态后必须通过普通 JSON 接口获取权威草案或受控错误。

Redis 写入、过期设置或读取失败只记录低敏日志和指标，绝不能取消 Agent、改变草案业务终态或阻塞后续 Agent 事件。浏览器可用 SSE id 记录最后成功处理的 Redis entry，并在断线后按 after 游标补发。

SSE 只发送工作状态、Tool 开始和结束、完成、失败或取消通知。SSE 终态只携带 `draftId` 和必要的低敏状态，前端收到终态后通过普通 JSON 接口获取权威草案。

### 6.4 启动、观察和取消是三个独立动作

```text
POST generation       -> 创建并启动业务任务
GET generation events -> 观察任务进度
POST cancel            -> 用户明确终止业务任务
```

观察连接不存在、断开或写失败，都不能改变业务任务状态。只有显式取消 API 可以触发上游 subscription 取消。

### 6.5 显式取消采用协作式中断

显式取消不使用 `Thread.stop()` 或其他强制终止手段。取消 handle 调用 Agent Runtime Publisher 的 `Flow.Subscription.cancel()`，复用当前 Agent 底座已经存在的协作式取消链路：

```text
Flow.Subscription.cancel()
  -> SingleSubscriberAgentStreamPublisher 取消
  -> AgentCancellationToken.cancel()
  -> 取消当前 LLM subscription
  -> interrupt Agent 工作线程
  -> Agent loop 在取消检查点收敛为 CANCELLED
  -> Agent run、治理租约和持久化状态收敛为 CANCELLED
```

协作式取消保证取消信号能够传递到 Agent loop 和当前 LLM 流，但不承诺强制终止任意正在执行的 Tool。Tool 如果响应线程中断，可以及时退出；如果忽略中断或执行不可中断 I/O，则允许它在返回后由 Agent loop 的下一个取消检查点停止后续步骤。

取消成功后不得再提交 `GENERATED`、调用下一轮 LLM 或启动新的 Tool。已经执行的 Tool 外部副作用不由取消自动回滚。

### 6.6 兼容改造先于最终接口切换

2026-08-20 更新：旧的 POST /drafts/stream 只作为短期兼容入口保留，不能承载新功能。第一阶段以前端和后端同次切换为目标，直接落地“启动 -> Redis Stream 观察 -> 数据库查询”；旧接口在切换后标记为待删除。

第一阶段先在现有 `/drafts/stream` 上补 `draftId`、幂等键和查询恢复，避免一次性同时改动数据库、后端执行模型和前端交互协议。恢复闭环稳定后，再切换为“启动 -> 观察 -> 查询”。

## 7. 领域状态与生成元数据

### 7.1 草案状态

`LearningPlanDraftStatus` 调整为：

```text
COLLECTING
GENERATING
GENERATED
GENERATION_FAILED
GENERATION_CANCELLED
CONFIRMED
EXPIRED
```

| 状态 | 语义 |
| --- | --- |
| `COLLECTING` | 输入信息不足，等待用户补充，不存在正在运行的 Agent |
| `GENERATING` | 输入完整，AI 正在生成草案 |
| `GENERATED` | 草案已生成并可预览、修订或确认 |
| `GENERATION_FAILED` | AI 生成失败，保存稳定错误信息，可按规则重试 |
| `GENERATION_CANCELLED` | 用户显式取消生成 |
| `CONFIRMED` | 草案已经转换为正式计划 |
| `EXPIRED` | 草案超过有效期 |

状态流转：

```text
有效输入：GENERATING -> GENERATED
                     -> GENERATION_FAILED
                     -> GENERATION_CANCELLED

缺少输入：COLLECTING
模板创建：GENERATED
```

`GENERATING` 的三个业务终态互斥，任何一方都不能覆盖已经提交的其他终态。

### 7.2 生成元数据

首次生成元数据保存在 `learning_plan_draft`：

```text
generation_request_key UUID
generation_request_fingerprint VARCHAR(64)
generation_run_id UUID
generation_error_code VARCHAR(120)
generation_error_message TEXT
generation_started_at TIMESTAMPTZ
generation_completed_at TIMESTAMPTZ
```

数据库约束：

```text
UNIQUE (user_id, generation_request_key)
WHERE generation_request_key IS NOT NULL
```

请求指纹由服务端对规范化请求 JSON 计算稳定 SHA-256，用于识别“相同幂等键、不同请求体”。`Idempotency-Key`、状态值、SSE 事件名、错误码和 API 路径必须放入常量类或枚举统一维护。

## 8. API 设计

### 8.1 模板创建草案

保留当前同步接口：

```http
POST /api/learning-plans/drafts/from-template
Content-Type: application/json
```

成功后直接返回完整 `LearningPlanDraftResponse`，状态为 `GENERATED`。该接口不创建异步执行记录，不使用 SSE，也不写入待恢复状态。

### 8.2 阶段一兼容接口

现有接口暂时保留：

```http
POST /api/learning-plans/drafts/stream
Idempotency-Key: <client-generated-uuid>
Accept: text/event-stream
```

兼容期要求：

1. 后端在启动 Agent 前创建 `GENERATING` 草案。
2. SSE 响应在 body 开始前返回 `X-Learning-Plan-Draft-Id`。
3. 首个业务事件也携带 `draftId`。
4. 最终 `draft_ready` 暂时保留完整草案，兼容旧前端。
5. 相同用户和相同幂等键返回相同 `draftId`，不得重复启动 Agent。
6. 如果既有草案仍为 `GENERATING`，兼容接口发送 `draft_in_progress` 后关闭本次 SSE，前端按 `draftId` 查询，不尝试重新订阅原有单订阅流。

### 8.3 启动 AI 草案生成

最终启动接口：

```http
POST /api/learning-plans/drafts/generations
Idempotency-Key: <client-generated-uuid>
Content-Type: application/json
```

输入完整并启动成功时返回 `202 Accepted`：

```json
{
  "success": true,
  "data": {
    "draftId": 102,
    "status": "GENERATING"
  }
}
```

同时返回：

```text
Location: /api/learning-plans/drafts/102
```

输入缺失时不启动 Agent，直接创建 `COLLECTING` 草案并返回 `201 Created` 普通 JSON。

幂等规则：

1. 同一用户使用相同 `Idempotency-Key` 重试时返回同一个 `draftId`。
2. 已存在 `GENERATING` 草案时不得再次启动 Agent。
3. 已进入终态时返回当前资源，不重复调用模型。
4. 同一个幂等键携带不同请求体时返回稳定冲突错误。

### 8.4 查询草案

```http
GET /api/learning-plans/drafts/{draftId}
```

生成中响应：

```json
{
  "success": true,
  "data": {
    "draftId": 102,
    "status": "GENERATING",
    "assistantMessage": "正在生成学习计划草案。",
    "missingFields": [],
    "draftPlan": null,
    "error": null
  }
}
```

生成完成后返回完整 `draftPlan`。失败或取消时返回受控错误对象，不返回原始 provider 错误和用户隐私内容。查询必须按当前受信用户过滤，禁止只按 `draftId` 查询。

### 8.5 观察 AI 草案生成

2026-08-20 更新：接口改为 GET /api/learning-plans/drafts/{draftId}/events?after={cursor}。after 缺失时等同 0-0；服务端以 XREAD 先回放已有 entry，再以有界 XREAD BLOCK 等待新 entry。每个 Redis entry ID 原样写入 SSE id，客户端只能在成功处理该事件后推进本地 cursor。

本节中“注册当前观察者”的旧表述不再适用。业务执行层不持有浏览器连接；若 Redis Stream 不可用、保留期已过或 SSE 正常关闭前未取得终态，前端必须读取草案查询接口。若数据库仍为 GENERATING，则使用最近 cursor 重新订阅，或在 Redis 持续不可用时有限退避轮询。

```http
GET /api/learning-plans/drafts/{draftId}/events
Accept: text/event-stream
```

连接建立前先读取草案状态：

- `GENERATING`：注册当前 SSE 观察者。
- `GENERATED`：立即发送 `draft_completed` 并关闭。
- `GENERATION_FAILED`：立即发送 `draft_failed` 并关闭。
- `GENERATION_CANCELLED`：立即发送 `draft_cancelled` 并关闭。
- 其他状态：返回与状态匹配的终态或稳定错误。

事件名：

```text
work_start
work_progress
work_tool_start
work_tool_end
draft_completed
draft_failed
draft_cancelled
```

终态示例：

```text
event: draft_completed
data: {"draftId":102}
```

本接口只定义业务观察语义。连接串行写、heartbeat 和连接关闭实现由 `docs/sse-managed-connection-heartbeat-design.md` 负责；在该基础设施落地前，可以继续使用现有 `SseEmitter` Subscriber 完成业务协议迁移。

### 8.6 取消 AI 草案生成

```http
POST /api/learning-plans/drafts/{draftId}/generation/cancel
```

语义：

1. 仅当前用户可以取消自己的草案生成。
2. `GENERATING` 状态下先通过条件更新提交 `GENERATION_CANCELLED`，再向对应 Agent subscription 派发协作式取消信号。
3. 已进入 `GENERATED`、`GENERATION_FAILED` 或 `GENERATION_CANCELLED` 时幂等返回当前状态。
4. 取消与完成竞争时，使用条件更新确保只有一个终态提交成功。
5. SSE 断开、页面卸载或浏览器关闭不得自动调用本接口。
6. 接口返回 `GENERATION_CANCELLED` 表示业务取消终态已经提交且取消信号已经登记或派发，不表示任意正在运行的 Tool 已被强制停止。
7. Agent Runtime 后续产生的取消事件不得把草案改写为 `GENERATION_FAILED`。

成功响应示例：

```json
{
  "success": true,
  "data": {
    "draftId": 102,
    "status": "GENERATION_CANCELLED"
  }
}
```

## 9. 后端执行模型

### 9.1 启动与观察分离

```text
Tomcat 启动请求线程
  -> 校验用户、请求和 Idempotency-Key
  -> 创建 GENERATING Draft
  -> 启动并订阅 Agent Publisher
  -> 注册 draftId 对应的 cancellation handle
  -> 返回 draftId

Agent 工作线程
  -> 阻塞读取 LLM stream
  -> 执行 Agent loop 和 Tool
  -> 更新草案状态和结果
  -> 尝试向当前观察者发送进度或终态

SSE 观察请求
  -> 校验草案归属和当前状态
  -> 注册当前观察者
  -> 返回 SseEmitter
```

启动 AI 的业务 Subscriber 独立于任意 SSE 连接存在。没有观察者、观察者断开或 SSE 写失败都不能停止业务 Subscriber。

### 9.2 与现有 Agent 线程模型的关系

本设计保留 `docs/agent-thread-model-refactoring-design.md` 固定的 Agent 工作线程模型，不新增 Agent 执行池、事件投递池或每连接发送队列。本次只改变学习计划域的生命周期语义：

- SSE 断开不再等同于取消 Agent。
- 业务结果提交不依赖 SSE 发送成功。
- 终态结果可通过普通 HTTP 查询恢复。

SSE 连接自身的并发写和 heartbeat 调度由独立设计处理，不阻塞本设计前三阶段发布。

### 9.3 业务任务控制注册表

为运行中的首次草案生成维护单实例内存控制注册表：

```text
draftId -> Agent cancellation handle
```

该注册表只负责业务任务控制，不持有 `SseEmitter`，也不承担观察连接生命周期。取消 handle 直接封装 `AgentRuntime.stream(...)` 返回 Publisher 在 `onSubscribe` 中提供的 `Flow.Subscription`，不重复实现 Agent 取消协议。

注册表槽位必须在启动 Agent 前创建，至少表达：

```text
STARTING
RUNNING(handle)
CANCEL_REQUESTED
TERMINATED
```

取消 handle 最终调用 Agent 上游 `Flow.Subscription.cancel()`，由现有 `SingleSubscriberAgentStreamPublisher` 和 `AgentCancellationToken` 继续取消 LLM subscription、关闭 provider stream 并中断 Agent 工作线程。

注册表要求：

- Agent 订阅建立后注册。
- 成功、失败、取消和启动异常时统一移除。
- 启动接口返回 `202` 前必须已经创建控制槽位；正常情况下应完成 handle 注册。
- handle 注册前收到取消请求时，将槽位置为 `CANCEL_REQUESTED`，订阅建立后立即调用 `cancel()`，不能短暂丢失取消信号。
- 显式取消接口找不到 handle 时重新读取数据库状态，避免把内存注册表作为事实来源。
- 应用停止时取消或收敛所有仍在运行的 handle。

### 9.4 协作式取消执行链

取消请求固定执行链：

```text
校验草案归属
  -> 条件更新 GENERATING -> GENERATION_CANCELLED
  -> 控制注册表登记或派发 cancel
  -> Flow.Subscription.cancel()
  -> AgentCancellationToken.cancel()
     -> 当前 LLM subscription.cancel()
     -> Agent 工作线程 interrupt()
  -> Agent loop 在当前等待或下一个检查点退出
  -> Agent run / 治理租约 / 持久化观察者记录 CANCELLED
  -> 尝试发送 draft_cancelled
```

当前 Agent 底座已具备以下能力，本设计应直接复用：

- `SingleSubscriberAgentStreamPublisher` 将下游 subscription 取消传递到 `AgentCancellationToken`。
- `AgentCancellationToken` 同时保存当前 LLM subscription 和 Agent 工作线程。
- OpenAI compatible 流取消时关闭 SDK stream，并中断当前流消费线程。
- Agent loop 在 step 开始、LLM 返回后、Tool 调用前后和权限等待路径检查取消。
- Agent Runtime 将治理租约收敛为 `CANCELLED`，持久化 observer 将 Agent run 记录为 `cancelled`。

业务层必须补齐当前首次草案生成缺失的部分：

- 在 Agent Subscriber 的 `onSubscribe` 中把 subscription 注册为 `draftId` 对应的 handle。
- 显式取消由取消 API 调用 handle，不能由 SSE 断开调用。
- 取消后的 Agent 错误不得复用普通 `failDraft(...)` 写入 `GENERATION_FAILED`。
- Agent 成功、失败和取消对草案的写入都必须使用 `WHERE status = 'GENERATING'` 的条件更新。
- 如果 Tool 在取消后才返回，其结果不得驱动下一轮模型调用或覆盖 `GENERATION_CANCELLED`。

### 9.5 业务观察者边界

2026-08-20 更新：本节改为 Redis Stream 读取模型。业务执行层不感知浏览器或 SseEmitter，只通过 run 级唯一事件出口追加公开进度或终态事件。SSE Controller 在鉴权后按 after 回放，读到终态或数据库已终态时关闭连接；连接读写失败只结束当前观察，绝不取消业务订阅。

业务执行层只感知“是否存在可接收事件的观察者”，不直接依赖 heartbeat 或连接写锁。观察适配器至少支持：

- 按 `draftId` 注册和移除观察者。
- 发送普通进度事件。
- 发送终态后结束当前观察。
- 观察写失败后移除观察者，但不取消业务任务。
- 注册后再次核验数据库状态，避免错过已经提交的终态。

底层可以先复用现有 SSE Subscriber，后续再由 `ManagedSseConnection` 统一承载 `SseEmitter` 操作。

## 10. 前端状态机

### 10.1 模板草案

```text
提交模板请求
  -> 普通 JSON 返回 GENERATED Draft
  -> 直接进入预览
```

### 10.2 阶段一兼容恢复

```text
生成 Idempotency-Key
  -> POST /drafts/stream
  -> 从响应头或首个事件取得 draftId
  -> sessionStorage 保存 pending generation
  -> 收到 draft_ready：展示兼容响应中的完整草案
  -> SSE 异常或无终态 EOF：GET /drafts/{draftId}
```

如果连接在客户端取得 `draftId` 前中断，使用原 `Idempotency-Key` 重试兼容接口。后端必须返回同一草案，不能再次调用模型。

### 10.3 最终正常路径

2026-08-20 更新：事件订阅必须显式带 after=0-0，短暂断线使用最后成功处理的 cursor 重连。只有 Redis 持续不可用或 Stream 已过期时才进入数据库退避轮询。

```text
生成 Idempotency-Key
  -> POST /drafts/generations
  -> 获得 draftId
  -> sessionStorage 保存 pending generation
  -> GET /drafts/{draftId}/events
  -> 收到 draft_completed
  -> GET /drafts/{draftId}
  -> 展示草案
  -> 清除 pending generation
```

### 10.4 SSE 断线路径

网络异常、无终态 EOF、SSE 超时或页面刷新后发现未完成 `draftId` 时，统一进入查询恢复：

```text
GET /drafts/{draftId}
  -> GENERATING：继续轮询
  -> GENERATED：展示草案并停止轮询
  -> GENERATION_FAILED：展示稳定错误并停止轮询
  -> GENERATION_CANCELLED：展示取消状态并停止轮询
```

建议前几次每 2 秒查询，随后退避至每 5 秒；页面不可见时降低频率；达到前端最大等待时间后停止自动轮询，但保留手动刷新能力。

### 10.5 幂等键生命周期

- 前端在发送启动请求前生成 UUID。
- 收到明确终态前，自动重试继续使用同一个幂等键。
- 用户明确点击“重新生成”时创建新幂等键。
- 页面刷新时保存 `draftId` 和幂等键。
- 不依赖 `pagehide`、`beforeunload` 或 `sendBeacon` 自动取消任务。

### 10.6 显式取消交互

只有用户点击明确的取消命令时调用取消接口：

```text
点击取消生成
  -> 禁用重复点击
  -> POST /drafts/{draftId}/generation/cancel
  -> 按返回状态收敛界面
  -> GET /drafts/{draftId} 获取权威状态
```

如果取消请求网络失败，不能假设任务已经取消。前端继续按 `draftId` 查询，直到读取到某个业务终态。

## 11. 并发与一致性

### 11.1 完成、失败与取消竞争

所有终态更新必须带当前状态条件：

```text
UPDATE learning_plan_draft
SET status = 'GENERATED', ...
WHERE id = ? AND user_id = ? AND status = 'GENERATING'
```

取消同理：

```text
UPDATE learning_plan_draft
SET status = 'GENERATION_CANCELLED', ...
WHERE id = ? AND user_id = ? AND status = 'GENERATING'
```

只有更新成功的一方拥有终态。失败的一方重新读取当前状态并按已提交结果收敛，不得覆盖终态。

建议取消顺序：

```text
校验资源归属与当前状态
  -> 条件更新为 GENERATION_CANCELLED
  -> 更新成功后调用 cancellation handle
  -> 尝试通知观察者
```

数据库终态先获胜可以避免上游取消成功但数据库仍停留在 `GENERATING`。如果 handle 暂时不存在，启动协调器必须保证订阅建立后立即取消。

取消接口不等待 Agent 工作线程完全退出后再返回。接口响应边界是：

1. 草案 `GENERATION_CANCELLED` 条件更新已经成功提交，或者已经读取到既有终态。
2. 控制注册表已经完成取消信号派发，或者已经在内存控制槽位中记录 `CANCEL_REQUESTED`，保证 handle 注册后立即取消。

Agent run、治理租约和 provider stream 可以在响应返回后短暂继续收敛。无论收敛耗时如何，后续完成逻辑都必须因为草案已不是 `GENERATING` 而放弃提交 `GENERATED`。

### 11.2 幂等并发

数据库唯一约束是幂等的最终保证。唯一约束冲突后必须查询并返回已有资源，不能重新启动 Agent。若相同幂等键的请求指纹不同，返回稳定冲突错误。

### 11.3 SSE 注册与业务完成竞争

2026-08-20 更新：SSE 请求不再注册内存观察者，而是以 after 游标回放 Redis Stream。每次 XREAD BLOCK 超时后读取一次数据库状态；数据库已终态但 Redis 中缺少终态 entry 时，服务端发送仅含 draftId 的合成终态并关闭，前端仍必须回读数据库。

观察连接注册与业务完成可能并发：

1. 注册前读取一次数据库状态。
2. 注册观察者。
3. 注册后再次读取状态，或由注册操作原子返回当前终态。
4. 如果任务已经完成，立即发送对应终态并关闭观察。

### 11.4 应用重启

本设计不引入持久化任务队列。应用启动时扫描超过阈值仍为 `GENERATING` 的草案，将其标记为稳定失败，并记录：

```text
LEARNING_PLAN_GENERATION_INTERRUPTED
```

不能让重启前遗留记录永久停留在 `GENERATING`。

## 12. 可观测性

### 12.1 业务指标

- `generation_started`
- `generation_completed`
- `generation_failed`
- `generation_cancelled`
- `generation_cancellation_signal_dispatched`
- `generation_cancellation_settled`
- `generation_cancellation_settle_duration`
- `generation_recovered_after_disconnect`
- `generation_idempotency_reused`
- `generation_duration`

SSE 断开不能直接记录 `learning_plan_generation_failed`。只有草案记录进入 `GENERATION_FAILED` 才记录生成失败。

`generation_cancelled` 在草案条件更新为 `GENERATION_CANCELLED` 时记录；`generation_cancellation_settled` 在 Agent run 和治理租约完成 `CANCELLED` 收敛后记录。两者之间的耗时用于观察 provider stream、权限等待和 Tool 对协作式取消的响应速度。

### 12.2 连接指标边界

`opened`、`timeout`、`send_failed`、`active_connections` 等 SSE 连接指标属于独立连接管理设计。本设计只消费这些指标，不把它们映射成学习计划业务终态。

### 12.3 结构化日志

允许记录 `draftId`、`generationRunId`、用户 ID 的现有受控表达、业务状态、耗时、错误码和幂等复用结果。禁止记录 API Key、Authorization、访问令牌、完整用户输入、完整模型输出、reasoning 内容和完整计划 JSON。

## 13. 实施阶段

以下阶段划分替代本节此前的兼容接口优先顺序。

### 当前阶段一：首次草案 Redis Stream 解耦

1. 为首次 AI 草案补齐 GENERATING、生成元数据、幂等键和按用户查询。
2. 新增启动、草案查询与带 after 游标的事件读取接口。
3. 将 Agent 业务 Subscriber 从 SseEmitter Subscriber 分离，改为独立持久化草案并写入 Redis Stream。
4. 为首次草案新增低敏事件投影、Redis key/envelope/cursor、TTL、独立配置和指标。
5. 前端切换到“启动 -> 游标订阅 -> 数据库查询”，终态 SSE 不再传输结构化草案。

### 后续阶段二：草案 AI 修订 Redis Stream 解耦

1. 草案修订启动返回既有持久化 revisionId，而不是 SSE 正文。
2. 新增 revision 状态读取与 revisionId 级 Redis Stream 事件接口。
3. 修订完成事件只携带 draftId 和 revisionId；前端重新读取 draftId 获取已提交草案。
4. 为重复提交补齐幂等键、请求指纹和 revision 终态竞争测试。
5. 不在本阶段迁移正式计划扩展提案。

### 原阶段一：结果恢复兼容闭环（已废止）

1. 增加 `GENERATING`、`GENERATION_FAILED`、`GENERATION_CANCELLED` 状态和生成元数据字段。
2. 增加 `GET /drafts/{draftId}`。
3. 给现有 `/drafts/stream` 增加 `Idempotency-Key`。
4. 在响应头和首个业务事件中返回 `draftId`。
5. 相同幂等键重试时返回同一草案，不重复启动 Agent。
6. 最终事件暂时保留完整草案，兼容旧前端。
7. 前端增加业务终态校验；EOF 或异常时按 `draftId` 查询。

### 原阶段二：拆分启动与观察（已废止）

1. 新增 `POST /drafts/generations`。
2. 新增 `GET /drafts/{draftId}/events`。
3. 让业务 Agent Subscriber 独立于 SSE Subscriber。
4. SSE 终态改为只返回资源引用。
5. 前端切换到“启动 -> 观察 -> 查询”的状态机。
6. 标记 `/drafts/stream` 为待删除兼容接口。

### 后续阶段：显式取消

1. 增加带 `STARTING/RUNNING/CANCEL_REQUESTED/TERMINATED` 槽位的 `draftId -> cancellation handle` 业务任务控制注册表。
2. 新增 `POST /drafts/{draftId}/generation/cancel`。
3. 使用条件更新处理取消与完成竞争。
4. 复用 Agent Runtime 的 `Flow.Subscription.cancel() -> AgentCancellationToken` 链路，将取消传递到 LLM subscription 和 Agent 工作线程。
5. 调整首次草案 Agent Subscriber，取消不得落入普通 `GENERATION_FAILED` 处理。
6. 前端增加明确取消命令，页面卸载和 SSE 断开不自动取消。
7. 补充取消状态查询、幂等响应、取消收敛指标和测试。

### 后续复用

前三阶段验收后，再为草案修订、正式计划扩展和扩展修订分别补齐稳定 revision/proposal ID、查询、观察和取消接口。`ManagedSseConnection + heartbeat` 可以在阶段二完成后独立实施，但不能成为阶段一至三正确性的依赖。

## 14. 测试设计

### 14.1 后端单元测试

- 模板创建仍同步返回 `GENERATED` 草案。
- AI 启动先创建 `GENERATING` 草案并返回 `draftId`。
- 相同幂等键返回同一个草案且只启动一次 Agent。
- 相同幂等键对应不同请求体时返回冲突。
- Agent 成功后先落库，再发送 `draft_completed`。
- SSE 发送终态失败后，查询接口仍返回 `GENERATED` 草案。
- SSE 断开不取消 Agent subscription。
- 显式取消会取消 Agent subscription 和 LLM subscription。
- 显式取消会中断 Agent 工作线程，并使 Agent run 与治理租约收敛为 `CANCELLED`。
- 取消 handle 注册前到达的取消请求最终仍能传递到上游。
- 取消接口返回后，迟到的 Agent 完成结果不能覆盖 `GENERATION_CANCELLED`。
- Agent Runtime 的 `CANCELLED` 事件不能把草案写成 `GENERATION_FAILED`。
- Tool 响应线程中断时能够及时退出，并且不会启动下一轮 LLM。
- Tool 忽略线程中断时，允许当前调用返回，但返回后必须在取消检查点结束 Agent loop。
- 完成、失败与取消竞争只能产生一个数据库终态。
- 应用启动能够收敛遗留 `GENERATING` 记录。

### 14.2 Controller 与 SSE 协议测试

- 兼容接口在响应头和首个事件中暴露相同 `draftId`。
- 已存在 `GENERATING` 草案时，兼容接口不会创建第二个 Agent run。
- 观察 `GENERATED/FAILED/CANCELLED` 草案时立即发送终态并关闭。
- 观察者断开或写失败时只移除观察者，不取消 Agent。
- 新观察者注册与业务完成并发时能够立即获得终态。
- 取消其他用户的草案被拒绝。
- 对已有终态重复取消时幂等返回当前状态。
- 取消接口成功响应只承诺业务终态提交和取消信号登记，不承诺 Tool 已被强制终止。

连接写锁、heartbeat 和连接关闭幂等测试归入 `docs/sse-managed-connection-heartbeat-design.md`。

### 14.3 前端测试

- 模板创建不建立 SSE。
- 兼容流拿到 `draftId` 后写入 pending 状态。
- SSE 抛出网络错误或无终态 EOF 时进入轮询。
- 页面刷新后根据 `sessionStorage` 恢复生成状态。
- 收到 `draft_completed` 后通过 GET 获取完整草案。
- `GENERATED/FAILED/CANCELLED` 时停止轮询并清理 pending 状态。
- 自动重试复用原幂等键，明确重新生成时创建新幂等键。
- 页面卸载不自动取消后台任务。
- 取消请求失败后继续查询权威状态，不直接展示已取消。

### 14.4 集成测试

- 启动真实 HTTP SSE 后主动断开客户端，验证后台仍完成草案并可查询。
- 在最终数据库提交后模拟 SSE 发送失败，验证前端轮询能够恢复。
- 并发发送相同幂等键，验证只创建一个草案和一个 Agent run。
- 模拟显式取消正在进行的 provider stream，验证 run、草案状态和治理租约正确收敛。
- 模拟取消正在等待 Tool 权限的 run，验证等待被解除且 Tool 不执行。
- 模拟响应中断的 Tool，验证工作线程退出且不再发起后续模型调用。
- 模拟忽略中断后延迟返回的 Tool，验证草案保持 `GENERATION_CANCELLED` 且不再进入下一 step。
- 模拟完成与取消竞争，验证数据库只保留一个终态且 SSE/查询结果一致。

## 15. 风险与取舍

### 15.1 兼容期存在两套入口

阶段一继续保留 `/drafts/stream`，阶段二新增 `/drafts/generations` 和观察接口。通过常量统一路径、明确前端切换点和完成后标记旧接口待删除，控制双协议维护时间。

### 15.2 单实例内存控制注册表

cancellation handle 只存在当前实例内存中。当前单实例部署可接受。未来多实例部署需要跨节点取消信号或持久化任务协调，本设计不提前实现。

### 15.3 服务重启不能续跑

数据库能够识别未完成草案，但当前 Agent run 不能跨进程继续。启动恢复只能将遗留状态标记为中断失败，用户按业务规则重新生成。

### 15.4 轮询增加查询压力

只有 SSE 异常和页面恢复进入轮询，正常路径收到终态后只查询一次。通过 2 秒到 5 秒退避、页面不可见降频和终态停止控制压力。

### 15.5 Redis Stream 回放与保留

每个 draft 的公开事件写入独立 Redis Stream。浏览器使用 after cursor 获取严格之后的事件，刷新后可从 0-0 回放尚在 TTL 内的同次生成。回放只改善实时进度体验，业务正确性仍由草案当前状态和最终查询保证。

Redis 过期、重启、写失败、读取失败和 cursor 无法继续回放都统一视为实时日志不可用；前端回读数据库，不新增 Redis 级业务错误或补偿队列。

### 15.6 协作式取消不能强制终止任意 Tool

Java 线程中断是协作信号。当前 LLM 流、Agent 等待和权限协调路径能够响应取消，但第三方 SDK、不可中断 I/O 或忽略中断的 Tool 可能继续运行到自身返回。

本阶段接受该边界，并固定以下保护：

- 不使用 `Thread.stop()` 等不安全的强制终止方式。
- 取消成功后立即固定草案业务终态，迟到结果不能覆盖。
- Tool 返回后立即检查取消，不启动下一轮 LLM 或新的 Tool。
- 已经发生的 Tool 外部副作用不自动回滚。
- 学习计划首次生成允许的 Tool 应优先保持只读；新增写 Tool 时必须单独设计幂等和取消后的副作用收敛。
- 通过 `generation_cancelled` 到 `generation_cancellation_settled` 的耗时指标观察实际收敛延迟。

## 16. 主要实施位置

后端预计涉及：

- `LearningPlanController`
- `LearningPlanDraftStreamService`
- `LearningPlanDraftRepository`
- `MyBatisLearningPlanRepository`
- `LearningPlanMapper` / `LearningPlanMapper.xml`
- `LearningPlanDraftStatus`
- `SseLearningPlanDraftStreamSubscriber`
- `ApiContractConstants`
- 复用 `SingleSubscriberAgentStreamPublisher`、`AgentCancellationToken` 和现有 Agent Runtime 取消链路
- 新增学习计划生成协调服务和 `LearningPlanDraftGenerationControlRegistry`
- Flyway migration

前端预计涉及：

- `frontend/src/services/api.ts`
- `frontend/src/types/api.ts`
- `frontend/src/learning-plans/LearningPlanCreatePage.tsx`
- `frontend/src/learning-plans/LearningPlanDraftPanel.tsx`
- 对应 Vitest/React Testing Library 测试

## 17. 验收标准

1. 模板创建继续同步返回草案，不建立 SSE。
2. AI 创建在模型调用前创建稳定 `draftId`，客户端能够尽早获得该 ID。
3. 完整草案不再只能从 SSE 获得。
4. SSE 正常完成后前端通过 GET 获取权威草案。
5. SSE 网络断开后无需重新调用模型即可恢复最终结果。
6. SSE 断开不会取消学习计划 Agent。
7. 显式取消能够向 Agent subscription 和 LLM subscription 派发协作式取消，并中断 Agent 工作线程。
8. Agent run、治理租约和持久化状态最终收敛为 `CANCELLED`。
9. 不响应中断的 Tool 返回后，Agent loop 不再启动后续步骤，且迟到结果不能覆盖 `GENERATION_CANCELLED`。
10. 取消与完成竞争只能提交一个业务终态。
11. 相同幂等键不会重复创建草案或重复调用模型。
12. SSE 连接指标与学习计划业务结果指标不再混淆。
13. 页面刷新能够恢复仍在生成的草案状态。
14. 最小相关后端和前端测试全部通过。
