# 学习计划业务数据模型精简研发设计

## 1. 文档信息

- 设计日期：2026-08-06
- 状态：待实施
- 适用范围：学习计划创建、草案修订、计划扩展、模板生成草案、模板 Seed 导入、学习计划查询与执行投影
- 不适用范围：AI 模型视图、编解码器、read/patch/write Tool、Plan Compiler 或 Agent 流程重构

本文只处理现有业务数据模型的字段精简。目标是让领域模型、API、JSONB 快照、模板关系表和 Seed Schema 使用同一套最小业务契约，不把之前讨论的 AI 模型工作副本设计混入本次改造。

## 2. 结论

第一批改造采用破坏性删除，不继续保留废弃字段的写入、返回或空值占位。

本批删除：

- 阶段字段：`objectives`、`acceptanceCriteria`、`reviewAdvice`、`recommendedTags`。
- 模板阶段英文对应字段：`objectivesEn`、`acceptanceCriteriaEn`、`reviewAdviceEn`。
- 计划与模板字段：`interviewOriented`。
- 模板业务字段：`difficultyMix`。
- 废弃 metadata：`problemRecommendationIncomplete`、`loadRisk`、`weeklyBuckets`、`nextTrainingPackage`。
- 模板草案 metadata 中重复的来源、统计和题目引用副本。
- `LearningPlanWeeklyBucket.reviewAdvice`，因为周桶计算不使用该字段。

本批保留：

- `LearningPlanTrainingPackage.reviewTask`，但改为服务端确定性派生。
- 页面上的阶段标签展示，但改为从阶段题目的可信 `tags` 派生。
- 模板主数据中的来源、许可证和整理说明。
- `difficultyDistribution`，其替换为 `difficultyPreference` 的语义迁移另立项目处理。
- 完整题目快照、阶段顺序和题目顺序。

数据库处理分为两类：

- 普通学习计划没有对应实体列，不做表结构 DDL，但要清理所有学习计划 JSONB 快照。
- 模板表存在对应实体列，必须新增 Flyway DDL 删除列、约束并重建英文完整性校验函数。

Seed Schema 从 v2 升级为 v3。新 Seed 不再包含废弃字段，同时 Seed Record 显式忽略未知字段，使历史 v2 Seed 仍可读取。

## 3. 当前问题

### 3.1 同一数据在多层重复存在

当前阶段文案同时出现在：

- `LearningPlanPhaseDraft`。
- AI structured output Schema。
- 模板领域模型和数据库列。
- 模板源文件和中英文翻译文件。
- 聚合 Seed。
- API DTO 和前端 TypeScript 类型。

这些字段一旦存在，所有创建、修订、扩展、模板和国际化链路都必须继续维护，即使页面和业务规则没有真正使用它们。

### 3.2 派生信息被持久化或重复表达

- `loadRisk` 与 `loadSummary.intensity` 表达同一负载结论。
- `interviewOriented` 与 `intent`、模板 `catalogCategory` 存在重复且不稳定的语义来源。
- `recommendedTags` 可以从阶段题目的可信 `tags` 得到，却由 AI 或模板单独维护。
- 模板来源和完整 problem refs 已存在于模板主数据及模板引用表，又被整体复制进每个草案 metadata。
- `difficultyMix` 可由模板 problem refs 的真实难度统计得到，不应作为模板业务字段重复保存。

### 3.3 无效字段提高误判概率

字段名具有业务含义，但没有有效消费者时，研发人员和模型都会误以为它们参与验收、复盘或推荐逻辑。典型字段包括：

- `objectives`。
- `acceptanceCriteria`。
- `problemRecommendationIncomplete`。
- metadata 兼容键 `weeklyBuckets`、`nextTrainingPackage`。

删除这些字段比继续保留空数组、默认文案或兼容读取更容易形成可靠契约。

## 4. 设计目标

