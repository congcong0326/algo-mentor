# Agent 执行组舱壁隔离研发设计

## 1. 文档信息

- 状态：已实施
- 日期：2026-08-12
- 适用范围：`backend/agent-core`、`backend/agent-runtime`、`backend/mentor-application`、`backend/mentor-api`
- 部署基线：单实例 `mentor-api`，JDK 17，4 核 CPU、8 GB 内存，5 至 20 人封闭内测
- 前置基线：统一 `AgentRuntime`、`AgentDefinition`、`ManagedAgentExecutor`、`SynchronousQueue + AbortPolicy`

本文定义 Agent 执行线程的业务舱壁隔离机制。每个 `AgentDefinition` 在代码中声明一个稳定执行组，启动时按当前生效 Definition 收集和校验执行组配置，为每个组创建独立并发许可，并由所有组容量之和推导底层物理线程池最大线程数。

本设计只改造 Agent 执行线程池和提交边界，不调整持久化队列、AI 准入额度、用户级运行锁、Provider 并发、模型路由或业务功能开关。

## 2. 背景

当前 Agent 统一使用一个 `ManagedAgentExecutor`：

```text
core pool size:       20
max pool size:        100
work queue:           SynchronousQueue
keep alive:           60s
allow core timeout:   true
rejection policy:     AbortPolicy
```

现有线程池提供了进程级硬上限和快速拒绝，但所有业务场景竞争同一份容量：

```text
题目训练聊天
学习计划创建、修订和扩展
Code Review 画像后台更新
```

当聊天请求占满执行池时，学习计划和画像后台任务无法获得工作线程。反过来，如果后台任务未来增加并发，也可能挤占在线聊天容量。现有用户级运行锁和 `USER_ENTRY / CHILD / BACKGROUND` 治理模式解决的是用户准入、额度和调用关系，不提供业务场景之间的线程资源隔离。

当前生产代码只有一个独立的后台 Agent：

```text
learner-memory.code-review.v2
  -> LearnerMemoryCodeReviewBatchConsumer
  -> LearnerMemoryCodeReviewUpdateService
  -> AgentInvocationMode.BACKGROUND
```

该 topic 在单实例内使用一个串行 worker，因此当前最多同时运行一个画像后台 Agent。为它固定保留一个 Agent 执行槽即可满足现阶段需求。

## 3. 设计目标

- 聊天流量不能占用学习计划和画像后台任务的保留容量。
- 学习计划任务不能阻止画像后台任务获得工作线程。
- 每个 Agent 场景必须在代码中声明稳定执行组，不能由请求参数或 metadata 指定。
- 同组 Agent 共享一份并发上限，不为每个 Definition 单独创建信号量。
- 各组使用严格舱壁，不互借空闲容量。
- 不在 Agent 执行池中保存等待任务，组容量耗尽时立即拒绝。
- 底层物理线程池最大线程数由当前生效执行组容量之和推导，不再独立配置。
- 同步 `execute` 和流式 `stream` 使用相同的执行组准入规则。
- `CHILD` 调用继续在父 Agent 工作线程内联执行，不重复获取许可或额外占用线程。
- 许可在成功、失败、取消、提交拒绝和应用关停路径中都能可靠释放。
- 使用低基数 Micrometer tag 观测各组容量、活跃数和拒绝数。

## 4. 非目标

- 不建设线程等待队列、优先级队列或任务抢占。
- 不修改 PostgreSQL 持久化队列的领取、确认、重试和死信语义。
- 不增加 Provider 级 semaphore、RPM、TPM 或连接池限制。
- 不调整用户每日 AI 额度、用户级运行锁或 AI 动态开关。
- 不根据用户角色、会员等级、管理员身份或用户组动态分配线程。
- 不从数据库动态维护执行组或容量配置。
- 不拆分多个物理 `ThreadPoolExecutor`。
- 不在多实例之间共享执行许可；容量仍是单 JVM 边界。
- 不允许不同执行组借用彼此的空闲许可。
- 不升级 JDK 21 或改用虚拟线程。

