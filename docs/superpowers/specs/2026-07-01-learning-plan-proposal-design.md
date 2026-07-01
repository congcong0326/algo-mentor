# 学习计划 AI 提案完整研发设计

## 背景

`algo-mentor` 的学习计划当前分为两个阶段：

- 确认前，用户通过 `POST /api/learning-plans/drafts/stream` 生成 `learning_plan_draft`，生成结果保存在 `draft_plan_json`。
- 确认后，`LearningPlanDraftService.confirmDraft(...)` 将草案保存为正式 `learning_plan`，并同步写入 `learning_plan_phase`、`learning_plan_recommended_problem`。

草案还没有训练进度，可以允许 AI 按用户反馈整体改写。正式计划已经承载练习会话和题目进度，进度存储在 `learning_plan_problem_progress`，不能整体替换，否则会破坏用户已完成题目、训练会话和历史记录。

正式计划的“扩展”在体验上也需要支持生成、调整、确认或放弃。它本质上是一个待追加的 AI 草案：应用前不能修改正式计划，应用时只能追加新阶段和新题目。草案迭代和正式计划扩展有相同的交互形态，但底层约束不同，因此需要统一抽象为学习计划提案，同时保留两类提案各自的结构化输出、校验规则和落库动作。

## 目标

- 支持用户在草案生成后输入自然语言要求，生成新的草案版本，并决定确认哪个版本。
- 支持正式计划在学习过程中生成扩展提案，并允许用户继续用自然语言调整扩展提案。
- 支持正式计划扩展提案的预览、应用和放弃；应用前不写入正式计划明细。
- 保证正式计划扩展不删除、不替换、不重排已有阶段和题目，不破坏已有进度。
- 复用现有 `AgentLoopRunner`、题库工具、provider-native JSON Schema 结构化输出、SSE 工作状态和本地校验能力。
- 保留提案组和版本历史，支持后续回看、放弃、重新生成和审计。
- 明确后端、前端、数据迁移和测试边界，使后续 implementation plan 可以按阶段落地。

## 非目标

- 不支持正式计划删除已有阶段或已有题目。
- 不支持正式计划整体重写 `plan_json` 后覆盖已有详情表。
- 不支持模型直接写库；模型只生成结构化提案，业务层校验后落库。
- 第一版不做复杂 diff 编辑器，只支持自然语言输入、流式生成、预览、调整、应用或放弃。
- 第一版正式计划扩展只追加新阶段和新题目；不在已有阶段内插入题目，不合并或拆分已有阶段。
- 第一版不提供完整历史回滚能力；历史版本用于审计和后续扩展。

## 核心概念

新增统一业务概念 `LearningPlanProposal`，分为提案组和提案版本两层。

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

同一提案组内可以有多个版本。用户每次继续调整，都生成一个新版本。提案组负责表达目标对象、提案类型、初始指令、当前最新版本和组级状态。

### 提案版本

提案版本代表某次 AI 输出结果。

- 草案版本输出完整 `LearningPlanDraftPlan`。
- 扩展版本输出完整 `LearningPlanExtensionDraft`，即待追加内容的完整快照。

扩展版本之间不是累加关系。新版本生成成功后会取代上一版成为当前待应用版本。最终应用时只应用最新 `READY` 扩展版本，不应用历史中间版本。

## 类型和状态

### 提案类型

```text
ProposalType
- DRAFT_REVISION
- PLAN_EXTENSION

ProposalTargetType
- DRAFT
- PLAN
```

### 提案组状态

```text
ACTIVE
APPLIED
DISCARDED
EXPIRED
```

### 提案版本状态

```text
GENERATING
READY
SUPERSEDED
FAILED
APPLIED
DISCARDED
EXPIRED
```

### 状态规则

