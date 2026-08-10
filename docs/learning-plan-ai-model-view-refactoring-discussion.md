# AI 学习计划草案修订模型视图与字段还原讨论稿

## 1. 文档信息

- 讨论日期：2026-08-06
- 状态：已收敛草案修订场景的数据边界
- 当前适用范围：AI 修订已经生成的学习计划草案，包括由模板生成后再进入 AI 修订的草案
- 当前不讨论：首次生成、正式计划扩展、完整 Agent 编排、Review、持久化表结构和最终生产级 Patch Schema
- 关联文档：
  - `docs/learning-plan-personalized-generation-redesign.md`
  - `docs/learning-plan-revision-performance-incident-2026-08-04.md`
  - `docs/learning-plan-revision-plan-compiler-design.md`

本文只回答六个问题：修订开始时服务端持有什么原始数据；哪些字段不交给 AI；AI 实际读取和修改哪些字段；短计划和 149 题大计划分别怎样提供基线；修订 Agent 需要哪些 Tool；AI 返回后服务端如何恢复完整 `LearningPlanDraftPlan`。

## 2. 当前实现现状

当前草案修订以 `LearningPlanDraft` 为业务入口，修订开始时可以读取：

- `LearningPlanDraft.brief`：已经规范化、校验过的规划约束。
- `LearningPlanDraft.draftPlan`：当前完整 canonical 草案。
- 用户本次自然语言修订要求。
- 本次运行重新生成的个性化上下文。

当前 `LearningPlanDraftRevision` 会冻结 `basePlan`，但不会独立冻结 `baseBrief`。Agent 输入仍然直接包含完整 `brief` 和完整 `draftPlan`，并被要求返回完整 `resolvedBrief + generatedContent`。

当前服务端已经做了两类有限还原：

1. `personalizationEnabled` 和 `contentLocale` 不信任模型值，而是从当前 Brief 恢复。
2. 模型返回题目后，服务端根据 slug 查询本地题库，重新写入题目事实和 `sortOrder`。

但当前实现仍有四个核心问题：

1. 未修改的 Brief、标题、摘要、阶段和题目仍主要依靠模型完整复制，不是服务端合并。
2. 模型输入和输出都包含题目 `frontendId`、双语标题、difficulty、tags、sortOrder，虽然最终服务端并不信任这些值。
3. 模型输出映射时先重建最小 metadata，再计算负载，原计划中的节奏配置和模板来源信息可能丢失。
4. 修订结果当前统一执行 `validateGeneratedPlan`，而模板草案创建时执行 `validateTemplatePlan`；149 题模板不能被当成“每阶段最多 5 题”的 AI 首次生成结果校验。

因此目标不应只是“删除几个 JSON 字段”，而应建立明确的四层数据边界：

```text
受信修订基线
  -> AI 读取模型视图
  -> AI 语义修订结果
  -> 服务端合并、水合、重算后的 canonical 草案
```

## 3. 修订的原始数据

### 3.1 受信修订基线

每次修订开始时，应冻结一个不可变的 `RevisionBaseSnapshot`。它至少包含：

```text
baseBrief
basePlan
```

其中：

- `baseBrief` 是用户规划约束的权威来源。
- `basePlan` 是当前计划语义、当前题目集合、题目推荐理由和 metadata 的权威基线。
- 用户修订要求只描述本次变化，不属于可被还原的原计划数据。
- 个性化上下文只辅助 AI 判断，不是恢复任何 canonical 字段的数据源。

当前只冻结 `basePlan` 不够清晰。虽然 `basePlan` 复制了大部分 Brief 字段，`personalizationEnabled` 和 `contentLocale` 也能从 metadata 间接取得，但恢复逻辑不应依赖重复字段和 metadata 反推。后续实现应显式冻结 `baseBrief + basePlan`。

修订完成时必须基于该冻结快照合并，不能重新读取一个可能已经变化的最新草案作为字段恢复来源。最新草案只用于并发版本和提案有效性检查。

### 3.2 其他受信数据源

除修订基线外，字段恢复还需要两个服务端数据源：

- 本地题库：根据 slug 校验题目并加载完整题目事实。
- 确定性领域服务：重新生成阶段序号、题目顺序、负载摘要等派生字段。

因此“还原省略字段”不等于全部从原 JSON 复制。最终字段有四种来源：原样保留、按 slug 水合、确定性重算、明确失效。

## 4. AI 读取的数据

AI 读取视图与 AI 写回结构不应完全相同。读取视图可以包含少量只读判断信号；写回结果只包含模型真正有权决定的语义。

建议的修订读取视图如下：

```json
{
  "constraints": {
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
    "additionalConstraints": "优先覆盖常见面试题。"
  },
  "plan": {
    "title": "栈与单调栈专项",
    "summary": "2 周补齐基础栈、表达式求值和单调结构。",
    "phases": [
      {
        "ref": "phase-1",
        "title": "基础栈与表达式",
        "focus": "稳定括号匹配、最小栈和表达式求值。",
        "problems": [
          {
            "slug": "valid-parentheses",
            "displayTitle": "有效的括号",
            "difficulty": "EASY",
            "reason": "训练括号嵌套匹配和失配检测。"
          }
        ]
      }
    ]
  },
  "signals": {
    "loadIntensity": "OVERLOADED"
  }
}
```

### 4.1 约束字段

AI 可以读取以下当前真实存在的 Brief 字段：

- `intent`
- `objective`
- `durationWeeks`
- `level`
- `weeklyHours`
- `programmingLanguage`
- `difficultyDistribution`
- `topicPreferences`
- `additionalConstraints`

这些字段默认保持，只有用户明确提出变化时才允许修改。

`interviewOriented` 已经从当前 Brief、计划和创建接口中删除，不应再出现在模型视图。面试倾向应由 `intent`、`objective` 或 `additionalConstraints` 表达。

### 4.2 计划语义字段

AI 可以读取：

- `plan.title`
- `plan.summary`
- `phase.ref`：服务端为本次 revision 生成的临时定位符，例如 `phase-1`；只用于局部 Patch 定位，不进入 canonical plan。
- `phase.title`
- `phase.focus`
- `problem.slug`
- `problem.displayTitle`：只保留当前 locale 的单一标题，帮助低能力模型理解不熟悉的 slug。
- `problem.difficulty`：只读判断信号，支持“减少 Hard”一类修订。
- `problem.reason`

题目读取视图不宜只保留 `slug + reason`。slug 足以标识题目，但不能稳定支持低能力模型判断陌生题目和当前难度结构。单语言标题和 difficulty 成本很低，同时不需要模型在写回时复制。

阶段级 `phaseIndex` 和 `durationWeeks` 不进入模型视图。阶段在当前产品中表达学习主题的先后关系，不直接表达按周拆分的任务；把阶段周数交给 AI 容易让模型误以为需要生成周计划。总周期只在 `constraints.durationWeeks` 中保留。

局部 Patch 仍需要稳定定位原阶段，因此投影器提供 revision 内有效的 `phase.ref`。例如 `phase-2` 只表示冻结快照中的第二个阶段，不是最终业务序号，也不会写入 `LearningPlanDraftPlan`。

### 4.3 只读负载信号

AI 可以读取从 `metadata.loadSummary.intensity` 投影出的 `signals.loadIntensity`，用于理解当前计划是宽松、合理、偏紧还是过载。

该字段不进入 AI 写回结构。完整负载点数、容量、建议、coverage policy 和计算过程仍由服务端持有。

## 5. 不交给 AI 的字段

### 5.1 身份、接口和运行字段

- `draftId`
- `proposalGroupId`
- `revisionId`
- `userId`
- `runId`
- `status`
- `success`
- `assistantMessage`
- `missingFields`
- `timestamp`

这些字段既不参与计划语义，也不得由模型决定。

### 5.2 Brief 控制字段

- `personalizationEnabled`
- `contentLocale`

二者可以影响运行方式或展示语言，但不是普通自然语言修订可以修改的计划内容，必须从 `baseBrief` 原样保留。

### 5.3 canonical 重复字段

`LearningPlanDraftPlan` 当前重复保存以下 Brief 字段：

- `intent`
- `objective`
- `durationWeeks`
- `level`
- `weeklyHours`
- `programmingLanguage`
- `difficultyDistribution`
- `topicPreferences`
- `additionalConstraints`

模型只在 `constraints` 中读取和修改一次，不再同时读取计划对象中的重复副本。生成最终 canonical plan 时，服务端从合并后的 Brief 统一回填这些字段。

### 5.4 题目事实字段

以下字段不要求 AI 写回：

- `frontendId`
- `title`
- `titleCn`
- `difficulty`
- `tags`
- `sortOrder`