## 5. 已定决策

1. 新增代码注册的稳定 `AgentExecutionGroup`，第一版包含 `PRACTICE`、`LEARNING_PLAN` 和 `LEARNER_PROFILE_BACKGROUND`。
2. `AgentDefinition<I>` 增加必填 `executionGroup()` 契约，不提供默认值。
3. 一个执行组可以包含多个 Definition，同组 Definition 共享一个并发许可池。
4. 执行组只能由受信 Definition 声明，不能从前端、HTTP 请求、普通 metadata 或数据库自由文本读取。
5. 第一版使用严格舱壁，默认容量为 `27 / 2 / 1`，总容量为 30。
6. 删除独立的 `core-pool-size` 和 `max-pool-size` 部署配置。
7. 底层 `ThreadPoolExecutor.maximumPoolSize` 等于当前生效执行组容量之和。
8. `corePoolSize` 由代码计算为 `min(10, totalCapacity)`；它只影响线程创建和复用，不表达业务保留容量。
9. 继续使用 `SynchronousQueue`、`AbortPolicy`、`allowCoreThreadTimeOut=true` 和现有限时优雅关闭语义。
10. 顶层 `USER_ENTRY` 和 `BACKGROUND` 调用按其 Definition 执行组获取一次许可。
11. `CHILD` 调用继承父 run 当前执行组并在线程内联执行，不获取子 Definition 的新许可。
12. 获取组许可使用非阻塞 `tryAcquire()`；失败立即返回稳定的组饱和拒绝。
13. 物理线程池拒绝仍保留为最终防御。即使组许可总数与物理最大线程数一致，`SynchronousQueue` 在线程结束与重新等待交接的极短窗口内仍可能拒绝新提交，必须独立计数并释放已获取许可。
14. 启动时收集当前生效 Definition 的执行组，校验配置完整性后创建信号量和线程池。
15. 总容量设置代码级安全上限 100，防止配置错误创建过多 JDK 17 平台线程；该上限不是部署容量配置。
16. 容量配置变更需要重启应用，不支持运行时热更新。

## 6. 执行组目录

### 6.1 稳定类型

建议在 `agent-core` 的执行契约包中新增：

```java
public enum AgentExecutionGroup {
  PRACTICE("practice"),
  LEARNING_PLAN("learning-plan"),
  LEARNER_PROFILE_BACKGROUND("learner-profile-background");

  private final String code;
}
```

`code` 是配置 key、指标 tag 和低敏日志使用的稳定值。Java 枚举名称不直接作为外部配置契约。

### 6.2 第一版映射

| Agent Definition | 调用模式 | 执行组 | 是否独立获取许可 |
| --- | --- | --- | --- |
| `PracticeChatAgentDefinition` | `USER_ENTRY` | `PRACTICE` | 是 |
| `PracticeCodeReviewAgentDefinition` | `CHILD` | `PRACTICE` | 否，继承父 run |
| `DeclaredProfileUpdateAgentDefinition` | `CHILD` | `PRACTICE` | 否，继承父 run |
| `LearningPlanDraftAgentDefinition` | `USER_ENTRY` | `LEARNING_PLAN` | 是 |
| `LearningPlanDraftRevisionAgentDefinition` | `USER_ENTRY` | `LEARNING_PLAN` | 是 |
| `LearningPlanExtensionAgentDefinition` | `USER_ENTRY` | `LEARNING_PLAN` | 是 |
| `LearnerMemoryCodeReviewUpdateAgentDefinition` | `BACKGROUND` | `LEARNER_PROFILE_BACKGROUND` | 是 |

第一版默认容量：

| 执行组 | 并发上限 | 资源语义 |
| --- | ---: | --- |
| `practice` | 27 | 题目聊天及其内联子 Agent |
| `learning-plan` | 2 | 学习计划创建、修订和扩展 |
| `learner-profile-background` | 1 | Code Review 异步画像更新 |
| 合计 | 30 | 物理 Agent 线程池最大线程数 |