- 新提案组创建后为 `ACTIVE`。
- 同一组内新版本生成成功后，上一版 `READY` 版本标记为 `SUPERSEDED`。
- 只有最新 `READY` 版本可以被确认、应用或继续调整。
- 草案版本确认后，草案进入现有 `CONFIRMED` 流程；提案组可以标记为 `APPLIED`。
- 扩展版本应用后，版本标记为 `APPLIED`，提案组标记为 `APPLIED`。
- 放弃提案组时，当前 `READY` 版本标记为 `DISCARDED`，提案组标记为 `DISCARDED`。
- 版本生成失败时，版本标记为 `FAILED`，提案组保持 `ACTIVE`，用户可以重试或放弃。

## 草案版本迭代

### 用户体验

草案生成完成后，在“确认保存当前计划”按钮附近增加自然语言调整输入框。

```text
对当前计划不满意？输入调整要求，例如：
减少动态规划题，增加数组和哈希表题，整体难度降低一些。

[按要求调整计划] [确认保存当前计划]
```

用户满意时直接确认。用户不满意时输入要求并点击“按要求调整计划”，页面进入流式调整状态，完成后替换为新的草案预览。

现有“编辑目标摘要并重新生成”的临时交互应被自然语言调整框替代。`LearningPlanDraftService` 中依赖 `请按新的目标摘要重新生成学习计划：` 前缀的路径后续可以下线，避免让前端依赖隐式文案协议。

### API

新增草案修订流式接口：

```http
POST /api/learning-plans/drafts/{draftId}/revisions/stream
Accept: text/event-stream
Content-Type: application/json

{
  "instruction": "减少动态规划题，增加数组和哈希表题，整体难度降低一些"
}
```

确认草案继续使用现有接口：

```http
POST /api/learning-plans/drafts/{draftId}/confirm
```

草案 revision 生成事件：

```text
work_start
work_progress
work_tool_start
work_tool_end
work_done
work_error
draft_revision_ready
draft_revision_error
```

`draft_revision_ready` 响应示例：

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

### Agent 输入

草案版本迭代 prompt 包含：

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

生成结果复用：

- `LearningPlanDraftStructuredOutputMapper`
- `LearningPlanDraftValidator.validateGeneratedPlan(...)`

保存流程：

1. 校验 draft 属于当前用户，且状态不是 `CONFIRMED`。
2. 创建或复用草案提案组。
3. 写入一条 `GENERATING` 草案 revision 版本。
4. Agent 输出结构化 JSON 后映射为 `LearningPlanDraftPlan`。
5. 本地校验通过后，将同组上一条 `READY` revision 标记为 `SUPERSEDED`。
6. 将当前 revision 标记为 `READY`。
7. 更新 `learning_plan_draft.draft_plan_json` 为新版本。
8. 追加用户指令到 `messages_json`。
9. 保持草案状态为 `GENERATED`。

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

调整完成后，页面展示新的扩展版本。用户最终点击“应用扩展”时，才把当前扩展版本追加到正式计划末尾。扩展提案预览必须明确展示这是“待追加内容”，不能让用户误以为当前正式计划已经改变。

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

扩展生成事件：

```text
work_start
work_progress
work_tool_start
work_tool_end
work_done
work_error
plan_extension_ready
plan_extension_error
```

`plan_extension_ready` 响应示例：

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

创建第一版扩展时，prompt 包含：

- 用户本次扩展意愿。
- 当前完整正式计划。
- 当前题目完成情况，按 `COMPLETED`、`IN_PROGRESS`、`SKIPPED`、`NOT_STARTED` 汇总。
- 已有阶段最大 `phaseIndex`。
- 已有题目 slug 集合。
- 约束说明：
  - 只能追加新阶段，`phaseIndex` 必须大于当前最大阶段。
  - 不能删除、修改、重排已有阶段。
  - 不能删除、替换、重排已有题目。
  - 新增题目不能和已有计划题目重复。
  - 新增题目必须来自本地题库工具。
  - 最终只输出扩展草案 JSON，不输出完整替换版计划。

调整扩展提案时，prompt 额外包含：

- 当前待调整的 `LearningPlanExtensionDraft`。
- 上一版扩展提案的 `summary` 和新增题目列表。
- 用户本次调整指令。
- 约束说明：
  - 新输出是完整替换上一版待追加内容，不是在上一版扩展后继续追加。
  - 历史版本不会自动应用，最终只应用最新 `READY` 版本。