其中单语言标题和 difficulty 可以出现在 AI 读取视图中，但属于只读提示，AI 写回时仍只表达 `slug + reason`。

### 5.5 结构与派生字段

- 最终 `phaseIndex`
- 最终 `sortOrder`
- 完整 `metadata`
- 完整负载摘要及其计算过程
- 题目数、阶段数等可确定性统计字段

这些字段由最终列表顺序、合并结果和领域服务重新生成。

## 6. AI 写回的语义

本阶段不冻结最终 Patch Schema，但写回边界必须满足以下规则：

1. Brief 只写用户明确要求改变的字段；缺失表示保持 `baseBrief`。
2. 标题、摘要、阶段标题和阶段重点属于模型可修改的计划语义。
3. 模型直接指定题目时只写 `slug + reason`；不写题目事实字段。
4. 当用户只给出“换成同主题 Medium”一类选择目标、模型又不应猜测精确 slug 时，可以写受约束的 `replacementSelection`，由编译器在本地题库中解析为最终 slug。
5. 显式新增 slug 必须提供 reason；编译器选择的题目必须根据 selection 的 `learningGoal` 和受信题目洞察生成可追溯 reason。
6. 保留题目可以沿用原 reason；只有训练作用变化时才写新 reason。
7. 删除和移动必须显式表达，不能把“字段未出现”解释为删除。
8. AI 使用 `phase.ref` 定位原阶段；最终 `phaseIndex` 仍由服务端生成。
9. AI 不读取也不写回 `phase.durationWeeks`；总周期变化后由服务端兼容布局策略处理。
10. AI 不写任何 metadata、题目事实或负载结果。

无论最后选择“完整精简快照”还是“局部 Patch”，都不能依赖模型复制未修改的完整 canonical 对象。推荐使用缺失即保持、删除必须显式表达的 Patch 语义。

## 7. AI 返回后的字段还原

### 7.1 合并 Brief

```text
resolvedBrief = merge(baseBrief, aiBriefPatch)
```

规则如下：

- AI 明确修改的规划字段覆盖基线。
- 未出现的规划字段从 `baseBrief` 保留。
- `personalizationEnabled` 和 `contentLocale` 无条件从 `baseBrief` 保留。
- 合并后重新执行与创建场景相同的 Brief 校验。
- 最终 canonical plan 中重复的约束字段全部从 `resolvedBrief` 回填，不从 AI 的 plan 部分读取。

### 7.2 合并计划语义

```text
resolvedSemanticPlan = merge(basePlanSemanticView, aiPlanPatch)
```

规则如下：

- 未修改的标题、摘要、阶段和题目从 `basePlan` 保留。
- 原阶段按 revision 内的受信 `phase.ref` 匹配；结构重建时以 AI 明确给出的新顺序为准。
- 题目按 slug 匹配。未显式删除的原题保持，显式移动的题目更换阶段但不更换身份。
- 原题未修改 reason 时，从 `basePlan` 保留原 reason。
- 新增 slug 必须携带 reason，并进入题库水合流程。

### 7.3 水合题目事实

对最终语义计划中的全部 slug 做一次批量校验和水合：

1. 原计划已有 slug 和新增 slug 都必须从本地题库命中。
2. `basePlan` 只用于保留题目所属阶段和原 reason，不作为最终题目事实来源。
3. 最终统一由受信题库事实写入 `frontendId`、`title`、`titleCn`、`difficulty` 和 `tags`。
4. AI 读取时看到的标题和 difficulty 不能直接写回 canonical plan。
5. slug 不存在时返回可修复诊断，不能静默保留模型猜测的题目事实。
6. 重复 slug 按业务规则报错或确定性去重，不能依赖模型自行纠正。

### 7.4 重建结构字段

- `phaseIndex` 按最终阶段列表从 1 连续生成。
- `sortOrder` 按每个阶段的最终题目列表从 1 连续生成。
- 阶段总周期必须等于 `resolvedBrief.durationWeeks`。
- 如果总周期和阶段结构均未变化，恢复器保留原阶段 `durationWeeks`，仅满足现有 canonical 兼容要求。
- 如果总周期或阶段数变化，由服务端的统一阶段布局策略重新分配兼容值，不让模型生成不一致的索引和总和。

这里的阶段 `durationWeeks` 不是 AI 规划语义。当前 `LearningPlanLoadService.weeklyBuckets`、进度节奏计算和 `LearningPlanDraftValidator` 仍依赖它，因此暂时由恢复器生成。若产品确认阶段不再承担周排期，后续应把周安排迁移到独立 schedule/bucket 数据中，再从 canonical phase 删除该字段。

如果未来要支持“第一阶段固定 2 周”这类显式非均衡要求，应单独扩展阶段周期 Patch 契约；不能让普通输出中的任意数字直接绕过布局校验。

### 7.5 恢复和重算 metadata

metadata 不能整体复制，也不能整体清空。必须按语义分类处理：

| 类型 | 字段示例 | 处理方式 |
| --- | --- | --- |
| 运行控制 | `contentLocale`、`personalizationEnabled` | 从 `baseBrief` 恢复 |
| 用户节奏配置 | `dailyProblemCount`、`trainingDaysPerWeek` | 从 `basePlan.metadata` 保留 |
| 规划策略 | `coveragePolicy` | 默认从基线保留；若后续开放修改，必须走显式服务端契约 |
| 派生结果 | `loadSummary` | 根据最终计划重新计算 |
| 来源信息 | `draftSource`、`template.templateId` | 按来源转换规则保留为 provenance，不交给 AI |
| 与旧内容绑定的断言 | `template.matchedProblemCount` | 题目集合变化后失效，不能原样复制 |

模板草案进入 AI 修订后，应保留“来源于哪个模板”的 provenance，但不能继续把修订后的结果当作未经修改的模板实例。具体 metadata key 可以在实施时冻结，但语义必须是：保留模板来源，失效模板完整匹配断言，再按 AI 草案规则校验最终结果。

### 7.6 最终组装与校验

最终 canonical 组装顺序固定为：

```text
冻结 baseBrief + basePlan
  -> 应用 AI 语义 Patch
  -> 得到 resolvedBrief
  -> 合并计划语义
  -> 批量水合题目事实
  -> 重建 phaseIndex 和 sortOrder
  -> 恢复控制字段、节奏配置和来源信息
  -> 重算 loadSummary
  -> 组装 LearningPlanDraftPlan
  -> 按草案来源和修订后语义执行目标计划校验
  -> 原子保存 revision proposedPlan 与 draft brief/plan
```

最终校验至少覆盖：

- Brief 字段完整性和范围。
- 阶段序号连续。
- 阶段周期之和等于总周期。
- AI 首次生成来源执行阶段数和每阶段题量上限；模板或由模板修订得到的来源不能误用 5 题限制。
- slug 存在且不重复。
- 题目事实来自受信题库。
- metadata 中不存在与最终内容冲突的旧派生值。
- 修订仍基于有效的 proposal group 和冻结基线，没有被更新请求取代。

## 8. 双处理器端到端 Demo

### 8.1 两个处理器的职责

本方案可以明确落成两个处理器：

```text
LearningPlanRevisionModelViewProjector
  输入：RevisionBaseSnapshot
  输出：LearningPlanRevisionModelView

LearningPlanRevisionCanonicalRestorer
  输入：RevisionBaseSnapshot + AiRevisionPatch + ProblemCatalog
  输出：resolvedBrief + canonical LearningPlanDraftPlan
```

第一个处理器负责“省略”，第二个处理器负责“合并和恢复”。二者的关键区别是：

- 投影器只生成给 AI 看的有损视图，不修改、也不删除冻结快照。
- 恢复器不能只拿 AI 看到的 JSON 反序列化，因为被省略的数据已经不在该 JSON 中。
- 恢复器必须根据受信 `revisionId` 重新读取同一次修订的 `RevisionBaseSnapshot`。
- AI 只接触模型视图和用户要求，不接触完整快照、数据库标识和恢复上下文。

完整调用关系如下：

```text
创建 revision 并冻结 baseBrief + basePlan
  -> 投影器生成精简模型视图
  -> AI 根据用户要求返回语义 Patch
  -> 恢复器重新读取同一 revision 的冻结快照
  -> 合并 Patch
  -> 从题库恢复题目事实
  -> 重建结构和 metadata
  -> 输出 canonical plan
```

### 8.2 Demo：处理前的冻结数据

假设用户当前有一个 2 周的栈专题草案。本次修订创建时冻结以下 `baseBrief`：