采用 `27 / 2 / 1` 而不是 `28 / 1 / 1` 的原因是学习计划创建、修订和扩展共用一个组。保留两个槽可以避免一个长时间计划任务使所有其他计划请求都立即失败，同时仍为当前唯一画像后台 Agent 固定保留一个槽。

## 7. 配置契约

### 7.1 目标配置

删除：

```yaml
core-pool-size: 20
max-pool-size: 100
```

目标配置：

```yaml
algo-mentor:
  agent:
    executor:
      groups:
        practice: 27
        learning-plan: 2
        learner-profile-background: 1
      keep-alive: 60s
      shutdown-timeout: 30s
      thread-name-prefix: agent-loop-
```

环境变量建议固定为：

```text
AGENT_EXECUTOR_GROUP_PRACTICE=27
AGENT_EXECUTOR_GROUP_LEARNING_PLAN=2
AGENT_EXECUTOR_GROUP_LEARNER_PROFILE_BACKGROUND=1
```

`AgentExecutorProperties` 保存稳定 group code 到正整数容量的映射，以及保留的线程生命周期参数。`maxPoolSize` 和 `corePoolSize` 不再由部署环境设置。

### 7.2 启动校验

启动期按以下顺序执行：

1. `AgentDefinitionRegistry` 注册当前配置下生效的 Definition。
2. 校验每个 Definition 的 `executionGroup()` 非空。
3. 收集当前生效 Definition 使用的唯一执行组。
4. 校验每个被使用的执行组都有容量配置。
5. 校验配置 key 都能解析为代码注册的稳定执行组。
6. 校验每个容量为正整数。
7. 允许配置代码已注册但当前没有生效 Definition 的执行组；该组不创建信号量，也不计入物理线程池容量，并记录一次启动信息日志。
8. 汇总当前生效组的容量，校验总数大于 0 且不超过代码级安全上限 100。
9. 只为当前生效执行组创建信号量。
10. 使用已创建信号量的容量总和创建 `ManagedAgentExecutor` 的物理线程池。

任一校验失败都阻止应用启动，不静默使用默认组、不回退旧 `max-pool-size`，也不忽略拼写错误的配置项。

若某个功能开关导致一整组 Definition 不再生效，可以保留该稳定组的容量配置，便于后续重新启用；启动时不为它创建信号量，也不把它计入物理线程池容量。若 Agent Runtime 被关闭，则不创建 bulkhead registry 和物理 Agent 执行池；若 Runtime 已开启但没有任何生效 Definition，则启动失败。

## 8. 核心类型与职责

### 8.1 `AgentDefinition`

目标契约：

```java
public interface AgentDefinition<I> {

  AgentKey<I> key();

  AgentExecutionGroup executionGroup();

  AgentLoopPolicy loopPolicy();

  List<String> allowedToolNames();

  AgentOutputContract outputContract();

  AgentPreparedRequest prepare(I input, AgentInvocationContext context);
}
```

Definition 只声明所属执行组，不拥有信号量、容量数值或线程池引用。

### 8.2 `AgentDefinitionRegistry`

Registry 继续负责 Definition 唯一注册和类型安全解析，并增加执行组完整性校验。建议提供只读查询：

```java
Set<AgentExecutionGroup> executionGroups();
```

Registry 不创建信号量，也不读取 Spring 配置。

### 8.3 `AgentExecutionBulkheadRegistry`

新增独立运行时组件，负责：

- 保存当前生效组及其并发上限。
- 为每个组创建一个 `Semaphore`。
- 非阻塞获取和释放组许可。
- 计算物理线程池总容量。
- 提供组级容量快照和 Micrometer 指标数据源。

建议许可使用显式 lease，避免调用方手工配对 release：

```java
public interface AgentExecutionPermit extends AutoCloseable {

  AgentExecutionGroup group();

  @Override
  void close();
}
```

