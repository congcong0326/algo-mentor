# LPGR-05：个性化上下文模型、裁剪与降级内核

> 波次：C
>
> 状态：DONE
>
> 直接依赖：LPGR-04
>
> 建议首轮文件上限：12

## 1. 目标与完成标准

在 `mentor-application` 中建立与数据库和 Spring 解耦的个性化上下文模型、单一聚合端口、选择规则、1000 token 裁剪和来源级降级逻辑。

本任务不接真实数据源、不修改 Agent Definition。完成后可用纯内存 fake 验证上下文内容、优先级、预算、关闭行为和部分失败。

## 2. 必须读取

- `CURRENT.md`、`LPGR-04` 完成备注。
- `CONTRACTS.md` 第 7、8、14 节。
- `LearnerMemoryClaimContract.java`、`LearnerMemoryClaimRevision.java`、`LearnerMemoryClaimQueryService.java`。
- `LearnerMemoryRecallBootstrapBuilder.java`，只参考完整条目裁剪和仓库 token 估算惯例。
- `LearningPlanLoadService.java` 的 pace/rhythm 返回类型。
- `ReviewSummary.java` 和能力标签 API record，只确认现有聚合字段，不依赖 API 类型。
- 一个学习计划 Prompt Builder 测试，确认 LlmMessage 断言模式。

## 3. 包与类型边界

在 `mentor-application/.../learningplan/personalization` 下创建：

- `LearningPlanPersonalizationContext`。
- `LearningPlanAbilityTagSummary`、`LearningPlanActiveProgressSummary`、`LearningPlanReviewLoadSummary`。
- `LearningPlanPersonalizationDataProvider`。
- `LearningPlanPersonalizationContextService`。
- `LearningPlanPersonalizationPromptRenderer`。
- `LearningPlanPersonalizationSnapshot` 或等价 run-local 结果，包含 context、渲染文本、token estimate、trimmed 和固定来源 outcome。
- 固定 source/outcome 枚举和预算常量，避免跨模块字符串字面量。

聚合端口按四个来源提供独立方法：ACTIVE claims、能力标签、当前激活计划摘要、复习负载摘要。任一方法异常不得阻止其他方法执行。

## 4. 选择与裁剪

严格实现 `CONTRACTS.md` 第 7、8 节：

1. declared/general 按允许 kind、dimension、状态和固定上限过滤、排序。
2. ability 排除零样本，弱项与强项按稳定规则选择且不重复。
3. active plan、review load 保持单一摘要，不包含业务 ID。
4. renderer 使用明确的 `<learning_plan_personalization_context>` 数据边界和“不可信参考数据”说明。
5. 预算按 4 chars/token 估算，只添加完整条目；裁剪顺序固定。
6. 空数据返回空 prompt text，不额外插入“没有画像”的 system message。
7. `enabled=false` 直接返回 disabled snapshot，provider 四个方法调用次数必须为零。
8. 来源异常只记录固定 `ERROR` outcome，不把异常消息或正文放入 snapshot。

## 5. 重点测试

- declared/general dimension 白名单、排序、条数上限。
- weak/strong 排序、同分决胜、零样本排除和不重复。
- 1000 token 边界、整条目裁剪、优先级和 trimmed 标记。
- Prompt 注入字符、伪 system 指令、XML 结束标签只作为数据文本存在，不改变消息角色或边界。
- 全空、单来源、四来源、部分失败、全部失败。
- disabled 时零 provider 调用、空 prompt、固定 outcome。
- snapshot 列表不可变，生成时间稳定，重复渲染结果确定。

## 6. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am \
  -Dtest='LearningPlanPersonalization*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false test

git diff --check
```

## 7. 非目标与停止条件

- 不读取 MyBatis、AbilityProfileService、LearningPlanRepository 或 ReviewQueueService。
- 不修改学习者记忆 claim/evidence 模型，不复用 Practice Chat recall Tool 或 run scope。
- 不增加配置表、数据库表或开放式 Map 数据源。
- 若禁用仍读取来源、某一来源异常会中断整体、或裁剪会截断正文中间，不得开始 `LPGR-06`。

## 8. 上下文交接

记录包入口、provider 四个方法、选择上限、预算常量、snapshot/outcome 类型和测试结果。不要携带 claim 测试文本。

## 9. 完成备注

完成时间：2026-08-03 05:38 UTC

状态：DONE

主要改动：

- 新增应用层个性化上下文、四来源 provider、固定 source/outcome、快照与安全渲染器。
- 按冻结白名单、排序、能力标签去重和 1000-token 完整条目预算组装上下文；来源异常独立降级。
- 个性化关闭时不读取任何 provider 来源，空数据不产生额外 prompt。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest='LearningPlanPersonalization*Test' -Dsurefire.failIfNoSpecifiedTests=false test`：PASS（6 tests）。
- `git diff --check`：PASS。

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`LPGR-06`