```json
{
  "intent": "TOPIC_BREAKTHROUGH",
  "objective": "掌握栈、表达式解析和单调栈的核心模式。",
  "durationWeeks": 2,
  "level": "INTERMEDIATE",
  "weeklyHours": 6,
  "programmingLanguage": "Java",
  "difficultyDistribution": {
    "easyPercent": 25,
    "mediumPercent": 65,
    "hardPercent": 10
  },
  "topicPreferences": [
    "Stack",
    "Monotonic Stack"
  ],
  "additionalConstraints": "优先覆盖高频面试题。",
  "personalizationEnabled": true,
  "contentLocale": "zh-CN"
}
```

同时冻结以下完整 `basePlan`：

```json
{
  "title": "栈与单调栈专项",
  "summary": "2 周完成基础栈、表达式和单调栈训练。",
  "intent": "TOPIC_BREAKTHROUGH",
  "objective": "掌握栈、表达式解析和单调栈的核心模式。",
  "durationWeeks": 2,
  "level": "INTERMEDIATE",
  "weeklyHours": 6,
  "programmingLanguage": "Java",
  "difficultyDistribution": {
    "easyPercent": 25,
    "mediumPercent": 65,
    "hardPercent": 10
  },
  "topicPreferences": [
    "Stack",
    "Monotonic Stack"
  ],
  "additionalConstraints": "优先覆盖高频面试题。",
  "phases": [
    {
      "phaseIndex": 1,
      "title": "基础栈",
      "durationWeeks": 1,
      "focus": "掌握括号匹配和辅助栈设计。",
      "problems": [
        {
          "slug": "valid-parentheses",
          "frontendId": 20,
          "title": "Valid Parentheses",
          "titleCn": "有效的括号",
          "difficulty": "EASY",
          "tags": ["STACK", "STRING"],
          "reason": "训练括号嵌套匹配和失配检测。",
          "sortOrder": 1
        },
        {
          "slug": "min-stack",
          "frontendId": 155,
          "title": "Min Stack",
          "titleCn": "最小栈",
          "difficulty": "MEDIUM",
          "tags": ["STACK", "DESIGN"],
          "reason": "训练主栈与辅助栈的同步维护。",
          "sortOrder": 2
        }
      ]
    },
    {
      "phaseIndex": 2,
      "title": "单调栈",
      "durationWeeks": 1,
      "focus": "掌握下一个更大元素和区间贡献计算。",
      "problems": [
        {
          "slug": "next-greater-element-i",
          "frontendId": 496,
          "title": "Next Greater Element I",
          "titleCn": "下一个更大元素 I",
          "difficulty": "EASY",
          "tags": ["STACK", "MONOTONIC_STACK"],
          "reason": "训练单调递减栈的基础模板。",
          "sortOrder": 1
        },
        {
          "slug": "trapping-rain-water",
          "frontendId": 42,
          "title": "Trapping Rain Water",
          "titleCn": "接雨水",
          "difficulty": "HARD",
          "tags": ["STACK", "TWO_POINTERS", "MONOTONIC_STACK"],
          "reason": "训练复杂边界下的区间蓄水计算。",
          "sortOrder": 2
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
      "durationWeeks": 2,
      "weeklyHours": 6,
      "weeklyCapacityPoints": 6.0,
      "totalCapacityPoints": 12.0,
      "plannedLoadPoints": 9.0,
      "loadRatio": 0.75,
      "plannedProblemCount": 4,
      "averageProblemsPerWeek": 2.0,
      "intensity": "RELAXED",
      "reviewBufferIncluded": true,
      "suggestions": [
        "当前节奏有复盘缓冲，可以稳定推进。"
      ]
    }
  }
}
```

用户本次要求为：

```text
每周只能学习 4 小时，删掉接雨水，第二阶段换成一道中等难度的经典单调栈题。
```

### 8.3 处理器一：投影前后

`LearningPlanRevisionModelViewProjector` 接收完整冻结快照，但只输出以下模型视图：

```json
{
  "constraints": {
    "intent": "TOPIC_BREAKTHROUGH",
    "objective": "掌握栈、表达式解析和单调栈的核心模式。",
    "durationWeeks": 2,
    "level": "INTERMEDIATE",
    "weeklyHours": 6,
    "programmingLanguage": "Java",
    "difficultyDistribution": {
      "easyPercent": 25,
      "mediumPercent": 65,
      "hardPercent": 10
    },
    "topicPreferences": [
      "Stack",
      "Monotonic Stack"
    ],
    "additionalConstraints": "优先覆盖高频面试题。"
  },
  "plan": {
    "title": "栈与单调栈专项",
    "summary": "2 周完成基础栈、表达式和单调栈训练。",
    "phases": [
      {
        "ref": "phase-1",
        "title": "基础栈",
        "focus": "掌握括号匹配和辅助栈设计。",
        "problems": [
          {
            "slug": "valid-parentheses",
            "displayTitle": "有效的括号",
            "difficulty": "EASY",
            "reason": "训练括号嵌套匹配和失配检测。"
          },
          {
            "slug": "min-stack",
            "displayTitle": "最小栈",
            "difficulty": "MEDIUM",
            "reason": "训练主栈与辅助栈的同步维护。"
          }
        ]
      },
      {
        "ref": "phase-2",
        "title": "单调栈",
        "focus": "掌握下一个更大元素和区间贡献计算。",
        "problems": [
          {
            "slug": "next-greater-element-i",
            "displayTitle": "下一个更大元素 I",
            "difficulty": "EASY",
            "reason": "训练单调递减栈的基础模板。"
          },
          {
            "slug": "trapping-rain-water",
            "displayTitle": "接雨水",
            "difficulty": "HARD",
            "reason": "训练复杂边界下的区间蓄水计算。"
          }
        ]
      }
    ]
  },
  "signals": {
    "loadIntensity": "RELAXED"
  }
}
```

投影器做了以下处理：

| 原始字段 | 投影结果 |
| --- | --- |
| `personalizationEnabled/contentLocale` | 完全省略，仍保存在冻结 `baseBrief` 中 |
| plan 中重复的 Brief 字段 | 从 plan 部分省略，只在 `constraints` 中保留一份 |
| canonical `phaseIndex` | 投影为 revision 内临时 `phase.ref`，只用于 Patch 定位 |
| `phase.durationWeeks` | 完全省略，恢复器从冻结基线保留或按总周期生成兼容值 |
| `frontendId/title/titleCn/tags/sortOrder` | 省略 |
| `titleCn` | 根据 `contentLocale=zh-CN` 投影为单一 `displayTitle` |
| `difficulty` | 保留为只读判断信号 |
| 完整 `metadata.loadSummary` | 只投影为 `loadIntensity` |
| 节奏和 coverage policy | 完全省略，仍保存在冻结 `basePlan` 中 |

这里没有生成一份“被删字段 Map”交给 AI，也没有尝试让模型视图可逆。完整数据始终保存在 `RevisionBaseSnapshot` 中。

### 8.4 AI 处理后的数据

AI 根据模型视图和用户要求返回语义 Patch。下面的字段名只用于说明本次数据变化，不在本文冻结最终 Patch Schema：

```json
{
  "briefPatch": {
    "weeklyHours": 4
  },
  "planPatch": {
    "summary": "2 周完成基础栈和经典单调栈训练，移除过重的困难题。",
    "phaseChanges": [
      {
        "phaseRef": "phase-2",
        "focus": "掌握下一个更大元素和单调栈的稳定出栈条件。",
        "problemChanges": [
          {
            "operation": "REMOVE",
            "slug": "trapping-rain-water"
          },
          {
            "operation": "ADD",
            "slug": "daily-temperatures",
            "reason": "训练下一个更大元素模型和单调栈出栈时机。",
            "afterSlug": "next-greater-element-i"
          }
        ]
      }
    ]
  }
}
```

AI 没有返回以下内容：

- 未修改的 Brief 字段。
- `personalizationEnabled` 和 `contentLocale`。
- 未修改的第一阶段。
- 第二阶段中保留题目的完整内容。
- 任何 canonical `phaseIndex` 或阶段级 `durationWeeks`。
- 新题目的 ID、标题、难度、标签和排序。
- 任何 metadata 或负载计算结果。

### 8.5 处理器二：如何恢复完整数据

`LearningPlanRevisionCanonicalRestorer` 不从上述 Patch 猜测缺失字段，而是重新读取同一个 revision 的冻结快照，然后按固定顺序处理。

#### 第一步：恢复 Brief

```text
baseBrief.weeklyHours = 6
aiBriefPatch.weeklyHours = 4

resolvedBrief.weeklyHours = 4
其他规划字段 = baseBrief 原值
personalizationEnabled = baseBrief.personalizationEnabled = true
contentLocale = baseBrief.contentLocale = zh-CN
```

处理后的 `resolvedBrief` 为：