Registry API 示例：

```java
Optional<AgentExecutionPermit> tryAcquire(AgentExecutionGroup group);

int totalCapacity();
```

`AgentExecutionPermit.close()` 必须幂等，防止异常清理路径重复释放导致许可数超过上限。

### 8.4 `AgentExecutor`

目标接口需要携带受信执行组：

```java
public interface AgentExecutor {

  void execute(AgentExecutionGroup group, Runnable task);

  boolean isShutdown();

  boolean inExecutorThread();
}
```

组参数只能来自已解析 Definition。业务层和 controller 不直接调用该接口。

### 8.5 `ManagedAgentExecutor`

`ManagedAgentExecutor` 组合 bulkhead registry 和 `ThreadPoolExecutor`：

```text
group tryAcquire
  -> 失败：GROUP_SATURATED
  -> 成功：向 ThreadPoolExecutor 提交包装任务
       -> 提交失败：释放许可并抛出拒绝异常
       -> 任务终态：finally 释放许可
```

线程池参数由 registry 推导：

```java
int maximumPoolSize = bulkheadRegistry.totalCapacity();
int corePoolSize = Math.min(10, maximumPoolSize);
```

仍保留：

```text
SynchronousQueue
AbortPolicy
allowCoreThreadTimeOut=true
```

`SynchronousQueue` 不保存等待任务。一个工作线程在任务包装器 `finally` 中释放组许可后，需要返回 `ThreadPoolExecutor` 主循环并重新进入直接交接等待；若新提交恰好落在这段极短窗口，提交方可能已经取得组许可，但底层仍返回 `RejectedExecutionException`。该情况继续按 `SATURATED` 处理，立即释放许可并由现有提交失败链路结束 run。组许可解决业务容量隔离，不替代物理线程池的最终拒绝保护。

## 9. 执行流程

### 9.1 顶层同步调用

```text
业务服务
  -> AgentRuntime.execute(invocation)
  -> 解析 AgentDefinition
  -> 读取 definition.executionGroup
  -> 完成 run 准备和治理租约
  -> executor.execute(group, task)
       -> 获取 group permit
       -> 提交工作线程
       -> 执行 Agent loop
       -> 终态结算
       -> finally 释放 permit
```

现有同步调用方会等待 Agent 工作线程完成。调用方等待本身不占 AgentExecutor 工作线程。

### 9.2 顶层流式调用

```text
业务服务
  -> AgentRuntime.stream(invocation)
  -> 返回 SingleSubscriberAgentStreamPublisher
  -> 首次订阅
  -> beforeSubmission 完成 run 准备
  -> executor.execute(group, workerTask)
       -> 获取 group permit
       -> Agent 工作线程同步投递 SSE 事件
       -> 完成、失败或取消
       -> finally 释放 permit
```

流式路径必须把执行组传入 Publisher 或等价提交包装，不能继续调用无组信息的 `execute(Runnable)`。

### 9.3 `CHILD` 内联调用

当前 `DefaultAgentRuntime.execute()` 在 `executor.inExecutorThread()` 为 true 时直接执行子 run，避免父 Agent 等待同一线程池中的子任务造成线程放大或死锁。

本设计保持该语义：

```text
父 Agent 已持有 PRACTICE permit
  -> 工具同步调用 CHILD Agent
  -> CHILD 在当前工作线程执行
  -> 不获取新的 group permit
  -> CHILD 完成后返回父 Agent
  -> 父顶层任务结束时统一释放 PRACTICE permit
```

`CHILD` 的 Definition 仍必须声明执行组，用于完整性、审计和未来独立入口检查；实际线程许可继承父 run。Runtime 应校验：

- `CHILD` 只能在 Agent 工作线程中内联执行，不能从普通业务线程伪造 `CHILD` 以绕过组容量。
- 子 Definition 声明的执行组必须与父线程当前执行组一致，禁止跨组子调用借用父组容量。
- `USER_ENTRY` 和 `BACKGROUND` 不能在已有 Agent 工作线程中伪装成新的顶层内联调用。

