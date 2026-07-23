# Agent 线程模型改造设计

## 文档状态

- 状态：讨论稿
- 日期：2026-07-23
- 适用范围：单实例 `mentor-api` 中的 Agent loop、SSE 事件投递和后续容量治理
- 第一阶段已确认：移除 Agent SSE 事件投递对 `ForkJoinPool.commonPool()` 的依赖

## 背景

当前项目以 Spring MVC `SseEmitter` 承载 AI 流式响应。通用 Agent 会话的主要执行链路如下：

```text
Tomcat 请求线程
  -> Controller 完成鉴权、AI 治理准入和 run 准备
  -> 创建 SseEmitter 并订阅 Agent Publisher
  -> 返回 SseEmitter，释放 Tomcat 请求线程

Agent 独立线程
  -> 阻塞读取 OpenAI stream
  -> 执行 Agent loop 和工具调用
  -> 向 SubmissionPublisher 提交 AgentStreamEvent

ForkJoinPool.commonPool 工作线程
  -> 调用 SSE Subscriber.onNext
  -> SseEmitter.send
  -> 将事件写入客户端连接
```

`AgentLoopRunner.stream(...)` 当前为每次订阅创建一个新的平台线程，同时为每次 run 创建一个
`SubmissionPublisher`。`SubmissionPublisher` 使用 `ForkJoinPool.commonPool()` 异步调用下游
Subscriber，Agent 工作线程与 SSE 写线程因此被拆成两组线程。

OpenAI 流在当前实现中是阻塞式消费：Agent 工作线程会顺序完成网络读取、事件解析、工具编排和下一步
模型调用。因此现有模型本质上已经接近“一条活跃 Agent run 占用一条工作线程”。

## 当前问题

### 公共线程池与阻塞 I/O 不匹配

`ForkJoinPool.commonPool()` 是 JVM 进程级共享资源，默认并行度通常接近 CPU 核数，主要适合短时间、
CPU 密集型任务。SSE 下游回调最终会调用 `SseEmitter.send(...)`，其中包含响应序列化、网络写入和 flush，
客户端慢或网络异常时可能阻塞。

将阻塞式 SSE 写入放到 common pool 会带来以下问题：

- 少量慢连接可能占满 common pool 工作线程。
- `CompletableFuture`、parallel stream 等其他使用 common pool 的功能会受到连带影响。
- SSE 事件消费变慢后，`SubmissionPublisher` 的单订阅者缓冲会持续堆积。
- 缓冲区满后，Agent 生产线程仍可能被反向阻塞，异步解耦无法消除最终背压。
- Agent 执行线程和 SSE 投递线程分离后，线程数量与系统真实活跃 run 数之间的关系不直观。

### 每次 run 直接创建线程

当前每个订阅通过 `new Thread(...)` 创建 Agent 工作线程，缺少统一的最大并发、拒绝策略、线程生命周期、
优雅停止和 Micrometer 指标。请求数量超过机器和模型提供商承载能力时，应用层没有稳定的系统总容量边界。

### 超时配置不一致

通用 Agent 会话的 `SseEmitter` 超时目前写死为 30 秒，而 OpenAI stream 默认超时为 5 分钟，其他业务
SSE 默认超时为 6 分钟。正常的多步骤 Agent run 可能先被 SSE 层中断，造成线程、上游连接和运行锁的
释放路径更复杂。

## 设计目标

- 第一阶段移除 `ForkJoinPool.commonPool()`，让业务 SSE 事件由当前 Agent loop 工作线程同步投递。
- 保持现有 `Flow.Publisher<AgentStreamEvent>` 对外契约和 SSE 事件协议不变。
- 让一个慢客户端只影响自己的 Agent run，不占用 JVM 公共线程池。
- 使用自然背压限制单条流的内存增长，不为慢客户端无限缓存 token。
- 后续把每次创建线程改为 Spring 管理的有界 Agent 执行池。
- 为约 100 名用户的并发使用建立可配置的系统总容量和明确的过载响应。
- 覆盖成功、异常、超时、客户端断连、任务取消和线程池拒绝的资源释放路径。

## 非目标

- 第一阶段不调整 Tomcat、Hikari 或 OpenAI HTTP client 的最终生产参数。
- 第一阶段不升级到 JDK 21 或引入虚拟线程。
- 第一阶段不修改前端 SSE 事件名、数据结构和交互语义。
- 第一阶段不引入多实例共享容量协调器；当前仍按单实例部署边界设计。
- 不使用无界任务队列吸收超过承载能力的长时间 Agent run。

## 目标线程模型

### 第一阶段目标

第一阶段保留现有 Agent 独立线程，但移除 SSE 投递的额外线程跳转：