1. 计划阶段只保存界面展示和执行真正需要的语义字段。
2. 同一业务判断只保留一个权威来源。
3. 可从计划内容稳定计算的展示字段不进入持久化模型。
4. 模板来源合规信息保留在模板主数据和 Seed manifest，不复制到用户计划。
5. 现有计划、草案和提案 JSONB 在迁移后统一为新结构。
6. 新代码能够容忍迁移前 JSON 和 v2 Seed 中的未知字段，避免反序列化事故。
7. 删除字段后不降低阶段展示、下一训练包、模板导入和计划确认的现有能力。

## 5. 非目标

本次不处理以下事项：

- 不设计 AI 专用精简模型、编码器或解码器。
- 不把题目业务快照压缩为 `slug + reason`。业务快照仍需要题名、难度、标签和顺序用于展示、负载计算与历史稳定性。
- 不重构学习计划 Agent Tool。
- 不修改阶段数量、每阶段题目上限和负载算法。
- 不删除 `topicPreferences`。
- 不删除模板 problem-ref 的 `sortOrder`、`sourceOrder`、`pattern`、来源链接或匹配状态。
- 不删除模板主数据中的 `sourceName`、`sourceUrl`、`sourceCommit`、`sourceDataPath`、`sourceDescription`、`curationNotes`、`licenseNotice`。
- 不在本批替换 `difficultyDistribution`。

## 6. 设计原则

### 6.1 事实、配置和投影分离

- 事实：计划标题、阶段、题目、题目理由、模板来源。
- 配置：周期、每周投入、难度偏好、训练天数、覆盖策略。
- 投影：阶段展示标签、负载摘要、周桶、下一训练包。

事实和用户配置可以持久化，投影默认由服务端或前端从事实和配置计算。

### 6.2 一个语义只保留一个来源

- 面试导向使用 `intent` 判断，模板目录展示使用 `catalogCategory`，不再额外保存 `interviewOriented`。
- 负载风险使用 `loadSummary.intensity`，不再保存 `loadRisk`。
- 模板难度默认使用 `difficultyPreference`，精确统计保留在 manifest，不进入模板领域对象。

### 6.3 删除必须贯穿全链路

一个字段只有同时从以下位置删除，才算真正完成精简：

- Java 领域模型。
- 请求和响应 DTO。
- structured output Schema 和当前 Prompt 约束。
- Repository、Mapper 和 SQL。
- PostgreSQL 列或 JSONB 历史数据。
- 模板源、翻译、生成器和聚合 Seed。
- 前端类型、展示逻辑和测试 fixture。
- 文档和代码索引。

## 7. 目标业务模型

### 7.1 学习计划 canonical 快照

精简后的 `LearningPlanDraftPlan` 持久化结构如下：

```json
{
  "title": "栈与单调栈专项",
  "summary": "2 周补齐基础栈、表达式求值、单调栈和单调队列窗口题。",
  "intent": "TOPIC_BREAKTHROUGH",
  "objective": "掌握栈状态维护、表达式解析和单调结构的不变量。",
  "durationWeeks": 2,
  "level": "INTERMEDIATE",
  "weeklyHours": 6,
  "programmingLanguage": "Java",
  "difficultyDistribution": {
    "easyPercent": 35,
    "mediumPercent": 55,
    "hardPercent": 10
  },
  "topicPreferences": [
    "Stack",
    "Monotonic Stack"
  ],
  "additionalConstraints": null,
  "phases": [
    {
      "phaseIndex": 1,
      "title": "基础栈、括号与表达式",
      "durationWeeks": 1,
      "focus": "稳定括号匹配、最小栈、路径解析和表达式求值。",
      "problems": [
        {
          "slug": "valid-parentheses",
          "frontendId": 20,
          "title": "Valid Parentheses",
          "titleCn": "有效的括号",
          "difficulty": "EASY",
          "tags": [
            "stack",
            "string"
          ],
          "reason": "训练括号嵌套匹配和失配检测。",
          "sortOrder": 1
        }
      ]
    }
  ],
  "metadata": {
    "contentLocale": "zh-CN",
    "personalizationEnabled": true,
    "dailyProblemCount": 2,
    "trainingDaysPerWeek": 5,
    "coveragePolicy": "FIT_USER_BUDGET",
    "loadSummary": {
      "intensity": "OVERLOADED"
    }
  }
}
```

