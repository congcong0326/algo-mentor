# 学习计划模板 Seed 设计

## 目标

模板 seed 的目标是把外部题单或内部整理路线转换为 algo-mentor 可直接执行的学习计划模板。模板阶段不再只是展示分组或草稿预览，而是从模板生成 draft 和正式计划时的完整路线来源。

当前约定：

- seed 只保存题单元数据、阶段规划、自写目标和来源归因，不复制第三方题解、文章正文、图示、代码或题面。
- 每个模板必须先规划完整阶段，再把题目引用分配到阶段。
- 从模板生成草稿是确定性流程，默认应保留所有本地匹配题；缺失本地题库的题只保留在模板 refs、manifest、metadata 和导入审计中。
- AI 个性化可以在草稿生成后通过现有修订流程调整，但第一版模板 seed 必须稳定、可复现、可审计。

## 模板源数据

人工维护入口：`data/learning-plan-template-sources/`。

目录约定：

- `template_order.json`：声明进入聚合 seed 的模板顺序。
- `templates/<templateId>/template.json`：单个模板的主体字段、用户边界、来源归因和阶段规划。
- `templates/<templateId>/problem_refs.jsonl`：单个模板的题目引用明细。

每个 `template.json` 必须维护固定枚举值 `catalogCategory`，并可按需维护正整数 `recommendedOrder`；非推荐模板省略推荐顺序字段。

源目录按模板拆分，不再把所有模板手工维护在一个 JSONL 中。`template.json` 不维护 `difficultyMix`、本地匹配数、缺失题列表和 `sourceTags` 这类派生统计；`problem_refs.jsonl` 不维护 `metadata.matchedLocalProblem`。生成器会根据当前 `data/seed/problems.jsonl` 重新计算这些字段。

## Seed 产物

聚合产物目录：`data/learning-plan-template-seed/`。

每次生成必须同时产出：

- `learning_plan_templates.jsonl`
- `learning_plan_template_problem_refs.jsonl`
- `learning_plan_template_seed_manifest.json`
- `learning_plan_template_seed_metadata.md`

这四个文件是后端导入使用的运行时 seed，必须由 `tools/learning_plan_template_seed/prepare_template_seed.py` 从模板源目录聚合生成，不应手工编辑。

模板 JSONL 必须包含目标人群、级别、难度分布、前置基础、适用和不适用边界、完成目标、来源说明、整理口径和授权说明。

模板 JSONL 的 `catalogCategory` 只能是 `SYSTEMATIC_LEARNING`、`INTERVIEW_PREP`、`TOPIC_BREAKTHROUGH` 或 `LANGUAGE_AND_ROLE`。`recommendedOrder` 非空时必须为唯一的正整数，整批推荐顺序从 1 连续递增，并且至少存在一个推荐模板。

题目引用 JSONL 必须记录完整路线，并包含 `templateId`、`phaseIndex`、`sortOrder`、`sourceOrder`、slug、来源标题、来源难度、pattern、来源 URL 和本地匹配 metadata。

## 阶段规划规则

- `phaseIndex` 从 1 开始连续递增。
- 每个 ref 的 `phaseIndex` 必须指向同模板中存在的阶段。
- 各阶段 `durationWeeks` 之和必须等于模板 `defaultDurationWeeks`。
- 阶段标题、focus、objectives、acceptanceCriteria 和 reviewAdvice 应能直接作为用户草稿展示内容。
- 长路线应拆成足够表达完整计划的阶段，不应为了适配旧草稿预览把 75/150 题路线压缩成少数阶段后再截断题目。
- 同一模板内默认不重复题目；如果为了复盘刻意跨阶段重复，必须在 ref metadata 中说明原因。

## 后端导入

相关表：

- `learning_plan_template`
- `learning_plan_template_phase`
- `learning_plan_template_problem_ref`
- `learning_plan_template_import_run`

导入配置：

- `algo-mentor.learning-plan-template.seed.enabled`
- `algo-mentor.learning-plan-template.seed.path`

`make db-seed` 会在题库和公司信号导入后启用模板 seed 导入。导入过程按 `template_id` upsert 模板，并替换其阶段和题目引用明细。后端会按当前本地题库重新计算 matched/missing；seed 中的 `metadata.matchedLocalProblem` 只作为生成时审计。

## API 和草稿生成

接口：

- `GET /api/learning-plan-templates`
- `GET /api/learning-plan-templates/{templateId}`
- `POST /api/learning-plans/drafts/from-template`

从模板生成草稿不调用 AI governance。服务会直接保存 `GENERATED` 状态的 `LearningPlanDraft`，再复用现有 `POST /api/learning-plans/drafts/{draftId}/confirm` 确认保存流程。

模板草稿生成约束：

- 阶段周数总和等于总周期。
- 草稿阶段应来自模板阶段，默认保留模板中所有本地匹配的题目引用。
- 缺失本地题库的 refs 不进入 `LearningPlanProblemDraft`，但必须保留在模板明细和草稿 metadata 中用于审计。
- 旧的“每阶段最多推荐 5 道题”只适用于 AI 生成草稿或明确声明的短预览模板，不适用于完整路线模板。

## 当前 Seed 状态

截至 2026-07-28，聚合 seed 已包含 35 个模板、1738 条题目引用，其中 1699 条本地匹配、39 条本地缺失。完整路线已覆盖 NeetCode、TIH、代码随想录、labuladong、LeetCode 官方面试计划、中文经典题单、算法专项以及 SQL、JavaScript、Pandas 练习计划。

本轮新增 6 个模板、336 条 refs，其中 331 条匹配、5 条缺失：

- `carl_algorithm_roadmap_full`
- `labuladong_algo_thinking`
- `leetcode_sql_50`
- `leetcode_javascript_30_days`
- `leetcode_pandas_introduction`
- `leetcode_pandas_30_days`

高级 SQL 50 和 Premium Algo 100 的官方接口当前不再返回可用计划数据，因此未生成模板。Coding Interview University、CS-Notes 仍需要非题目任务和外链学习材料模型，不进入当前 phase/problem-ref seed。

## 验证

最小验证包括：

- Python seed 生成测试：输出四个必要文件，缺少必填模板元数据、阶段周数不匹配、refs 指向不存在阶段时失败。
- 后端导入测试：区分 matched/missing，写入 import run，缺少 metadata markdown 时失败。
- 应用层草稿测试：从模板生成草稿后，阶段周数合计等于总周期，所有本地匹配 refs 进入 `LearningPlanProblemDraft`，缺失 refs 只保留在 metadata 中。
- API 测试：模板查询返回阶段和题目引用；从模板生成草稿不触发 AI governance。

## Skill 沉淀

项目内 `.codex/skills/learning-plan-template-integrator/SKILL.md` 是后续接入或重建模板的执行入口。接入 TIH、Halfrost、代码随想录、NeetCode 等资料源时，需要先读该 Skill，再评估资料源、规划完整阶段、生成 JSONL/manifest/metadata、处理 slug 匹配和授权备注，并运行 seed、导入和草稿生成验证。