### 结构化输出

正式计划扩展不输出完整 `LearningPlanDraftPlan`，而输出 append-only 的 `LearningPlanExtensionDraft`：

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

后端新增领域模型：

```java
public record LearningPlanExtensionDraft(
    String summary,
    List<LearningPlanPhaseDraft> newPhases,
    Map<String, Object> metadata
) {}
```

`newPhases` 表示待追加阶段，不是完整计划阶段列表。新增阶段和题目仍复用 `LearningPlanPhaseDraft`、`LearningPlanProblemDraft`，保持前后端展示结构一致。

### 校验规则

新增 `LearningPlanExtensionValidator`，校验规则包括：

- `newPhases` 非空。
- 每个新阶段 `phaseIndex > currentMaxPhaseIndex`。
- 新阶段 `phaseIndex` 递增且不重复。
- 新增题目 slug 不在已有计划中。
- 新增题目 slug 不在本次扩展内重复。
- 每阶段题目数不超过上限，沿用每阶段最多 5 道题。
- 新题目必须存在于本地题库。
- 调整扩展提案时，新版本必须重新完整通过校验，不能只校验和上一版之间的差异。
- 应用扩展时必须基于最新计划重新校验，不能只信任生成时的 base snapshot。

### 应用扩展

正式计划扩展不能复用当前 `LearningPlanRepository.save(...)` 的整体保存路径，因为 `MyBatisLearningPlanRepository.save(...)` 会先删除 `learning_plan_phase` 再重建明细。扩展必须新增 append-only repository 方法。

```java
LearningPlan appendPhases(long userId, long planId, List<LearningPlanPhaseDraft> newPhases);
```

应用流程：

1. 校验 proposal 属于当前用户和计划。
2. 校验 proposal 状态为 `READY`，且是所在提案组最新版本。
3. 校验提案组状态为 `ACTIVE`。
4. 重新读取当前正式计划和进度，执行 append-only 校验。
5. 如果当前最大 `phaseIndex` 和生成时不同，可按当前最大阶段重新顺序编号待追加阶段。
6. 如果出现题目重复、计划状态不允许扩展或其他业务冲突，拒绝应用并提示重新生成。
7. 向 `learning_plan_phase` 追加新阶段。
8. 向 `learning_plan_recommended_problem` 追加新题目。
9. 更新 `learning_plan.plan_json` 快照，把新阶段追加到末尾。
10. 更新 proposal 状态为 `APPLIED`，更新 proposal group 状态为 `APPLIED`。

应用扩展必须在事务内完成。`plan_json` 和明细表要么同时更新成功，要么同时回滚。

## 数据模型

采用统一提案组表，草案 revision 和扩展 revision 分表。这样可以统一处理同一用户意图下的多次调整，同时避免把两类不同结构化输出塞进一张过宽表。

### `learning_plan_proposal_group`

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

CREATE INDEX idx_learning_plan_proposal_group_target
  ON learning_plan_proposal_group(user_id, proposal_type, target_type, target_id, status);
```

字段说明：

- `proposal_type`：`DRAFT_REVISION` 或 `PLAN_EXTENSION`。
- `target_type`：`DRAFT` 或 `PLAN`。
- `target_id`：对应 `draft_id` 或 `plan_id`。
- `latest_proposal_id`：当前组内最新可展示版本的 id。因为草案 revision 和扩展 revision 分表，该字段只作为业务引用，第一版不加外键。

### `learning_plan_draft_revision`

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

CREATE INDEX idx_learning_plan_draft_revision_draft
  ON learning_plan_draft_revision(user_id, draft_id, status, created_at DESC);
```

### `learning_plan_extension_revision`

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

CREATE INDEX idx_learning_plan_extension_revision_plan
  ON learning_plan_extension_revision(user_id, plan_id, status, created_at DESC);
