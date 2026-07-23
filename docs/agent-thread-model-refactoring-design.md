# Agent 线程模型改造设计

## 文档状态

- 状态：当前实施基线
- 日期：2026-07-23
- 部署基线：单实例 `mentor-api`，4 核 CPU、8 GB 内存，机器资源主要供 Java 进程使用
- 第一阶段：移除 Agent SSE 事件投递对 `ForkJoinPool.commonPool()` 的依赖
- 第二阶段：使用 `20/100 + SynchronousQueue + AbortPolicy` 的专用 Agent 执行池
- 当前不实施：独立的用户等级、业务 purpose、provider 和系统总容量准入限流
- Tomcat：保持 Spring Boot/Tomcat 默认线程与连接参数，暂不在配置文件中覆盖

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
优雅停止和 Micrometer 指标。请求数量持续增长时，Java 进程没有明确的 Agent 工作线程硬上限。

### 超时配置不一致

通用 Agent 会话的 `SseEmitter` 超时目前写死为 30 秒，而 OpenAI stream 默认超时为 5 分钟，其他业务
SSE 默认超时为 6 分钟。正常的多步骤 Agent run 可能先被 SSE 层中断，造成线程、上游连接和运行锁的
释放路径更复杂。

## 设计目标

- 第一阶段移除 `ForkJoinPool.commonPool()`，让业务 SSE 事件由当前 Agent loop 工作线程同步投递。
- 保持现有 `Flow.Publisher<AgentStreamEvent>` 对外契约和 SSE 事件协议不变。
- 让一个慢客户端只影响自己的 Agent run，不占用 JVM 公共线程池。
- 使用自然背压限制单条流的内存增长，不为慢客户端无限缓存 token。
- 把每次创建线程改为 Spring 管理的无队列、有最大线程数的 Agent 执行池。
- 以 100 个 Agent 工作线程作为当前单实例的执行硬上限。
- 线程池饱和时立即拒绝新任务，不让长时间 Agent run 在内存队列中等待。
- 覆盖成功、异常、超时、客户端断连、任务取消和线程池拒绝的资源释放路径。

## 非目标

- 当前不调整 Tomcat、Hikari 或模型 provider HTTP client 的默认线程与连接参数。
- 当前不升级到 JDK 21 或引入虚拟线程。
- 当前不修改前端 SSE 事件名、数据结构和交互语义。
- 当前不实现用户等级、业务 purpose、provider 或系统总容量的独立准入许可。
- 当前不引入多实例共享容量协调器，仍按单实例部署边界设计。
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
  -> 执行现有鉴权和 AI 治理检查
  -> 准备 run 和 SseEmitter
  -> 向 AgentExecutor 提交任务
  -> 返回 SseEmitter

AgentExecutor 工作线程
  -> OpenAI stream + Agent loop + 工具调用 + 业务 SSE 事件投递
  -> 终态释放现有用户锁和 task 锁
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

- 慢客户端只占用自己的 Agent 工作线程。
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

第一阶段稳定后，将 `new Thread(...)` 替换为 Spring 管理的专用 `AgentExecutor`。当前参数以单实例
4 核 CPU、8 GB 内存和最多约 100 条同时运行的 Agent 流为基线。

- 使用平台线程和有界 `ThreadPoolExecutor`，适配当前 JDK 17 与阻塞式 OpenAI 流。
- 使用 `SynchronousQueue`，不在执行池内存中保存等待任务。
- 拒绝策略使用 `AbortPolicy`，禁止 `CallerRunsPolicy`，避免 Agent loop 回退到 Tomcat 请求线程执行。
- 允许核心线程超时，空闲时释放超过实际负载需要的工作线程。
- 线程名称使用稳定前缀，例如 `agent-loop-`。
- 通过 task decorator 或等价机制传递 `RequestTraceContext`。
- 线程池暴露 active、pool size、completed、rejected 和 queue size 指标。
- 应用停止时停止接收新 run，取消或等待已有任务，并设置明确的 shutdown timeout。

已确认参数：

```text
core pool size:       20
max pool size:        100
work queue:           SynchronousQueue（零容量）
keep alive:           60s
allow core timeout:   true
rejection policy:     AbortPolicy
```

`SynchronousQueue` 只负责把任务直接交给空闲工作线程，不保存排队任务。当没有空闲线程且当前线程数低于
100 时，执行池继续创建工作线程；达到 100 后，新任务立即触发 `RejectedExecutionException`。

当前不通过 `activeCount`、`poolSize` 等近似统计提前判断是否存在可用线程，也不改造
`ThreadPoolExecutor` 内部调度逻辑。任务直接提交给执行池，由执行池的原子调度和拒绝机制维护硬边界。

### 线程池拒绝处理

由于当前不增加独立容量准入层，线程池拒绝是本阶段实际的过载信号。提交方必须捕获
`RejectedExecutionException`，并区分以下情况：

- 执行池已关闭：应用正在停止，按服务不可用处理。
- 执行池仍在运行：100 个 Agent 工作线程均无法接收新任务，按临时过载处理。

拒绝发生时不得使用 `CallerRunsPolicy` 在 Tomcat 请求线程中执行 Agent loop，也不得把任务放入其他无界队列。
API 层应返回稳定的服务繁忙错误；具体错误码可以在实现时沿用或补充 AI 治理错误枚举。