为支持该校验，`ManagedAgentExecutor` 的线程上下文应从当前单一 depth 标记扩展为 `depth + executionGroup`，嵌套返回时恢复上一层上下文，不能依赖线程名称判断执行组。

### 9.4 后台画像调用

```text
persistent-queue topic worker
  -> agentRuntime.execute(BACKGROUND)
  -> 获取 LEARNER_PROFILE_BACKGROUND permit
  -> 使用唯一保留槽执行画像 Agent
  -> worker 同步等待完成
  -> 完成后继续下一批
```

当前单 topic 单 worker 使画像后台 Agent 的实际并发不会超过 1，与组容量一致。本文不修改队列在执行失败时的确认和重试行为。

## 10. 拒绝与错误语义

扩展稳定拒绝原因：

```java
public enum AgentExecutionRejectionReason {
  GROUP_SATURATED,
  SATURATED,
  SHUTDOWN
}
```

语义：

| 原因 | 含义 | 预期场景 |
| --- | --- | --- |
| `GROUP_SATURATED` | 当前业务执行组许可耗尽 | 正常舱壁拒绝 |
| `SATURATED` | 已获得组许可，但物理执行池仍拒绝 | 直接交接窗口、线程创建失败或实现不变量异常 |
| `SHUTDOWN` | 应用正在停止 | 关停期间拒绝 |

组饱和继续映射为现有 `AGENT_EXECUTOR_OVERLOADED`，对外不暴露其他组容量。低敏错误 metadata 可以增加稳定 group code 和拒绝原因，但不得包含 userId、API Key、Provider 配置或用户输入。

组许可获取失败发生在远程模型调用前，不产生 Provider 费用。若 run 和治理租约已经创建，必须复用现有 submission failure 路径完成失败终态和资源释放。

## 11. 许可生命周期

许可必须覆盖完整工作线程占用时间：

```text
获取许可
  -> 提交线程池
  -> Agent loop
  -> SSE 同步写入或同步结果组装
  -> Runtime 成功/失败/取消结算
  -> 释放 run resource
  -> 释放组许可
```

必须覆盖以下路径：

- 同步 Agent 正常完成。
- 流式 Agent 正常完成。
- Provider 异常。
- Tool 异常。
- 结构化输出失败。
- SSE subscriber 异常。
- 客户端取消。
- Agent cancellation token 取消。
- 线程池提交拒绝。
- `beforeSubmission` 初始化失败；此时尚未获取组许可，不得改变许可计数。
- 应用关停中断。
- 任务抛出 `Error` 或其他非 RuntimeException。

建议只在 `ManagedAgentExecutor` 的任务包装器中持有顶层许可，使用 `finally` 释放；Runtime 不手工释放 semaphore。提交失败时由 executor 在任务尚未开始的路径中关闭 permit。

## 12. 并发不变量

实现必须保持以下不变量：

```text
active(practice) <= limit(practice)
active(learning-plan) <= limit(learning-plan)
active(learner-profile-background) <= limit(learner-profile-background)

sum(active(group)) <= ThreadPoolExecutor.maximumPoolSize

ThreadPoolExecutor.maximumPoolSize == sum(limit(active groups))
```

禁止使用 `ThreadPoolExecutor.getActiveCount()` 作为准入判断。该值是观测数据，不是原子许可，多个提交线程可能同时读取相同值并突破上限。

## 13. 观测设计

保留现有物理线程池指标：

```text
algo.mentor.agent.executor.active
algo.mentor.agent.executor.pool.size
algo.mentor.agent.executor.completed
algo.mentor.agent.executor.rejected
algo.mentor.agent.executor.queue.size
```

新增组级低基数指标：

