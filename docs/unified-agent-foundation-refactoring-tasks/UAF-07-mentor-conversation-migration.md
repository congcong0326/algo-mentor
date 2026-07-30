# UAF-07：Mentor Conversation 迁移

> 波次：C
>
> 状态：DONE
>
> 直接依赖：UAF-06
>
> 建议上下文上限：12 个生产/测试文件

## 1. 目标与完成标准

将普通导师会话迁移到 `AgentRuntime.stream`，保留已有会话 task、上下文召回、task 级互斥、幂等 replay 和 SSE 契约。本任务只迁移非 Practice 的 Mentor Conversation；Practice Chat 在 UAF-09 单独迁移。

完成后，普通导师会话的业务代码不再直接调用 `AgentLoopRunner`，同一请求也不会同时走旧 loop 和 Runtime。

## 2. 必须读取

- `CURRENT.md` 和 UAF-06 完成备注，不重读 UAF-01 至 UAF-05。
- `AgentConversationService.java`、`AgentConversationRunCoordinator.java`。
- `AgentConversationCommand.java`、`AgentConversationRun.java`。
- `AgentConversationRepository` 的准备、幂等和 recent messages 契约。
- `AgentConversationController.java` 中非 Practice 请求路径。
- `AgentConversationApiAutoConfiguration.java` 的会话 Bean。
- `AgentConversationServiceTest.java`、`AgentConversationRunCoordinatorTest.java`、`AgentConversationControllerTest.java`。

## 3. Mentor Definition

新增类型化输入，名称可按现有包结构调整：

```text
MentorConversationAgentInput
  taskId (可空，新会话)
  userId
  userMessage
  idempotencyKey
  requestSize
```

Definition 要求：

- key 使用 `AiBusinessScenario.MENTOR_CONVERSATION.code()`。
- 使用现有 `MENTOR_CONVERSATION` 受管理 Prompt。
- 复用现有 summary、recent messages 和当前 user message 组装顺序。
- 已有 task 时复用该 task 并创建新 turn；新会话创建用户归属 task。
- 输出为文本。
- 工具必须形成显式最小白名单，不能继续继承全局 Registry。
- UAF-00 没有证明为产品行为的工具不得仅因“已注册”而加入。至少排除代码 Review 和画像更新工具。
- 非空白名单的 `maxSteps` 使用 UAF-00 冻结并在本任务记录的场景值，不得无说明沿用全局 `50`。

若保留题库查询工具，`read_tool_result` 只能作为可能产生大结果引用的工具的配套能力加入；不能单独暴露。

## 4. 会话准备与锁语义

复用现有机制，不重新设计会话模型：

1. `taskId` 存在时继续验证 task 归属当前用户。
2. 同一 task 同时只允许一个活跃 run。
3. 相同幂等键冲突继续返回已有 run 的 replay 事件，不再次执行模型。
4. 新会话在获得实际 taskId 后、进入 loop 前完成 task 锁保护。
5. task 锁 token 继续通过受信 metadata 或 UAF-06 已建立的资源清理机制释放。
6. Runtime 的 USER_ENTRY 用户锁与会话 task 锁是两层不同语义，不互相替代。
7. Runtime begin 之后的准备、订阅、executor 和模型失败必须同时释放治理租约与 task 锁。

优先把现有 prepare/replay/lock 逻辑改造成 Definition 使用的窄适配器。不得在 `DefaultAgentRuntime` 中增加 Mentor 字符串分支。

## 5. 迁移步骤

1. 从当前联合命令中提取非 Practice 的类型化输入和 Definition。
2. 将 prompt/context 准备、task/turn/run 准备和 task 锁接到 Runtime 已有扩展点。
3. 让普通会话入口调用 `AgentRuntime.stream`。
4. 从 controller 删除普通会话的直接 AI admission；只传受信用户、请求大小和幂等键。
5. 在迁移期保留 Practice 分支的旧实现，代码中明确标记由 UAF-09 删除。
6. 删除普通 Mentor 路径对 `AgentLoopRunner` 的生产调用。
7. 更新 Spring 条件装配，缺少 Mentor Definition 或 Runtime 时保持稳定的不可用错误。

## 6. 重点测试

- 新 task 与已有 task 都写入正确用户、turn 和 run。
- recent messages、summary、Prompt snapshot 和当前消息顺序不变。
- 相同幂等键不创建第二组消息或执行第二次模型。
- 不同幂等键的并发请求继续产生 task 冲突。
- task 锁在同步失败、异步错误、取消和正常结束后释放。
- USER_ENTRY 治理只由 Runtime 执行，controller 不再重复准入。
- 普通 Mentor Definition 不暴露 Review、画像或其他未声明工具。
- SSE 事件名、taskId/runId metadata、错误响应保持兼容。

## 7. 非目标

- 不迁移 Practice Chat。
- 不改变前端 API。
- 不重写 context compaction 或 summary 策略。
- 不把 task 锁合并进用户治理锁。

## 8. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-runtime,mentor-application,mentor-api -am \
  -Dtest=AgentConversationServiceTest,AgentConversationRunCoordinatorTest,AgentConversationControllerTest test

git diff --check
```

补充执行新 Definition 和 Runtime 会话适配测试。

## 9. 停止条件

普通会话尚存在同一请求双执行、task 锁泄漏或跨用户 task 复用时，不得开始 UAF-09；不影响本任务完成的 Practice 旧分支需记录给 UAF-09。

## 10. 上下文交接

记录 Mentor Definition 类、最终工具白名单、会话准备适配器、task 锁释放入口、仍保留的 Practice 旧分支和测试结果。不要复制 Prompt 正文或 repository SQL。

## 11. 完成备注

完成时间：2026-07-29

状态：DONE

主要改动：

- 新增 `MentorConversationAgentInput`、`MentorConversationAgentDefinition` 和
  `MentorConversationRunAdapter`；Definition 使用 `MENTOR_CONVERSATION`、空工具白名单和单 step。
- `AgentConversationService.prepareMentorRun` 保留 task/turn/run 准备、幂等 replay、上下文顺序及 task 锁。
- `AgentPreparedRequest`/`DefaultAgentRuntime` 支持已持久化 run 与终态资源，统一释放会话 task 锁。
- 普通会话 Controller 改经 `AgentRuntime.stream`；Practice 旧协调器分支保留，由 UAF-09 删除。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-runtime,mentor-application,mentor-api -am -Dtest=DefaultAgentRuntimeTest,MentorConversationAgentDefinitionTest,AgentConversationServiceTest,AgentConversationRunCoordinatorTest,AgentConversationControllerTest,MentorAiConfigurationTest test`：通过。
- `make backend-test`：通过；`git diff --check`：通过。

偏离计划：无。

遗留事项：Practice Chat 仍使用旧 admission/coordinator 路径，作为 UAF-09 的明确迁移范围。

下一任务：`UAF-08`
