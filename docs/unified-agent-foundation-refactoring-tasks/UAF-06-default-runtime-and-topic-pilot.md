# UAF-06：Default Runtime 与 Topic 完整竖切

> 波次：B
>
> 状态：DONE
>
> 直接依赖：UAF-02、UAF-03、UAF-04、UAF-05
>
> 建议上下文上限：14 个生产/测试文件

## 1. 目标与完成标准

实现可用于生产的 `DefaultAgentRuntime`，并将 `TOPIC_EXPLANATION` 同步与流式入口完整迁移。该任务是统一底座的首个端到端证明，不同时迁移其他场景。

## 2. 必须读取

- UAF-01 至 UAF-05 的完成备注和 `CURRENT.md`，不重读这些任务全部文件。
- 新建的 Runtime、同步 loop、工具视图、持久化和治理公开契约。
- `ExplainTopicUseCase.java` 及测试。
- `AiExplanationService.java`、相关 controller/SSE 测试。
- `ManagedSystemPromptDefinitions.TOPIC_EXPLANATION` 和 prompt resolver。
- `MentorAiConfiguration.java` 中旧 runner 与 Topic Bean 装配。

## 3. DefaultAgentRuntime 固定流程

```text
校验 Invocation 和 Definition
  -> 开始治理租约
  -> Definition 解析 Prompt / 组装受信 Context
  -> 创建或复用 task/turn/run
  -> 合并受信 metadata、路由 target 和 execution options
  -> 选择 run-local tools 与 maxSteps
  -> 当前为 Agent 工作线程：内联同步 loop
     否则：提交 executor
  -> 发布 stream 或收集 AgentRunResult
  -> 持久化和治理 complete/fail/cancel
```

任何 begin 之后的异常都必须经过唯一终态路径。Publisher 订阅前同步失败和 executor 拒绝也必须清理治理租约。

## 4. Topic Definition

新增类型化输入，例如：

```text
TopicExplanationAgentInput
  userId
  LearningTopic
  idempotencyKey
  display metadata
```

Definition 要求：

- key value 使用 `AiBusinessScenario.TOPIC_EXPLANATION.code()`。
- 使用现有受管理 Topic system prompt。
- 工具白名单为空。
- `maxSteps=1`。
- 输出为文本。
- 同步和流式调用共享 Definition。

业务 `LearningTopic` 校验在调用 Runtime 前完成。

## 5. Spring 装配

- `mentor-api` 作为 composition root 注册 executor、governance、persistence port、Definition Registry 和 `DefaultAgentRuntime`。
- Topic Definition 可以由 `mentor-application` 提供 Bean 或由 API 显式装配 application-owned Definition。
- 本阶段 Registry 只要求当前已迁移 Definition 正确，不要求其他八个场景空壳注册。
- 旧 `AgentLoopRunner`、`AgentRunner` Bean 继续服务未迁移场景。

## 6. 场景迁移

1. 将 `ExplainTopicUseCase` 依赖改为 `AgentRuntime`。
2. 同步 `explain` 使用 `execute`，流式 `stream` 使用 Runtime `stream`。
3. 将 controller/AiExplanationService 的业务前置校验保留在 Runtime 前。
4. 删除 Topic 路径直接治理准入，避免双扣额度。
5. 保持 HTTP 和 SSE 对外事件不变。
6. 确认 Topic run 创建用户独立审计 task/turn。
7. 删除 Topic 对 `AgentRunner` 和 `AgentLoopRunner` 的生产引用。

## 7. 重点测试

- execute/stream 最终文本一致。
- USER_ENTRY 只消耗一次额度并正确释放用户锁。
- 空工具请求不携带 tools，step 数为 1。
- 非 Agent 线程提交 executor。
- executor 拒绝、取消、Prompt 解析失败和 LLM 失败都有唯一终态。
- Topic task/turn/run 归属当前用户。
- controller 不再在 Runtime 前执行 AI admission。
- 旧 Topic SSE event name、顺序和错误映射不变。

## 8. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-runtime,mentor-application,mentor-api -am \
  -Dtest=ExplainTopicUseCaseTest,MentorAiConfigurationTest test

make backend-test
git diff --check
```

补充执行实际新增 Runtime、controller 或 SSE 测试类。

## 9. 停止条件

Topic 竖切未证明额度只扣一次、锁可释放、审计 run 可落库时，不得开始 UAF-07。

## 10. 上下文交接

`CURRENT.md` 记录 Runtime 主流程、Topic Definition 路径、Spring Bean 入口、关键测试和仍保留的旧入口。后续任务以 Topic 作为参考实现，不再重读 UAF-01 至 UAF-05 的详细实现历史。

## 11. 完成备注

完成时间：2026-07-29

状态：DONE

主要改动：

- 新增生产级 `DefaultAgentRuntime`，统一同步/流式主流程、审计准备、治理租约、执行器和唯一终态清理。
- 新增无工具单步的 `TopicExplanationAgentDefinition`，并由 `MentorAiConfiguration` 注册 Definition、Registry、Runtime 和治理适配。
- `ExplainTopicUseCase` 同步/流式统一调用 Runtime；`AiExplanationService` 保留业务校验并移除 Topic 重复准入。
- 将受影响的 MVC 测试切换为可控 slice，避免可选 AI Provider 配置影响接口契约测试。

验证：

- Runtime、Topic Definition、Topic use case、Spring 配置、SSE 及 `AgentConversationControllerTest` 目标测试通过。
- `make backend-test` 通过（261 份 Surefire 报告 failures/errors 均为 0）。
- `git diff --check` 通过。

偏离计划：无。

遗留事项：无。

下一任务：`UAF-07`
