# 学习计划 AI 提案设计

## 背景

当前学习计划有两个明显阶段：

1. 用户确认前，`POST /api/learning-plans/drafts/stream` 生成 `learning_plan_draft` 草案，草案内容保存在 `draft_plan_json`。
2. 用户确认后，`LearningPlanDraftService.confirmDraft(...)` 把草案保存为正式 `learning_plan`，并同步写入 `learning_plan_phase`、`learning_plan_recommended_problem`。

确认前草案还没有训练进度，可以允许 AI 按用户反馈整体改写。确认后的正式计划已经承载练习会话和题目进度，进度存储在 `learning_plan_problem_progress`，不能像草案一样整体替换，否则会破坏用户已完成题目、训练会话和历史记录。

正式计划的“扩展”在用户体验上也应支持生成、调整、确认或放弃。扩展数据本质上是一个“待追加的草案”：用户可以反复调整这部分追加内容，直到满意后再应用到正式计划。这个流程和确认前草案重写的业务感受一致，但底层约束不同：

- 草案重写的目标是替换一个尚未确认的完整计划草案。
- 计划扩展的目标是生成一个待追加片段，应用前不得修改正式计划，应用时只能追加。

因此需要把二者统一设计为 AI 辅助提案，同时保留不同的结构化输出、校验规则和落库动作。

## 目标

- 支持用户在草案生成后输入自然语言要求，生成新的草案版本，再决定确认哪个版本。
- 支持正式计划在学习过程中生成扩展提案，并允许用户继续用自然语言调整扩展提案。
- 支持正式计划扩展提案的预览、应用和放弃；应用前不写入正式计划明细。
- 保证正式计划扩展不删除、不替换、不重排已有阶段和题目，不破坏已有进度。
- 复用现有 `AgentLoopRunner`、题库工具、结构化输出、SSE 工作状态和本地校验能力。
- 保留提案组和版本历史，方便后续支持回看、放弃、重新生成和审计。

## 非目标

- 不支持正式计划删除已有阶段或已有题目。
- 不支持正式计划直接整体重写 `plan_json` 后覆盖已有详情表。
- 不支持模型直接写库；模型只生成结构化提案，业务层校验后落库。
- 第一阶段不做复杂 diff 编辑器，先支持自然语言输入、流式生成、预览、调整、应用或放弃。
- 第一阶段正式计划扩展只追加新阶段和新题目；不在已有阶段内插入题目，不合并或拆分已有阶段。

## 核心概念

建议引入统一概念：`LearningPlanProposal`。提案分为“提案组”和“提案版本”两层。

```text
LearningPlanProposal
├── Draft Proposal Group
│   └── Draft Revision：确认前草案版本迭代，可整体替换草案内容
└── Extension Proposal Group
    └── Extension Revision：确认后扩展版本迭代，只能替换待追加内容
```

### 提案组

提案组代表用户的一次连续意图。例如：

- “把这个草案调简单一点”对应一个草案提案组。
- “我想接下来练图论和动态规划”对应一个扩展提案组。

同一个提案组内可以有多个版本。用户每次继续调整，都生成一个新版本。

### 提案版本

提案版本代表某次 AI 输出结果。

- 草案版本输出完整 `LearningPlanDraftPlan`。
- 扩展版本输出完整 `LearningPlanExtensionDraft`，即“待追加内容”的完整快照。

扩展版本之间不是累加关系。新版本生成成功后会取代上一版成为当前待应用版本。最终应用时只应用最新的 `READY` 扩展版本，不应用历史中间版本。

### 两类提案的共同点

- 都接收用户自然语言指令。
- 都基于现有计划上下文构造 Agent prompt。
- 都使用 provider-native JSON Schema 结构化输出。
- 都使用题库工具调用和本地 slug 校验。
- 都通过 SSE 暴露工作状态。
- 都保留提案组、版本、状态和错误信息。

### 两类提案的差异

