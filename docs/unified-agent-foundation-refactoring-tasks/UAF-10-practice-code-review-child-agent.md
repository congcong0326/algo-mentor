# UAF-10：Practice Code Review 子 Agent

> 波次：D
>
> 状态：IN_PROGRESS
>
> 直接依赖：UAF-09
>
> 建议上下文上限：13 个生产/测试文件

## 1. 目标与完成标准

将 Practice Code Review 的结构化模型调用从 `AiCompletionGateway` 迁移为 `AgentRuntime.execute` 的 `CHILD` 调用。Review 工具仍负责受信上下文读取和权限确认，Review service 仍负责领域校验与原子落库。

完成后，父 Practice run 的 Agent 工作线程内直接执行子 loop，不再次提交同一个 executor；子 run 有独立路由、记账和审计 task/turn/run。

## 2. 必须读取

- `CURRENT.md` 和 UAF-09 完成备注。
- `PracticeCodeReviewAgentTool.java` 及测试。
- `PracticeCodeReviewPermissionHook.java` 及测试。
- `PracticeCodeReviewService.java`、Prompt Builder、Schema、Output Mapper 及测试。
- `PracticeCodeReviewCommitService` 与 observer 的公开契约。
- `PracticeCodeReviewConfiguration.java`。
- UAF-04 retry/parent 持久化契约和 UAF-05 CHILD 治理公开接口。

## 3. Child Definition

新增类型化输入，例如：

```text
PracticeCodeReviewAgentInput
  trusted PracticeTurnContext
  idempotencyKey
```

Definition 固定：

- key 使用 `AiBusinessScenario.PRACTICE_CODE_REVIEW.code()`。
- Prompt 继续使用现有 Review Prompt Builder 和受管理 Prompt。
- 输出继续使用现有严格 JSON Schema。
- 工具集合为空，强制 `NONE + maxSteps=1`。
- Definition 只生成模型输入，不查询现有 Review，也不提交领域事务。

## 4. 父子调用语义

工具构造 `CHILD` Invocation 时必须使用：

- 受信 userId；
- 父 `agent_run` 数据库 ID，而不是仅用于 trace 的字符串 run UUID；
- `AgentExecutionContext.stepIndex()`；
- `PRACTICE_CODE_REVIEW` 自己的 Agent key；
- 基于 `sessionId + userMessageId` 的稳定低敏幂等键，使用常量前缀。

子调用必须：

- 不消费共享交互额度；
- 不获取用户锁或父 task 锁；
- 检查动态开关；
- 独立解析 Review 模型路由并记录 Token；
- 创建同用户的独立审计 task/turn/run；
- 写入 `parentRunId + parentStepIndex`，但不复用父 Practice task。

若当前线程由 Agent executor 标记，`execute` 必须内联。测试必须修改线程名前缀或使用普通线程名，证明实现没有依赖名称判断。

## 5. 业务幂等与失败降级

- `repository.findByUserMessage` 命中时直接返回已有 Review，不创建 child run。
- replay 缺少既有 Review 时继续返回当前稳定失败码，不补做模型调用。
- child Runtime 成功后，由现有 Output Mapper 决定 REVIEWED/UNREVIEWABLE。
- 只有 REVIEWED 才进入 commit service；正式事实、标签和队列发布事务语义不变。
- Runtime 拒绝、路由缺失、结构化输出无效或 LLM 失败继续映射为普通失败工具结果，使父 Agent 可以继续回答。
- 工具不得向模型参数开放 userId、sessionId、代码正文或 parent run 字段。

## 6. 实施步骤

1. 新增 Review input、Definition 和 Definition 测试。
2. 将 `PracticeCodeReviewService` 的模型执行依赖替换为 `AgentRuntime`。
3. 将 `AgentRunResult.output().structured()` 交给现有 mapper。
4. 从 Runtime result 的受信 metadata 取得实际 provider/model/usage；不得继承父路由快照。
5. 修改工具使用父 run DB id 和 step 构造 CHILD Invocation。
6. 更新 Spring 条件装配，Review 开启时 Runtime、Definition、commit 依赖缺一即启动失败。
7. 删除 Review 生产路径对 `AiCompletionGateway` 的引用。

## 7. 重点测试

- 父 Agent 工作线程内 child execute 不调用 executor.submit。
- 外部线程直接调用 child execute 时会提交 executor。
- child 不消耗额度、不获取用户锁，父 USER_ENTRY 仍只扣一次。
- child 使用 Review 场景的独立模型 route 和 Token 台账。
- child task 不等于父 task，且 userId、parent run、parent step 正确。
- 同一 userMessage 已有 Review 时没有 child Agent run。
- 用户拒绝/超时发生在工具执行前，不启动 child run。
- unreviewable、invalid output、Runtime failure 和 save failure 的工具 JSON 保持兼容。
- 正式 Review、标签关联和画像队列消息仍原子提交。

## 8. 非目标

- 不把 Review commit 放入 Runtime。
- 不新增 child 并行或通用多 Agent 调度。
- 不改变 Review 产品评分、Schema 或确认弹窗。

## 9. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-runtime,mentor-application,mentor-api -am \
  -Dtest=PracticeCodeReviewServiceTest,PracticeCodeReviewAgentToolTest,PracticeCodeReviewPermissionHookTest,PracticeCodeReviewFlowTest,PracticeCodeReviewFormalFactIT test

git diff --check
```

补充执行 child inline、父子持久化和治理模式测试。

## 10. 停止条件

child 仍使用父场景模型、重复扣额度、复用父会话 task、或在 Agent 工作线程再次提交 executor 时，不得进入 UAF-11。

## 11. 上下文交接

记录 Review Definition、稳定 child 幂等键、父 run DB id/step 来源、失败映射、内联测试和审计关联。不要携带完整 Review Schema。

## 12. 完成备注

完成时间：2026-07-29

状态：DONE

主要改动：

- 新增无工具单 step 的 Review Definition 及 CHILD 输入，服务和工具均迁移到 AgentRuntime。
- 子调用使用稳定幂等键、父 run 数据库 ID 与当前 step；实际模型元数据来自 Runtime result。
- 更新 Runtime 成功元数据、Review 装配及 Definition 注册表测试。

验证：

- Review 目标集、`DefaultAgentRuntimeTest`、两项 Spring 配置测试均通过（61 tests）。
- `git diff --check` 通过。

偏离计划：无。

遗留事项：无。

下一任务：`UAF-11`
