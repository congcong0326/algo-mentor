# UAF-09：Practice Chat 根场景迁移

> 波次：C
>
> 状态：DONE
>
> 直接依赖：UAF-07、UAF-08
>
> 建议上下文上限：14 个生产/测试文件

## 1. 目标与完成标准

将题目训练聊天根 run 迁移到 `AgentRuntime.stream`，复用 Practice Session 已绑定的会话 task，并保留题目上下文、教练风格、回复语言、画像召回、工具权限、SSE 和 session touch 行为。

完成后，所有用户直接发起的正式 AI 场景都只通过 Runtime；子工具内部的结构化推理留给 UAF-10、UAF-11。

## 2. 必须读取

- `CURRENT.md`、UAF-07 和 UAF-08 的完成备注。
- UAF-07 最终的会话准备、task 锁和 replay 适配器。
- `PracticeMessageStreamService.java`、`PracticeTurnOrchestrator.java` 及测试。
- `AgentConversationService.java` 中 Practice context 组装部分。
- `PracticeChatPromptSectionProvider.java`、`PracticeChatPromptConstants.java`。
- `LearnerProfileRecallService` 和 Prompt section provider 的公开契约。
- `PracticeSessionController.java` 的 message stream 入口。
- `AgentConversationApiAutoConfiguration.java` 的 Practice Bean。

## 3. Practice Definition

新增 `PracticeChatAgentInput`，至少包含：

```text
userId
practiceSessionId
agentTaskId
planId / phaseIndex / problemSlug
userMessage
idempotencyKey
locale
coachStyle / responseLanguage
requestSize
```

输入中的 session、plan、phase、problem 和 task 关联必须来自服务端 repository，不信任请求体直接声明。

Definition 要求：

- key 使用 `AiBusinessScenario.PRACTICE_CHAT.code()`。
- 复用现有 Practice Prompt assembly、summary/history 和 learner profile recall。
- 复用 Practice Session 的 agent task，新建 turn/run。
- 输出为文本，使用显式非空工具白名单和场景 `maxSteps`。
- 完整生产配置至少允许 `submit_practice_code_review` 与 `update_learner_declared_profile`。
- 功能关闭时，对应工具不注册也不进入白名单；不能注册 no-op 工具骗过 Definition 校验。
- 当前题面已经由服务端注入，默认不加入题库搜索/读取工具。只有 UAF-00 或明确测试证明存在真实调用需求时才加入。
- `calculator` 等通用工具同样需要真实 Prompt/测试依据。
- `read_tool_result` 只有在某个允许工具会产生可读取的大结果引用时才加入。

## 4. 业务前检

`PracticeMessageStreamService` 在构造 Invocation 前完成：

- session 存在且归属当前用户；
- session 状态为 ACTIVE；
- session 存在 agentTaskId；
- plan、phase、problem 关联仍有效；
- locale、教练风格和回复语言使用服务端规则解析。

Definition/context assembler 可以在 run 准备时再次读取动态题面和画像，但不得替代上述所有权校验。业务前检失败不进入 Runtime，也不消费额度。

## 5. 根 run 与工具边界

保持以下链路：

```text
PracticeSessionController
  -> PracticeMessageStreamService
       -> PracticeTurnOrchestrator
            -> AgentRuntime.stream(PRACTICE_CHAT, USER_ENTRY)
                 -> run-local Practice tools
```

- `PracticeTurnOrchestrator` 不再依赖旧 `AgentConversationRunCoordinator` 的 model execution 路径。
- 可以复用 UAF-07 的通用会话 task/lock/replay 适配器，但不能重新合并 Mentor 与 Practice 的类型化输入。
- 工具只从受信 Runtime metadata 读取 userId、sessionId、task/turn/run 和当前 step。
- `PracticeCodeReviewPermissionHook` 继续在 Review 工具执行前请求用户确认。
- 父 run 只负责工具调用；Review/画像内部模型路由仍暂由旧服务执行，分别在 UAF-10、UAF-11 替换。

## 6. Controller 与 SSE

1. 从 `PracticeSessionController` 删除 `AiRunAdmissionService` 和 `AiActorResolver` 的 AI 准入用途。
2. Controller 只解析当前用户、幂等键、locale、request size 并调用 application service。
3. 保持现有 `PRACTICE_MESSAGE` SSE 类型、事件名、错误映射和取消处理。
4. `touchLastMessageAt` 继续只在 run end 后执行；失败只记录日志，不改变流终态。
5. replay 的 session touch 行为按现有 characterization test 保持，不在本任务重新定义。

## 7. 重点测试

- 越权、缺失、归档或无 agentTaskId 的 session 不调用 Runtime。
- Practice task 复用、新 turn 创建、幂等 replay 和 task 锁行为正确。
- Prompt 包含服务端题面、计划阶段、教练风格、语言和裁剪后的画像快照。
- 未声明的题库、后台画像或其他工具 schema 不进入模型请求。
- Review/profile 工具按功能开关进入白名单，权限 hook 仍生效。
- USER_ENTRY 只由 Runtime 准入一次；controller 不再直接消耗额度。
- run end 后 touch session；error/cancel 不误报成功。
- Practice SSE 和 API response 保持兼容。

## 8. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-runtime,mentor-application,mentor-api -am \
  -Dtest=PracticeTurnOrchestratorTest,PracticeMessageStreamServiceTest,PracticeChatPromptSectionProviderTest,AgentConversationControllerTest,PracticeSessionControllerTest test

make backend-test
git diff --check
```

补充执行 Definition 白名单、权限 hook 和画像 recall 相关测试。

## 9. 停止条件

父 run 无法提供稳定 `runDbId + stepIndex` 给工具、Practice task 被错误替换为独立审计 task、或 controller 仍重复准入时，不得进入 UAF-10。

## 10. 上下文交接

记录 Practice Definition、最终工具/maxSteps、受信 metadata 来源、根 run DB id 取得方式、task/lock/replay 入口和波次 C 全量结果。不要复制完整题面或 Prompt。

## 11. 完成备注

完成时间：2026-07-29

状态：DONE

主要改动：

- 新增 `PracticeChatAgentInput`、`PracticeChatAgentDefinition` 与 `PracticeChatRunAdapter`；根 run
  使用 `PRACTICE_CHAT`、文本输出、`8` steps，仅暴露已装配的 Review 与自述画像工具。
- `PracticeMessageStreamService` 在调用 Runtime 前完成 session 所有权、ACTIVE、agent task 和受信关联
  前检；`PracticeTurnOrchestrator` 统一调用 `AgentRuntime.stream(USER_ENTRY)`，task lock、replay 和
  run 资源由适配器负责。
- `PracticeSessionController` 保持轻量入口；通用 `AgentConversationController` 的 Practice 分支改为
  仅接收 `sessionId` 并委派同一 application service，删除直接准入和旧协调器执行路径。

验证：

- UAF-09 目标测试通过（25 tests）；Definition、权限 hook、画像 recall 与 Practice context 补充测试
  通过（19 tests）。
- `make backend-test`：通过，205 份 Surefire 报告无 failures/errors；`git diff --check`：通过。

偏离计划：无。

遗留事项：无。

下一任务：`UAF-10`