| 维度 | 草案版本迭代 | 正式计划扩展 |
| --- | --- | --- |
| 业务对象 | `learning_plan_draft` | `learning_plan` |
| 输出形态 | 完整计划草案 | 待追加扩展草案 |
| 是否可替换已有内容 | 可以替换草案内任意内容 | 不可以修改正式计划已有内容 |
| 生成成功后的影响 | 可更新当前草案预览 | 只更新待应用提案 |
| 最终确认动作 | 确认草案成为正式计划 | 应用扩展到正式计划末尾 |
| 落库方式 | 更新 `draft_plan_json` | 事务内 append-only 写入 |

## 状态模型

提案组状态建议：

```text
ACTIVE
APPLIED
DISCARDED
EXPIRED
```

提案版本状态建议：

```text
GENERATING
READY
SUPERSEDED
FAILED
APPLIED
DISCARDED
EXPIRED
```

状态规则：

- 新提案组创建后为 `ACTIVE`。
- 同一组内新版本生成成功后，上一版 `READY` 版本标记为 `SUPERSEDED`。
- 只有最新的 `READY` 版本可以被确认或应用。
- 草案版本确认后，草案进入现有 `CONFIRMED` 流程。
- 扩展版本应用后，版本标记为 `APPLIED`，提案组标记为 `APPLIED`。
- 放弃提案组时，当前 `READY` 版本标记为 `DISCARDED`，提案组标记为 `DISCARDED`。

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

生成草案修订版本：

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
  "proposalGroupId": 10,
  "proposalId": 12,
  "draftId": 100,
  "revisionNo": 3,
  "status": "READY",
  "supersededProposalId": 11,
  "draft": {
    "draftId": 100,
    "status": "GENERATED",
    "assistantMessage": "已按要求调整学习计划草案。",
    "missingFields": [],
    "draftPlan": {}
  }
}
```

确认草案继续使用现有接口：

```http
POST /api/learning-plans/drafts/{draftId}/confirm
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

1. 创建或复用草案提案组。
2. 写入一条草案 revision 提案版本。
3. 将同组上一条 `READY` revision 标记为 `SUPERSEDED`。
4. 更新 `learning_plan_draft.draft_plan_json` 为新版本。
5. 追加用户指令到 `messages_json`。
6. 保持草案状态为 `GENERATED`。

草案 revision 生成成功后可以直接成为当前草案预览，因为正式计划尚未创建，不存在进度破坏问题。

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

用户可以继续输入自然语言调整扩展提案：

```text
对扩展建议不满意？输入调整要求：
图论少一点，增加两周动态规划基础训练。

[按要求调整扩展] [应用扩展] [放弃]
```

调整完成后，页面展示新的扩展版本。用户最终点击“应用扩展”时，才把当前扩展版本追加到正式计划末尾。

### API

创建扩展提案组并生成第一版：

```http
POST /api/learning-plans/{planId}/extension-proposals/stream
Accept: text/event-stream
Content-Type: application/json

{
  "instruction": "我已经完成大部分数组题，接下来想增加图论和动态规划训练"
}
```

基于已有扩展提案继续生成新版本：

```http
POST /api/learning-plans/{planId}/extension-proposals/{proposalId}/revisions/stream
Accept: text/event-stream
Content-Type: application/json

{
  "instruction": "图论少一点，增加两周动态规划基础训练"
}
```

应用扩展提案：

```http
POST /api/learning-plans/{planId}/extension-proposals/{proposalId}/apply
```

放弃扩展提案组：

```http
POST /api/learning-plans/{planId}/extension-proposals/{proposalId}/discard
```

