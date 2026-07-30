# UAF-12：Code Review Profile 后台 Agent

> 波次：D
>
> 状态：DONE
>
> 直接依赖：UAF-11
>
> 建议上下文上限：13 个生产/测试文件

## 1. 目标与完成标准

将正式 Code Review 批量画像更新从后台 direct completion 迁移为 `AgentRuntime.execute` 的 `BACKGROUND` 调用，保留严格五条满批、十题窗口、最多一次 stale retry、最多一次出队和失败降级语义。

完成后，队列 consumer 仍只负责批次校验和回调，模型判定通过统一 Agent loop 获得独立用户审计记录。

## 2. 必须读取

- `CURRENT.md` 和 UAF-11 完成备注。
- `CodeReviewProfileBatchConsumer.java` 及测试。
- `CodeReviewProfileUpdateService.java`、Prompt Builder、Schema、Output Mapper 及测试。
- `CodeReviewProfileConsumerConstants.java`、Queue Contracts。
- `CodeReviewProfileConsumerProperties.java`。
- `AgentConversationApiAutoConfiguration.java` 的后台画像 Bean。
- `LearnerProfileEndToEndIT.java`、`LearnerProfileFailureDegradationIT.java` 的相关路径。
- UAF-11 的 retry 实现参考，但不读取 declared profile 全部代码。

## 3. Background Definition

新增 `CodeReviewProfileUpdateAgentInput`，至少包含：

```text
userId
trusted review fact window
current profile candidates/snapshots
logical batch idempotency key
```

Definition 固定：

- key 使用 `AiBusinessScenario.CODE_REVIEW_PROFILE_UPDATE.code()`。
- 使用现有 BATCH scope 受管理 Prompt 和严格 JSON Schema。
- 工具集合为空，`maxSteps=1`。
- 不读取 queue、Review 或画像 repository，不执行 applyBatch。
- 输出继续通过现有 whitelist mapper 校验 general dimensions 和 tag candidates。

## 4. BACKGROUND 治理与审计

- Invocation mode 为 `BACKGROUND`，不得设置 parent run/step。
- Runtime 检查动态功能、purpose 和模型 route，替代 `completionGateway.isAllowed`。
- 不消费共享用户交互额度，不获取用户锁。
- 使用 `CODE_REVIEW_PROFILE_UPDATE` 自己的模型路由和调用级 Token 记账。
- 每个逻辑批次创建归属该用户的独立审计 task/turn/run。
- 幂等键由 userId 与排序后的受信 reviewIds 生成稳定摘要；不包含 Review 正文、代码或画像正文。

Runtime deny、route 缺失或模型失败映射为现有 `FAILED` 结果。当前队列是最多一次出队，callback 失败不得把已出队消息恢复为 PENDING，也不得抛出队列重试信号。

## 5. Stale retry

- 第一次模型判定后 applyBatch 出现 STALE 时，重新读取全部候选 snapshot。
- 第二次模型判定复用同一 background task/turn，创建新的 run attempt 和 `retry_of_run_id`。
- 两个 attempt 都不扣交互额度，但分别记录真实模型 Token。
- 超过配置重试次数后返回 FAILED，不进行部分画像写入。
- 最终写入的 provider/model 来自成功 attempt 的 Runtime result。

## 6. 实施步骤

1. 新增 Background input、Definition 和输出契约测试。
2. 将 update service 的 `isAllowed/complete` 替换为 Runtime execute。
3. 使用稳定 batch key 和 UAF-04 retry 契约实现 stale retry。
4. 保持 consumer 的 full-batch、ownership、distinct review 和 callback swallow 逻辑不变。
5. 更新条件装配：consumer 开启时 Runtime、Definition 和领域依赖必须完整。
6. 删除后台画像生产路径对 `AiCompletionGateway` 的引用。
7. 更新 PostgreSQL E2E，验证 queue SUCCEEDED、Agent 审计记录和画像 ACTIVE 结果。

## 7. 重点测试

- 非五条、跨 user、重复 review 或事实缺失批次在 Runtime 前失败。
- 合法批次只创建一个 background task/turn；stale 时增加一个 run attempt。
- BACKGROUND 不扣额度、不加用户锁、无 parent 字段。
- 使用后台画像独立模型 route 和 Token 台账。
- disabled/route missing/LLM failure 后 queue message 仍保持既有最多一次语义。
- invalid/out-of-scope output 导致整批零写入并记录现有指标。
- stale retry 重新加载 snapshot，第二次 stale 后停止。
- E2E 中五条正式 Review、queue dispatch、画像更新和后续 recall 仍闭环。

## 8. 非目标

- 不改变 persistent-queue 的最多一次语义。
- 不增加死信、自动回放或定时补偿。
- 不改变满批大小、事实窗口或画像内容策略。

## 9. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-runtime,mentor-application,mentor-api -am \
  -Dtest=CodeReviewProfileUpdateServiceTest,CodeReviewProfileBatchConsumerTest,LearnerProfileEndToEndIT,LearnerProfileFailureDegradationIT test

make backend-test
git diff --check
```

补充执行 Background 治理、审计 task 和 retry attempt 测试。

## 10. 停止条件

BACKGROUND 误扣交互额度、写入 parent 字段、callback 失败触发队列重放、或 stale retry 产生部分写入时，不得开始 UAF-13。

## 11. 上下文交接

记录 Background Definition、batch 幂等摘要、retry 记录、queue 失败语义、E2E 结果和剩余旧入口引用数量。不要携带 Review 事实或画像正文。

## 12. 完成备注

完成时间：2026-07-29

状态：DONE

主要改动：

- 新增 Code Review Profile BACKGROUND Definition 与类型化受信输入，固定 BATCH Prompt、严格 JSON
  Schema、空工具和单 step。
- 批量画像 service 改由 AgentRuntime 执行；幂等键仅由 user 与排序 review ID 摘要组成，stale retry
  复用 task/turn 并写入 retry 来源。
- 自动配置仅在 Runtime、Definition 与领域依赖完整时创建后台 consumer；队列最多一次和失败吞没语义不变。

验证：

- UAF-12 目标 Maven 集通过（持久化 10、Runtime 15、应用 10、API 19 tests）。
- `make backend-test` 通过（276 份 Surefire 报告，无 failures/errors）。
- `git diff --check` 通过。

偏离计划：无。

遗留事项：无。

下一任务：`UAF-13`