模板生成的计划只额外保留最小来源身份和确认校验信息：

```json
{
  "draftSource": "TEMPLATE",
  "template": {
    "templateId": "topic_stack_monotonic",
    "matchedProblemCount": 18
  }
}
```

`loadSummary` 当前仍被草案修订规则读取，因此第一批继续保留。后续如果修订链路改为显式传递负载投影，可以再独立删除其 metadata 副本。

### 7.2 阶段模型

目标 `LearningPlanPhaseDraft`：

```java
public record LearningPlanPhaseDraft(
    int phaseIndex,
    String title,
    int durationWeeks,
    String focus,
    List<LearningPlanProblemDraft> problems
) {
}
```

阶段模型只承担：

- 连续顺序。
- 展示标题。
- 周期分配。
- 训练重点。
- 有序题目列表。

### 7.3 模板阶段模型

目标 `LearningPlanTemplatePhase`：

```java
public record LearningPlanTemplatePhase(
    Long id,
    int phaseIndex,
    String title,
    String titleEn,
    int durationWeeks,
    String focus,
    String focusEn,
    List<LearningPlanTemplateProblemRef> problemRefs
) {
}
```

模板阶段继续保留中英文标题和 focus，因为它们直接用于模板草案展示。目标、验收和复盘文案不再作为模板阶段契约。

### 7.4 模板主模型

`LearningPlanTemplate` 删除：

- `interviewOriented`。
- `difficultyMix`。

保留：

- `intent` 作为计划用途语义。
- `catalogCategory` 作为模板目录分类。
- `difficultyPreference` 作为模板默认难度配置。
- `topicPreferences` 作为模板主题范围。
- 来源、许可证和整理字段作为合规与审计信息。

## 8. 字段决策

### 8.1 直接删除字段

| 字段 | 当前问题 | 删除后的处理 |
| --- | --- | --- |
| `phase.objectives` | 无生产界面和业务规则消费者 | 不替代 |
| `phase.acceptanceCriteria` | 无验收执行闭环 | 不替代 |
| `phase.reviewAdvice` | 不直接展示，只被复制到派生对象 | `reviewTask` 改为服务端派生 |
| `phase.recommendedTags` | 与题目真实 tags 重复，可能冲突 | 页面从阶段题目 tags 派生 |
| `objectivesEn` | 主字段删除 | 不替代 |
| `acceptanceCriteriaEn` | 主字段删除 | 不替代 |
| `reviewAdviceEn` | 主字段删除 | 使用本地化的派生 `reviewTask` |
| `interviewOriented` | 与 intent/category 重复且可能矛盾 | 计划逻辑使用 intent，目录使用 category |
| `template.difficultyMix` | 可从 refs 统计，无运行时消费者 | 仅在 Seed manifest 保留统计 |
| `weeklyBucket.reviewAdvice` | 周进度计算不读取，前端不展示 | 删除 |

### 8.2 metadata 删除字段

| metadata 路径 | 处理 |
| --- | --- |
| `metadata.problemRecommendationIncomplete` | 删除模型输出、服务端写入、常量和历史 JSON 键 |
| `metadata.loadRisk` | 删除，统一读取 `loadSummary.intensity` |
| `metadata.weeklyBuckets` | 删除兼容键和前端 fallback；顶层计算字段不受影响 |
| `metadata.nextTrainingPackage` | 删除兼容键和前端 fallback；顶层计算字段不受影响 |
| `metadata.template.sourceName` | 删除草案副本，模板主表继续保留 |
| `metadata.template.sourceUrl` | 删除草案副本，模板主表继续保留 |
| `metadata.template.sourceCommit` | 删除草案副本，模板主表继续保留 |
| `metadata.template.sourceDataPath` | 删除草案副本，模板主表继续保留 |
| `metadata.template.problemCount` | 删除，计划自身可统计题量 |
| `metadata.template.missingProblemCount` | 删除，导入审计继续保留 |
| `metadata.template.problemRefs` | 删除，引用仍存在模板关系表和 Seed refs 文件 |

