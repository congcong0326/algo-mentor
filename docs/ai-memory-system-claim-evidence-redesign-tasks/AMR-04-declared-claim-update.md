# AMR-04：用户自述原子 Claim 更新链路

> 波次：B
>
> 状态：DONE
>
> 直接依赖：AMR-03
>
> 建议首轮文件上限：12

## 1. 目标与完成标准

把 `update_learner_declared_profile` 从 dimension 整段 `NO_CHANGE / REPLACE` 改为原子 claim 的 `ADD / REVISE / RETIRE / no-op`，并把当前受信用户消息作为 message evidence。

完成后，用户纠正一个事实不会覆盖同 dimension 的其他 claim；工具失败仍降级，不阻断 Practice Chat；本 run 的召回快照不刷新。

## 2. 必须读取

- `CURRENT.md`、`AMR-03` 完成备注和统一 apply API。
- `CONTRACTS.md` 第 6、8、12 节。
- `UpdateLearnerDeclaredProfileAgentTool.java` 及测试。
- `DeclaredProfileUpdateRequest.java`、`DeclaredProfileUpdateResult.java`、`LearnerDeclaredProfileToolContracts.java`。
- `DeclaredProfileUpdateService.java`、Definition、Prompt Builder、JSON Schema 及对应测试。
- `AgentTurnMessageLookupRepository.java`、`AgentTurnMessages.java`。
- `PracticeCodeReviewPermissionHook.java`，只参考从父 run 读取当前用户消息的方式。
- `AgentConversationApiAutoConfiguration.java` 的 declared Bean 片段。

## 3. 受信输入与范围

- 工具参数继续只包含最多 5 个唯一 declared dimension、statement 和 intent；不接受 user ID、message ID、claim ID 或状态。
- 服务端从 `AgentExecutionContext` 取得当前用户、父 run DB ID 和父 step。
- 使用 turn message lookup 取得当前 user message ID 和原文，并通过数据库再次验证归属。
- Agent 初始输入包含请求 dimension 下全部 ACTIVE declared claim，而不是每 dimension 一个聚合正文。
- `SELF_ABILITY_ASSESSMENT` 只能生成 declared claim，不能修改 tag/system claim。

## 4. Agent 输出与 evidence

- 根对象统一为 `operations`，严格 JSON Schema，未知字段拒绝。
- declared Agent 只允许 `ADD / REVISE / RETIRE`，不允许 `CONFIRM`。
- operation 必须在请求允许的 declared dimension 内；最多 10 项，同一 ACTIVE revision 最多一次。
- `ADD` 自动绑定当前消息 `DECLARED` evidence 和 `USER_DECLARATION`。
- 明确纠正或声明不再成立时，`REVISE / RETIRE` 自动绑定当前消息 `CORRECTED`；需要时复制旧声明 evidence，形成自包含 `USER_CORRECTION`。
- 模型不输出 message ID、evidence grade、origin、provider 或时间。

## 5. Run、并发与结果契约

- update run trigger 为 `DECLARED_FACT`，幂等键绑定父 run、父 step、工具名和规范化请求。
- child Agent retry 继续复用当前最多一次 stale 策略，并写 `retry_of_run_id`。
- 成功 apply 后更新 run 为 `SUCCEEDED`；空 operation 为 `NO_CHANGE`；解析、AI 或应用失败为 `FAILED`。
- 工具结果继续保留稳定 `type/status/message/items`，前端现有“更新中/已更新/无需更新/失败”映射不破坏。
- 结果摘要只做受限长度展示，不返回 message 原文、历史 claim 或 evidence。

## 6. 实施步骤

1. 将 declared Schema/Prompt/mapper 改为 operation 契约，先固定严格输出测试。
2. 接入受信 turn message 查询和 ownership 校验。
3. 重写 service：读取 scope snapshot -> 事务外 child Agent -> 统一原子 apply -> stale 时完整重读一次。
4. 调整 tool result 映射和自动配置，删除旧 `ProfileUpdateDecision` 依赖。
5. 更新单元测试、PostgreSQL IT 和失败降级 E2E。

## 7. 重点测试

- 同 dimension 已有多条 claim 时只修订目标 claim。
- 新声明、明确纠正、明确不再成立、无长期信息四类样例。
- 模型伪造 revision、越权 dimension、输出 CONFIRM/系统 claim、重复目标时整批拒绝。
- 当前消息 evidence ID/原文来自数据库，不信任 tool statement。
- stale retry 重新读取全部 requested scope；第二次 stale 零写入。
- LLM 失败、invalid output、数据库失败只返回工具失败，Practice Chat 主链继续。
- 现有前端 tool lifecycle 状态测试保持通过。

## 8. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am \
  -Dtest='*Declared*Memory*Test,*DeclaredProfile*Test,*UpdateLearnerDeclaredProfile*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test='DeclaredProfileUpdateIT,LearnerProfileFailureDegradationIT' verify

npm --cache ./.npm --prefix frontend test -- \
  src/learning-plans/PracticeChatWorkbench.test.tsx

git diff --check
```

## 9. 非目标与停止条件

- 不实现用户手动编辑、拒绝、抑制或消息深链。
- 不让 declared 更新同步刷新当前 run 的 recall snapshot。
- 若 evidence 仍来自模型传入的 statement 或 message ID，不得标记完成。

## 10. 上下文交接

记录 declared Schema 版本、child 幂等键、受信消息查询入口、工具结果兼容情况和 stale 行为。不要记录用户消息样例。

## 11. 完成备注

完成时间：2026-07-30

状态：DONE

主要改动：

- 自述子 Agent 改为 `operations` 严格 Schema，仅允许 `ADD / REVISE / RETIRE / no-op`。
- 当前用户消息经 `AgentTurnMessageLookupRepository` 查询并由数据库复核归属；请求 statement 仅参与幂等键规范化。
- 通过统一原子 apply 写入声明/纠正消息证据，保留纠正目标的旧消息证据；失败 run 和一次 stale 重算均受控。
- 工具维持既有 `type/status/message/items` 返回契约，异常降级为普通失败结果。

验证：

- `mentor-application` declared 目标测试：通过（7 项）。
- `DeclaredProfileUpdateIT`：通过（9 项）；`LearnerProfileFailureDegradationIT`：通过（2 项）。
- `PracticeChatWorkbench.test.tsx`：通过（31 项）；`git diff --check`：通过。

偏离计划：

- 为覆盖真实 stale，在 PostgreSQL 集成测试中用受控并发更新触发快照变化；未引入额外重试策略。

遗留事项：

- 无。

下一任务：`AMR-05`
