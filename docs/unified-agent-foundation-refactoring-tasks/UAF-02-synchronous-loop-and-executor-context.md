# UAF-02：同步 loop 与线程模型

> 波次：A
>
> 状态：DONE
>
> 直接依赖：UAF-01
>
> 建议上下文上限：10 个生产/测试文件

## 1. 目标与完成标准

把 Agent loop 控制流提取为可在当前线程同步运行的内核，并让 executor 提供可靠的工作线程归属判断。现有 `AgentLoopRunner.stream` 继续兼容所有未迁移场景。

## 2. 必须读取

- `AgentLoopRunner.java` 及其测试。
- `SingleSubscriberAgentStreamPublisher.java`、`AgentStreamEventSink.java`、`AgentCancellationToken.java`。
- `AgentExecutor.java`、`ManagedAgentExecutor.java` 及其测试。
- `RequestTraceContext` 的包装方式。

只打开 loop 执行和 executor 相关文件，不读取业务场景代码。

## 3. 目标结构

优先在 `agent-core` 提取同步内核，保留兼容 wrapper：

```text
AgentLoopRunner.stream
  -> SingleSubscriber publisher
  -> AgentExecutor.execute
  -> AgentLoopEngine.run synchronously

DefaultAgentRuntime（UAF-06）
  -> AgentLoopEngine.run synchronously
```

具体类名可调整，但必须避免 `agent-core -> agent-runtime` 反向依赖。

## 4. 线程契约

扩展 `AgentExecutor`：

- 增加 `boolean inExecutorThread()`，默认实现返回 `false`，兼容现有测试替身。
- `ManagedAgentExecutor` 在执行任务的最外层使用 ThreadLocal 或等价显式上下文标记。
- 标记必须在 `finally` 中清理。
- 不通过线程名称、线程 ID 范围或调用栈推断。
- Request trace 包装与 executor 标记的嵌套顺序必须通过测试固定。

本任务只提供判断能力；“当前线程内联，否则提交”的 Runtime 调度在 UAF-06 实现。

## 5. 实施步骤

1. 提取同步 loop 内核，保持 run/step/tool/final/error 事件顺序不变。
2. 让旧 `AgentLoopRunner` 委托同步内核，不改变构造函数和生产 Bean。
3. 确保同步内核的异常仍转成现有 `AgentException` 和唯一终态事件。
4. 扩展 executor 线程契约并实现 Managed 标记。
5. 增加同步执行、异步 wrapper、线程内/线程外、异常清理和线程名前缀变化测试。
6. 运行 UAF-00 固定的 loop 与 executor 回归。

## 6. 非目标

- 不改变工具白名单和 maxSteps 来源。
- 不实现 `AgentRuntime` 调度。
- 不迁移业务调用者。
- 不实现通用递归深度或子 Agent scheduler。

## 7. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-core -am \
  -Dtest=AgentLoopRunnerTest,AgentLoopLifecycleTest test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dtest=ManagedAgentExecutorTest test

git diff --check
```

## 8. 停止条件

如果提取同步内核必须改变生命周期事件顺序，先补兼容适配并保持现状，不把事件协议变化带入本任务。

## 9. 上下文交接

记录最终同步入口签名、executor 判断方法、兼容 wrapper 和测试结果。下一任务不需要重读完整 runner，只读取新内核和工具请求构造位置。

## 10. 完成备注

完成时间：2026-07-29

状态：DONE

主要改动：

- 新增同步 `AgentLoopEngine.run(request, sink, cancellationToken)`，旧 `AgentLoopRunner.stream` 保持 publisher/executor 兼容适配。
- `AgentExecutor` 新增默认 `inExecutorThread()`；Managed 实现使用带 finally 清理的 ThreadLocal 深度标记，并保留 trace 上下文传播。
- 增加同步 engine、线程名变化、trace 嵌套和失败后标记清理测试。

验证：

- `-pl agent-core -am -Dtest=AgentLoopRunnerTest,AgentLoopLifecycleTest,AgentLoopEngineTest test`：PASS。
- `-pl mentor-api -am -Dtest=ManagedAgentExecutorTest test`：PASS。
- `git diff --check`：PASS。

偏离计划：无。

遗留事项：无。

下一任务：`UAF-03`