`metadata.template.templateId` 和 `metadata.template.matchedProblemCount` 保留。前者用于来源身份，后者仍被模板草案确认校验使用。

### 8.3 明确保留字段

| 字段 | 保留原因 |
| --- | --- |
| `phaseIndex` | 阶段连续性、进度和题目归属契约 |
| `problem.sortOrder` | 题目训练顺序 |
| `problem.frontendId/title/titleCn/difficulty/tags` | 页面展示、负载计算和历史快照稳定性 |
| `problem.reason` | 用户可见的选题理由 |
| `topicPreferences` | 计划主题范围，专项计划校验依赖 |
| `difficultyDistribution` | 当前创建表单、Prompt 和本地推荐仍使用 |
| `loadSummary` | 当前草案修订逻辑读取过载状态 |
| 模板来源与许可证字段 | 合规、归因和 Seed 审计 |
| problem-ref 顺序与来源字段 | 模板完整路线和可追溯性 |

## 9. 删除字段后的行为替代

### 9.1 阶段标签

`PlanPreview` 和 `LearningPlanExtensionPanel` 不再读取 `phase.recommendedTags`，改为从 `phase.problems[*].tags` 派生。

固定规则：

1. 按 `problem.sortOrder` 遍历题目。
2. 按每道题现有 tags 顺序遍历。
3. 使用稳定 value 去重。
4. 最多展示 4 个标签。
5. 没有可信标签时隐藏标签行。

该规则只用于展示，不把结果写回 API 或 JSONB。

### 9.2 下一训练包复盘任务

`LearningPlanTrainingPackage.reviewTask` 继续保留，因为页面会展示它。`LearningPlanLoadService` 不再读取阶段 `reviewAdvice`，而是根据当前阶段和 `contentLocale` 生成：

```text
zh-CN: 复盘「{phase.title}」训练中的卡点、错因和边界条件。
en-US: Review blockers, mistakes, and edge cases from "{phase.title}".
```

没有可用阶段时使用本地化通用文案。该字段是请求时计算的执行投影，不进入计划快照 metadata。

### 9.3 面试导向

- 计划生成、修订和扩展统一以 `intent == INTERVIEW_SPRINT` 表达面试冲刺语义。
- 模板列表的分类和筛选继续使用 `catalogCategory == INTERVIEW_PREP`。
- 创建表单不再从场景选项额外生成并提交 `interviewOriented`。

### 9.4 模板难度统计

`difficultyMix` 从模板领域对象、数据库和模板 JSONL 删除。生成器仍可根据 problem refs 计算难度分布，并写入 `learning_plan_template_seed_manifest.json` 和研发 metadata 报告，用于质量审计，不参与后端导入。

## 10. API 契约调整

### 10.1 请求

`LearningPlanCreateDraftRequest` 删除 `interviewOriented`。调用方只提交 `intent`。

草案修订的 `resolvedBrief` structured output 同步删除 `interviewOriented`。服务端不再要求模型复制该字段。

### 10.2 响应

以下响应删除 `interviewOriented`：

- 草案计划响应。
- 正式计划详情响应。
- 模板列表摘要响应。
- 模板详情响应。

以下阶段响应删除四个阶段字段：

- 草案阶段。
- 正式计划阶段。
- 模板阶段。
- 扩展阶段。

`LearningPlanWeeklyBucket` 删除 `reviewAdvice`。

### 10.3 运行态投影

以下顶层字段继续保留，不得与废弃 metadata 键混淆：

- `loadSummary`。
- `weeklyBuckets`。
- `nextTrainingPackage`。
- `rhythmSettings`。
- `paceSummary`。

`LearningPlanPublicMetadataMapper` 精简后只允许返回：

- `dailyProblemCount`。
- `trainingDaysPerWeek`。
- `coveragePolicy`。
- `loadSummary`。

前端 `load.ts` 删除对 `metadata.weeklyBuckets`、`metadata.nextTrainingPackage` 和 `metadata.loadRisk` 的兼容读取。

