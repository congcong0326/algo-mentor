# 学习计划 AI 提案设计

## 背景

当前学习计划有两个明显阶段：

1. 用户确认前，`POST /api/learning-plans/drafts/stream` 生成 `learning_plan_draft` 草案，草案内容保存在 `draft_plan_json`。
2. 用户确认后，`LearningPlanDraftService.confirmDraft(...)` 把草案保存为正式 `learning_plan`，并同步写入 `learning_plan_phase`、`learning_plan_recommended_problem`。

确认前草案还没有训练进度，可以允许 AI 按用户反馈整体改写。确认后的正式计划已经承载练习会话和题目进度，进度存储在 `learning_plan_problem_progress`，不能像草案一样整体替换，否则会破坏用户已完成题目、训练会话和历史记录。

因此需要把“确认前迭代草案”和“确认后扩展计划”都设计成 AI 辅助提案，但二者必须有不同的业务约束。

## 目标

- 支持用户在草案生成后输入自然语言要求，生成新的草案版本，再决定确认哪个版本。
- 支持正式计划在学习过程中按用户意愿和完成情况继续扩展，只追加新阶段和新题目。
- 复用现有 `AgentLoopRunner`、题库工具、结构化输出、SSE 工作状态和本地校验能力。
- 保证正式计划扩展不删除、不替换、不重排已有阶段和题目，不破坏已有进度。
- 保留提案历史，方便后续支持回看、放弃、重新生成和审计。

## 非目标

- 不支持正式计划删除已有阶段或已有题目。
- 不支持正式计划直接整体重写 `plan_json` 后覆盖已有详情表。
- 不让模型直接写库；模型只生成结构化提案，业务层校验后落库。
- 第一阶段不做复杂 diff 编辑器，先支持自然语言输入、流式生成、预览、接受或放弃。

## 核心概念

建议引入统一概念：`LearningPlanProposal`。

```text
LearningPlanProposal
├── Draft Revision：确认前草案版本迭代，可整体替换草案内容
└── Plan Extension：确认后正式计划扩展，只能追加新阶段和新题目
```

两者共享：

- 用户自然语言指令。
- 基于现有计划上下文的 Agent prompt。
- provider-native JSON Schema 结构化输出。
- 题库工具调用和本地 slug 校验。
- SSE 工作状态。
- 提案记录、状态流转和最终应用动作。

两者分开：

- API 路径分开。
- 结构化输出 schema 分开。
- 业务校验器分开。
- 落库动作分开。
- 前端结果预览分开展示语义。

## 草案版本迭代

### 用户体验

草案生成完成后，在“确认保存当前计划”按钮上方增加自然语言调整输入框：

```text
对当前计划不满意？输入调整要求，例如：
减少动态规划题，增加数组和哈希表题，整体难度降低一些。

[按要求调整计划] [确认保存当前计划]
```

用户满意时直接确认。用户不满意时输入要求并点击“按要求调整计划”，页面进入流式调整状态，完成后替换为新的草案预览。

### API

```http
POST /api/learning-plans/drafts/{draftId}/revisions/stream
Accept: text/event-stream
Content-Type: application/json

{
  "instruction": "减少动态规划题，增加数组和哈希表题，整体难度降低一些"
}
```

响应事件：

- `work_start`
- `work_progress`
- `work_tool_start`
- `work_tool_end`
- `work_done`
- `work_error`
- `draft_revision_ready`
- `draft_revision_error`

`draft_revision_ready` 建议返回：

```json
{
  "proposalId": 12,
  "draftId": 100,
  "revisionNo": 3,
  "status": "READY",
  "draft": {
    "draftId": 100,
    "status": "GENERATED",
    "assistantMessage": "已按要求调整学习计划草案。",
    "missingFields": [],
    "draftPlan": {}
  }
}
```

### Agent 输入

草案版本迭代 prompt 应包含：

- 用户本次调整指令。
- 原始 `LearningPlanDraftCommand`。
- 当前草案完整 `LearningPlanDraftPlan`。
- 当前草案中的题目列表，包括 `phaseIndex`、`slug`、`titleCn`、`difficulty`、`tags`、`reason`。
- 约束说明：
  - 可以整体调整草案。
  - 可以保留、替换、重排题目。
  - 推荐题必须来自本地题库工具。
  - 最终只输出完整 `LearningPlanDraftPlan` JSON。

### 校验和保存