```json
{
  "intent": "TOPIC_BREAKTHROUGH",
  "objective": "掌握栈、表达式解析和单调栈的核心模式。",
  "durationWeeks": 2,
  "level": "INTERMEDIATE",
  "weeklyHours": 4,
  "programmingLanguage": "Java",
  "difficultyDistribution": {
    "easyPercent": 25,
    "mediumPercent": 65,
    "hardPercent": 10
  },
  "topicPreferences": [
    "Stack",
    "Monotonic Stack"
  ],
  "additionalConstraints": "优先覆盖高频面试题。",
  "personalizationEnabled": true,
  "contentLocale": "zh-CN"
}
```

#### 第二步：恢复计划语义

恢复器以 `basePlan` 为底稿应用操作：

```text
plan.title
  Patch 未出现 -> 保留“栈与单调栈专项”

plan.summary
  Patch 出现 -> 使用 AI 新摘要

phase-1
  Patch 未出现 -> 整体保留

phase-2
  focus 出现 -> 使用 AI 新 focus
  REMOVE trapping-rain-water -> 明确删除
  next-greater-element-i 未删除 -> 保留原题和原 reason
  ADD daily-temperatures -> 插入到 next-greater-element-i 之后
```

此时得到的只是语义题目列表：

```json
[
  {
    "phaseRef": "phase-1",
    "problemSlugs": [
      "valid-parentheses",
      "min-stack"
    ]
  },
  {
    "phaseRef": "phase-2",
    "problemSlugs": [
      "next-greater-element-i",
      "daily-temperatures"
    ]
  }
]
```

#### 第三步：从题库水合题目事实

恢复器一次性查询四个最终 slug。题库返回：

```json
{
  "valid-parentheses": {
    "frontendId": 20,
    "title": "Valid Parentheses",
    "titleCn": "有效的括号",
    "difficulty": "EASY",
    "tags": ["STACK", "STRING"]
  },
  "min-stack": {
    "frontendId": 155,
    "title": "Min Stack",
    "titleCn": "最小栈",
    "difficulty": "MEDIUM",
    "tags": ["STACK", "DESIGN"]
  },
  "next-greater-element-i": {
    "frontendId": 496,
    "title": "Next Greater Element I",
    "titleCn": "下一个更大元素 I",
    "difficulty": "EASY",
    "tags": ["STACK", "MONOTONIC_STACK"]
  },
  "daily-temperatures": {
    "frontendId": 739,
    "title": "Daily Temperatures",
    "titleCn": "每日温度",
    "difficulty": "MEDIUM",
    "tags": ["STACK", "ARRAY", "MONOTONIC_STACK"]
  }
}
```

其中：

- 三道保留题目的 reason 从 `basePlan` 取得。
- 新增 `daily-temperatures` 的 reason 从 AI Patch 取得。
- 所有 ID、标题、难度和标签只使用题库返回值。
- 每阶段 `sortOrder` 根据最终列表重新从 1 编号。

#### 第四步：恢复 metadata 并重算负载

```text
contentLocale = baseBrief.contentLocale = zh-CN
personalizationEnabled = baseBrief.personalizationEnabled = true
dailyProblemCount = basePlan.metadata.dailyProblemCount = 2
trainingDaysPerWeek = basePlan.metadata.trainingDaysPerWeek = 5
coveragePolicy = basePlan.metadata.coveragePolicy = FIT_USER_BUDGET
loadSummary = 根据 resolvedBrief 和最终题目重新计算
```

本例中每周投入从 6 小时降为 4 小时，困难题被中等题替换。按照当前负载模型，重新计算结果为：

```json
{
  "durationWeeks": 2,
  "weeklyHours": 4,
  "weeklyCapacityPoints": 4.0,
  "totalCapacityPoints": 8.0,
  "plannedLoadPoints": 8.0,
  "loadRatio": 1.0,
  "plannedProblemCount": 4,
  "averageProblemsPerWeek": 2.0,
  "intensity": "RECOMMENDED",
  "reviewBufferIncluded": true,
  "suggestions": [
    "当前节奏合理，建议按周完成题目并保留复盘。"
  ]
}
```

原来的 `loadSummary` 不能复制，因为它基于旧的 `weeklyHours=6` 和旧题目集合计算。

### 8.6 处理器二的最终输出

恢复器最终组装出的 canonical `LearningPlanDraftPlan` 为：

```json
{
  "title": "栈与单调栈专项",
  "summary": "2 周完成基础栈和经典单调栈训练，移除过重的困难题。",
  "intent": "TOPIC_BREAKTHROUGH",
  "objective": "掌握栈、表达式解析和单调栈的核心模式。",
  "durationWeeks": 2,
  "level": "INTERMEDIATE",
  "weeklyHours": 4,
  "programmingLanguage": "Java",
  "difficultyDistribution": {
    "easyPercent": 25,
    "mediumPercent": 65,
    "hardPercent": 10
  },
  "topicPreferences": [
    "Stack",
    "Monotonic Stack"
  ],
  "additionalConstraints": "优先覆盖高频面试题。",
  "phases": [
    {
      "phaseIndex": 1,
      "title": "基础栈",
      "durationWeeks": 1,
      "focus": "掌握括号匹配和辅助栈设计。",
      "problems": [
        {
          "slug": "valid-parentheses",
          "frontendId": 20,
          "title": "Valid Parentheses",
          "titleCn": "有效的括号",
          "difficulty": "EASY",
          "tags": ["STACK", "STRING"],
          "reason": "训练括号嵌套匹配和失配检测。",
          "sortOrder": 1
        },
        {
          "slug": "min-stack",
          "frontendId": 155,
          "title": "Min Stack",
          "titleCn": "最小栈",
          "difficulty": "MEDIUM",
          "tags": ["STACK", "DESIGN"],
          "reason": "训练主栈与辅助栈的同步维护。",
          "sortOrder": 2
        }
      ]
    },
    {
      "phaseIndex": 2,
      "title": "单调栈",
      "durationWeeks": 1,
      "focus": "掌握下一个更大元素和单调栈的稳定出栈条件。",
      "problems": [
        {
          "slug": "next-greater-element-i",
          "frontendId": 496,
          "title": "Next Greater Element I",
          "titleCn": "下一个更大元素 I",
          "difficulty": "EASY",
          "tags": ["STACK", "MONOTONIC_STACK"],
          "reason": "训练单调递减栈的基础模板。",
          "sortOrder": 1
        },
        {
          "slug": "daily-temperatures",
          "frontendId": 739,
          "title": "Daily Temperatures",
          "titleCn": "每日温度",
          "difficulty": "MEDIUM",
          "tags": ["STACK", "ARRAY", "MONOTONIC_STACK"],
          "reason": "训练下一个更大元素模型和单调栈出栈时机。",
          "sortOrder": 2
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
      "durationWeeks": 2,
      "weeklyHours": 4,
      "weeklyCapacityPoints": 4.0,
      "totalCapacityPoints": 8.0,
      "plannedLoadPoints": 8.0,
      "loadRatio": 1.0,
      "plannedProblemCount": 4,
      "averageProblemsPerWeek": 2.0,
      "intensity": "RECOMMENDED",
      "reviewBufferIncluded": true,
      "suggestions": [
        "当前节奏合理，建议按周完成题目并保留复盘。"
      ]
    }
  }
}
```

恢复结果的字段来源可以逐项核对：

| 最终变化 | 来源 |
| --- | --- |
| `weeklyHours: 6 -> 4` | AI `briefPatch` |
| `personalizationEnabled=true` | 冻结 `baseBrief` |
| `contentLocale=zh-CN` | 冻结 `baseBrief` |
| 标题未变化 | 冻结 `basePlan` |
| 摘要和第二阶段 focus 变化 | AI `planPatch` |
| 第一阶段完整保留 | 冻结 `basePlan` |
| 删除 `trapping-rain-water` | AI 显式 REMOVE |
| 保留 `next-greater-element-i` 及原 reason | 冻结 `basePlan` |
| 新增题目的 slug 和 reason | AI 显式 ADD |
| 新增题目的 ID、标题、难度和标签 | 本地题库 |
| `phaseIndex/sortOrder` | 最终列表顺序重建；不采用 `phaseRef` 作为业务值 |
| `phase.durationWeeks` | 基线未变时保留；总周期或结构变化时由服务端生成兼容值 |
| 节奏配置和 coverage policy | 冻结 `basePlan.metadata` |
| 新 `loadSummary` | `LearningPlanLoadService` 重算 |

这个 Demo 中最重要的结论是：恢复器不是把 AI 输出“补几个字段”，而是以冻结 canonical 数据为底稿执行一次受控合并。AI Patch 只提供变化，最终完整对象始终由服务端重新组装。

## 9. 大计划输入策略与 Tool Demo