## 11. 后端改造

### 11.1 领域模型

需要调整：

- `LearningPlanBrief`。
- `LearningPlanDraftPlan`。
- `LearningPlanPhaseDraft`。
- `LearningPlanWeeklyBucket`。
- `LearningPlanTemplate`。
- `LearningPlanTemplatePhase`。
- `LearningPlanTemplateSeedRecord`。
- `LearningPlanTemplatePhaseSeedRecord`。

对会直接反序列化历史 JSON 的 record 增加：

```java
@JsonIgnoreProperties(ignoreUnknown = true)
```

至少覆盖：

- `LearningPlanBrief`。
- `LearningPlanDraftPlan`。
- `LearningPlanPhaseDraft`。
- `LearningPlanTemplateSeedRecord`。
- `LearningPlanTemplatePhaseSeedRecord`。
- `LearningPlanExtensionDraft`，如果继续读取历史扩展提案。

该兼容只负责读取旧数据，不允许新序列化结果继续输出废弃字段。

### 11.2 当前 AI structured output

本次不重构 Agent 架构，但现有 Schema 必须和业务模型同步：

- `LearningPlanGeneratedContentJsonSchema` 删除四个阶段字段。
- `LearningPlanExtensionJsonSchema` 删除四个阶段字段。
- `LearningPlanDraftRevisionJsonSchema.resolvedBrief` 删除 `interviewOriented`。
- 删除模型输出中的 `metadata.problemRecommendationIncomplete`。
- 初次生成若 metadata 只承载该字段，则直接删除模型输出 `metadata` 节点。
- Prompt 删除对 `recommendedTags` 和 `problemRecommendationIncomplete` 的输出要求。

题目 structured output 本轮保持原样，避免混入 AI 模型视图重构。

### 11.3 服务和 Mapper

需要同步修改：

- 初次生成、本地兜底生成、草案修订、扩展生成和扩展应用中的 record 构造。
- `LearningPlanLoadService` 的周桶和下一训练包计算。
- `LearningPlanTemplateDraftService` 的模板阶段转换和最小 metadata 构造。
- `LearningPlanDraftValidator` 的模板 matched 数校验保持不变。
- `LearningPlanTemplateSeedImportService` 的字段映射和必填校验。
- `LearningPlanResponseMapper`、`LearningPlanTemplateResponseMapper` 和相关 API records。
- `MyBatisLearningPlanTemplateRepository`、row records 和 Mapper XML。
- `LearningPlanDraftMetadataKeys` 和 `LearningPlanPublicMetadataMapper`。

## 12. 数据库迁移

### 12.1 迁移文件

新增下一可用版本的 Flyway migration，建议命名：

```text
V54__simplify_learning_plan_data_model.sql
```

如果合并前已有 V54，则顺延版本。不得修改已执行的 V9、V17、V24、V43 或 V51。

### 12.2 普通计划表

以下表不需要删除实体列：

- `learning_plan_draft`。
- `learning_plan`。
- `learning_plan_draft_revision`。
- `learning_plan_extension_revision`。

原因是业务对象保存在 JSONB 中。

迁移必须清理以下 JSONB：

| 表 | JSONB 列 | 处理 |
| --- | --- | --- |
| `learning_plan_draft` | `command_json` | 删除 `interviewOriented` |
| `learning_plan_draft` | `draft_plan_json` | 精简完整计划结构 |
| `learning_plan` | `plan_json` | 精简完整计划结构 |
| `learning_plan_draft_revision` | `base_plan_json` | 精简完整计划结构 |
| `learning_plan_draft_revision` | `proposed_plan_json` | 精简完整计划结构 |
| `learning_plan_extension_revision` | `base_plan_json` | 精简完整计划结构 |
| `learning_plan_extension_revision` | `previous_extension_json` | 精简 `newPhases` 和 metadata |
| `learning_plan_extension_revision` | `proposed_extension_json` | 精简 `newPhases` 和 metadata |