```text
algo.mentor.agent.executor.group.limit{group="practice"}
algo.mentor.agent.executor.group.active{group="practice"}
algo.mentor.agent.executor.group.available{group="practice"}
algo.mentor.agent.executor.group.completed{group="practice"}
algo.mentor.agent.executor.group.rejected{group="practice",reason="group_saturated"}
```

允许的 tag 只包含代码注册的 `group` 和固定 `reason`。不得使用 userId、runId、模型名、provider instance ID 或其他高基数值。

建议告警基线：

- `learner-profile-background` 长时间 `active=0` 且队列存在满批待处理消息。
- 任一组持续出现 `group_saturated`。
- 物理线程池持续出现 `SATURATED`；单次直接交接窗口可以接受，持续发生表示线程创建、交接或容量实现存在问题。
- 总 active 长时间接近 30，同时 Provider timeout 或 SSE 取消耗时升高。

## 14. Spring 装配顺序

目标装配关系：

```text
List<AgentDefinition<?>>
  -> AgentDefinitionRegistry
  -> AgentExecutionBulkheadRegistry
       + AgentExecutorProperties.groups
  -> ManagedAgentExecutor(totalCapacity)
  -> DefaultAgentRuntime
```

`AgentExecutionBulkheadRegistry` 依赖已经完成注册的 Definition，因此可以在创建线程池前确定唯一容量事实。`ManagedAgentExecutor` 不再只依赖原始 properties 计算最大线程数。

需要避免以下循环依赖：

```text
AgentDefinition -> AgentRuntime -> AgentExecutor -> AgentDefinitionRegistry
```

Definition 本身不得注入 Runtime 或 Executor。当前需要子 Agent 的业务 service 继续在 Definition 外持有 Runtime，保持现有应用层调用方式。

## 15. 测试方案

### 15.1 配置与启动校验

- 正确绑定三个稳定 group code 和默认容量。
- 缺少当前生效组配置时启动失败。
- 未知 group code 时启动失败。
- 容量为 0 或负数时启动失败。
- 总容量超过 100 时启动失败。
- Definition 返回空执行组时启动失败。
- 配置包含代码已注册但当前未生效执行组时允许启动，且该组不计入总容量。
- Runtime 开启但没有任何生效 Definition 时启动失败。
- Runtime 关闭时不创建 bulkhead registry 和物理 Agent 执行池。
- 推导得到 `core=10`、`max=30`。
- 总容量小于 10 时，`core=totalCapacity`。

### 15.2 舱壁行为

- 27 个 `PRACTICE` 任务占满后，第 28 个立即得到 `GROUP_SATURATED`。
- `PRACTICE` 满载时，两个 `LEARNING_PLAN` 和一个画像后台任务仍能提交。
- 两个计划任务占满后，新计划任务被拒绝，但聊天和画像任务不受影响。
- 一个画像后台任务占满后，第二个画像任务被拒绝，但聊天和计划任务不受影响。
- 一个组空闲时，其他组不能借用其容量。
- 所有组同时满载时物理 active 不超过 30。
- 模拟物理 `SATURATED` 时释放已获取组许可并记录独立拒绝原因。

### 15.3 许可释放

- Runnable 正常返回后释放许可。
- Runnable 抛出 RuntimeException、Error 后释放许可。
- 线程池提交失败后释放许可。
- shutdown 拒绝后释放许可。
- 流式 subscriber 取消后最终释放许可。
- `beforeSubmission` 初始化失败时不获取许可，组 available 保持不变。
- Provider 失败和 SSE 写失败后释放许可。
- permit 重复 close 不会增加可用许可。

### 15.4 Runtime 语义

- 同步 `execute` 使用 Definition 声明的执行组。
- 流式 `stream` 首次订阅时使用同一执行组。
- 重复订阅不重复获取许可。
- `CHILD` 在父 Agent 线程内联执行，不获取第二个许可。
- 普通线程直接提交 `CHILD` 被拒绝，不能绕过组并发。
- 子 Definition 执行组与父线程当前组不一致时被拒绝。
- Agent 工作线程内伪造新的 `USER_ENTRY` 或 `BACKGROUND` 顶层调用时被拒绝。
- `USER_ENTRY` 和 `BACKGROUND` 顶层调用分别获取自己的组许可。