### 9.1 当前阶段的选择：服务端冻结全量，模型按需读取

“是否把基线计划全部给模型”需要拆成两个问题：

1. 服务端是否保存完整基线：必须保存。每次 revision 都冻结完整 `baseBrief + basePlan`，否则无法可靠合并和恢复省略字段。
2. 模型是否一次看到完整基线：不必固定为全量。应根据投影后的实际大小，在完整精简视图和摘要加 Tool 之间选择。

推荐当前阶段采用混合策略：

| 计划规模 | 首次输入给模型 | 查询 Tool | 编译 Tool |
| --- | --- | --- | --- |
| 小计划 | 完整 compact semantic view | 通常不需要，但可以保留 | 必须使用 |
| 大模板计划 | 约束、计划摘要、阶段摘要和统计 | 按需读取冻结计划 | 必须使用 |

这里的大小不应再由 AI 生成 Schema 的“最多 5 个阶段、每阶段最多 5 题”推断。该限制只约束 AI 首次生成内容；模板草案走 `validateTemplatePlan`，会保留全部本地匹配题。本文按当前已确认的 149 题运行态规模设计大计划 Demo。仓库 seed 报告中的 150 题和运行态 149 题属于来源题数与实际匹配题数口径，不影响结论：不能把 25 题当作修订基线的最大规模。

初版路由可以使用配置项而不是业务常量：

```text
projectedProblemCount <= maxInlineProblems
AND projectedPayloadTokens <= maxInlinePayloadTokens
  -> INLINE_FULL
otherwise
  -> SUMMARY_WITH_TOOLS
```

例如可以先把 `maxInlineProblems` 配为 30，再根据真实 token、延迟和修订成功率调整。30 只是输入路由阈值，不是计划题数上限，也不是领域校验规则。

无论走哪一种模式，模型都不读取 canonical 原对象，最终也都必须经过同一个编译入口。区别只在于模型第一次看到全部精简题目，还是先看到摘要再查询局部。

### 9.2 两个处理器和两个 Tool 的关系

处理器与 Tool 不应一一等同：

```text
RevisionBaseSnapshot
  -> LearningPlanRevisionModelViewProjector
       -> 小计划：完整 compact view
       -> 大计划：overview + phase summaries

AI revision agent
  -> query_learning_plan_revision      按需读冻结快照
  -> compile_learning_plan_revision    提交语义 Patch
       -> LearningPlanRevisionCanonicalRestorer
       -> ProblemCatalog
       -> LearningPlanLoadService
       -> LearningPlanDraftValidator
       -> revision proposed artifact
```

- `LearningPlanRevisionModelViewProjector` 不是 Tool。它在调用模型前由服务端运行，保证第一次输入已经受控。
- `LearningPlanRevisionCanonicalRestorer` 也不直接暴露给模型。它是编译 Tool 内部的确定性处理器。
- 模型只需要一个只读查询 Tool 和一个写入临时提案的编译 Tool。
- 编译 Tool 只生成本次 revision 的 proposed artifact，不直接确认正式计划，也不绕过 proposal group 和并发版本检查。

当前不建议把 `list_problem_filters`、全库 `search_problems`、`get_problem_statement` 或通用 `read_tool_result` 暴露给修订 Agent。修订任务首先要理解当前计划，而不是自由浏览整个题库；新增题目的候选检索可以由编译器根据语义选择条件在服务端完成，减少模型自己拼 slug 和处理分页结果的负担。

### 9.3 Tool 一：`query_learning_plan_revision`

这是只读 Tool，只能查询当前 Agent run 允许访问的不可变基线。`revisionId`、`draftId`、`userId` 和 snapshot version 由服务端 Tool context 注入，不让模型作为参数传入，避免跨草稿或跨 revision 读取。

草稿第一次形成完整计划时，服务端额外冻结 `originBrief + originPlan`。这个冻结点位于统一的草稿持久化边界，因此模板创建和 AI 创建的第一版草稿走同一套逻辑，不需要模板专用 Tool。

Tool 提供三个受信基线：

| baseline | 含义 | 典型用户表达 |
| --- | --- | --- |
| `CURRENT_REVISION` | 本次修订开始时冻结的当前草稿，默认值 | “删除 Hard”“调整第二阶段” |
| `ORIGINAL_DRAFT` | 草稿第一次形成完整计划时冻结的版本，来源无关 | “恢复原样”“恢复第一版”“恢复最原始计划” |
| `PREVIOUS_REVISION` | 上一次修订开始前的草稿 | “撤销上次修订”“回到上次修改前” |

不提供 `CURRENT_TEMPLATE`。模板库可能在草稿创建后更新，用模板当前版本恢复会产生版本漂移；`ORIGINAL_DRAFT` 才是用户实际拿到的精确第一版。AI 首版草稿也天然具备同样的恢复能力。

提供三个 operation：

| operation | 能力 | 典型用途 |
| --- | --- | --- |
| `READ_BASELINE_OPTIONS` | 查看三个受信基线是否可用 | 恢复或撤销前确定语义和可用性 |
| `READ_PHASE` | 按 `phaseRef` 分页读取阶段和题目语义 | 用户要求调整某个阶段，需要查看局部顺序和 reason |
| `FIND_PROBLEMS` | 在当前冻结计划内按阶段、slug、难度或关键词筛选 | “删除所有 Hard”“把包含 DP 的题找出来” |

统一约束如下：

- 查询范围永远是本次 revision 允许的受信基线，不是数据库中最新的可变草案，也不是当前模板库。
- `phaseRef` 只在当前 revision 的所选基线内有效。
- `cursor` 是服务端生成的不透明游标，并绑定当前 revision ID、所选 baseline 和筛选条件。
- `limit` 建议限制在 1 到 30；返回值始终有界。
- 只返回 compact semantic fields，不返回 canonical 题目事实全集、metadata 或数据库标识。
- `FIND_PROBLEMS` 不是全题库搜索，不能用它发现计划外候选题。

#### `READ_BASELINE_OPTIONS` Demo

模型请求不包含任何业务 ID：

```json
{
  "operation": "READ_BASELINE_OPTIONS",
  "baseline": null,
  "phaseRef": null,
  "phaseRefs": [],
  "slugs": [],
  "difficulty": null,
  "keyword": null,
  "cursor": null,
  "limit": null
}
```

服务端根据当前受信 revision 返回：

```json
{
  "status": "OK",
  "snapshotState": "FROZEN",
  "currentBaseline": "CURRENT_REVISION",
  "baselines": [
    {"baseline": "CURRENT_REVISION", "available": true},
    {"baseline": "ORIGINAL_DRAFT", "available": true},
    {"baseline": "PREVIOUS_REVISION", "available": true}
  ]
}
```

#### `READ_PHASE` 请求结构

```json
{
  "operation": "READ_PHASE",
  "baseline": "ORIGINAL_DRAFT",
  "phaseRef": "phase-1",
  "cursor": null,
  "limit": 15
}
```

返回结构：

```json
{
  "snapshotState": "FROZEN",
  "baseline": "ORIGINAL_DRAFT",
  "phase": {
    "phaseRef": "phase-1",
    "title": "数组与字符串",
    "focus": "完成数组与字符串主线，稳定原地修改、模拟和边界处理。",
    "problemCount": 23,
    "difficultyCounts": {
      "easy": 9,
      "medium": 11,
      "hard": 3
    }
  },
  "problems": [],
  "nextCursor": null
}
```

`problems` 中每一项只包含：

```json
{
  "slug": "trapping-rain-water",
  "displayTitle": "接雨水",
  "difficulty": "HARD",
  "reason": "训练复杂边界下的区间蓄水计算。",
  "position": 15
}
```

`position` 是冻结阶段中的只读位置，帮助模型理解相邻题；模型修改时仍使用 `slug` 和显式 `beforeSlug/afterSlug`，不直接写 canonical `sortOrder`。

#### `FIND_PROBLEMS` 请求结构

```json
{
  "operation": "FIND_PROBLEMS",
  "baseline": "CURRENT_REVISION",
  "phaseRefs": [],
  "slugs": [],
  "difficulty": "HARD",
  "keyword": null,
  "cursor": null,
  "limit": 30
}
```

空的 `phaseRefs` 表示全部阶段；空的 `slugs` 表示不按 slug 限制。筛选条件只作用于冻结计划。

### 9.4 Tool 二：`compile_learning_plan_revision`

这是修订的唯一写 Tool。它接收 `baseline + 语义 Patch`，不接受完整 canonical plan，并在服务端完成：

