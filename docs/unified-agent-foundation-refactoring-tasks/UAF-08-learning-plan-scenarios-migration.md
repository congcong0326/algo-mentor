# UAF-08：Learning Plan 三场景迁移

> 波次：C
>
> 状态：DONE
>
> 直接依赖：UAF-06
>
> 建议上下文上限：14 个生产/测试文件

## 1. 目标与完成标准

将学习计划草案、草案修订和计划扩展三个流式场景迁移到统一 Runtime，同时保留各自的领域状态机、单订阅 Publisher、结构化输出校验、事务边界和 stale 防覆盖逻辑。

完成后，三个 service 不再注入 `AgentLoopRunner`，`LearningPlanController` 不再直接编排 AI admission/lifecycle。

## 2. 必须读取

- `CURRENT.md` 和 UAF-06 完成备注。
- `LearningPlanDraftStreamService.java` 及测试。
- `LearningPlanDraftRevisionStreamService.java` 及测试。
- `LearningPlanExtensionProposalStreamService.java` 及测试。
- 两个 Prompt Builder、三个 JSON Schema/StructuredOutputMapper。
- `LearningPlanConfiguration.java`。
- `LearningPlanController.java` 的四个 AI stream 入口。
- 两类 Learning Plan SSE subscriber 测试。

先用 `rg` 定位实际类名；一次只展开一个场景的 service 和测试，避免同时加载三个完整测试文件。

## 3. 三个 Definition

建立三个独立类型化输入和 Definition：

| Agent key | 输入至少包含 | 输出 | task/turn |
| --- | --- | --- | --- |
| `LEARNING_PLAN_DRAFT` | userId、draft command、幂等键 | draft JSON Schema | 独立审计 task/turn |
| `LEARNING_PLAN_REVISION` | userId、draftId、instruction、幂等键 | 完整 draft JSON Schema | 独立审计 task/turn |
| `LEARNING_PLAN_EXTENSION` | userId、planId、groupId/上一版本、instruction、幂等键 | extension JSON Schema | 独立审计 task/turn |

固定要求：

- 三者均使用 `USER_ENTRY`。
- Prompt 继续由现有 Builder 和受管理 Prompt 生成。
- structured output 使用现有 provider-native JSON Schema 配置。
- 领域 mapper/validator 继续位于 `mentor-application`，Definition 不写学习计划表。
- 工具白名单从当前 Prompt 的真实需要逐项声明，初始候选为 `list_problem_filters`、`search_problems`、`get_problem_statement`；只有存在大结果引用需求时才加入 `read_tool_result`。
- Revision 即使当前 Prompt 较短，也必须根据 UAF-00 的实际行为决定是否需要题库工具，不能因其他两个场景需要而机械复制。
- 每个场景显式设置 `maxSteps`，全局 `50` 只作为硬上限。

## 4. 业务前检与流式时序

在 Runtime 前完成不产生 AI 副作用的业务检查：

- Draft 缺少必填字段时继续直接返回 `COLLECTING` 草案，不进入 Runtime。
- Revision 先验证 draft 存在、归属用户、未确认且存在可修订计划。
- Extension 先验证 plan 存在、归属用户、状态允许、proposal group/上一版本合法。

并发下仍要在原事务内重新加锁和复核。前检后发生的状态变化属于准入后失败，由 Runtime 进入失败终态，不通过回退额度掩盖。

保持以下流式约束：

- Publisher 未订阅前不创建 GENERATING revision，不调用 Runtime。
- Publisher 只允许订阅一次。
- Runtime 的 Agent events 继续投影为现有 work/draft/proposal SSE events。
- 只有最终无工具 step 的结构化输出可以进入领域 mapper。
- Runtime 已负责治理终态后，API SSE subscriber 不得再次调用 `AiRunLifecycleService`。

## 5. 领域事务保持不变

