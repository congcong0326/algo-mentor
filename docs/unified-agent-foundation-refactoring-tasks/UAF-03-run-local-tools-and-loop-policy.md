# UAF-03：run-local 工具与 loop policy

> 波次：A
>
> 状态：DONE
>
> 直接依赖：UAF-02
>
> 建议上下文上限：9 个生产/测试文件

## 1. 目标与完成标准

让同步 loop 每次运行只看到当前 Definition 允许的工具，并让 `maxSteps` 成为 run 参数而不是 runner 全局实际值。完成后新增工具不会自动暴露给其他场景。

## 2. 必须读取

- `AgentToolRegistry.java`、`AgentToolRegistryTest.java`。
- UAF-02 提取出的同步 loop 内核及相关测试。
- `AgentLlmRequestFactory.java`。
- `AgentExecutionOptions.java`、`AgentLoopDefaults.java`。
- 权限 guard 在 loop 中的工具查找位置。

## 3. 工具选择契约

复用同一个全局 `AgentToolRegistry`，新增 run-local 只读选择能力。可以返回受限 Registry 或专用 `AgentToolSet`，但不能再创建第二套全局工具容器。

必须校验：

- 工具名非空且不重复。
- Definition 引用的工具存在于全局 Registry。
- 选择结果保持 Definition 声明顺序或稳定注册顺序。
- LLM request 只携带选择结果的 specs。
- Tool Call 即使命中全局工具，只要不在 run-local 集合中也不能执行。

未允许工具调用使用稳定错误码。优先新增 `TOOL_NOT_ALLOWED`；若现有错误模型不适合增加枚举，必须在完成备注记录最终选择并保持测试可区分 unknown 与 forbidden。

## 4. loop policy

- 同步 loop 接收本次 `maxSteps` 和 run-local tools。
- 空工具集合强制 `LlmToolChoice.none()` 和 `maxSteps=1`。
- 非空工具集合使用 `LlmToolChoice.auto()`。
- Definition 的 `maxSteps` 必须为正数。
- 全局硬上限由 Runtime 配置在 UAF-06 接线，本任务只提供校验函数或契约。
- 旧 `AgentLoopRunner` 继续使用当前全量工具和现有配置，保证未迁移场景不变。

## 5. 实施步骤

1. 增加 Registry 选择与验证 API。
2. 调整同步 loop 和 request factory 使用 run-local specs。
3. 调整工具执行查找只使用受限视图。
4. 将 maxSteps/toolChoice 从同步内核构造期状态改为 run 参数；兼容 wrapper 负责传旧值。
5. 增加空集合、子集、未知名称、重复名称、越权 Tool Call 和最大 step 回归测试。
6. 运行波次 A 全量后端测试。

## 6. 非目标

- 不注册业务 Definition。
- 不实现 Spring 条件装配。
- 不增加 `REQUIRED`、`SPECIFIC` 或 Definition timeout。
- 不修改权限 hook 业务判断。

## 7. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-core,agent-runtime -am \
  -Dtest=AgentToolRegistryTest,AgentLoopRunnerTest test

make backend-test
git diff --check
```

## 8. 上下文交接

记录 run-local 类型名称、同步 loop 新参数、未允许工具错误码和兼容 wrapper 行为。不要带入所有工具 schema。

## 9. 完成备注

完成时间：2026-07-29

状态：DONE

主要改动：

- `AgentToolRegistry.select(...)` 验证空名、重复名和未知工具，并保留 Definition 声明顺序。
- `AgentLoopExecution` 承载单次工具白名单、步数和工具策略；空工具固定 `NONE + 1 step`，并提供全局硬上限校验。
- `AgentLoopEngine` 按运行时工具集构造请求和执行调用；已注册但未允许的工具返回 `TOOL_NOT_ALLOWED`，未知工具仍返回 `UNKNOWN_TOOL`。
- `AgentLoopRunner` 通过 legacy execution 保持旧全量工具与既有 tool choice 行为。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-core,agent-runtime -am -Dtest=AgentToolRegistryTest,AgentLoopEngineTest,AgentLoopRunnerTest test`：通过（42 tests）。
- `make backend-test`：通过。
- `git diff --check`：通过。

偏离计划：无。

遗留事项：无。

下一任务：`UAF-04`