```text
Tomcat 请求线程
  -> 完成请求准备并返回 SseEmitter

Agent loop 工作线程
  -> 阻塞读取一个 OpenAI 流事件
  -> 执行 lifecycle/observer
  -> 同步调用下游 Subscriber.onNext
  -> SseEmitter.send
  -> 继续读取下一个 OpenAI 流事件
```

这个模型中，客户端消费速度会直接影响对应 Agent run 的读取速度，形成单连接自然背压。项目当前是一条
Agent run 对应一个 SSE 消费者，不需要通过公共投递池提高多订阅者吞吐。

### 后续目标

后续将 Agent 独立线程替换为受管、有界的执行池：

```text
Tomcat 请求线程
  -> 用户级并发检查
  -> 系统级容量准入
  -> 准备 run 和 SseEmitter
  -> 向 AgentExecutor 提交任务
  -> 返回 SseEmitter

AgentExecutor 工作线程
  -> OpenAI stream + Agent loop + 工具调用 + 业务 SSE 事件投递
  -> 终态释放用户锁和系统容量令牌
```

## 第一阶段：移除 ForkJoinPool SSE 投递

### 已确认决策

- 不再使用 `ForkJoinPool.commonPool()` 执行 Agent SSE Subscriber 回调。
- 业务 `AgentStreamEvent` 由执行当前 run 的 Agent loop 工作线程同步投递。
- 保持单订阅者语义，不为同一次 run 支持多个并发 SSE 消费者。
- 保持现有下游 demand、cancel、onError 和 onComplete 契约。
- 客户端取消时继续通过 `AgentCancellationToken` 取消 LLM subscription 并中断 Agent 工作线程。
- 不把业务 SSE 事件投递迁移到 Tomcat 请求线程。

### 推荐实现方向

不建议只把 `SubmissionPublisher` 的 executor 替换为 `Runnable::run`。这种方式虽然可以减少线程跳转，
但仍保留异步 Publisher 的缓冲、调度和回调重入语义，难以表达当前明确的一对一同步传输模型。

推荐在 `agent-core` 内提供单订阅者同步事件出口，由 `AgentLoopLifecycle` 在 Agent 工作线程中调用。对外仍返回
`Flow.Publisher<AgentStreamEvent>`，但内部不再依赖 `SubmissionPublisher` 和公共 executor。

实现需要保证：

- Subscriber 只能订阅一次，重复订阅返回稳定错误。
- Subscriber 发出有效 demand 后才投递事件。
- `cancel()` 能让尚未开始或正在运行的 Agent run 尽快终止。
- 终态事件最多投递一次，之后只允许 `onComplete` 或 `onError` 中的一种终止信号。
- `onNext`、`onError`、`onComplete` 在同一 run 内保持串行顺序。
- `RequestTraceContext` 在 Agent 工作线程中继续可用。

### 慢客户端语义

`SseEmitter.send(...)` 变慢时，对应 Agent 工作线程会暂停读取后续 OpenAI 事件。这是第一阶段接受的行为：

- 慢客户端只占用自己的 Agent 工作线程和系统容量令牌。
- 不继续为该客户端堆积大量 token，降低内存风险。
- SSE 写失败后应立即取消 OpenAI stream，避免继续产生费用和无效计算。

必须通过 SSE 总超时、上游流超时、客户端断连检测和 Agent 取消共同限制单条慢连接的最长占用时间。

### 心跳边界

业务事件改为 Agent 工作线程同步投递后，Agent 等待模型首 token、长工具调用或人工权限决策期间无法自行产生
心跳。心跳如果需要落地，应由一个小型共享调度器触发，而不是重新引入完整的 SSE 业务事件投递池。

心跳与业务事件对同一 `SseEmitter` 的写入必须串行化，并在连接终止时注销对应的心跳任务。

### 第一阶段测试

- 验证 Agent 事件回调运行在 Agent loop 工作线程，而不是 common pool 工作线程。
- 验证事件顺序、终态事件和现有 SSE 映射保持不变。
- 验证 Subscriber 取消会取消 OpenAI subscription 并中断工作线程。
- 验证 `SseEmitter.send` 抛出异常后 run 能进入取消或失败终态。
- 使用阻塞 Subscriber 验证单条慢流不会占用 common pool，也不会影响另一条 run。
- 验证重复订阅、非法 demand 和终态后事件不会破坏 Flow 契约。

### 第一阶段验收标准

- 生产代码中 Agent SSE 事件链路不再引用 `ForkJoinPool.commonPool()`。
- Agent loop 不再依赖 `SubmissionPublisher` 完成一对一 SSE 事件投递。
- 现有 Agent loop、SSE mapper、Controller 和取消相关测试通过。
- 新增线程归属、慢消费者和资源释放测试。
- SSE 对外协议没有变化。

## 第二阶段：Agent 执行线程池

