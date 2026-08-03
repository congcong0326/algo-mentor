# LPGR-08：扩展、模板与下游 objective 收口

> 波次：D
>
> 状态：DONE
>
> 直接依赖：LPGR-07
>
> 建议首轮文件上限：16

## 1. 目标与完成标准

把计划扩展接入 run-local 个性化上下文，并收口模板、Practice Chat、列表/详情和计划复制路径对 objective、constraints、难度分布及内部个性化开关的消费。

完成后扩展仍只能追加阶段，模板仍确定性生成，所有非模板下游不再读取计划 `goal`。

## 2. 必须读取

- `CURRENT.md`、`LPGR-07` 完成备注。
- `CONTRACTS.md` 第 5、8、10、12、15 节。
- `LearningPlanExtensionAgentInput.java`、Definition、JsonSchema、StructuredOutputMapper、ProposalStreamService 及测试。
- `LearningPlanProposalPromptBuilder.java` 的 extension/revision 方法。
- `LearningPlanExtensionApplyService.java`、`LearningPlanExtensionValidator.java`。
- `MyBatisLearningPlanRepository.appendPhases` 和 proposal repository extension 映射。
- `LearningPlanTemplateDraftService.java` 与模板相关测试，只确认固定边界。
- `PracticeChatPromptSectionProvider.java` 计划片段及测试。
- 后端/前端生产源码中 remaining `goal/profileSummary/difficultyPreference` 的 `rg` 命中列表，只打开非模板文件。

## 3. 扩展接入

1. 从正式计划 metadata 读取 `personalizationEnabled`；false 时 provider 零调用。
2. 创建 extension revision 的短事务结束后，再组装 context snapshot 和 Agent invocation。
3. extension 与 extension revision 每个 run 各只读取一次最新上下文。
4. Prompt 顺序为固定 system、可选个性化 system、最后一条 user instruction + 当前计划 + 进度。
5. 模型输出仍只包含 extension summary/new phases/受限 metadata，不返回或修改 Brief。
6. apply 只追加 reindex 后的新阶段，保留原计划 objective、constraints、distribution、locale、personalization metadata、负载和节奏。

## 4. 下游收口

- Practice Chat 计划块将 `goal` 标签和值改为 `objective`，其他上下文保持不变。
- 列表、详情、负载、节奏、Living Contract、Today Pack、提案 snapshot 和复制构造器全部使用新字段。
- 模板领域/API/seed 允许保留旧命名；模板到计划的 mapper 必须是唯一跨边界转换点。
- 模板创建不得调用 context service，内部 personalization metadata 为 false。
- 前端扩展面板、详情和 fixture 若仍有旧字段，在本任务同步清理并运行相关测试。

## 5. 重点测试

- AI 计划 personalization=true 的扩展注入上下文；false 和模板计划零读取。
- extension revision 新 run 重读最新聚合，但同 run 不重读。
- 部分来源失败仍产出扩展；上下文不能改变只追加校验。
- apply 后旧阶段和全部 Brief 字段不变，新阶段索引连续。
- Practice Chat Prompt 使用 objective，不出现 profileSummary 或计划 goal。
- 模板生成 objective、固定分布、constraints=null、personalization=false，并保持既有题目/负载。
- 前端扩展/详情对新 API fixture 可渲染。

## 6. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am \
  -Dtest='LearningPlanExtension*Test,LearningPlanProposalPromptBuilderTest,LearningPlanTemplateDraftServiceTest,PracticeChatPromptSectionProviderTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest='MyBatisLearningPlanRepositoryTest,MyBatisLearningPlanProposalRepositoryTest,LearningPlanControllerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

npm --cache ./.npm --prefix frontend test -- \
  src/learning-plans/LearningPlanExtensionPanel.test.tsx \
  src/learning-plans/LearningPlanDetail.test.tsx \
  src/learning-plans/PracticeChatWorkbench.test.tsx

npm --cache ./.npm --prefix frontend run build

git diff --check
```

## 7. 非目标与停止条件

- 不让扩展修改 objective、周期、原阶段或 Brief；这类需求仍属于新计划或草案修订。
- 不重命名模板 source/seed/API 的 goal。
- 不增加扩展页个性化开关。
- 若模板读取画像、扩展覆盖原字段、或 Practice Chat 仍消费计划 goal，不得进入最终波次。

## 8. 上下文交接

记录扩展 context 调用点、metadata 开关读取、apply 保留字段、Practice Chat 新标签、模板 allowlist 和测试结果。不要复制计划 JSON。

## 9. 完成备注

完成时间：2026-08-03 06:28 UTC

状态：DONE

主要改动：

- 扩展在短事务后按正式计划 metadata 固定一次个性化 snapshot，关闭和模板计划零读取；Definition 注入可选 system message 且 metadata 仅保留低敏摘要。
- apply 只追加重编号阶段并保留 Brief、locale、个性化开关、负载和节奏 metadata；Practice Chat 使用 `objective` 标签。
- 模板继续确定性映射 `goal` 和难度枚举，固定 `additionalConstraints=null`、`personalizationEnabled=false`，不依赖 context service。

验证：

- 应用层扩展、模板和 Practice Chat 定向测试：PASS（61 tests）。
- API repository/controller 定向测试：PASS（31 tests）。
- 前端扩展、详情与 Practice Chat 测试：PASS（3 files，48 tests）；前端生产构建：PASS；`git diff --check`：PASS。

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`LPGR-09`