生成结果仍复用：

- `LearningPlanDraftStructuredOutputMapper`
- `LearningPlanDraftValidator.validateGeneratedPlan(...)`

保存时：

1. 写入一条草案 revision 提案记录。
2. 更新 `learning_plan_draft.draft_plan_json` 为新版本。
3. 追加用户指令到 `messages_json`。
4. 保持草案状态为 `GENERATED`。

## 正式计划扩展

### 用户体验

正式计划详情页增加“扩展计划”入口：

```text
想继续学习？描述接下来的目标：
我已经完成大部分数组题，接下来想增加图论和动态规划训练。

[生成扩展建议]
```

生成完成后展示扩展提案：

- 新增阶段列表。
- 每个新阶段的目标、周期、验收标准、复盘建议。
- 每个新阶段下新增题目。

用户可以“应用扩展”或“放弃”。应用后，新阶段追加到正式计划末尾。

### API

生成扩展提案：

```http
POST /api/learning-plans/{planId}/extensions/stream
Accept: text/event-stream
Content-Type: application/json

{
  "instruction": "我已经完成大部分数组题，接下来想增加图论和动态规划训练"
}
```

应用扩展提案：

```http
POST /api/learning-plans/{planId}/extensions/{proposalId}/apply
```

响应事件：

- `work_start`
- `work_progress`
- `work_tool_start`
- `work_tool_end`
- `work_done`
- `work_error`
- `plan_extension_ready`
- `plan_extension_error`

`plan_extension_ready` 建议返回：

```json
{
  "proposalId": 20,
  "planId": 88,
  "status": "READY",
  "summary": "建议追加 2 个阶段，分别覆盖图论基础和动态规划入门。",
  "newPhases": []
}
```

### Agent 输入

正式计划扩展 prompt 应包含：

- 用户本次扩展意愿。
- 当前完整正式计划。
- 当前题目完成情况：
  - `COMPLETED`
  - `IN_PROGRESS`
  - `SKIPPED`
  - `NOT_STARTED`
- 已有阶段最大 `phaseIndex`。
- 已有题目 slug 集合。
- 约束说明：
  - 只能追加新阶段，`phaseIndex` 必须大于当前最大阶段。
  - 不能删除、修改、重排已有阶段。
  - 不能删除、替换、重排已有题目。
  - 新增题目不能和已有计划题目重复。
  - 新增题目必须来自本地题库工具。
  - 最终只输出扩展提案 JSON，不输出完整替换版计划。

### 结构化输出

正式计划扩展不应输出完整 `LearningPlanDraftPlan`，而应输出 append-only 提案：

```json
{
  "summary": "扩展说明",
  "newPhases": [
    {
      "phaseIndex": 5,
      "title": "第 5 阶段：图论基础",
      "durationWeeks": 2,
      "focus": "掌握图遍历和最短路径基础",
      "objectives": [],
      "recommendedTags": [],
      "acceptanceCriteria": [],
      "reviewAdvice": "每周复盘图建模方式。",
      "problems": []
    }
  ],
  "metadata": {
    "reason": "基于已完成数组和哈希表题目，追加图论训练。"
  }
}
```

### 校验规则

正式计划扩展必须新增专用校验器，例如 `LearningPlanExtensionValidator`：

- `newPhases` 非空。
- 每个新阶段 `phaseIndex > currentMaxPhaseIndex`。
- 新阶段 `phaseIndex` 递增且不重复。
- 新增题目 slug 不在已有计划中。
- 新增题目 slug 不在本次扩展内重复。
- 每阶段题目数不超过上限，建议沿用每阶段最多 5 道题。
- 新题目必须存在于本地题库。
- 不能修改已有 `plan_json` 中的旧阶段内容。

### 应用扩展

正式计划扩展不能复用当前 `LearningPlanRepository.save(...)` 的整体保存路径，因为现有实现会删除并重建 `learning_plan_phase` 明细。

建议新增 repository 方法：

```java
LearningPlan appendPhases(long userId, long planId, List<LearningPlanPhaseDraft> newPhases);
```

应用时：

1. 校验 proposal 属于当前用户和计划。
2. 校验 proposal 状态为 `READY`。
3. 重新读取当前正式计划和进度，执行 append-only 校验，避免基于旧快照应用。
4. 向 `learning_plan_phase` 追加新阶段。
5. 向 `learning_plan_recommended_problem` 追加新题目。
6. 更新 `learning_plan.plan_json` 快照，把新阶段追加到末尾。
7. 更新 proposal 状态为 `APPLIED`。