第一阶段稳定后，将 `new Thread(...)` 替换为 Spring 管理的专用 `AgentExecutor`。当前讨论形成的原则如下，
具体数值需要结合部署规格和压测结果确认：

- 使用平台线程和有界 `ThreadPoolExecutor`，适配当前 JDK 17 与阻塞式 OpenAI 流。
- 长任务不进入无界队列；倾向使用零容量或极小容量队列。
- 拒绝策略使用 `AbortPolicy`，禁止 `CallerRunsPolicy`，避免 Agent loop 回退到 Tomcat 请求线程执行。
- 线程名称使用稳定前缀，例如 `agent-loop-`。
- 通过 task decorator 或等价机制传递 `RequestTraceContext`。
- 线程池暴露 active、pool size、completed、rejected 和 queue size 指标。
- 应用停止时停止接收新 run，取消或等待已有任务，并设置明确的 shutdown timeout。

初始容量候选值：

```text
core pool size:       20
max pool size:        120
queue capacity:       0
keep alive:           60s
system active limit:  100
```

这些参数表示单实例最多允许约 100 条用户 Agent run 同时占用容量，同时为执行池保留少量实现和收尾余量。
最终值不在第一阶段固化。

## 第三阶段：系统容量与过载处理

线程池最大线程数只是最后一道资源边界，不能代替业务准入。计划增加独立的系统级 Agent 容量限制，并在创建
长连接、扣减每日额度和持久化 run 之前完成检查。

初步错误语义：

- 同一用户已有活跃 AI run：保持 `409 AI_CONCURRENT_RUN_CONFLICT`。
- 用户额度耗尽：保持 `429 AI_QUOTA_EXCEEDED`。
- 单实例系统容量已满：新增 `503 AI_CAPACITY_EXCEEDED`，附带 `Retry-After`。
- Executor 意外拒绝任务：作为兜底映射为相同的系统容量错误。

系统容量拒绝不应：

- 扣减用户每日额度。
- 创建新的 Agent run 或用户消息。
- 打开一个无业务事件输出的 SSE 连接。
- 遗留用户锁、task 锁或容量令牌。

容量令牌需要在以下路径释放：

- run 正常完成。
- 模型或工具执行失败。
- SSE 超时。
- 客户端主动断连。
- 模型调用超时。
- 线程池提交失败。
- run 准备或订阅阶段同步失败。

## 第四阶段：容器线程和关联资源

在 Agent 执行池和系统准入稳定后，再调整 Tomcat 和关联资源。当前讨论的单实例候选基线为：

```text
Tomcat max threads:       64
Tomcat min spare threads: 10
Tomcat max connections:   512
Tomcat accept count:      100
Hikari max pool size:     20-30
SSE timeout:              5-6m
```

Tomcat 线程可以小于 SSE 连接数，因为 Controller 返回 `SseEmitter` 后，请求线程不再持续占用；但鉴权、
AI 治理准入、run 准备、普通 API 和突发建连仍需要 Tomcat 工作线程。连接数需要高于目标 SSE 数，因为同一
用户还会发起普通 API 和静态资源请求。

## 观测与压测要求

需要补充或确认以下指标：

- Agent executor active、pool size、queue size、completed 和 rejected。
- 系统容量已用、剩余和拒绝次数。
- 按 stream type 统计的活跃 SSE 连接数。
- Agent 首事件时间、首 token 时间、run 总耗时和取消耗时。
- Tomcat current/busy threads 和当前连接数。
- Hikari active、pending 和 timeout。
- OpenAI rate limit、timeout 和 provider unavailable。

目标压测场景：

- 100 条 SSE 同时运行 2 至 5 分钟，普通 API 保持可用。
- 超过系统限制时，请求快速返回稳定的容量错误，不形成长任务排队。
- 批量断开客户端后，Agent 线程、上游连接、用户锁和容量令牌及时释放。
- 慢客户端只影响自身 run，不拖慢其他流。
- 模型长时间无响应时，超时后可以恢复容量。

## 已确认与待确认事项

已确认：

- 第一阶段移除 `ForkJoinPool.commonPool()`。
- Agent loop 工作线程直接投递业务 SSE 事件。
- 不为长时间 Agent run 使用无界队列。
- 系统过载应快速拒绝，不让用户在无输出 SSE 连接中长时间等待。

待确认：

- 同步单订阅 Publisher 的最终类名和内部接口形态。
- 心跳间隔、调度器实现和代理层 idle timeout。
- Agent executor 的 core/max/keep-alive 最终数值。
- 单实例系统活跃上限是否直接设为 100，或为后台 AI 任务预留独立配额。
- 不同 AI purpose 是否需要共享总容量下的子上限和公平策略。
- 生产部署的 CPU、内存、OpenAI RPM/TPM 与数据库容量基线。
- 多实例部署前，用户锁、工具权限 coordinator 和系统容量是否迁移到共享存储。