1. 校验 Agent run 与冻结 snapshot 仍然有效。
2. 从当前受信 revision 解析 `CURRENT_REVISION`、`ORIGINAL_DRAFT` 或 `PREVIOUS_REVISION`，模型不能传业务 ID。
3. 将 `briefPatch` 合并到所选基线的 Brief。
4. 在所选基线的 Plan 上执行阶段和题目操作。
5. 对 replacement selection 做内部题库候选检索和确定性选择。
6. 批量水合最终 slug 的题目事实。
7. 重建 `phaseIndex`、`sortOrder` 和兼容阶段周期。
8. 恢复控制字段、节奏配置与来源 provenance，失效旧内容断言。
9. 重算负载并执行对应来源类型的计划校验。
10. 保存有版本约束的 revision proposed artifact。
11. 返回有界的 `PASS` 或 `NEEDS_REVISION` 诊断。

初版语义操作能力建议限制为：

| Patch 区域 | 支持的能力 |
| --- | --- |
| `briefPatch` | 只修改用户明确要求变化的 Brief 规划字段 |
| `planPatch` | 修改 `title`、`summary` |
| `phaseChanges` | 更新阶段 `title/focus`，以及显式新增、删除、移动阶段 |
| `problemChanges` | `ADD`、`REMOVE`、`MOVE`、`REPLACE`，目标题使用 slug 定位 |
| 新题选择 | 已知题目时给精确 `slug + reason`；未知题目时给 `replacementSelection` |

`replacementSelection` 不是开放式题库查询。它只描述难度、主题提示和学习目标；编译器使用固定排序、排除当前计划重复题，并把选择结果与 reason 一起返回。候选不足时返回 `NEEDS_REVISION`，不能偷偷放宽用户指定的难度。

成功响应不需要把 149 道完整 canonical 题目再次塞回模型上下文，只返回变化摘要、编译统计、关键选择结果和 artifact 标识。完整计划由服务端保存，供最终 revision 结果接口读取。

#### 恢复 Demo：18 题删到 4 题后恢复第一版

当前 revision 基线只有 4 道 Easy，原始草稿快照仍有 18 题。模型不需要逐题重新 ADD，只提交正确基线和空 Patch：

```json
{
  "baseline": "ORIGINAL_DRAFT",
  "briefPatch": {},
  "planPatch": {
    "title": null,
    "summary": null,
    "phaseChanges": []
  }
}
```

编译器以原始 18 题快照组装 canonical 结果，但变化统计以当前 4 题 revision 为 before：

```json
{
  "status": "PASS",
  "artifactRef": "draft-revision:41:compiled",
  "baseline": "ORIGINAL_DRAFT",
  "changed": true,
  "changeSummary": {
    "problemCountBefore": 4,
    "problemCountAfter": 18,
    "difficultyCountsBefore": {"easy": 4, "medium": 0, "hard": 0},
    "difficultyCountsAfter": {"easy": 4, "medium": 13, "hard": 1}
  }
}
```

这套调用对模板第一版和 AI 第一版完全一致。区别只存在于冻结快照内的 provenance metadata，不存在于 Tool 契约和恢复流程中。

失败时也不抛给模型一段后端异常堆栈。例如：

```json
{
  "status": "NEEDS_REVISION",
  "diagnostics": [
    {
      "code": "NO_MATCHING_REPLACEMENT",
      "path": "planPatch.phaseChanges[0].problemChanges[1]",
      "message": "没有找到满足当前阶段主题、MEDIUM 难度且未在计划中出现的题目。",
      "retryHint": {
        "relax": [
          "topicHints"
        ],
        "keep": [
          "difficulty"
        ]
      }
    }
  ]
}
```

Agent 可以根据该诊断缩小改动、放宽选择条件或向用户解释无法满足；不能绕过编译器直接构造 proposed plan。

### 9.5 149 题 Demo：模型第一次看到什么

假设 revision 冻结的实际草案有 149 题、10 个阶段。用户要求为：

```text
先检查整套计划里的困难题。本次只把第一阶段的 3 道 Hard 换成同主题的 Medium，题量保持 149，其他阶段和学习约束都不动。
```

服务端仍保存全部 149 题的 `RevisionBaseSnapshot`，但投影器选择 `SUMMARY_WITH_TOOLS`，第一次只给模型以下内容：

```json
{
  "projectionMode": "SUMMARY_WITH_TOOLS",
  "constraints": {
    "intent": "INTERVIEW_SPRINT",
    "objective": "系统完成算法面试高频题并形成复盘清单。",
    "durationWeeks": 10,
    "level": "INTERMEDIATE",
    "weeklyHours": 12,
    "programmingLanguage": "Java",
    "difficultyDistribution": {
      "easyPercent": 26,
      "mediumPercent": 62,
      "hardPercent": 12
    },
    "topicPreferences": [],
    "additionalConstraints": null
  },
  "plan": {
    "title": "面试经典 149 题计划",
    "summary": "按高频主题完成一轮完整算法面试训练。",
    "problemCount": 149,
    "difficultyCounts": {
      "easy": 39,
      "medium": 92,
      "hard": 18
    },
    "phases": [
      {
        "ref": "phase-1",
        "title": "数组与字符串",
        "focus": "完成数组与字符串主线，稳定原地修改、模拟和边界处理。",
        "problemCount": 23,
        "difficultyCounts": {"easy": 9, "medium": 11, "hard": 3}
      },
      {
        "ref": "phase-2",
        "title": "双指针、窗口与矩阵",
        "focus": "训练双指针、滑动窗口和二维矩阵中的状态维护。",
        "problemCount": 14,
        "difficultyCounts": {"easy": 2, "medium": 10, "hard": 2}
      },
      {
        "ref": "phase-3",
        "title": "哈希、区间与栈",
        "focus": "覆盖映射计数、区间操作和栈结构的高频面试模型。",
        "problemCount": 18,
        "difficultyCounts": {"easy": 9, "medium": 8, "hard": 1}
      },
      {
        "ref": "phase-4",
        "title": "链表",
        "focus": "系统完成链表改写、环、复制、排序和缓存设计。",
        "problemCount": 11,
        "difficultyCounts": {"easy": 2, "medium": 8, "hard": 1}
      },
      {
        "ref": "phase-5",
        "title": "二叉树与搜索树",
        "focus": "训练树的递归、层序遍历和二叉搜索树性质。",
        "problemCount": 21,
        "difficultyCounts": {"easy": 7, "medium": 13, "hard": 1}
      },
      {
        "ref": "phase-6",
        "title": "图与 Trie",
        "focus": "完成图遍历、拓扑关系、最短路径前置和前缀树。",
        "problemCount": 12,
        "difficultyCounts": {"easy": 0, "medium": 10, "hard": 2}
      },
      {
        "ref": "phase-7",
        "title": "回溯与分治",
        "focus": "训练选择树、剪枝、递归分解和归并式问题求解。",
        "problemCount": 11,
        "difficultyCounts": {"easy": 1, "medium": 8, "hard": 2}
      },
      {
        "ref": "phase-8",
        "title": "Kadane、二分与堆",
        "focus": "用连续状态、边界搜索和优先队列处理高频综合题。",
        "problemCount": 13,
        "difficultyCounts": {"easy": 1, "medium": 9, "hard": 3}
      },
      {
        "ref": "phase-9",
        "title": "位运算与数学",
        "focus": "补齐位操作、数值规律、几何和进制相关面试题。",
        "problemCount": 12,
        "difficultyCounts": {"easy": 7, "medium": 4, "hard": 1}
      },
      {
        "ref": "phase-10",
        "title": "动态规划综合收尾",
        "focus": "完成一维和多维动态规划，并进行整套路线复盘。",
        "problemCount": 14,
        "difficultyCounts": {"easy": 1, "medium": 11, "hard": 2}
      }
    ]
  },
  "signals": {
    "loadIntensity": "OVERLOADED"
  },
  "availableTools": [
    "query_learning_plan_revision",
    "compile_learning_plan_revision"
  ]
}
```

这里有意不包含 149 个 `problems`。模型已经知道全局难度结构、每阶段规模和第一阶段确实有 3 道 Hard，足以决定先查询哪些数据。

### 9.6 149 题 Demo：查询所有 Hard

模型先执行：

```json
{
  "operation": "FIND_PROBLEMS",
  "phaseRefs": [],
  "slugs": [],
  "difficulty": "HARD",
  "keyword": null,
  "cursor": null,
  "limit": 30
}
```

Tool 在冻结计划中查到 18 道 Hard：

