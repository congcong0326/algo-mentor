# AMR-06：Code Review Claim 更新 Agent

> 波次：C
>
> 状态：DONE
>
> 直接依赖：AMR-03、AMR-05
>
> 建议首轮文件上限：14

## 1. 目标与完成标准

把 Code Review Profile 后台 Agent 从单 step、无工具、整段 `NO_CHANGE / REPLACE` 改为最多 4 step、三项只读工具和严格原子 operations，并通过统一 apply 内核完成版本与 evidence 写入。

本任务完成后，更新服务可以被单独调用并生成新 claim；队列仍使用旧 topic，生产提交链路在 `AMR-07` 才切换。

## 2. 必须读取

- `CURRENT.md`、`AMR-03` 与 `AMR-05` 完成备注。
- `CONTRACTS.md` 第 4、6、7、12 节。
- `CodeReviewProfileUpdateAgentDefinition.java`、Input、Prompt Builder、JSON Schema、Output Mapper 及测试。
- `CodeReviewProfileUpdateService.java` 及测试。
- `CodeReviewProfileConsumerConstants.java`。
- 新 `LearnerMemoryAtomicApplyService`、update run service、review query/tools 和 run scope registry。
- `DefaultAgentRuntimeTest` 中 `AgentRunResource` 释放测试，只读取相关测试方法。

## 3. 初始输入

更新服务构造最多 10 道不同题目的横向窗口，并读取：

- 三个允许的 general dimension 下全部 ACTIVE claim。
- 横向窗口受影响 tag 下全部 ACTIVE `TAG_MASTERY` claim。
- 用户级 snapshot token、ACTIVE 总数和容量状态。
- 固定 5 条触发 Review ID，供 update run 幂等和关联表记录。

Prompt 不包含无关 scope、完整历史代码、Review Markdown 或用户 declared claim。

## 4. Definition 与工具

- Definition key 继续使用 `CODE_REVIEW_PROFILE_UPDATE` 业务场景。
- `maxSteps=4`，allowed tools 仅为 AMR-05 的三项工具。
- Definition 基于受信 input 打开 update scope，把不透明 `scopeRef` 放入 metadata，并通过 `AgentRunResource` 释放。
- replay 不重新执行工具或 apply；retry 创建新 Agent run 并指向前一 run。
- metadata 只记录窗口题目数、初始 claim 数、容量状态、工具计数和 Prompt/schema 版本。

## 5. 结构化输出

根对象只允许 `operations`，数组最多 12 项。每项严格包含 action 对应的合法字段：

- `ADD`：scope、claim text、pattern、reason、review evidence。
- `CONFIRM`：当前 revision ID、pattern、reason、完整 review evidence；不接受 claim text。
- `REVISE`：当前 revision ID、新文本、pattern、reason、完整 review evidence。
- `RETIRE`：当前 revision ID、pattern、reason、导致退役的 evidence；不接受新文本。

模型输出的 evidence 只有 review ID 和 role。grade、顺序、归属、tag、pattern 最小结构和 scope 全部由服务端重算。

## 6. 更新编排

1. 以 `userId + sorted(batchReviewIds)` 创建/取得业务 update run，并写 5 条触发 Review 顺序。
2. 构造横向窗口和相关 ACTIVE snapshot。
3. 事务外执行 Agent，映射严格 operations。
4. 调用统一 apply；STALE 时重新读取窗口相关 claim，创建 retry Agent run，最多一次。
5. 成功有 operation 为 `SUCCEEDED`，空 operation 为 `NO_CHANGE`；invalid output、AI、工具或 apply 失败为 `FAILED`。
6. 更新 run 的最终 agent run、operation/tool 数和低敏失败码。

## 7. Prompt 语义

- general observation 必须具备跨题或纵向 evidence；`SINGLE_REVIEW` 只允许 tag claim。
- 同题修正优先形成成长 claim，不把已经解决的问题继续写成当前稳定弱点。
- 同题多版不能声称跨题复现；一题五版对 tag breadth 仍只算一题。
- 达到软上限时优先 CONFIRM/REVISE/RETIRE 和去重，不额外启动模型任务。
- 证据不足时返回空 operations。

## 8. 重点测试

- Definition 只暴露三项工具、4 step，scope 在所有终态释放。
- Schema unknown field、非法 action/字段组合、超过 12 项、重复 revision、非法 tag 整批拒绝。
- 三个 general dimension 与受影响 tag 白名单正确；禁止 declared 和 independence。
- 六个业务语义样例全部固定为 eval/单测 fixture。
- stale retry 重新读取 claim，第二次 stale 零写入；一个业务 update run 关联两次 Agent attempt。
- 最终 evidence、grade 和顺序来自服务端，不信任模型。

## 9. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am \
  -Dtest='CodeReviewProfile*Test,*CodeReviewMemory*Test,*LearnerMemory*Semantic*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test='*CodeReviewMemoryUpdateIT' verify

git diff --check
```

## 10. 非目标与停止条件

- 不切换 queue topic，不改 Review 提交事务，不改 Practice Chat recall。
- 若模型能修改不允许 scope、工具 scope 泄漏或 apply 产生部分写入，不得开始 `AMR-07`。

## 11. 上下文交接

记录 Prompt/schema 版本、allowed tools、maxSteps、update service 入口、stale/retry 和六类语义 fixture 结果。不要带入 Prompt 全文。

## 12. 完成备注

完成时间：2026-07-30

状态：DONE

主要改动：

- `CODE_REVIEW_PROFILE_UPDATE` Definition 改为最多 4 step，只暴露三项 AMR-05 只读工具；从受信输入创建 `LearnerMemoryRunScopeRegistry` lease，将不透明 scope ref 写入 metadata，并通过 `AgentRunResource` 覆盖全部 runtime 终态释放。
- Code Review 输入、Prompt、JSON Schema 和输出 Mapper 切换为 v2 严格 `operations`。服务端校验 action/字段组合、允许 scope、revision、evidence 与容量，不信任模型提供的 grade、顺序或归属。
- `CodeReviewProfileUpdateService` 固定校验五条不同 Review，以 user 和排序后的 Review ID 创建/复用 `CODE_REVIEW_BATCH` update run 及触发关联；构造最多 10 题窗口、相关 claim snapshot/容量和 review evidence context，并在事务外编排 Agent 与统一原子 apply。首次 `STALE` 会完整重读并重试一次，第二次 stale 或任何失败均按低敏错误码结束 run。
- 自动配置接入 Review 历史、claim/evidence/update-run repository、原子 apply 和 lifecycle service。`CodeReviewMemoryUpdateIT` 覆盖五条正式 Review 经原有 topic 消费后写入两条 claim/evidence，并断言 update run 绑定真实 `agent_run` 审计记录。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest='CodeReviewProfile*Test,*CodeReviewMemory*Test,*LearnerMemory*Semantic*Test' test`（7 项通过）
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=NoMatchingUnitTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='*CodeReviewMemoryUpdateIT' verify`（1 项 PostgreSQL 集成测试通过）
- `git diff --check`（通过）

偏离计划：

- 无。仍保留既有 queue topic 和 Review 提交链路，切换留给 `AMR-07`。

遗留事项：

- `AMR-07` 负责切换正式 Review 提交与消费到 v2 topic，并清理遗留 v1 queue 数据。

下一任务：`AMR-07`
