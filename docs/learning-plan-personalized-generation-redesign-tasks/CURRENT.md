# 当前执行上下文

更新时间：2026-08-03

当前状态：全部完成

当前任务：无

当前任务文件：无

下一任务：无

## 当前目标

LPGR-00 至 LPGR-10 已全部完成，学习计划个性化生成重构进入最终交付。

## 已知仓库事实

- 设计文档：`docs/learning-plan-personalized-generation-redesign.md`。
- LPGR-00 基线通过：后端定向测试 133 个、前端定向测试 60 个，`git diff --check` 通过。
- 旧符号命中 31 个生产文件、9 个测试文件；`goal/difficultyPreference` 命中 48 个生产文件、13 个测试文件。
- JSONB 快照列为 `command_json`、`draft_plan_json`、`plan_json`、`base_plan_json`、`proposed_plan_json`；无需新增迁移。
- 模板最终 allowlist：`LearningPlanTemplate`、`LearningPlanTemplateDraftService`、`LearningPlanTemplateRow`、`LearningPlanTemplateDetailResponse`、`LearningPlanTemplateSummaryResponse`、`LearningPlanTemplateResponseMapper`、`MyBatisLearningPlanTemplateRepository`、`LearningPlanTemplateSeedImportService`、`LearningPlanTemplateSeedRecord`、`V24__learning_plan_template_schema.sql`、`LearningPlanTemplateMapper.xml`。
- 既有文档改动为 `docs/code-index.md`、设计原文和本任务目录；未回退。
- LPGR-01 新增 `LearningPlanBrief`、`LearningPlanDifficultyDistribution`、`LearningPlanDifficultyDistributions` 和 `LearningPlanObjectiveDefaults`；`LearningPlanDraftValidator` 复用为 Brief 字段校验入口。
- LPGR-02 已完成：生产快照、模板、响应投影、追加阶段、节奏更新和 Practice Chat 已使用 `objective`、`difficultyDistribution`、`additionalConstraints`。
- JSONB 草案/正式计划/提案快照只写新字段；内部 metadata 保存 `contentLocale` 与 `personalizationEnabled`，公共响应不暴露后者。
- LPGR-03 已完成：创建 API、command JSON、初次 Prompt/Schema 与 SSE 输入均使用 `LearningPlanBrief`；`LearningPlanDraftCommand` 和旧桥接已删除。
- 初次生成使用 `learning_plan_generated_content` / `v2`；草案修订使用严格的 `learning_plan_draft_revision` / `v2`，根对象为 `resolvedBrief + generatedContent`。
- LPGR-04 已完成：AI 创建表单、前端类型、草案预览与列表消费者已改用 `objective`、精确难度分布和 `additionalConstraints`；默认开启个性化开关，旧表单摘要链已删除。
- LPGR-05 已完成：`learningplan.personalization` 提供四来源端口、选择裁剪服务、不可变快照与安全渲染；关闭时零读取，单来源失败独立降级。
- LPGR-06 已完成：API adapter 聚合四个既有来源；初次草案在校验后每 run 固定一次 snapshot，并将其传入 Prompt 和低敏 Agent metadata。
- LPGR-07 已完成：修订 run 在短事务后固定一次 context snapshot，严格解析 resolved Brief 与生成内容，并原子写回 Brief、草案计划和 READY proposal。
- LPGR-08 已完成：扩展 run 在短事务后按计划 metadata 固定一次个性化 snapshot；模板固定关闭；Practice Chat 使用 objective；扩展 apply 保留 Brief、locale、开关和节奏 metadata。
- LPGR-09 已完成：`LearningPlanPersonalizationMetrics` 使用固定低基数标签，安全回归保持 Prompt/工具/schema 边界；PostgreSQL IT 已验证四类 JSONB 快照无旧字段。
- LPGR-10 已完成：删除旧字段专用创建请求 allowlist、fixture 和前端摘要文案；模板边界保留允许的旧命名，代码索引已更新。

## 恢复入口

1. 完整读取 `README.md` 和 `CONTRACTS.md`。
2. 读取 `LPGR-08-extension-template-and-downstream-consumers.md` 的完成备注。
3. 若有后续改动，先读取相关功能文档和代码索引，再创建新的独立任务。

## 最近验证

- `mvn -f backend/pom.xml ... -Dtest='LearningPlan*Test,*LearningPlan*Mapper*Test,*LearningPlan*ControllerTest' ... test`：PASS。
- `npm --cache ./.npm --prefix frontend test -- ...`：PASS。
- `git diff --check`：PASS。
- `mvn -f backend/pom.xml ... -Dtest='LearningPlanBrief*Test,LearningPlanDifficulty*Test,LearningPlanDraftValidatorTest' ... test`：PASS（10 tests）。
- `mvn -f backend/pom.xml ... -pl mentor-application -am -DskipTests compile`：PASS。
- `mvn -f backend/pom.xml ... -pl mentor-api -am -DskipTests compile`：PASS。
- LPGR-02 应用层定向测试：PASS（58 tests）。
- LPGR-02 API 定向测试：PASS（28 tests）。
- 波次 A `mentor-application` 全量测试：PASS（73 reports，无失败）。
- LPGR-03 应用层定向测试：PASS（53 tests）。
- LPGR-03 API 定向测试：PASS（27 tests）。
- `rg -n 'LearningPlanDraftCommand' backend/mentor-application/src backend/mentor-api/src`：零命中。
- `git diff --check`：PASS。
- LPGR-04 定向前端测试：PASS（8 files，76 tests）。
- LPGR-04 补充前端测试：PASS（3 files，114 tests）。
- `npm --cache ./.npm --prefix frontend run build`：PASS。
- `rg -n 'buildLearningPlanGoal|profileSummary' frontend/src`：零命中。
- LPGR-05 个性化内核定向测试：PASS（6 tests）。
- LPGR-06 应用层个性化与草案定向测试：PASS（16 tests）。
- LPGR-06 API 定向测试：PASS（26 tests）。
- 波次 C `mentor-application` 全量测试：PASS（302 tests）。
- 前端学习计划测试：PASS（11 files，93 tests）。
- 前端生产构建：PASS。
- `mapRevision` 与临时完整计划修订桥接活动源码检索：零命中。
- `git diff --check`：PASS。
- LPGR-08 应用层定向测试：PASS（61 tests）。
- LPGR-08 API 定向测试：PASS（31 tests）。
- LPGR-08 前端学习计划测试：PASS（3 files，48 tests）；前端生产构建：PASS。
- 非模板旧字段扫描仅保留模板边界和遗留请求字段拒绝清单。
- LPGR-09 应用层门禁：PASS（80 tests）。
- LPGR-09 API 门禁：PASS（含 `LearningPlanPersonalizedGenerationIT`，1 个 PostgreSQL IT）。
- LPGR-09 前端指定测试：PASS（5 files，90 tests）；前端生产构建和 `git diff --check`：PASS。
- LPGR-10 `make backend-test`：PASS（1244 tests）；`make backend-build`：PASS。
- LPGR-10 `make frontend-test`、`make frontend-build` 与 `LearningPlanPersonalizedGenerationIT`：PASS；旧桥接/画像符号扫描零命中，模板 allowlist 审核通过。

## 交接限制

- 不在本文件粘贴设计原文、Prompt、JSON fixture、测试日志或用户学习数据。
- 旧创建命令链已删除；模板 allowlist 中的 `goal/difficultyPreference` 不在 LPGR-04 改名。