```json
{
  "snapshotState": "FROZEN",
  "matchCount": 18,
  "problems": [
    {"phaseRef": "phase-1", "slug": "candy", "displayTitle": "分发糖果", "difficulty": "HARD"},
    {"phaseRef": "phase-1", "slug": "trapping-rain-water", "displayTitle": "接雨水", "difficulty": "HARD"},
    {"phaseRef": "phase-1", "slug": "text-justification", "displayTitle": "文本左右对齐", "difficulty": "HARD"},
    {"phaseRef": "phase-2", "slug": "substring-with-concatenation-of-all-words", "displayTitle": "串联所有单词的子串", "difficulty": "HARD"},
    {"phaseRef": "phase-2", "slug": "minimum-window-substring", "displayTitle": "最小覆盖子串", "difficulty": "HARD"},
    {"phaseRef": "phase-3", "slug": "basic-calculator", "displayTitle": "基本计算器", "difficulty": "HARD"},
    {"phaseRef": "phase-4", "slug": "reverse-nodes-in-k-group", "displayTitle": "K 个一组翻转链表", "difficulty": "HARD"},
    {"phaseRef": "phase-5", "slug": "binary-tree-maximum-path-sum", "displayTitle": "二叉树中的最大路径和", "difficulty": "HARD"},
    {"phaseRef": "phase-6", "slug": "word-ladder", "displayTitle": "单词接龙", "difficulty": "HARD"},
    {"phaseRef": "phase-6", "slug": "word-search-ii", "displayTitle": "单词搜索 II", "difficulty": "HARD"},
    {"phaseRef": "phase-7", "slug": "n-queens-ii", "displayTitle": "N 皇后 II", "difficulty": "HARD"},
    {"phaseRef": "phase-7", "slug": "merge-k-sorted-lists", "displayTitle": "合并 K 个升序链表", "difficulty": "HARD"},
    {"phaseRef": "phase-8", "slug": "median-of-two-sorted-arrays", "displayTitle": "寻找两个正序数组的中位数", "difficulty": "HARD"},
    {"phaseRef": "phase-8", "slug": "ipo", "displayTitle": "IPO", "difficulty": "HARD"},
    {"phaseRef": "phase-8", "slug": "find-median-from-data-stream", "displayTitle": "数据流的中位数", "difficulty": "HARD"},
    {"phaseRef": "phase-9", "slug": "max-points-on-a-line", "displayTitle": "直线上最多的点数", "difficulty": "HARD"},
    {"phaseRef": "phase-10", "slug": "best-time-to-buy-and-sell-stock-iii", "displayTitle": "买卖股票的最佳时机 III", "difficulty": "HARD"},
    {"phaseRef": "phase-10", "slug": "best-time-to-buy-and-sell-stock-iv", "displayTitle": "买卖股票的最佳时机 IV", "difficulty": "HARD"}
  ],
  "nextCursor": null
}
```

模型从该结果确认：全计划有 18 道 Hard，但用户只授权修改 `phase-1` 中的 3 道，其他 15 道不能顺手删除。

### 9.7 149 题 Demo：分页读取第一阶段

为了理解第一阶段的顺序和每道题的训练作用，模型再调用：

```json
{
  "operation": "READ_PHASE",
  "phaseRef": "phase-1",
  "cursor": null,
  "limit": 15
}
```

第一屏响应：

```json
{
  "snapshotState": "FROZEN",
  "phase": {
    "phaseRef": "phase-1",
    "title": "数组与字符串",
    "focus": "完成数组与字符串主线，稳定原地修改、模拟和边界处理。",
    "problemCount": 23,
    "difficultyCounts": {"easy": 9, "medium": 11, "hard": 3}
  },
  "problems": [
    {"position": 1, "slug": "merge-sorted-array", "displayTitle": "合并两个有序数组", "difficulty": "EASY", "reason": "训练原地合并和逆向双指针。"},
    {"position": 2, "slug": "remove-element", "displayTitle": "移除元素", "difficulty": "EASY", "reason": "训练原地覆盖和有效区间维护。"},
    {"position": 3, "slug": "remove-duplicates-from-sorted-array", "displayTitle": "删除有序数组中的重复项", "difficulty": "EASY", "reason": "训练有序数组的快慢指针。"},
    {"position": 4, "slug": "remove-duplicates-from-sorted-array-ii", "displayTitle": "删除有序数组中的重复项 II", "difficulty": "MEDIUM", "reason": "训练带保留次数约束的原地写入。"},
    {"position": 5, "slug": "majority-element", "displayTitle": "多数元素", "difficulty": "EASY", "reason": "训练计数抵消和候选维护。"},
    {"position": 6, "slug": "rotate-array", "displayTitle": "轮转数组", "difficulty": "MEDIUM", "reason": "训练数组变换和空间优化。"},
    {"position": 7, "slug": "best-time-to-buy-and-sell-stock", "displayTitle": "买卖股票的最佳时机", "difficulty": "EASY", "reason": "训练单次遍历中的最优前缀。"},
    {"position": 8, "slug": "best-time-to-buy-and-sell-stock-ii", "displayTitle": "买卖股票的最佳时机 II", "difficulty": "MEDIUM", "reason": "训练贪心累计局部收益。"},
    {"position": 9, "slug": "jump-game", "displayTitle": "跳跃游戏", "difficulty": "MEDIUM", "reason": "训练最远可达边界。"},
    {"position": 10, "slug": "jump-game-ii", "displayTitle": "跳跃游戏 II", "difficulty": "MEDIUM", "reason": "训练分层贪心和最少跳跃次数。"},
    {"position": 11, "slug": "insert-delete-getrandom-o1", "displayTitle": "O(1) 时间插入、删除和获取随机元素", "difficulty": "MEDIUM", "reason": "训练数组与哈希表的协同设计。"},
    {"position": 12, "slug": "product-of-array-except-self", "displayTitle": "除自身以外数组的乘积", "difficulty": "MEDIUM", "reason": "训练前后缀积和常数额外空间。"},
    {"position": 13, "slug": "gas-station", "displayTitle": "加油站", "difficulty": "MEDIUM", "reason": "训练全局可行性和起点选择。"},
    {"position": 14, "slug": "candy", "displayTitle": "分发糖果", "difficulty": "HARD", "reason": "训练双向约束下的贪心修正。"},
    {"position": 15, "slug": "trapping-rain-water", "displayTitle": "接雨水", "difficulty": "HARD", "reason": "训练复杂边界下的区间蓄水计算。"}
  ],
  "nextCursor": "phase-1:after:15:snapshot-v7"
}
```

模型继续使用 Tool 返回的游标，而不是自己计算 offset：

```json
{
  "operation": "READ_PHASE",
  "phaseRef": "phase-1",
  "cursor": "phase-1:after:15:snapshot-v7",
  "limit": 15
}
```

第二屏响应：

```json
{
  "snapshotState": "FROZEN",
  "phase": {
    "phaseRef": "phase-1",
    "title": "数组与字符串",
    "problemCount": 23
  },
  "problems": [
    {"position": 16, "slug": "roman-to-integer", "displayTitle": "罗马数字转整数", "difficulty": "EASY", "reason": "训练字符串规则扫描。"},
    {"position": 17, "slug": "integer-to-roman", "displayTitle": "整数转罗马数字", "difficulty": "MEDIUM", "reason": "训练有序规则的贪心编码。"},
    {"position": 18, "slug": "length-of-last-word", "displayTitle": "最后一个单词的长度", "difficulty": "EASY", "reason": "训练字符串边界扫描。"},
    {"position": 19, "slug": "longest-common-prefix", "displayTitle": "最长公共前缀", "difficulty": "EASY", "reason": "训练多字符串逐列比较。"},
    {"position": 20, "slug": "reverse-words-in-a-string", "displayTitle": "反转字符串中的单词", "difficulty": "MEDIUM", "reason": "训练切分、清理和顺序重组。"},
    {"position": 21, "slug": "zigzag-conversion", "displayTitle": "Z 字形变换", "difficulty": "MEDIUM", "reason": "训练字符串模拟和方向切换。"},
    {"position": 22, "slug": "find-the-index-of-the-first-occurrence-in-a-string", "displayTitle": "找出字符串中第一个匹配项的下标", "difficulty": "EASY", "reason": "训练基础字符串匹配。"},
    {"position": 23, "slug": "text-justification", "displayTitle": "文本左右对齐", "difficulty": "HARD", "reason": "训练复杂字符串布局和边界分配。"}
  ],
  "nextCursor": null
}
```

到这里模型已经看到了完成本次修改所需的全部局部事实，无需读取其他 126 道题的 reason 和顺序。

### 9.8 149 题 Demo：提交语义 Patch

模型不需要自己知道替代题目的精确 slug。它把“选择什么题”的语义约束交给编译器：