- Draft 的初始草案和最终 GENERATED/FAILED 状态仍由 draft service 管理。
- Revision/Extension 的 group、revisionNo、GENERATING、READY、FAILED 与 stale transition 仍在现有事务边界内。
- 较旧完成不得覆盖较新 READY revision 或 draft/plan。
- 结构化输出无效、Runtime 启动失败和订阅后错误都必须落现有业务失败状态。
- Runtime 审计 task/turn/run 与学习计划 draft/proposal 表是不同模型，不增加直接外键，必要关联只写稳定低敏 metadata。

## 6. 实施顺序

1. 先迁移 Draft 并通过最小测试。
2. 再迁移 Revision，重点验证 single-use 与 stale completion。
3. 最后迁移 Extension first/next revision 两条入口。
4. 更新 `LearningPlanConfiguration` 注册三个 Definition 和 service。
5. 删除 controller 的 admission/lifecycle 编排及 subscriber 的重复终态调用。
6. 删除三个 service 对 `AgentLoopRunner` 的生产依赖。
7. 更新完整配置装配测试，确保必需题库工具缺失时启动失败。

## 7. 重点测试

- 缺字段 Draft 不调用 Runtime、不消费 AI 交互额度。
- 每个真实生成入口只进行一次 USER_ENTRY 准入。
- 三个 run 使用各自的业务场景路由，而不是共享 Draft 场景。
- tool specs 只包含各 Definition 白名单。
- structured output、work status 和对外 SSE 顺序保持兼容。
- duplicate subscription 不创建第二个业务 revision 或 Agent run。
- startup failure、invalid output、cancel、stale completion 都产生唯一业务终态。
- 独立审计 task 归属当前用户，不进入 Mentor/Practice 会话历史。

## 8. 非目标

- 不修改学习计划产品契约或前端。
- 不把 draft/proposal 状态机移入 Runtime。
- 不合并三个 Definition。
- 不新增 Workflow 或跨场景编排器。

## 9. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-runtime,mentor-application,mentor-api -am \
  -Dtest=LearningPlanDraftStreamServiceTest,LearningPlanDraftRevisionStreamServiceTest,LearningPlanExtensionProposalStreamServiceTest,LearningPlanControllerTest test

git diff --check
```

补充执行两个 SSE subscriber 测试和新增 Definition 测试。UAF-09 完成后统一执行波次 C 的 `make backend-test`。

## 10. 停止条件

任一场景仍由 controller 和 Runtime 双重治理、旧 revision 能覆盖新结果、或失败后业务记录永久停留在 GENERATING 时，不得标记完成。

## 11. 上下文交接

记录三个 Definition、最终工具/maxSteps、业务前检入口、SSE adapter 变化和 stale 事务测试结果。不要带入完整 Schema 或长测试日志。

## 12. 完成备注

完成时间：2026-07-29

状态：DONE

主要改动：

- 新增 Draft、Revision、Extension 类型化 Definition；三者固定 `24` max steps，使用现有 JSON Schema。
- Draft/Revision/Extension service 改经 `AgentRuntime.stream`，保留各自领域状态机、事务与 stale 防覆盖。
- 配置注册三项 Definition 并校验声明工具；Controller 与 SSE subscriber 移除重复 admission/lifecycle。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-runtime,mentor-application,mentor-api -am -Dtest=LearningPlanDraftStreamServiceTest,LearningPlanDraftRevisionStreamServiceTest,LearningPlanExtensionProposalStreamServiceTest,LearningPlanDraftAgentDefinitionTest,LearningPlanControllerTest,SseLearningPlanDraftStreamSubscriberOpsTest,SseLearningPlanProposalStreamSubscriberOpsTest,MentorAiConfigurationTest test`：通过（36 tests）。
- `git diff --check`：通过。

偏离计划：无。

遗留事项：C 波次完整 `make backend-test` 由 UAF-09 完成后统一执行。

下一任务：`UAF-09`