流式生成响应事件：

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
  "proposalGroupId": 30,
  "proposalId": 32,
  "planId": 88,
  "revisionNo": 2,
  "status": "READY",
  "supersededProposalId": 31,
  "summary": "建议追加 2 个阶段，分别覆盖动态规划基础和图论入门。",
  "extensionDraft": {
    "newPhases": []
  }
}
```

### Agent 输入

创建第一版扩展时，prompt 应包含：

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
  - 最终只输出扩展草案 JSON，不输出完整替换版计划。

调整扩展提案时，prompt 还应额外包含：

- 当前待调整的 `LearningPlanExtensionDraft`。
- 上一版扩展提案的 `summary` 和新增题目列表。
- 用户本次调整指令。
- 约束说明：
  - 新输出是“完整替换上一版待追加内容”，不是在上一版扩展后继续追加。
  - 历史版本不会自动应用，最终只应用最新 `READY` 版本。

### 结构化输出

正式计划扩展不应输出完整 `LearningPlanDraftPlan`，而应输出 append-only 的 `LearningPlanExtensionDraft`：

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

`newPhases` 表示“待追加阶段”，不是完整计划阶段列表。

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
- 调整扩展提案时，新版本必须重新完整通过校验，不能只校验和上一版之间的差异。

### 应用扩展

正式计划扩展不能复用当前 `LearningPlanRepository.save(...)` 的整体保存路径，因为现有实现会删除并重建 `learning_plan_phase` 明细。

建议新增 repository 方法：

```java
LearningPlan appendPhases(long userId, long planId, List<LearningPlanPhaseDraft> newPhases);
```

应用时：

1. 校验 proposal 属于当前用户和计划。
2. 校验 proposal 状态为 `READY`，且是所在提案组最新版本。
3. 校验提案组状态为 `ACTIVE`。
4. 重新读取当前正式计划和进度，执行 append-only 校验，避免基于旧快照应用。
5. 如果当前最大 `phaseIndex` 和生成时不同，可按当前最大阶段重新顺序编号待追加阶段；如果出现题目重复或其他业务冲突，则拒绝应用并提示重新生成。
6. 向 `learning_plan_phase` 追加新阶段。
7. 向 `learning_plan_recommended_problem` 追加新题目。
8. 更新 `learning_plan.plan_json` 快照，把新阶段追加到末尾。
9. 更新 proposal 状态为 `APPLIED`，更新 proposal group 状态为 `APPLIED`。

应用扩展必须在事务内完成。`plan_json` 和明细表要么同时更新成功，要么同时回滚。

## 数据模型建议

建议增加一张通用提案组表，再分别增加草案 revision 和扩展 revision 表。这样可以统一处理同一用户意图下的多次调整，同时避免把两类不同结构化输出塞进一张过宽表。

### learning_plan_proposal_group

```sql
CREATE TABLE learning_plan_proposal_group (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  proposal_type VARCHAR(40) NOT NULL,
  target_type VARCHAR(40) NOT NULL,
  target_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL,
  initial_instruction TEXT NOT NULL,
  latest_proposal_id BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

字段说明：

- `proposal_type`：`DRAFT_REVISION` 或 `PLAN_EXTENSION`。
- `target_type`：`DRAFT` 或 `PLAN`。
- `target_id`：对应 `draft_id` 或 `plan_id`。
- `latest_proposal_id`：当前组内最新可展示版本的 id。因为草案 revision 和扩展 revision 分表，该字段只作为业务引用，不建议第一阶段加外键。

### learning_plan_draft_revision

```sql
CREATE TABLE learning_plan_draft_revision (
  id BIGSERIAL PRIMARY KEY,
  proposal_group_id BIGINT NOT NULL REFERENCES learning_plan_proposal_group(id) ON DELETE CASCADE,
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
  CONSTRAINT uk_learning_plan_draft_revision_no UNIQUE (proposal_group_id, revision_no)
);
```

### learning_plan_extension_revision

```sql
CREATE TABLE learning_plan_extension_revision (
  id BIGSERIAL PRIMARY KEY,
  proposal_group_id BIGINT NOT NULL REFERENCES learning_plan_proposal_group(id) ON DELETE CASCADE,
  plan_id BIGINT NOT NULL REFERENCES learning_plan(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL,
  revision_no INTEGER NOT NULL,
  status VARCHAR(40) NOT NULL,
  instruction TEXT NOT NULL,
  base_plan_json JSONB NOT NULL,
  progress_snapshot_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  base_max_phase_index INTEGER NOT NULL,
  previous_extension_json JSONB,
  proposed_extension_json JSONB,
  applied_at TIMESTAMPTZ,
  error_code VARCHAR(120),
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_learning_plan_extension_revision_no UNIQUE (proposal_group_id, revision_no)
);
```

`previous_extension_json` 只在调整扩展提案时保存上一版待追加内容，便于审计和问题排查。业务应用时只读取当前 proposal 的 `proposed_extension_json`。

## 服务边界

建议新增应用层服务：

```text
LearningPlanProposalGroupService
LearningPlanDraftRevisionStreamService
LearningPlanExtensionProposalStreamService
LearningPlanExtensionApplyService
LearningPlanExtensionValidator
LearningPlanProposalPromptBuilder
```

职责建议：

- `LearningPlanProposalGroupService`：创建提案组、维护 latest proposal、处理 supersede、discard、expire。
- `LearningPlanDraftRevisionStreamService`：编排草案 revision 的 Agent 调用、校验和草案更新。
- `LearningPlanExtensionProposalStreamService`：编排扩展首版和扩展调整版本的 Agent 调用、校验和提案保存。
- `LearningPlanExtensionApplyService`：事务内应用最新扩展版本，保证 append-only。
- `LearningPlanExtensionValidator`：校验扩展提案输出和应用时的最新计划状态。
- `LearningPlanProposalPromptBuilder`：复用题目摘要、计划摘要、用户指令、约束说明，但按 profile 生成不同 prompt。

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
- 扩展提案调整 textarea。
- `按要求调整扩展`、`应用扩展` 和 `放弃`。

扩展提案预览必须明确展示这是“待追加内容”，不要让用户误以为当前正式计划已经改变。

应用后刷新计划详情，并显示新阶段。放弃后关闭扩展提案预览，不修改正式计划。

## 并发和过期

扩展提案从生成到应用之间，正式计划可能发生变化，例如另一个扩展已经被应用。应用扩展时必须重新读取最新计划并处理并发：

- 如果只是最大 `phaseIndex` 变化，可以按当前最大阶段重新编号待追加阶段。
- 如果新增题目和当前计划已有题目重复，拒绝应用并提示用户重新生成。
- 如果正式计划状态已不允许扩展，拒绝应用。
- 如果 proposal group 已经 `APPLIED`、`DISCARDED` 或 `EXPIRED`，拒绝应用。

第一阶段可以不做复杂自动合并。只在 append-only 语义明确成立时应用；其他情况返回业务错误并要求重新生成。

## 风险和约束

- 正式计划扩展必须严格 append-only，否则会破坏练习进度和会话引用。
- `phaseIndex` 当前被多个练习接口使用，扩展时必须避免重排已有阶段。
- 扩展调整生成的是新的待追加快照，不是追加在上一版扩展后面。
- 应用扩展需要基于最新计划重新校验，不能只信任生成时的 base snapshot。
- 模型可能推荐重复题或不存在题，必须继续通过本地题库校验。
- 草案 revision 如果每次都覆盖当前草案，需要保留 revision 历史，避免用户无法回溯。
- 正式计划 `plan_json` 和明细表需要保持一致，应用扩展必须在事务内完成。

## 实施阶段

### 阶段一：草案自然语言迭代

- 新增 `learning_plan_proposal_group`。
- 新增 `learning_plan_draft_revision`。
- 新增草案 revision 流式接口。
- 复用现有结构化草案 schema 和 validator。
- 前端在草案预览页增加自然语言调整框。
- 替换当前“编辑 goal + 特殊前缀消息”的临时方案。

### 阶段二：正式计划扩展提案和调整

- 新增 `learning_plan_extension_revision`。
- 新增扩展提案 schema、mapper、validator。
- 新增正式计划扩展首版流式接口。
- 新增正式计划扩展调整流式接口。
- 前端支持扩展提案预览和继续调整。

### 阶段三：应用和放弃扩展

- Repository 增加 append-only 写入方法。
- 应用扩展时事务内追加阶段和题目，并更新 `plan_json`。
- 支持放弃扩展提案组。
- 前端支持应用后刷新正式计划详情。

### 阶段四：体验增强

- 支持查看草案 revision 历史。
- 支持查看扩展 proposal group 历史。
- 支持更细粒度的学习计划生成步骤展示。
- 支持基于完成率自动给出扩展建议入口。
