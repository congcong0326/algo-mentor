# LPGR-02：计划快照、模板映射与 JSON 持久化

> 波次：A
>
> 状态：IN_PROGRESS
>
> 直接依赖：LPGR-01
>
> 建议首轮文件上限：16

## 1. 目标与完成标准

把草案、正式计划和提案使用的 `LearningPlanDraftPlan` 切换为 objective、精确难度分布和补充限制，删除计划级 `profileSummary`，并保持模板确定性生成、负载和节奏逻辑可用。

本任务只切计划 snapshot；创建命令和初次模型协议在 `LPGR-03` 切换。允许一个任务内私有旧命令映射作为编译桥接，下一任务必须删除。

## 2. 必须读取

- `CURRENT.md`、`LPGR-01` 完成备注。
- `CONTRACTS.md` 第 4、5、12、13 节。
- `LearningPlanDraftPlan.java`、`LearningPlanLoadService.java`、`LearningPlanService.java`。
- `LearningPlanDraftStructuredOutputMapper.java`、`LearningPlanDraftValidator.java`。
- `LearningPlanTemplateDraftService.java` 与测试。
- `MyBatisLearningPlanRepository.java`、`MyBatisLearningPlanProposalRepository.java` 相关 JSON 映射片段与测试。
- `LearningPlanDraftPlanResponse.java`、`LearningPlanSummaryResponse.java`、`LearningPlanDetailResponse.java`、`LearningPlanResponseMapper.java`。
- `PracticeChatPromptSectionProvider.java` 只定位计划字段消费，实际文案切换可留到 `LPGR-08`，但必须保持编译。

先运行：

```bash
rg -l 'new LearningPlanDraftPlan|\.goal\(\)|profileSummary\(\)|difficultyPreference\(\)' \
  backend/mentor-application/src backend/mentor-api/src | sort
```

## 3. 快照切换

1. 按 `CONTRACTS.md` 第 5 节修改 `LearningPlanDraftPlan`，所有 copy/with 方法完整保留新字段。
2. load、rhythm、append phases、update rhythm、confirm、proposal base/proposed snapshot 均使用新结构。
3. 公共响应改为 `objective`、`difficultyDistribution`、`additionalConstraints`，删除 `profileSummary`。
4. `LearningPlanPublicMetadataMapper` 不暴露内部 `personalizationEnabled`。
5. 旧命令桥接仅允许：`goal -> objective`、枚举经唯一 mapper 转分布、`additionalConstraints=null`、`personalizationEnabled=true`；不得双写旧字段到 snapshot。
6. 模板直接映射 `template.goal(locale) -> objective`，难度使用唯一 mapper，constraints 为 null，metadata 个性化为 false。
7. 模型当前仍可能输出 `profileSummary`；本任务 mapper 必须丢弃它，不能继续落入 snapshot。

## 4. 持久化要求

- `draft_plan_json`、`plan_json`、提案 base/proposed JSON 只写新快照结构。
- repository 测试直接断言 JSON 不含 `goal`、`difficultyPreference`、`profileSummary`。
- 不兼容旧 JSON；测试 fixture 全部更新为新结构。
- 不修改 SQL 表结构、mapper 列或历史 migration。

## 5. 重点测试

- load/rhythm/append/update 操作不丢 objective、distribution、constraints、locale 和 personalization metadata。
- 草案确认后正式计划 snapshot 与草案一致。
- 模板四种难度映射、objective 和 personalization=false。
- repository round-trip 覆盖草案、正式计划、草案修订和扩展提案 snapshot。
- API summary/detail/draft response 不返回旧字段。
- `profileSummary` 即使由旧模型桥接输入提供也不会持久化。

## 6. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am \
  -Dtest='LearningPlanLoadServiceTest,LearningPlanServiceTest,LearningPlanTemplateDraftServiceTest,LearningPlanDraftValidatorTest,*LearningPlan*Proposal*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest='MyBatisLearningPlanRepositoryTest,MyBatisLearningPlanProposalRepositoryTest,LearningPlanControllerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

git diff --check
```

## 7. 非目标与停止条件

- 不切换 AI 创建请求或初次生成 Schema，不接入个性化数据，不修改前端。
- 不重命名模板领域的 `goal/difficultyPreference`。
- 若任何 snapshot 仍写旧字段、copy 路径丢内部 metadata、或模板开始读取画像，不得开始 `LPGR-03`。

## 8. 上下文交接

记录新快照构造入口、内部 metadata key、模板 mapper、JSON round-trip 测试和唯一临时桥接位置。不要携带完整 JSON fixture。

## 9. 完成备注

完成时间：2026-08-03

状态：DONE

主要改动：

- 计划、草案、提案与公开响应快照切换为 objective、难度分布和补充限制。
- 模板快照使用集中难度映射，并持久化 locale 与内部个性化开关。
- JSONB repository、追加阶段、节奏更新、确认保存和 API 响应补充新字段保留与旧字段拒绝断言。

验证：

- mentor-application 定向测试：PASS（58 tests）。
- mentor-api 定向测试：PASS（28 tests）。
- mentor-application 全量测试：PASS（73 reports，无失败）。
- `git diff --check`：PASS。

偏离计划：

- 补齐三处遗留旧测试 fixture，并使缺失模型 phases/metadata 继续进入领域校验而非抛出空指针。

遗留事项：

- `LPGR-03` 删除旧命令桥接并切换初次生成。

下一任务：`LPGR-03`