### 15.5 回归验证

- Agent run、step、trace 和 AI 调用台账语义不变。
- 用户额度和用户级运行锁行为不变。
- 模型路由和 reasoning effort 行为不变。
- SSE 事件协议、顺序和取消语义不变。
- Practice Code Review 和自述画像 CHILD Agent 继续在父线程执行。
- 应用 shutdown 继续等待运行中任务并在超时后中断。

## 16. 实施拆分

### 阶段一：核心契约

- 新增 `AgentExecutionGroup`。
- 为 `AgentDefinition` 增加必填 `executionGroup()`。
- 补齐七个现有 Definition 的组声明。
- 在 `AgentDefinitionRegistry` 增加执行组完整性和查询能力。

### 阶段二：配置与 Bulkhead Registry

- 将 `AgentExecutorProperties` 改为 group capacity 配置。
- 删除 `corePoolSize` 和 `maxPoolSize` 配置字段及环境变量。
- 新增 `AgentExecutionBulkheadRegistry` 和幂等 permit lease。
- 实现启动完整性、总容量和安全上限校验。

### 阶段三：执行器与 Runtime

- 修改 `AgentExecutor.execute`，要求传入执行组。
- 改造 `ManagedAgentExecutor` 的许可获取、包装和释放。
- 改造 `DefaultAgentRuntime.execute` 和 `stream` 提交链路。
- 改造 `SingleSubscriberAgentStreamPublisher` 以携带执行组。
- 保持 `CHILD` 内联和父组继承语义。

### 阶段四：指标与验证

- 增加组级指标和稳定拒绝原因。
- 补齐配置、并发、释放、流式、取消、关停和回归测试。
- 使用 `27 / 2 / 1` 做并发验证。
- 更新线程模型文档中的旧 `20/100` 参数基线和代码索引。

## 17. 发布与回滚

### 17.1 发布

发布前需要：

- 配置三个执行组容量。
- 删除旧 `AGENT_EXECUTOR_CORE_POOL_SIZE` 和 `AGENT_EXECUTOR_MAX_POOL_SIZE` 环境变量，避免运维误以为仍然生效。
- 确认总容量为 30。
- 确认画像队列 consumer 只在预期单节点启用。
- 观察各组 active、available、rejected 和物理 executor 指标。

### 17.2 停止条件

出现以下情况应停止放量：

- `learner-profile-background` 无法获得其唯一保留许可。
- 组许可释放后 available 超过配置 limit。
- 正常负载下频繁出现物理 `SATURATED`。
- `CHILD` 调用额外占用线程或发生自锁。
- SSE 取消后组 active 长时间不下降。
- 计划组拒绝率明显影响当前内测流程。

### 17.3 回滚

代码回滚可以恢复旧的单池 `20/100` 配置。由于本设计不修改数据库、API 或业务数据，不需要数据库回滚。回滚时需要同步恢复旧环境变量，否则执行池配置无法绑定。

## 18. 后续扩展边界

新增 AI 业务场景时必须明确选择现有执行组或通过代码评审新增稳定组。不得为临时需求直接增加自由文本配置 key。

未来只有在以下需求出现时，才考虑在本设计之上继续扩展：

- 用户等级需要不同的组内并发配额。
- 后台任务需要排队等待 executor 而不是留在业务持久化队列。
- Provider 需要独立于线程数的并发和 token 预算。
- 后台 worker 独立部署，需要单独的 AgentRuntime 和执行池。
- 多实例部署需要集群级容量协调。
- 不同执行组需要不同线程工厂、超时或关停策略，此时再评估拆分物理线程池。

在这些需求出现前，第一版保持“一个物理执行池、三个严格执行组、容量总和推导物理上限”的简单模型。