`messages_json` 和 Agent 审计输出属于历史对话与执行证据，不作为 canonical 业务对象迁移，保持原样。

建议参考 V43 的模式建立事务内 `pg_temp` JSONB 变换函数，要求：

1. 同时识别 `phases` 和 `newPhases` 数组。
2. 保持数组原顺序。
3. 从每个阶段删除四个废弃键。
4. 从计划根对象删除 `interviewOriented`。
5. 从 metadata 删除四个废弃键。
6. 将 `metadata.template` 收敛为 `templateId + matchedProblemCount`。
7. 只更新 `IS DISTINCT FROM` 的行，避免无意义写放大。
8. 更新拥有 `updated_at` 的业务行时间。

### 12.3 模板表 DDL

`learning_plan_template` 删除：

```text
interview_oriented
difficulty_mix_json
```

`learning_plan_template_phase` 删除：

```text
objectives_json
objectives_en_json
recommended_tags_json
acceptance_criteria_json
acceptance_criteria_en_json
review_advice
review_advice_en
```

### 12.4 英文完整性约束

迁移必须删除 V51 中引用废弃英文列的约束：

- `ck_learning_plan_template_phase_review_advice_en_non_blank`。
- `ck_learning_plan_template_phase_objectives_en_non_empty`。
- `ck_learning_plan_template_phase_acceptance_en_non_empty`。

`validate_learning_plan_template_english_content()` 重建后，英文模板阶段只校验：

- 至少存在一个阶段。
- `phase.title_en` 非空。
- `phase.focus_en` 非空。

为避免函数引用已删除列，迁移应显式删除两个 constraint trigger 和旧函数，完成列删除后再创建新函数与 trigger，不使用 `CASCADE` 隐式扩大删除范围。

### 12.5 DDL 发布约束

删除模板列后，旧版本应用无法继续运行，因此该迁移是向前迁移：

- 发布前备份相关表和 JSONB。
- Flyway migration、后端新代码和 Seed v3 必须在同一发布版本交付。
- 不允许新旧应用版本同时连接迁移后的数据库。
- 回滚优先采用向前修复；需要回退旧版本时必须同时恢复数据库备份。

## 13. Seed v3 设计

### 13.1 新模板记录

`learning_plan_templates.jsonl` 删除：

- `interviewOriented`。
- `difficultyMix`。

每个 phase 删除：

- `objectives`。
- `objectivesEn`。
- `recommendedTags`。
- `acceptanceCriteria`。
- `acceptanceCriteriaEn`。
- `reviewAdvice`。
- `reviewAdviceEn`。

目标 phase 示例：

```json
{
  "phaseIndex": 1,
  "title": "基础栈、括号与表达式",
  "titleEn": "Basic stacks, brackets, and expressions",
  "durationWeeks": 1,
  "focus": "稳定括号匹配、最小栈、路径解析和表达式求值。",
  "focusEn": "Build reliable stack, bracket, path, and expression patterns."
}
```

`learning_plan_template_problem_refs.jsonl` 的字段和顺序完全保持不变。

### 13.2 源文件与翻译

所有 `data/learning-plan-template-sources/templates/*/template.json` 删除：

- 根级 `interviewOriented`。
- phase 的四个中文字段。

所有 `translations/en-US.json` phase 删除：

- `objectives`。
- `acceptanceCriteria`。
- `reviewAdvice`。

保留 phase 的 `phaseIndex`、`title`、`focus`，并继续校验翻译索引与中文阶段一致。

### 13.3 生成器

`prepare_template_seed.py` 调整：

- 删除 phase 文案默认生成函数和必填校验。
- 不再从 `recommendedTags` 汇总 `topicPreferences`，模板根级必须显式提供 `topicPreferences`。
- 不再向模板 JSONL 写 `difficultyMix`，但继续向 manifest 和 metadata 报告写统计。
- manifest `schemaVersion` 从 `2` 升为 `3`。
- 重新计算四个聚合文件的 bytes 和 SHA256。

`translate_template_sources.py` 删除三个阶段翻译字段的提取、锁定和校验。