```json
{
  "briefPatch": {},
  "planPatch": {
    "phaseChanges": [
      {
        "phaseRef": "phase-1",
        "problemChanges": [
          {
            "operation": "REPLACE",
            "slug": "candy",
            "replacementSelection": {
              "difficulty": "MEDIUM",
              "topicHints": ["ARRAY", "GREEDY"],
              "learningGoal": "保留贪心决策训练，但降低双向约束复杂度"
            }
          },
          {
            "operation": "REPLACE",
            "slug": "trapping-rain-water",
            "replacementSelection": {
              "difficulty": "MEDIUM",
              "topicHints": ["ARRAY", "TWO_POINTERS", "MONOTONIC_STACK"],
              "learningGoal": "保留边界维护训练，但降低综合推导难度"
            }
          },
          {
            "operation": "REPLACE",
            "slug": "text-justification",
            "replacementSelection": {
              "difficulty": "MEDIUM",
              "topicHints": ["STRING", "SIMULATION"],
              "learningGoal": "保留字符串布局与模拟训练，但减少极端边界数量"
            }
          }
        ]
      }
    ]
  }
}
```

`compile_learning_plan_revision` 内部完成候选检索。假设它从本地题库选择了三道不在当前计划中的题：

```json
{
  "status": "PASS",
  "artifactRef": "revision-artifact-7-1",
  "baseline": "CURRENT_REVISION",
  "changed": true,
  "changeSummary": {
    "briefChanged": false,
    "changedPhaseRefs": ["phase-1"],
    "problemCountBefore": 149,
    "problemCountAfter": 149,
    "difficultyCountsBefore": {"easy": 39, "medium": 92, "hard": 18},
    "difficultyCountsAfter": {"easy": 39, "medium": 95, "hard": 15}
  },
  "replacementResolutions": [
    {
      "removedSlug": "candy",
      "selectedSlug": "partition-labels",
      "displayTitle": "划分字母区间",
      "difficulty": "MEDIUM",
      "reason": "保留贪心边界决策训练，同时降低双向约束复杂度。"
    },
    {
      "removedSlug": "trapping-rain-water",
      "selectedSlug": "daily-temperatures",
      "displayTitle": "每日温度",
      "difficulty": "MEDIUM",
      "reason": "通过单调栈继续训练边界维护和出栈时机。"
    },
    {
      "removedSlug": "text-justification",
      "selectedSlug": "string-compression",
      "displayTitle": "压缩字符串",
      "difficulty": "MEDIUM",
      "reason": "保留字符串原地模拟和分组边界处理。"
    }
  ],
  "restorationSummary": {
    "preservedBriefFields": 11,
    "preservedUnchangedPhases": 9,
    "hydratedProblemCount": 149,
    "rebuiltPhaseIndexes": 10,
    "rebuiltSortOrders": 149,
    "compatibilityPhaseDurationsRestored": true,
    "metadataRecalculated": ["loadSummary"],
    "metadataPreserved": ["contentLocale", "personalizationEnabled", "dailyProblemCount", "trainingDaysPerWeek", "coveragePolicy"],
    "metadataInvalidated": ["template.matchedProblemCount"]
  }
}
```

这个响应足够模型确认结果，但不会再次传回 149 道完整题目。服务端保存的 artifact 内部已经是完整 canonical plan：

```text
冻结 baseBrief + basePlan
  -> 应用 3 个 REPLACE
  -> 为 selection 选择 3 个本地题库 slug
  -> 水合最终 149 道题的 frontendId/title/titleCn/difficulty/tags
  -> phaseIndex 重新编号 1..10
  -> 每阶段 sortOrder 重新编号
  -> phase.durationWeeks 从基线恢复兼容值
  -> metadata 按保留、重算、失效规则处理
  -> validateTemplatePlan 或来源转换后的目标校验
  -> 保存 revision proposed artifact
```

模型拿到 `PASS` 后只需要向用户说明三道替换题和难度变化；不需要再调用查询 Tool 验证编译器刚刚生成的完整对象。若用户继续提出修改，应创建新 Patch 或进入下一次 revision，而不是把 artifact 当作新的自由文本上下文。

### 9.9 为什么当前只需要这两个 Tool

| 能力 | 是否作为 Agent Tool | 原因 |
| --- | --- | --- |
| 投影初始模型视图 | 否 | 模型调用前必须完成，不能由模型决定自己看到什么 |
| 查询当前计划阶段 | 是 | 大计划需要按需读取局部题目和顺序 |
| 在当前计划中筛选题目 | 是 | 支持跨阶段的难度、slug 和关键词修订 |
| 搜索整个题库 | 否，编译器内部能力 | 避免模型处理大量候选和编造题目事实 |
| 获取完整题面 | 否 | 计划修订依赖训练定位，不需要题面全文 |
| 合并并恢复 canonical plan | 是，通过 compile | 这是唯一允许产出 revision artifact 的入口 |
| Review 编译结果 | 暂不提供 | 先依靠确定性校验；质量 Review 可以后续独立加入 |
| 确认正式计划 | 否 | 属于用户确认和业务状态流转，不应由修订 Agent 自动执行 |

因此当前最小闭环就是：初始投影、按需查询、一次或多次编译重试、输出 proposed artifact。继续增加 Tool 之前，应先用短计划和 149 题计划分别验证 token、Tool 调用次数、编译修复率和最终修订正确性。

## 10. 字段来源总表

| 最终字段 | AI 读取 | AI 修改 | 最终来源 |
| --- | --- | --- | --- |
| Brief 规划字段 | 是 | 用户明确要求时 | `baseBrief + aiBriefPatch` |
| `personalizationEnabled` | 否 | 否 | `baseBrief` |
| `contentLocale` | 否 | 否 | `baseBrief` |
| `plan.title/summary` | 是 | 是 | `basePlan + aiPlanPatch` |
| `phase.title/focus` | 是 | 是 | `basePlan + aiPlanPatch` |
| `phase.ref` | 是，只作定位 | Patch 中引用 | revision 冻结快照，不进入最终计划 |
| `phase.durationWeeks` | 否 | 否 | 保留原值或服务端兼容布局策略 |
| `phaseIndex` | 否 | 否 | 最终阶段顺序 |
| `problem.slug` | 是 | 是 | AI 精确指定，或编译器根据 `replacementSelection` 解析，经题库校验 |
| `problem.reason` | 是 | 是 | 原 reason 或 AI 新 reason |
| 单语言题目标题 | 是，只读 | 否 | 仅用于输入投影 |
| `frontendId/title/titleCn/difficulty/tags` | difficulty 和单语言标题只读可见 | 否 | 本地题库 |
| `sortOrder` | 否 | 否 | 最终题目顺序 |
| 节奏 metadata | 否 | 否 | `basePlan.metadata` |
| `loadSummary` | 只投影 `loadIntensity` | 否 | 领域服务重算 |
| 模板来源 provenance | 否 | 否 | 基线来源转换规则 |
| 运行和数据库标识 | 否 | 否 | 服务端运行上下文 |

## 11. 当前结论

草案修订的核心数据契约应固定为：

1. `baseBrief + basePlan` 是不可变的当前修订基线；草稿第一次完整落库时还要冻结来源无关的 `originBrief + originPlan`。
2. AI 读取精简语义视图，不再读取完整 canonical plan。
3. AI 读取和写回非对称：题目可读单语言标题与 difficulty，但只写 `slug + reason`。
4. 未修改字段由服务端从冻结基线合并，不能依赖模型复制。
5. 省略字段按“保留、水合、重算、失效”四种规则处理，不能统一复制 metadata。
6. 最终 `LearningPlanDraftPlan` 只能由服务端组装和校验，AI 输出永远不是 canonical 数据源。
7. 小计划可以直接内联完整 compact view；149 题一类大计划只内联摘要，题目详情通过冻结快照查询 Tool 按需读取。
8. 当前修订 Agent 只需要 `query_learning_plan_revision` 和 `compile_learning_plan_revision` 两个 Tool。
9. 查询 Tool 只读本次 revision 允许的 `CURRENT_REVISION`、`ORIGINAL_DRAFT`、`PREVIOUS_REVISION`；编译 Tool 是 canonical 恢复、校验和 proposed artifact 保存的唯一入口。
10. 阶段级 `phaseIndex` 和 `durationWeeks` 不交给模型；查询与 Patch 使用临时 `phaseRef`，canonical 兼容字段由编译器恢复。
11. 第一版恢复能力与草稿来源无关；模板草稿和 AI 草稿使用相同的 `ORIGINAL_DRAFT`，不提供会发生版本漂移的模板当前版本读取能力。

当前实现已经冻结两个 Tool 的基础请求/响应 Schema 和来源无关的基线选择。后续仍需用真实 provider 请求持续验证 baseline 选择准确率、replacement selection 的候选排序，以及模板来源修订后的校验策略；Review Tool 可以等最小闭环数据稳定后再决定。
