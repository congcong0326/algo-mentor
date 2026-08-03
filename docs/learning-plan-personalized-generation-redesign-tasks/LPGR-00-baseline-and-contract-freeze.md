# LPGR-00：基线、契约与旧符号冻结

> 波次：A
>
> 状态：DONE
>
> 直接依赖：无
>
> 建议首轮文件上限：10

## 1. 目标与完成标准

在修改业务代码前验证学习计划后端、前端、JSONB 持久化和模板链路基线，冻结旧符号范围与最终允许名单。

完成后必须能够回答：当前最小测试是否通过、哪些生产文件消费计划 `goal/profileSummary/difficultyPreference`、哪些命中属于模板允许范围、是否存在需要数据库迁移或额外产品决策的代码事实。

本任务不修改业务行为，不创建迁移，不顺手修复无关失败。

## 2. 必须读取

- `CURRENT.md`、`README.md`、`CONTRACTS.md`。
- `docs/learning-plan-personalized-generation-redesign.md`；这是唯一默认完整读取设计原文的任务。
- `docs/code-index.md` 中学习计划、学习者记忆、能力画像、复习和 Practice Chat 条目。
- `LearningPlanDraftCommand.java`、`LearningPlanDraftPlan.java`、`LearningPlanCreateDraftRequest.java`。
- `LearningPlanDraftPromptBuilder.java`、`LearningPlanDraftJsonSchema.java`、`LearningPlanDraftStructuredOutputMapper.java`。
- `LearningPlanCreateForm.tsx`、`frontend/src/types/api.ts` 的学习计划类型片段。

先执行扫描，按文件列表而不是命中全文记录：

```bash
rg -l 'LearningPlanDraftCommand|profileSummary|buildLearningPlanGoal' \
  backend/mentor-application/src backend/mentor-api/src frontend/src | sort

rg -l '\bgoal\b|difficultyPreference' \
  backend/mentor-application/src backend/mentor-api/src frontend/src \
  --glob '!**/target/**' --glob '!**/dist/**' | sort

rg -n 'command_json|draft_plan_json|plan_json|base_plan_json|proposed_plan_json' \
  backend/mentor-api/src/main/resources/mapper/learningplan \
  backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan
```

## 3. 基线检查

1. 记录 `git status --short` 和 `git diff --stat`，区分用户已有文档改动。
2. 记录旧符号的生产文件数、测试文件数和模板允许命中数，不复制正文。
3. 确认相关数据库列均为 JSONB snapshot 或既有标题/明细列，本重构不需要 Flyway。
4. 确认模板 `goal/difficultyPreference` 与 AI 计划同名但边界不同，形成最终扫描 allowlist。
5. 运行最小后端和前端基线；失败时只记录测试名、错误类别和首个原因。
6. 核对 `CONTRACTS.md` 与代码事实；只修正事实错误，不重新讨论已冻结产品方向。

## 4. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application,mentor-api -am \
  -Dtest='LearningPlan*Test,*LearningPlan*Mapper*Test,*LearningPlan*ControllerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

npm --cache ./.npm --prefix frontend test -- \
  src/learning-plans/options.test.ts \
  src/learning-plans/LearningPlanCreateModal.test.tsx \
  src/learning-plans/LearningPlanCreatePage.test.tsx \
  src/learning-plans/LearningPlanDraftPanel.test.tsx \
  src/services/api.test.ts

git diff --check
```

## 5. 出口与停止条件

- 基线结果、旧符号计数、模板 allowlist 和“无需迁移”结论已写入 `CURRENT.md`。
- `CONTRACTS.md` 不存在与实际字段、服务入口或模板边界冲突的事实错误。
- 若基线无法编译、JSON 并非仅 snapshot、或必须新增未决产品选择，不得开始 `LPGR-01`。

## 6. 上下文交接

只记录测试摘要、旧符号文件数、模板 allowlist、JSONB 结论和已有用户改动路径。不要携带设计原文、Prompt 或 fixture。

## 7. 完成备注

完成时间：2026-08-03 04:33 UTC

状态：DONE

主要改动：

- 冻结旧符号范围与模板 allowlist；确认相关持久化均为既有 JSONB 快照。
- 核对固定契约与现有创建、模板和持久化链路，无需修正事实或新增迁移。

验证：

- `mvn -f backend/pom.xml ... -Dtest='LearningPlan*Test,*LearningPlan*Mapper*Test,*LearningPlan*ControllerTest' ... test`：PASS（133 tests）。
- `npm --cache ./.npm --prefix frontend test -- ...`：PASS（60 tests）。
- `git diff --check`：PASS。

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`LPGR-01`