## 数据模型建议

第一阶段可以用两张表，分别承载草案 revision 和正式计划 extension。

### learning_plan_draft_revision

```sql
CREATE TABLE learning_plan_draft_revision (
  id BIGSERIAL PRIMARY KEY,
  draft_id BIGINT NOT NULL REFERENCES learning_plan_draft(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL,
  revision_no INTEGER NOT NULL,
  status VARCHAR(40) NOT NULL,
  instruction TEXT NOT NULL,
  base_plan_json JSONB,
  proposed_plan_json JSONB,
  error_code VARCHAR(120),
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_learning_plan_draft_revision_no UNIQUE (draft_id, revision_no)
);
```

### learning_plan_extension_proposal

```sql
CREATE TABLE learning_plan_extension_proposal (
  id BIGSERIAL PRIMARY KEY,
  plan_id BIGINT NOT NULL REFERENCES learning_plan(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL,
  instruction TEXT NOT NULL,
  base_plan_json JSONB NOT NULL,
  progress_snapshot_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  proposed_extension_json JSONB,
  applied_at TIMESTAMPTZ,
  error_code VARCHAR(120),
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

状态建议：

```text
GENERATING
READY
FAILED
APPLIED
DISCARDED
EXPIRED
```

草案 revision 通常生成成功后立即成为当前草案版本，不一定需要单独 apply。正式计划 extension 必须显式 apply。

## 服务边界

建议新增应用层服务：

```text
LearningPlanDraftRevisionStreamService
LearningPlanExtensionStreamService
LearningPlanExtensionApplyService
LearningPlanExtensionValidator
LearningPlanProposalPromptBuilder
```

其中 `LearningPlanProposalPromptBuilder` 可以复用公共片段，例如题目摘要、计划摘要、用户指令、约束说明，但草案 revision 和正式 extension 应有不同 profile。

`agent-core` 不需要感知这些业务概念。它仍然只接收 `AgentRequest`，执行工具调用，并返回结构化最终输出。

## 前端设计

### 草案页

在 `LearningPlanDraftPanel` 的 `GENERATED` 状态中增加：

- 调整说明 textarea。
- `按要求调整计划` 按钮。
- `确认保存当前计划` 按钮。
- 调整时展示 `AgentWorkIndicator` 或更细粒度步骤列表。

现有“编辑目标摘要并重新生成”可以被自然语言调整框替代。

### 正式计划详情页

在 `LearningPlanDetail` 中增加：

- `扩展计划` 入口。
- 扩展意愿 textarea。
- 流式生成扩展建议。
- 扩展提案预览。
- `应用扩展` 和 `放弃`。

应用后刷新计划详情，并显示新阶段。

## 风险和约束

- 正式计划扩展必须严格 append-only，否则会破坏练习进度和会话引用。
- `phaseIndex` 当前被多个练习接口使用，扩展时必须避免重排。
- 应用扩展需要基于最新计划重新校验，不能只信任生成时的 base snapshot。
- 模型可能推荐重复题或不存在题，必须继续通过本地题库校验。
- 草案 revision 如果每次都覆盖当前草案，需要保留 revision 历史，避免用户无法回溯。
- 正式计划 `plan_json` 和明细表需要保持一致，应用扩展必须在事务内完成。

## 实施阶段

### 阶段一：草案自然语言迭代

- 新增 `learning_plan_draft_revision`。
- 新增草案 revision 流式接口。
- 复用现有结构化草案 schema 和 validator。
- 前端在草案预览页增加自然语言调整框。
- 替换当前“编辑 goal + 特殊前缀消息”的临时方案。

### 阶段二：正式计划扩展提案

- 新增 `learning_plan_extension_proposal`。
- 新增扩展提案 schema、mapper、validator。
- 新增正式计划扩展流式接口。
- 新增扩展提案预览 UI。

### 阶段三：应用扩展

- Repository 增加 append-only 写入方法。
- 应用扩展时事务内追加阶段和题目，并更新 `plan_json`。
- 前端支持应用后刷新正式计划详情。

### 阶段四：体验增强

- 支持放弃提案、重新生成提案。
- 支持查看草案 revision 历史。
- 支持更细粒度的学习计划生成步骤展示。
- 支持基于完成率自动给出扩展建议入口。