```

`previous_extension_json` 只在调整扩展提案时保存上一版待追加内容，便于审计和问题排查。业务应用时只读取当前 proposal 的 `proposed_extension_json`。

## 后端服务边界

新增应用层服务：

```text
LearningPlanProposalGroupService
LearningPlanDraftRevisionStreamService
LearningPlanExtensionProposalStreamService
LearningPlanExtensionApplyService
LearningPlanExtensionValidator
LearningPlanProposalPromptBuilder
```

职责：

- `LearningPlanProposalGroupService`：创建提案组、维护 latest proposal、处理 supersede、discard、expire。
- `LearningPlanDraftRevisionStreamService`：编排草案 revision 的 Agent 调用、校验和草案更新。
- `LearningPlanExtensionProposalStreamService`：编排扩展首版和扩展调整版本的 Agent 调用、校验和提案保存。
- `LearningPlanExtensionApplyService`：事务内应用最新扩展版本，保证 append-only。
- `LearningPlanExtensionValidator`：校验扩展提案输出和应用时的最新计划状态。
- `LearningPlanProposalPromptBuilder`：复用题目摘要、计划摘要、用户指令、约束说明，但按 profile 生成不同 prompt。

`agent-core` 不感知这些业务概念。它仍然只接收 `AgentRequest`，执行工具调用，并返回结构化最终输出。

## API 层和契约常量

新增路径、SSE 事件名、请求字段和状态值属于跨模块公共契约，应抽象到常量类或枚举中统一管理，避免 controller、SSE mapper、前端服务层重复硬编码。

建议新增或扩展：

- `LearningPlanProposalApiContractConstants`：proposal 相关路径、SSE 事件名。
- `LearningPlanProposalType`、`LearningPlanProposalTargetType`、`LearningPlanProposalGroupStatus`、`LearningPlanProposalRevisionStatus`。
- `LearningPlanRevisionRequest`：包含 `instruction`。
- `LearningPlanDraftRevisionReadyResponse`。
- `LearningPlanExtensionReadyResponse`。
- `LearningPlanProposalApplyResponse`。

SSE mapper 可以沿用 `LearningPlanDraftStreamSseMapper` 的工作状态映射方式，但草案 revision 和扩展 proposal 应有各自业务 ready/error 事件，避免前端把不同结构误认为同一种 draft event。

## 前端设计

### 草案页

在 `LearningPlanDraftPanel` 的 `GENERATED` 状态中增加：

- 调整说明 textarea。
- `按要求调整计划` 按钮。
- `确认保存当前计划` 按钮。
- 调整时展示 `AgentWorkIndicator` 或现有工作状态列表。

前端通过 `frontend/src/services/api.ts` 新增草案 revision 流式请求封装，并在 `frontend/src/types/api.ts` 中定义 SSE 事件类型。调整完成后用 `draft_revision_ready.draft` 替换当前草案预览。

### 正式计划详情页

在 `LearningPlanDetail` 中增加：

- `扩展计划` 入口。
- 扩展意愿 textarea。
- 流式生成扩展建议。
- 扩展提案预览。
- 扩展提案调整 textarea。
- `按要求调整扩展`、`应用扩展` 和 `放弃`。

扩展提案预览显示为独立的待追加区块，不混入当前正式计划阶段列表。应用后刷新计划详情，并显示新阶段。放弃后关闭扩展提案预览，不修改正式计划。

## 并发和过期

扩展提案从生成到应用之间，正式计划可能发生变化，例如另一个扩展已经被应用。应用扩展时必须重新读取最新计划并处理并发：

- 如果只是最大 `phaseIndex` 变化，可以按当前最大阶段重新编号待追加阶段。
- 如果新增题目和当前计划已有题目重复，拒绝应用并提示用户重新生成。
- 如果正式计划状态已不允许扩展，拒绝应用。
- 如果 proposal group 已经 `APPLIED`、`DISCARDED` 或 `EXPIRED`，拒绝应用。

第一版不做复杂自动合并。只在 append-only 语义明确成立时应用；其他情况返回业务错误并要求重新生成。

## 错误处理

建议新增稳定错误码：

```text
LEARNING_PLAN_PROPOSAL_NOT_FOUND
LEARNING_PLAN_PROPOSAL_NOT_ACTIVE
LEARNING_PLAN_PROPOSAL_NOT_READY
LEARNING_PLAN_PROPOSAL_NOT_LATEST
LEARNING_PLAN_DRAFT_REVISION_NOT_ALLOWED
LEARNING_PLAN_EXTENSION_INVALID
LEARNING_PLAN_EXTENSION_CONFLICT
LEARNING_PLAN_EXTENSION_PLAN_NOT_ACTIVE
LEARNING_PLAN_EXTENSION_STRUCTURED_OUTPUT_INVALID
LEARNING_PLAN_EXTENSION_APPLY_FAILED
```

错误响应应通过现有 `LearningPlanExceptionHandler` 统一映射。SSE 流中的生成失败通过 `draft_revision_error` 或 `plan_extension_error` 返回，包含 `code`、`message`、`retryable`。

## 观测和安全

- AI 生成仍走 AI governance admission/lifecycle，`AiPurpose` 使用 `LEARNING_PLAN`。
- 草案 revision 的 `AiRunSource` 可以沿用或扩展为 `LEARNING_PLAN_DRAFT_REVISION`。
- 扩展 proposal 建议新增 `LEARNING_PLAN_EXTENSION_PROPOSAL` 来源，便于统计。
- 日志不得输出 API key、Authorization、完整用户隐私内容或完整 prompt。
- 结构化日志记录 `userId`、`draftId`、`planId`、`proposalGroupId`、`proposalId`、状态、错误码、耗时。
- Micrometer 指标覆盖 revision 生成次数、扩展应用成功/失败次数、SSE 连接、校验失败原因和 Agent 错误。

## 测试计划

### 后端单元测试

- `LearningPlanProposalGroupServiceTest`
  - 创建提案组后状态为 `ACTIVE`。
  - 新 `READY` 版本生成后 supersede 上一版。
  - 放弃提案组后当前版本和组状态正确。
- `LearningPlanDraftRevisionStreamServiceTest`
  - revision 成功后更新 `draft_plan_json`。
  - 已确认 draft 不允许 revision。
  - 结构化输出非法时写入 `FAILED` revision 并发送 error 事件。
- `LearningPlanExtensionValidatorTest`
  - 拒绝空 `newPhases`。
  - 拒绝阶段编号不大于当前最大阶段。
  - 拒绝已有计划重复题目。
  - 拒绝扩展内部重复题目。
  - 拒绝不存在于题库的 slug。
- `LearningPlanExtensionApplyServiceTest`
  - 只允许最新 `READY` proposal 应用。
  - 应用后 append 新阶段和题目，不删除旧阶段。
  - 当前最大阶段变化时重新编号。
  - 题目冲突时拒绝应用。

### API 和 repository 测试

- MyBatis migration resource test 覆盖新增迁移脚本。
- repository 测试覆盖 proposal group、draft revision、extension revision 的增改查。
- `appendPhases(...)` 测试确认不会调用 `deletePlanPhases`，且 `plan_json` 和明细表一致。
- controller 测试覆盖新 endpoint 的鉴权、参数校验和错误映射。

### 前端测试

- 草案生成完成后展示自然语言调整框。
- 点击调整后消费 `draft_revision_ready` 并替换草案预览。
- 正式计划详情页可生成扩展提案并展示待追加预览。
- 点击放弃后关闭扩展预览且不刷新正式计划为修改状态。
- 点击应用成功后刷新正式计划详情并显示新增阶段。

## 分阶段交付

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

## 风险

- 正式计划扩展如果误用整体保存路径，会删除并重建阶段明细，破坏训练进度引用。
- `phaseIndex` 被练习接口使用，扩展时必须避免重排已有阶段。
- 扩展调整生成的是新的待追加快照，不是追加在上一版扩展后面。
- 应用扩展需要基于最新计划重新校验，不能只信任生成时的 base snapshot。
- 模型可能推荐重复题或不存在题，必须继续通过本地题库校验。
- 草案 revision 如果每次都覆盖当前草案，需要保留 revision 历史，避免用户无法审计生成过程。
- 正式计划 `plan_json` 和明细表需要保持一致，应用扩展必须在事务内完成。

