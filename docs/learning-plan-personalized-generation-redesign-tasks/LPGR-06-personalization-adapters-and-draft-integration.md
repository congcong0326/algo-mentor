# LPGR-06：聚合数据适配与初次生成注入

> 波次：C
>
> 状态：DONE
>
> 直接依赖：LPGR-05
>
> 建议首轮文件上限：16

## 1. 目标与完成标准

用现有学习者记忆、能力画像、激活计划进度和复习队列实现聚合端口，并把一次性个性化 snapshot 注入初次学习计划 Agent。

完成后 AI 创建在开启时能使用有界学习数据，关闭、空数据或单来源异常时仍生成草案；同一个 run 不重复查询上下文。

## 2. 必须读取

- `CURRENT.md`、`LPGR-05` 完成备注及其稳定类型。
- `CONTRACTS.md` 第 6 至 8、14 节。
- `AbilityProfileService.java`、能力 response/row 和对应 service 测试。
- `LearnerMemoryClaimQueryService.java`。
- `LearningPlanActivationService.java`、`LearningPlanRepository.java`、`PracticeSessionRepository.java`。
- `LearningPlanLoadService.java`、`LearningPlanContractService.java` 及其摘要类型。
- `ReviewQueueService.java`、`ReviewSummary.java`。
- `LearningPlanConfiguration.java`。
- `LearningPlanDraftAgentInput.java`、Definition、Prompt Builder、StreamService 及测试。

先用 `rg` 定位上述 bean 的实际装配位置，只打开命中片段；不要通读所有自动配置。

## 3. API 适配器

在 `mentor-api` 学习计划包中实现 `LearningPlanPersonalizationDataProvider`：

1. claims 使用 `LearnerMemoryClaimQueryService.snapshot(userId)`，不读取 evidence、文档投影或 recall Tool。
2. ability 复用 `AbilityProfileService` 的确定性分数，不复制 SQL 或重新计算模型分数；转换为应用层 record。
3. active plan 通过激活选择、计划 repository、计划进度和既有 pace/rhythm/contract summary 组装，不暴露 plan ID。
4. review load 复用 `ReviewQueueService.summary(userId, "UTC")`，不读取卡片正文。
5. 各依赖使用明确的可用性边界；某个 bean 缺失只使对应 source 返回 unavailable/error，不使 provider bean 整体消失。
6. 不新增跨层对 API DTO、MyBatis row 的应用层依赖。

## 4. 初次生成接入

1. `LearningPlanDraftStreamService.stream` 校验 Brief 后只调用一次 context service。
2. snapshot 放入 `LearningPlanDraftAgentInput`；Runtime retry/replay 使用同一 input，不重新查询。
3. Definition 把 snapshot 交给 Prompt Builder；消息顺序严格为固定 system、可选个性化 system、Brief user。
4. `personalizationEnabled=false` 时不调用 adapter，Prompt 只有两条消息。
5. Agent metadata 只写 enabled、source outcome、条目数、token estimate、trimmed；key 统一放常量类。
6. 个性化装配失败不改变 draft 状态、SSE event name、错误码或确认流程。

## 5. 重点测试

- adapter 对四类现有服务的字段映射和空值处理。
- 无 datasource/无 memory bean/无 review bean 时只降级对应来源。
- enabled、disabled、全空、单来源失败和多来源成功的 Prompt 消息数量与顺序。
- Brief 与画像时间/目标冲突时，最后一条 Brief user message 保持本次值。
- 同一 run context service 只调用一次；新 run 重新调用。
- metadata 不包含 objective、claim、tag label、plan/card/user ID 或异常正文。
- 初次草案 mapper 和现有题库 Tool 白名单不受影响。

## 6. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am \
  -Dtest='LearningPlanPersonalization*Test,LearningPlanDraftPromptBuilderTest,LearningPlanDraftAgentDefinitionTest,LearningPlanDraftStreamServiceTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest='*LearningPlanPersonalization*Test,AbilityProfileServiceTest,LearningPlanControllerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

git diff --check
```

波次 C 出口额外运行：

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am test
```

## 7. 非目标与停止条件

- 不接详细 Review、代码、聊天记录或记忆 Tool。
- 不修改能力画像算法、复习调度或激活计划规则。
- 不在数据库事务中执行 Agent；adapter 只做只读聚合。
- 若 disabled 仍触发查询、同 run 重读、Prompt 顺序错误或部分失败阻断生成，不得开始 `LPGR-07`。

## 8. 上下文交接

记录 adapter 类、四个依赖入口、context 组装调用点、Agent input 字段、metadata key 和测试结果。不要复制画像或 Prompt 内容。

## 9. 完成备注

完成时间：2026-08-03 05:54 UTC

状态：DONE

主要改动：

- 新增 API 聚合 adapter，复用学习者记忆、能力画像、激活计划进度和复习队列摘要。
- 初次草案 run 固定一次个性化 snapshot，并在 Agent input、Prompt 与低敏 metadata 中传递。
- 补充 adapter 映射、缺失来源降级、消息顺序、单次读取和 metadata 脱敏测试。

验证：

- 应用层个性化与草案定向测试：PASS（16 tests）。
- API 个性化、能力画像和学习计划控制器定向测试：PASS（26 tests）。
- 波次 C `mentor-application` 全量测试：PASS（302 tests）。
- `git diff --check`：PASS。

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`LPGR-07`