即使暂不实施独立准入，拒绝路径仍必须完成资源回滚：

- 释放本次请求已经获取的用户级 AI run 锁和 task 锁。
- 将已经创建的 run 更新为明确的失败或取消终态，不能遗留为运行中。
- 关闭尚未开始输出的 SSE emitter。
- 不启动 OpenAI、DeepSeek 等模型 provider 调用。
- 记录 executor rejected 指标和低敏结构化日志。

## 延期项：独立容量准入

用户等级和会员模型尚未稳定，当前不实现线程池前置的多维容量管理器，也不增加以下准入维度：

- 用户等级总并发。
- 单用户可配置并发数。
- 业务 purpose 并发上限。
- OpenAI、DeepSeek 等 provider 并发上限。
- 独立于线程池最大线程数的系统容量许可。

现有 AI 治理中的用户锁、每日额度、功能开关和 purpose 策略继续生效。本阶段只用 Agent executor 的
`maximumPoolSize=100` 作为进程内工作线程硬上限。

后续用户等级形成稳定模型后，可以在线程池之前增加容量管理器。该能力应与线程池解耦，通过全局、用户、业务和
provider 许可控制任务是否允许提交；线程池的 `AbortPolicy` 继续作为最终物理保护。

## Tomcat 配置决策

当前部署机器为 4 核 CPU、8 GB 内存，并且资源主要供 Java 进程使用。Tomcat 保持 Spring Boot/Tomcat 默认
线程池、连接数和 accept backlog 配置，本阶段不在 `application.yml` 中增加 `server.tomcat.*` 覆盖项。

保持默认配置的原因：

- `SseEmitter` 返回后，长连接不会持续占用 Tomcat 请求工作线程。
- Tomcat 线程仍负责鉴权、AI 治理、数据库 run 准备、普通 API 和突发建连。
- 8 GB 内存能够容纳默认 Tomcat 工作线程和最多 100 个 Agent 工作线程的线程栈与运行开销。
- 当前没有压测数据证明 Tomcat 默认值构成资源浪费或性能瓶颈。

Tomcat 参数仍需要通过 Actuator/Micrometer 观测。只有出现 Tomcat busy threads 长期偏低且线程内存成为问题，
或连接数、accept queue、普通 API 延迟出现异常时，再单独调整。Agent executor 的最大线程数不能通过修改
Tomcat 参数间接控制。

Hikari、模型 provider HTTP client、JVM heap、线程栈和文件描述符配置不在本次参数决策范围内，后续结合
100 并发压测结果单独确认。

## 超时边界

线程池没有等待队列，运行中的任务仍可能因为模型、工具、权限确认或慢客户端长时间占用工作线程。实现线程池
时需要同步统一以下超时，但具体参数仍待后续确认：

- 单次 provider stream timeout。
- Agent run 总超时。
- SSE emitter 总超时。
- 工具权限等待超时。
- 应用关闭时的 executor shutdown timeout。

外层 SSE 超时应晚于 Agent run 和 provider 超时，确保内部任务先进入可观测终态并释放线程。通用 Agent 会话
当前写死的 30 秒 SSE 超时需要在后续实现中改为统一配置。

## 观测与压测要求

需要补充或确认以下指标：

- Agent executor active、pool size、queue size、completed 和 rejected。
- 按 stream type 统计的活跃 SSE 连接数。
- Agent 首事件时间、首 token 时间、run 总耗时和取消耗时。
- Tomcat current/busy threads 和当前连接数。
- Hikari active、pending 和 timeout。
- OpenAI rate limit、timeout 和 provider unavailable。

目标压测场景：

- 100 条 SSE 同时运行 2 至 5 分钟，普通 API 保持可用。
- 第 101 个及后续任务快速收到稳定的线程池饱和错误，不形成长任务排队。
- 批量断开客户端后，Agent 线程、上游连接和现有运行锁及时释放。
- 慢客户端只影响自身 run，不拖慢其他流。
- 模型长时间无响应时，超时后可以恢复容量。
- Tomcat 使用默认参数时，100 条 SSE 建连和普通 API 延迟保持稳定。

## 已确认与待确认事项

已确认：

- 第一阶段移除 `ForkJoinPool.commonPool()`。
- Agent loop 工作线程直接投递业务 SSE 事件。
- Agent executor 使用 `core=20`、`max=100`、`keepAlive=60s`。
- Agent executor 使用 `SynchronousQueue`、`allowCoreThreadTimeOut=true` 和 `AbortPolicy`。
- 不为长时间 Agent run 保存等待队列，线程池饱和时立即拒绝。
- 当前不增加独立准入限流，后续等待用户等级和会员模型稳定后再设计。
- Tomcat 保持 Spring Boot/Tomcat 默认配置。

待确认：

- 同步单订阅 Publisher 的最终类名和内部接口形态。
- 心跳间隔、调度器实现和代理层 idle timeout。
- 线程池拒绝对应的稳定 API/SSE 错误码和前端提示。
- provider、Agent run、SSE 和 shutdown 的最终超时数值。
- 用户等级稳定后的全局、用户、业务 purpose 和 provider 容量模型。
- OpenAI、DeepSeek 等 provider 的真实 RPM/TPM 与连接池容量基线。
- 多实例部署前，用户锁、工具权限 coordinator 和系统容量是否迁移到共享存储。
