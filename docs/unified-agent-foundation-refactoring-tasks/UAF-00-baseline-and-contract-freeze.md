# UAF-00：基线与行为冻结

> 波次：A
>
> 状态：DONE
>
> 直接依赖：无
>
> 建议上下文上限：8 个生产/测试文件

## 1. 目标与完成标准

在修改执行底座前固定当前行为、调用入口和测试基线。完成后必须能够回答：哪些场景依赖哪个旧入口，哪些事件和治理行为不得回归，当前全量测试是否存在与本重构无关的失败。

本任务不引入新 Runtime，不移动生产类，不修改数据库 schema。

## 2. 必须读取

- `docs/unified-agent-foundation-refactoring-design.md` 的第 2、3、10 节。
- `AgentLoopRunner.java`、`AgentLoopLifecycle.java`、`AgentLoopRunnerTest.java`。
- `ManagedAgentExecutor.java`、`ManagedAgentExecutorTest.java`。
- `AiRunAdmissionServiceTest.java`、`AiGovernedCompletionServiceTest.java`。
- `PersistentAgentRunObserverTest.java`、`PostgresAgentConversationRepositoryTest.java`。

先用 `rg` 定位这些文件，不读取其他任务文件。

## 3. 实施步骤

1. 记录 `git status --short`，区分用户已有修改与本任务改动。
2. 用 `rg` 生成并核对以下生产调用清单：
   - `AgentLoopRunner` 注入点；
   - `AgentRunner` 注入点；
   - `AiCompletionGateway` 注入点；
   - controller 直接调用 `AiRunAdmissionService` 的入口；
   - 全局 `AgentToolRegistry.specs()` 和全局 `maxSteps` 使用点。
3. 运行 agent-core、executor、治理和持久化最小测试，记录基线结果。
4. 检查现有测试是否已经固定以下行为；缺失时只补 characterization test：
   - run/step/tool/final/error 事件顺序；
   - structured output 最终捕获；
   - stream 取消和 executor 拒绝；
   - unknown tool 终止；
   - USER_ENTRY 消耗额度并持有用户锁；
   - PARENT_RUN/BACKGROUND 不走额度；
   - run 成功、失败和 trace snapshot 持久化。
5. 运行 `make backend-test`，把失败分为本任务新增失败、已存在失败或环境失败。
6. 更新本文件完成备注、状态板和 `CURRENT.md`。

## 4. 允许修改的主要范围

- 现有测试文件。
- 当前任务文档和状态文档。

除非为了让 characterization test 可注入依赖，不修改生产行为。

## 5. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-core -am \
  -Dtest=AgentLoopRunnerTest,AgentLoopLifecycleTest,AgentToolRegistryTest test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl ai-governance -am \
  -Dtest=AiRunAdmissionServiceTest,AiGovernedCompletionServiceTest test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-persistence-postgres,mentor-api -am \
  -Dtest=PersistentAgentRunObserverTest,PostgresAgentConversationRepositoryTest,ManagedAgentExecutorTest test

make backend-test
git diff --check
```

## 6. 停止条件

- 发现现有测试无法确定某个真实业务行为时，记录为 `UAF-06` 或对应场景任务的迁移门禁，不在本任务设计新语义。
- 全量测试因外部数据库或服务不可用失败时，保留最小测试结果和环境证据，不修改业务代码绕过。

## 7. 上下文交接

完成备注只保留调用入口数量、补充的测试、基线命令结果和下一个任务。不要复制测试日志。

## 8. 完成备注

完成时间：2026-07-29

状态：DONE

主要改动：

- 核对旧入口：6 个直接 loop 场景、1 个 `AgentRunner` 同步兼容入口、3 个 direct completion 场景。
- 冻结全局工具 `specs()` 与全局 `maxSteps` 使用点，以及 controller 直接准入入口。
- 确认既有测试覆盖事件顺序、结构化输出、取消、executor 拒绝、未知工具、额度/锁、run/turn 终态和 trace 持久化。

验证：

- agent-core 最小测试：PASS（36 tests）。
- ai-governance 最小测试：PASS（8 tests）。
- persistence/executor 最小测试：PASS（17 tests）。
- `make backend-test`：PASS。

偏离计划：无。

遗留事项：无。

下一任务：`UAF-01`