### 13.4 兼容策略

Seed v3 是当前生产格式。v2 兼容方式是：

- Seed Record 使用 `@JsonIgnoreProperties(ignoreUnknown = true)`。
- v2 中的额外字段被忽略。
- v3 必填校验不再要求废弃字段。
- 不允许生成器继续产生 v2 字段。

兼容目标是“旧文件可读”，不是“新系统继续保存旧字段”。

### 13.5 数据规模基线

当前聚合 Seed 基线：

- 35 个模板。
- 178 个阶段。
- 1738 条 problem refs。
- 1699 条本地匹配 refs。
- 39 条本地缺失 refs。

迁移后模板数、阶段数、refs 数、匹配数和缺失数必须保持不变。只允许模板 JSONL 体积和 manifest 哈希变化。

## 14. 前端改造

### 14.1 类型

从 `frontend/src/types/api.ts` 删除：

- 创建请求、草案、正式计划和模板响应中的 `interviewOriented`。
- 阶段类型中的四个废弃字段。
- `LearningPlanWeeklyBucket.reviewAdvice`。

### 14.2 创建表单

`planScenarioOptions` 不再维护 `interviewOriented` 属性。表单提交只发送场景对应的 `intent`。

### 14.3 展示

- `PlanPreview` 使用阶段题目 tags 派生标签。
- `LearningPlanExtensionPanel` 使用同一个派生 helper。
- 删除对 metadata 周桶和下一训练包的 fallback。
- 下一训练包继续展示服务端返回的 `reviewTask`。

共享派生 helper 应集中在 `frontend/src/learning-plans/`，避免两个页面各自实现不同的去重和截断规则。

## 15. 实施顺序

### 阶段一：固定契约和兼容读取

1. 增加旧 JSON 和 v2 Seed 兼容测试。
2. 为历史 JSON/Seed record 增加忽略未知字段配置。
3. 固定目标 JSON、API 和 Seed v3 fixture。

### 阶段二：领域与应用代码精简

1. 删除 Java record 字段和构造参数。
2. 修改生成、修订、扩展、模板草案和负载服务。
3. 修改 structured output Schema 和当前 Prompt。
4. 修改 API DTO、Mapper、Repository row 和 MyBatis XML。

### 阶段三：Seed v3

1. 修改 35 个模板源和翻译。
2. 修改生成与翻译脚本。
3. 重新生成四个聚合产物。
4. 验证基线计数和导入幂等。

### 阶段四：数据库迁移

1. 清理计划、草案和提案 JSONB。
2. 删除模板列和英文约束。
3. 重建英文完整性函数与 trigger。
4. 使用迁移后的数据库运行 Repository 和 API 集成测试。

### 阶段五：前端与端到端验证

1. 更新 TypeScript 类型和 fixture。
2. 接入阶段标签派生 helper。
3. 验证中英文下一训练包复盘文案。
4. 完成创建、模板生成、修订、扩展、确认和详情页回归。

## 16. 测试设计

### 16.1 后端单元测试

- 精简后的 record 能序列化为目标 JSON。
- 带旧字段的计划 JSON 能反序列化，新序列化结果不再包含旧字段。
- `reviewTask` 按当前阶段和 locale 确定性生成。
- 周桶不再包含 `reviewAdvice`。
- 模板草案 metadata 只保留最小模板身份和 matched 数。
- `loadRisk` 不再写入，过载判断仍由 `loadSummary.intensity` 表达。
- structured output Schema 不再要求废弃字段。

### 16.2 数据库迁移测试

迁移前插入包含全部旧字段的：

- draft command。
- draft plan。
- confirmed plan。
- draft revision base/proposed plan。
- extension base/previous/proposed JSON。
- 中英文模板及阶段。

迁移后验证：

- JSONB 中废弃键全部不存在。
- 阶段和题目顺序未改变。
- 模板最小 metadata 保留。
- 模板废弃列不存在。
- 英文模板仍满足新的 title/focus 完整性约束。
- 新 Repository 可以读取全部历史业务行。

