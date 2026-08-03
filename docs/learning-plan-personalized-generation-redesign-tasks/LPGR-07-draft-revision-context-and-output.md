# LPGR-07：草案修订 Brief 解析与上下文注入

> 波次：D
>
> 状态：DONE
>
> 直接依赖：LPGR-06
>
> 建议首轮文件上限：16

## 1. 目标与完成标准

把聊天修订从“模型输出完整计划并由旧 Brief 覆盖”改为严格的 `resolvedBrief + generatedContent`，并在每个修订 run 重新组装一次最新个性化上下文。

完成后修订可以更新允许的 Brief 字段，未提及字段保持不变，Brief 与计划在同一事务中一起更新；临时完整计划修订 Schema 被删除。

## 2. 必须读取

- `CURRENT.md`、`LPGR-06` 完成备注。
- `CONTRACTS.md` 第 6、8、9、13 节。
- `LearningPlanDraftRevisionAgentInput.java`、Definition、StreamService 及测试。
- `LearningPlanDraftRevision.java`、proposal repository 的 base/proposed plan JSON 映射片段。
- `LearningPlanDraft.java` 的 brief/plan 更新方法。
- 初次生成的 generated content Schema、mapper 和 Brief validator。
- 个性化 context service、snapshot 和 Prompt renderer 稳定入口。
- `LearningPlanProposalPromptBuilder.java` 的草案修订方法；若 Definition 已自行组装消息，只保留一个权威入口。

## 3. 修订输出契约

新增修订专用严格 Schema 与 mapper：

```text
root
  resolvedBrief
  generatedContent
```

- 根对象和两个子对象均 `additionalProperties=false`。
- generated content 复用初次生成的字段定义，不复制一套容易漂移的 phase/problem Schema。
- resolved Brief 字段完整，但 `contentLocale`、`personalizationEnabled` 最终必须保持当前 Brief 值。
- mapper 先解析、规范化和校验 resolved Brief，再规范化题库事实并组装完整计划。
- 删除 `LPGR-03` 保留的完整计划修订 Schema 和旧 mapper 分支。

## 4. 编排与事务边界

1. 创建 proposal revision 的短事务只锁定当前 draft、校验状态并保存 GENERATING revision。
2. 事务提交后再组装个性化上下文和创建 Agent invocation，禁止在 proposal 事务中执行多来源读取或模型调用。
3. Agent input 固定当前 Brief、当前完整计划、instruction、context snapshot 和 idempotency key。
4. 完成时重新锁 draft/group，执行现有 stale/superseded 校验。
5. 在同一事务中保存 READY proposal，并把 resolved Brief、组装后的计划、消息和状态一起写回 draft。
6. 任一校验、持久化或 stale 失败不得只更新 Brief 或只更新计划。

## 5. Prompt 语义

- 消息顺序：固定 system、可选个性化 system、当前 Brief/计划上下文、最后一条用户修订 instruction。
- 明确要求未被 instruction 修改的字段原样保留。
- 明确结构化字段优先于 additional constraints 和个性化参考。
- 支持至少：改周期、改每周时间、改 objective、改难度比例、增减专题、修改 constraints。
- 不允许聊天切换 locale 或 personalization enabled。

## 6. 重点测试

- 六类字段修订和多字段组合修订。
- 未提及字段保持；模型擅自改 locale/personalization 被服务端恢复。
- resolved objective 为空走同一默认规则，非法分布、周期、时间、constraints 整体失败。
- Brief 更新后 `command_json` 与新 draft plan 一致，proposal snapshot 可重建规划字段。
- stale、并发新 revision、模型错误和 repository 失败均无部分更新。
- context 在事务外组装、每 run 一次、disabled 零读取、部分失败可继续。
- 临时完整计划修订 Schema 和旧 mapper 分支零引用。

## 7. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am \
  -Dtest='LearningPlanDraftRevision*Test,LearningPlanPersonalization*Test,LearningPlanGenerated*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest='MyBatisLearningPlanProposalRepositoryTest,MyBatisLearningPlanRepositoryTest,LearningPlanControllerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

git diff --check
```

## 8. 非目标与停止条件

- 不改变 proposal group、SSE event 或前端聊天交互。
- 不让模型修改 plan ID、用户、状态、locale、个性化开关或 metadata。
- 若 Brief/plan 不能原子更新、上下文查询发生在提案事务内、或旧完整计划 Schema 仍被使用，不得开始 `LPGR-08`。

## 9. 上下文交接

记录修订 Schema/version、mapper 入口、事务外 context 调用点、原子更新方法、stale 结果和测试覆盖。不要携带 instruction 或模型输出 fixture。

## 10. 完成备注

完成时间：2026-08-03 06:13 UTC

状态：DONE

主要改动：

- 修订输出切换为严格的 `resolvedBrief + generatedContent`，复用初次生成内容 Schema 与题库事实规范化。
- 修订 run 在短事务结束后固定一次个性化 snapshot，并将 Brief、计划和 snapshot 固定传入 Agent。
- READY 事务原子保存 proposal、resolved Brief 与重组后的草案计划；修订 Prompt 使用受管理 revision 定义。
- 删除旧完整计划修订 Prompt 组装入口和 mapper 分支，补充 Brief/计划及 JSON 快照一致性测试。

验证：

- 应用层修订、个性化、生成内容及 Prompt registry 定向测试：PASS（25 tests）。
- API repository/controller 定向测试：PASS（31 tests）。
- 前端学习计划测试：PASS（11 files，93 tests）；前端生产构建：PASS。
- 旧桥接引用检索与 `git diff --check`：PASS。

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`LPGR-08`