### 16.3 Seed 测试

- v3 聚合 Seed 生成稳定。
- v3 不包含任何废弃字段。
- v2 fixture 可以被新 Reader 读取。
- 缺少 title/focus 或英文对应字段时失败。
- problem refs 指向不存在阶段时继续失败。
- 重复导入 v3 两次保持幂等。
- 35/178/1738/1699/39 基线不变。

### 16.4 前端测试

- 阶段标签由 problems tags 稳定去重并限制为 4 个。
- 无 tags 时不渲染空标签容器。
- 创建请求不包含 `interviewOriented`。
- 页面可以渲染不含四个旧阶段字段的草案、模板和正式计划。
- 下一训练包继续显示 `reviewTask`。
- metadata fallback 删除后，顶层投影仍正常展示。

### 16.5 建议验证命令

```bash
make backend-test
make frontend-test
uv run python -m unittest tools.learning_plan_template_seed.prepare_template_seed_test
uv run python -m unittest tools.learning_plan_template_seed.translate_template_sources_test
make db-seed
```

数据库迁移集成测试按项目现有 PostgreSQL/Failsafe 入口执行。

## 17. 验收标准

1. 生产 Java 和 TypeScript 业务类型中不存在本设计删除的字段。
2. 新 API 请求和响应不包含删除字段。
3. 新生成的计划、草案和提案 JSONB 不包含删除字段。
4. 已有 JSONB 经 Flyway 清理后不包含删除字段。
5. 模板表的九个废弃列全部删除。
6. Seed schemaVersion 为 3，聚合产物和 manifest 校验通过。
7. v2 Seed 兼容 fixture 可读取，v3 不再生成旧字段。
8. 模板和 refs 基线计数不变。
9. 阶段标签、下一训练包、模板草案和英文模板没有功能回退。
10. `rg` 检索删除字段时，只允许出现在历史 migration、兼容 fixture、迁移断言和本设计文档中。

## 18. 风险与回滚

### 18.1 模板列删除不可被旧版本代码兼容

该 DDL 会使旧 Repository SQL 失效。发布必须保证应用和数据库同步，不支持迁移后回滚旧二进制而不恢复数据库。

### 18.2 历史 JSON 反序列化

仅依赖 Spring Boot 默认 Jackson 配置不够明确。必须通过 record 注解和迁移测试固定忽略未知字段行为。

### 18.3 `reviewAdvice` 的隐藏依赖

当前 `reviewAdvice` 会影响页面展示的 `nextTrainingPackage.reviewTask`。删除时必须先完成本地化派生规则和测试，不能只删除字段。

### 18.4 阶段标签变化

题目 tags 派生结果可能与旧 `recommendedTags` 不完全相同。这是预期收敛，以题库可信 tags 为准。展示数量固定为 4，避免标签过多改变布局。

### 18.5 Seed 哈希整体变化

四个聚合产物的 bytes 和 SHA256 会变化。发布前必须确认变化只来自 Schema v3 和生成报告更新，problem refs 内容与顺序不得变化。

## 19. 主要改动位置

后端领域与服务：

- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/template/`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/stream/`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/`

API、Repository 与迁移：

- `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/`
- `backend/mentor-api/src/main/resources/mapper/learningplan/`
- `backend/mentor-api/src/main/resources/db/migration/`

Seed：

- `data/learning-plan-template-sources/`
- `data/learning-plan-template-seed/`
- `tools/learning_plan_template_seed/`

前端：

- `frontend/src/types/api.ts`
- `frontend/src/learning-plans/`
- `frontend/src/services/api.test.ts`

## 20. 后续独立议题

完成本设计后，再分别评估：

1. `difficultyDistribution` 是否迁移为 `difficultyPreference`。
2. `loadSummary` 是否从持久化 metadata 移为纯计算投影。
3. 顶层 `weeklyBuckets` 是否仍需要作为公共 API 返回。
4. AI 是否使用独立于业务 canonical model 的精简工作视图。

这些事项不得阻塞本次字段删除，也不得在同一个数据库迁移中顺带实施。

