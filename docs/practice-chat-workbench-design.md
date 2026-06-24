# 题目聊天工作台研发设计

## 背景

当前训练方案页面已经能够展示正式方案库，但方案仍停留在“查看和管理”层面。用户需要从方案中的具体题目进入一个围绕该题的训练空间，在这里查看题面、和 AI 教练讨论思路、粘贴代码或 LeetCode 反馈，并在 LeetCode 通过后把题目标记为完成。

本设计把第一版做题工作台定位为“计划题目的聊天页”，而不是内置 IDE、在线判题或复杂任务面板。用户仍在 LeetCode 完成提交，系统负责保留方案上下文、题面上下文、聊天过程和题目进度。

## 目标

- 在方案库列表增加查看入口，支持进入方案详情页。
- 在方案详情页展示完整方案、阶段和题目清单，并显示每道题的进度状态。
- 点击方案中的题目后进入题目聊天工作台。
- 工作台进入时自动创建或复用题目训练会话，并把未开始题目标记为进行中。
- 聊天页第一条消息由教练自动发送，内容为该题 Markdown 题面。
- 用户可在聊天页自然输入思路、问题、代码或 LeetCode 反馈。
- 顶部固定窄工具栏，提供返回、题目信息、状态、Review 记录占位、LeetCode 外链和题目已完成操作。
- 用户点击题目已完成后，方案详情页可以看到进度变化。

## 非目标

- 第一版不做内置代码运行、在线判题或沙箱执行。
- 第一版不提供“我卡住了”“请求提示”“提交 Review”等快捷动作按钮。
- 第一版不引入 `已 Review` 题目状态。
- 第一版不实现结构化代码 Review 记录，只预留入口。
- 第一版不要求 AI 生成第一条题面消息；题面消息应由后端用题库内容确定性创建，避免额外成本和题面幻觉。

## 用户闭环

```text
方案详情
  -> 点击题目
  -> 进入题目聊天页
  -> 系统自动展示 Markdown 题面并把题目置为进行中
  -> 用户和 AI 交流思路、复杂度、代码、LeetCode 反馈
  -> 用户跳转到 LeetCode 做题
  -> 通过后回到聊天页点击题目已完成
  -> 方案详情页看到进度更新
```

## 页面结构

### 方案详情页

路由建议：

```text
/learning-plans/:planId
```

页面内容：

- 顶部展示方案标题、目标、状态、周期、每周投入、水平、语言和创建时间。
- 主体按阶段展示：
  - 阶段标题、周期、重点。
  - 阶段目标 `objectives`。
  - 推荐标签 `recommendedTags`。
  - 验收标准 `acceptanceCriteria`。
  - 复盘建议 `reviewAdvice`。
  - 题目列表。
- 每道题展示：
  - 题号、标题、难度、标签。
  - 推荐原因。
  - 当前状态。
  - `开始练习` 或 `继续练习`。
  - `查看题目`，可跳题库详情或题库搜索。

题目状态显示应来自后端进度数据。没有进度记录时默认为 `未开始`。

### 题目聊天工作台

路由建议：

```text
/learning-plans/:planId/phases/:phaseIndex/problems/:slug/chat
```

页面采用三段式布局：

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│ AppShell 顶部导航                                                             │
├──────────────────────────────────────────────────────────────────────────────┤
│ ← 返回方案   1. 两数之和   Easy   状态：进行中          Review记录  LeetCode  题目已完成 │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │ 教练                                                                  │  │
│  │ # 两数之和                                                            │  │
│  │ 给定一个整数数组 nums 和一个整数目标值 target...                      │  │
│  │                                                                        │  │
│  │ ## 示例                                                               │  │
│  │ ```text                                                               │  │
│  │ 输入：nums = [2,7,11,15], target = 9                                   │  │
│  │ 输出：[0,1]                                                           │  │
│  │ ```                                                                   │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │ 我                                                                    │  │
│  │ 我想先用哈希表记录已经见过的数字。                                    │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  中间消息列表可滚动                                                           │
│                                                                              │
├──────────────────────────────────────────────────────────────────────────────┤
│  输入你的思路、问题、代码或 LeetCode 反馈...                         发送    │
└──────────────────────────────────────────────────────────────────────────────┘
```

移动端折叠：

```text
┌──────────────────────────────┐
│ AppShell 顶部导航             │
├──────────────────────────────┤
│ ← 1. 两数之和        Easy     │
│ 状态：进行中                  │
│ Review记录  LeetCode  完成    │
├──────────────────────────────┤
│ 教练                          │
│ # 两数之和                    │
│ 给定一个整数数组...           │
│                               │
│ 我                            │
│ 我准备用哈希表...             │
│                               │
│ 教练                          │
│ 这个思路可以...               │
│                               │
│ 消息列表可滚动                │
├──────────────────────────────┤
│ 输入消息...             发送  │
└──────────────────────────────┘
```

布局约束：

- AppShell 顶部导航保持原有位置。
- 题目工具栏固定在 AppShell 导航下方。
- 底部输入框固定在工作台底部。
- 只有中间消息列表滚动。
- Markdown 消息支持代码块、列表、表格和题面图片。
- AI 流式回复时，如果用户停留在底部附近，则自动滚动到底部；如果用户主动上滑查看历史，则不强制拉回底部。

## 工具栏设计

工具栏只承载题目级操作，不展示所属方案和阶段。

左侧：

- 返回方案。
- 题号和题名。
- 难度。
- 当前状态。

右侧：

- `Review 记录`：第一版只做预留入口。
- `LeetCode`：打开题目外链。
- `题目已完成`：把题目状态更新为已完成。

`Review 记录` 第一版行为：

- 点击后打开抽屉或弹窗。
- 展示空态文案：`代码 Review 记录暂未开放。`
- 不新增结构化 review 表。
- 后续实现时再把用户提交代码和 AI 简评沉淀为结构化记录。

## 题目状态

第一版只保留四个状态：

```text
NOT_STARTED  未开始
IN_PROGRESS  进行中
COMPLETED    已完成
SKIPPED      已跳过
```

状态流：

```text
未开始 -> 进入聊天页 -> 进行中
进行中 -> 点击题目已完成 -> 已完成
未开始/进行中 -> 用户手动跳过 -> 已跳过
已跳过 -> 再次进入或恢复 -> 进行中
```

第一版可先不在聊天页放 `跳过` 操作，只在方案详情页题目行提供状态菜单。聊天页最重要的状态操作是 `题目已完成`。

## 消息模型

工作台聊天历史应对齐现有 Agent runtime，而不是再建一套独立消息表。当前仓库已经有：

- `agent_task`：长期会话线程。
- `agent_turn`：用户一轮意图。
- `agent_message`：用户可见消息，角色只支持 `user` 和 `assistant`。
- `agent_run` / trace 表：模型调用、SSE、工具调用和上下文快照。

因此第一版 practice 只新增题目进度和题目会话锚点。题面消息、用户消息和 AI 回复落到 `agent_message`，由 `practice_session.agent_task_id` 关联读取。

聊天页展示角色：

```text
USER       用户消息，对应 agent_message.role = user
ASSISTANT  教练消息，对应 agent_message.role = assistant
```

消息类型通过 `agent_message.metadata` 区分：

```text
PROBLEM_STATEMENT  题面消息
CHAT               普通讨论消息
```

状态变化、会话创建、完成操作属于系统事件，不放进聊天流。第一版可记录在 `practice_session_event`，或只通过 progress/session 更新时间表达。

### 第一条题面消息

- 创建会话时由后端确定性写入，不调用 AI。
- 角色为 `assistant`，`metadata.messageType = "PROBLEM_STATEMENT"`。
- 内容来自题库 `ProblemDetail.contentMarkdown`；如果题面为空，写入明确空态说明。
- 同一个 `practice_session` 只创建一次，复用会话时不能重复插入。
- 题面消息应该插入到该 session 绑定的 `agent_task` 下。由于 `agent_message.turn_id` 非空，需要创建一条 seed `agent_turn`，状态直接置为 `succeeded`，并让 `assistant_message_id` 指向题面消息。

### 普通聊天消息

- 用户发送 Markdown 文本，前端可乐观展示为 pending。
- 后端把用户消息作为新的 `agent_turn` 写入 `agent_message`。
- 后端构造 AI 上下文并通过 SSE 返回 assistant Markdown 增量。
- Assistant 回复完成后由现有 Agent runtime 持久化到 `agent_message`。
- 用户粘贴代码、LeetCode 错误、复杂度分析问题都作为普通聊天消息处理，不需要特殊按钮。

实现时需要注意当前轮消息不能在模型上下文里重复出现。也就是说，如果准备 run 时已经把当前 user message 写入 `agent_message`，上下文组装查询历史时应排除当前 turn，或者 `ContextAssembler` 不再额外追加同一条 `currentUserMessage`。

## 后端数据模型

新增 migration 建议放在 `backend/mentor-api/src/main/resources/db/migration`。当前工程多个模块共享同一个 Flyway 版本空间，新增 `V` 版本号必须跨 `agent`、`auth`、`ai`、`mentor-api` 迁移唯一。

### 题目进度表

表名：

```text
learning_plan_problem_progress
```

字段建议：

```sql
id BIGSERIAL PRIMARY KEY,
user_id BIGINT NOT NULL,
plan_id BIGINT NOT NULL,
phase_index INT NOT NULL,
problem_slug VARCHAR(255) NOT NULL,
status VARCHAR(32) NOT NULL,
started_at TIMESTAMPTZ NULL,
completed_at TIMESTAMPTZ NULL,
skipped_at TIMESTAMPTZ NULL,
created_at TIMESTAMPTZ NOT NULL,
updated_at TIMESTAMPTZ NOT NULL
```

约束与索引：

```sql
UNIQUE (user_id, plan_id, phase_index, problem_slug)
CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED'))
INDEX (user_id, plan_id)
INDEX (user_id, plan_id, status)
```

用途：

- 方案详情页快速回显每道题状态。
- 工作台进入时执行 `NOT_STARTED/SKIPPED -> IN_PROGRESS`。
- 用户点击题目已完成时写入 `COMPLETED` 和 `completed_at`。
- 后续可作为学习进度看板、复盘和推荐的事实来源。

### 训练会话表

表名：

```text
practice_session
```

字段建议：

```sql
id BIGSERIAL PRIMARY KEY,
user_id BIGINT NOT NULL,
plan_id BIGINT NOT NULL,
phase_index INT NOT NULL,
problem_slug VARCHAR(255) NOT NULL,
status VARCHAR(32) NOT NULL,
agent_task_id BIGINT NOT NULL REFERENCES agent_task(id),
problem_statement_message_id BIGINT NULL REFERENCES agent_message(id),
last_message_at TIMESTAMPTZ NULL,
created_at TIMESTAMPTZ NOT NULL,
updated_at TIMESTAMPTZ NOT NULL
```

约束与索引：

```sql
UNIQUE (user_id, plan_id, phase_index, problem_slug)
UNIQUE (agent_task_id)
CHECK (status IN ('ACTIVE', 'ARCHIVED'))
INDEX (user_id, plan_id)
```

用途：

- 表示“某个用户在某个方案题目上的训练聊天线程”。
- 通过 `agent_task_id` 复用 Agent runtime 的消息、run、trace、上下文快照和后续压缩能力。
- 通过 `problem_statement_message_id` 保证题面消息可定位、可幂等检查。

`agent_task.metadata` 建议写入：

```json
{
  "scenario": "PRACTICE_CHAT",
  "practiceSessionId": 100,
  "planId": 12,
  "phaseIndex": 1,
  "problemSlug": "two-sum"
}
```

### 训练会话事件表

第一版如果需要审计状态变化，建议新增轻量事件表；如果暂时不做审计，可以后移。

表名：

```text
practice_session_event
```

字段建议：

```sql
id BIGSERIAL PRIMARY KEY,
session_id BIGINT NOT NULL REFERENCES practice_session(id),
user_id BIGINT NOT NULL,
event_type VARCHAR(64) NOT NULL,
old_status VARCHAR(32) NULL,
new_status VARCHAR(32) NULL,
metadata JSONB NOT NULL DEFAULT '{}',
created_at TIMESTAMPTZ NOT NULL
```

第一版事件类型：

```text
SESSION_CREATED
STATUS_CHANGED
PROBLEM_STATEMENT_CREATED
```

该表不用于聊天流展示。聊天页只展示 `agent_message` 中的 user/assistant 消息。

## 后端服务边界

建议新增 practice 包，第一版可以放在 `mentor-api`，因为它主要聚合现有学习计划、题库、Agent runtime 和 SSE 适配：

```text
backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/controller
backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model
backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/repository
backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/service
backend/mentor-api/src/main/resources/mapper/practice
```

主要类建议：

```text
PracticeSessionController
PracticeSessionService
PracticeSessionRepository
PracticeProgressRepository
PracticePromptBuilder
PracticeResponseMapper
PracticeSessionExceptionHandler
```

职责划分：

- `PracticeSessionController`：HTTP 契约、认证用户解析、SSE emitter 生命周期。
- `PracticeSessionService`：会话幂等创建、状态流转、题面 seed 消息、发送聊天消息。
- `PracticeSessionRepository`：practice 表读写，不直接拼 AI prompt。
- `PracticePromptBuilder`：把方案、阶段、题目和会话上下文转换为模型提示。
- `AgentConversationRepository` 扩展：支持创建 task、追加 seed assistant message、按 task 分页读取消息。
- `LlmStreamSseMapper` 或 practice 专用 mapper：复用现有 `message_start`、`content_delta`、`message_end`、`error` 等 SSE 事件。

当 practice 逻辑稳定并需要复用于 worker、移动端或 CLI 时，再把 use case 抽到 `mentor-application`。

## 核心流程

### 进入聊天页

```text
frontend route load
  -> POST create-or-reuse practice session
  -> 校验当前用户拥有 plan
  -> 校验 phaseIndex + slug 存在于 plan snapshot
  -> 读取题库 ProblemDetail
  -> upsert learning_plan_problem_progress
  -> upsert practice_session
  -> 创建或复用 agent_task
  -> 如无题面消息，创建 seed turn + assistant PROBLEM_STATEMENT message
  -> NOT_STARTED/SKIPPED 状态更新为 IN_PROGRESS
  -> 返回 session、problem、progress、messages
```

进入页面导致的状态流转必须是幂等的。并发打开两个标签页时，唯一约束和事务应保证只生成一个 session、一个 progress 记录和一条题面消息。

已完成题目再次进入不应回退为 `IN_PROGRESS`。

### 发送消息

```text
user submit
  -> POST /api/practice-sessions/{sessionId}/messages/stream
  -> 校验 session 属于当前用户
  -> 校验没有同一 task 的运行中 run，或通过同一 Idempotency-Key 返回已有 run
  -> 写入 user agent_message
  -> 组装 prompt 和最近历史
  -> AgentLoopRunner stream
  -> SSE 返回 content_delta
  -> run 成功后写入 assistant agent_message
  -> 更新 practice_session.last_message_at
```

如果 SSE 中断：

- 用户消息已经保存。
- 如果 run 仍在执行，前端刷新后可以通过 session 消息列表看到已持久化的最终 assistant 消息，或看到仍无回复。
- 用户重试同一请求应带相同 `Idempotency-Key`，后端返回同一 run，避免重复用户消息。

### 完成题目

```text
click complete
  -> PATCH /api/practice-sessions/{sessionId}/progress-status
  -> 校验 session 属于当前用户
  -> 更新 progress.status = COMPLETED
  -> 写 completed_at
  -> 可写 practice_session_event
  -> 返回最新 session/progress
```

完成操作应允许重复点击，重复请求返回当前 `COMPLETED` 状态。

## API 设计

所有路径常量应放入 `ApiContractConstants` 或 practice 模块下的契约常量类。所有 SSE 事件名应放入 `SseEventNames` 或 practice 专用常量类。

### 获取方案详情

现有接口：

```text
GET /api/learning-plans/{planId}
```

推荐把详情响应从直接暴露 `LearningPlanPhaseDraft` 改成 API 专用 DTO，并在每道题里补充进度：

```json
{
  "id": 12,
  "title": "哈希表面试冲刺",
  "phases": [
    {
      "phaseIndex": 1,
      "title": "基础模式",
      "problems": [
        {
          "slug": "two-sum",
          "frontendId": 1,
          "title": "Two Sum",
          "titleCn": "两数之和",
          "difficulty": "EASY",
          "tags": ["Array", "Hash Table"],
          "reason": "用于建立哈希表查找模式。",
          "sortOrder": 1,
          "progressStatus": "IN_PROGRESS",
          "practiceSessionId": 100,
          "startedAt": "2026-06-24T10:00:00Z",
          "completedAt": null
        }
      ]
    }
  ]
}
```

如果希望降低后端 DTO 改动，可以先在详情响应顶层增加独立数组：

```json
{
  "problemProgress": [
    {
      "phaseIndex": 1,
      "problemSlug": "two-sum",
      "status": "IN_PROGRESS",
      "practiceSessionId": 100
    }
  ]
}
```

第一版更推荐嵌入到题目项，前端渲染简单，也不需要在页面层重复做 key join。

### 创建或复用题目会话

```text
POST /api/learning-plans/{planId}/phases/{phaseIndex}/problems/{slug}/practice-session
```

响应：

```json
{
  "session": {
    "id": 100,
    "planId": 12,
    "phaseIndex": 1,
    "problemSlug": "two-sum",
    "progressStatus": "IN_PROGRESS",
    "agentTaskId": 220,
    "createdAt": "2026-06-24T10:00:00Z",
    "updatedAt": "2026-06-24T10:00:00Z"
  },
  "problem": {
    "slug": "two-sum",
    "frontendId": 1,
    "title": "Two Sum",
    "titleCn": "两数之和",
    "difficulty": "EASY",
    "tags": ["Array", "Hash Table"],
    "leetcodeUrl": "https://leetcode.com/problems/two-sum/"
  },
  "messages": [
    {
      "id": 300,
      "role": "ASSISTANT",
      "messageType": "PROBLEM_STATEMENT",
      "contentMarkdown": "# Two Sum\n...",
      "createdAt": "2026-06-24T10:00:00Z"
    }
  ]
}
```

行为：

- 校验当前用户拥有该方案。
- 校验 `phaseIndex + slug` 存在于方案快照中。
- 创建或复用 `learning_plan_problem_progress`。
- 创建或复用 `practice_session` 和 `agent_task`。
- 自动创建第一条题面消息。
- 返回首屏所需全部数据，避免前端再串行请求题库详情。

### 获取会话详情

```text
GET /api/practice-sessions/{sessionId}
```

用于刷新页面、SSE 中断恢复和从方案详情页继续练习。返回结构与创建接口相同。

### 分页获取消息

```text
GET /api/practice-sessions/{sessionId}/messages?beforeMessageId=300&pageSize=30
```

第一版可以只返回最近 50 条；如果消息量不大，也可以先合并在 session 详情接口中。接口预留分页有利于后续长会话。

### 更新题目进度状态

```text
PATCH /api/practice-sessions/{sessionId}/progress-status
```

请求：

```json
{
  "status": "COMPLETED"
}
```

该接口更新的是 `learning_plan_problem_progress.status`，不是 `practice_session.status`。第一版在已有 session 的聊天页只需要支持：

```text
IN_PROGRESS -> COMPLETED
IN_PROGRESS -> SKIPPED
SKIPPED -> IN_PROGRESS
```

聊天页只暴露 `COMPLETED` 操作。

如果方案详情页需要在未创建 session 前直接跳过题目，使用计划题目维度接口：

```text
PATCH /api/learning-plans/{planId}/phases/{phaseIndex}/problems/{slug}/progress
```

请求：

```json
{
  "status": "SKIPPED"
}
```

该接口只操作 progress 记录，不强制创建 `practice_session`。当用户后续进入聊天页时，再创建或复用 session，并按状态流把 `SKIPPED` 恢复为 `IN_PROGRESS`。

### 发送聊天消息

```text
POST /api/practice-sessions/{sessionId}/messages/stream
Idempotency-Key: <uuid>
Accept: text/event-stream
```

请求：

```json
{
  "message": "我准备用哈希表记录已经遍历过的值。"
}
```

SSE 事件第一版复用现有事件名：

```text
message_start
content_delta
message_end
usage
error
heartbeat
agent_run_start
agent_run_end
agent_error
```

前端只需要消费 `content_delta` 组装 assistant 文本，并在 `message_end` 后把临时消息标为完成。`agent_*` 事件可用于调试或内部状态，不在普通用户界面展示。

错误码建议：

```text
PRACTICE_SESSION_NOT_FOUND
PRACTICE_PROBLEM_NOT_IN_PLAN
PRACTICE_SESSION_CONFLICT
PRACTICE_MESSAGE_EMPTY
PRACTICE_STATUS_TRANSITION_INVALID
```

## AI 上下文设计

### Prompt 目标

Practice chat 的系统提示应和通用主题讲解区分开。建议由 `PracticePromptBuilder` 生成：

- 你是算法训练教练，面向中文用户回复。
- 当前任务是陪用户完成指定 LeetCode 题，而不是替用户一开始交付完整答案。
- 优先使用引导式提示、边界条件检查、复杂度分析和代码定位。
- 用户明确要求完整解法、代码或最终答案时可以提供，但要先保证解释清楚。
- 用户粘贴 LeetCode 报错、WA 用例、TLE 反馈时，优先定位原因和最小修正。
- 不要编造题面、提交结果、通过状态或用户没有提供的代码。
- 回复中的代码默认使用学习计划里的编程语言；没有配置时优先 Java。

### 上下文组成

每次发送消息时，模型上下文应包含：

- 稳定系统提示。
- 方案上下文：标题、目标、摘要、语言、周期、用户水平。
- 阶段上下文：阶段标题、重点、目标、验收标准、复盘建议。
- 当前题目上下文：题号、标题、难度、标签、推荐原因、LeetCode 链接。
- 题面：优先完整题面；如果题面过长，保留约束、样例和关键描述。
- 最近聊天历史：按策略保留最近 N 轮。
- 当前用户消息。

题面虽然会作为第一条 assistant 消息展示，但上下文组装不能完全依赖最近历史。长会话中第一条题面可能被截断，所以 `PracticePromptBuilder` 应在系统上下文中始终提供压缩后的题目事实。

### 长上下文控制

- 用户消息原文完整持久化。
- 发给模型前可对超长代码进行裁剪，保留文件语言、首尾片段、错误信息和用户问题。
- 裁剪必须只影响模型输入，不改写 `agent_message.content`。
- 历史消息按最近 N 轮截断，后续接入 agent artifact 摘要后再纳入长期上下文。
- 如果题面和历史超过预算，优先保留当前用户消息、题目约束、样例和最近 assistant 诊断。

## 前端设计

### 路由

建议新增：

```text
/learning-plans/:planId
/learning-plans/:planId/phases/:phaseIndex/problems/:slug/chat
```

`phaseIndex` 使用方案快照中的阶段编号，不使用数组下标推断。`slug` 必须 URL encode。

### 组件

建议新增：

```text
frontend/src/practice/PracticeChatPage.tsx
frontend/src/practice/PracticeToolbar.tsx
frontend/src/practice/PracticeMessageList.tsx
frontend/src/practice/PracticeMessage.tsx
frontend/src/practice/PracticeComposer.tsx
frontend/src/practice/ReviewHistoryDrawer.tsx
frontend/src/practice/usePracticeSession.ts
frontend/src/practice/usePracticeStream.ts
```

建议扩展：

```text
frontend/src/LearningPlans.tsx
frontend/src/learning-plans/LearningPlanDetail.tsx
frontend/src/learning-plans/PlanPreview.tsx
frontend/src/app/navigation.ts
frontend/src/services/api.ts
frontend/src/types/api.ts
frontend/src/styles.css
```

### 状态流

页面加载：

```text
idle -> loadingSession -> ready
idle -> loadingSession -> notFound/error
```

发送消息：

```text
ready -> streaming
streaming -> ready
streaming -> streamError
```

完成题目：

```text
ready -> updatingStatus -> ready
updatingStatus -> statusError
```

交互规则：

- `streaming` 时禁用发送按钮，输入框可继续编辑但不能提交第二条消息。
- 流式回复中展示 assistant pending 气泡。
- `题目已完成` 在 `COMPLETED` 状态下禁用或显示为已完成。
- Review 抽屉第一版只显示空态，不请求后端 review 数据。
- LeetCode 链接缺失时按钮禁用，并提供 tooltip。

### 布局和样式

- AppShell 顶部导航保持现状。
- 工作台容器高度应是视口剩余高度，使用 `min-height: 0` 避免滚动容器失效。
- 工作台内部使用 `grid-template-rows: auto 1fr auto`。
- 工具栏固定在 AppShell 下方，输入框固定在底部。
- 消息区独立滚动，页面 body 不应因为消息增多而滚动。
- Markdown 渲染支持代码块、列表、表格和图片，代码块横向滚动。
- 移动端工具栏可换行，主标题保留单行省略，状态和操作按钮换到第二行。
- AI 流式回复时，如果用户停留在底部附近，自动滚到底部；如果用户上滑查看历史，不强制拉回。

### 前端类型

建议新增类型：

```ts
export type PracticeProgressStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';
export type PracticeMessageRole = 'USER' | 'ASSISTANT';
export type PracticeMessageType = 'PROBLEM_STATEMENT' | 'CHAT';

export interface PracticeMessage {
  id: number;
  role: PracticeMessageRole;
  messageType: PracticeMessageType;
  contentMarkdown: string;
  createdAt: string;
}

export interface PracticeSessionResponse {
  session: PracticeSessionSummary;
  problem: PracticeProblemSummary;
  messages: PracticeMessage[];
}
```

## 一致性、并发与安全

### 幂等和并发

- 创建会话依赖 `(user_id, plan_id, phase_index, problem_slug)` 唯一约束和事务。
- 题面消息依赖 `practice_session.problem_statement_message_id` 幂等检查。
- 聊天消息流接口必须要求 `Idempotency-Key`，并复用现有 agent run 幂等机制。
- 同一个 `agent_task` 同时只允许一个 active run。若已有 run 进行中，返回 409 或同幂等键对应的已有 run。
- 状态更新使用条件更新，避免 `COMPLETED` 被页面进入动作回退成 `IN_PROGRESS`。

### 权限

所有 practice 接口都必须使用当前登录用户：

- `plan_id` 必须属于当前用户。
- `practice_session.user_id` 必须等于当前用户。
- 不接受前端传入 `userId`。
- `agent_task.user_id` 与 session 用户保持一致。

### 数据安全

- 日志不得输出完整用户代码、大段题面、Authorization、Cookie、API key 或模型 token。
- AI trace、request snapshot 和 tool result 继续走现有 agent persistence 的脱敏策略。
- 用户粘贴的代码和错误信息按用户内容处理，不进入普通应用日志。
- LeetCode 链接只来自题库字段，不从用户输入拼接跳转 URL。

## 观测与治理

Practice chat 属于 AI 用户交互，应接入现有 AI 治理：

- 新增或复用 `AiPurpose`，建议增加 `PRACTICE_CHAT`。
- 新增或复用 `AiRunSource`，建议增加 `PRACTICE_SESSION_MESSAGE`。
- admission metadata 写入 `practiceSessionId`、`planId`、`phaseIndex`、`problemSlug`。
- 超时使用 `ApiSseProperties` 或 practice 专用配置，不使用无限等待。

建议指标：

```text
practice.session.created.count
practice.session.reused.count
practice.progress.status.changed.count
practice.message.stream.started.count
practice.message.stream.failed.count
practice.message.stream.duration
practice.sse.active.connections
```

## 错误与空状态

- 方案不存在或不属于当前用户：404。
- 题目不在方案快照中：404。
- 题库题面缺失：仍创建会话，题面消息显示“题面暂不可用”，LeetCode 按钮如有链接仍可用。
- LeetCode 外链缺失：工具栏禁用外链按钮。
- 消息为空或只包含空白：400。
- 同一会话已有运行中回复：409，前端提示当前回复生成中。
- AI 回复失败：用户消息保留，assistant pending 气泡转为错误态，可重新发送。
- 状态更新失败：前端回滚按钮状态并展示错误。
- SSE 断开：前端停止 pending 状态，提供重试入口，并允许刷新后重新读取消息。

## 测试设计

后端测试：

- migration 资源能被 Flyway 发现，版本号不冲突。
- 创建或复用题目会话时只插入一次 session、progress 和题面消息。
- 并发创建同一题目会话不会重复插入题面消息。
- 进入会话自动把 `NOT_STARTED` 和 `SKIPPED` 更新为 `IN_PROGRESS`。
- `COMPLETED` 题目再次进入不回退为 `IN_PROGRESS`。
- 状态更新同步 session 响应和 progress 表。
- 非当前用户方案不能创建或读取会话。
- `phaseIndex + slug` 不在方案快照中时拒绝。
- 发送空消息返回 400。
- 同一 `Idempotency-Key` 重放不会重复写用户消息。
- AI stream 失败时用户消息保留，run 标记失败。

前端测试：

- 方案列表显示查看入口并进入详情页。
- 方案详情页显示题目状态和开始/继续练习入口。
- 进入聊天页后渲染固定工具栏、滚动消息区和固定输入框。
- 第一条题面 Markdown 正常渲染代码块和列表。
- 发送消息时追加用户气泡和 assistant pending 气泡。
- `content_delta` 能持续追加到 assistant 气泡。
- 点击 LeetCode 打开外链。
- 点击题目已完成后状态更新，并禁用完成按钮。
- Review 记录按钮打开预留抽屉。
- 移动端工具栏不挤压题名和按钮。

建议验证命令：

```text
make backend-test
make frontend-test
make frontend-build
```

涉及 CSS 布局时，应额外用浏览器检查桌面和移动端视口。

## 分阶段实施

### 阶段一：方案详情和进度回显

- 扩展 `/api/learning-plans/{planId}` 响应，补充题目进度。
- 前端支持 `/learning-plans/:planId`。
- 列表查看按钮跳转详情页。
- 详情页按阶段展示完整方案和题目状态。
- 题目行提供开始/继续练习入口。

### 阶段二：进度和会话基础

- 新增 `learning_plan_problem_progress`、`practice_session`，可选 `practice_session_event`。
- 新增 practice repository/service/controller。
- 新增创建或复用题目会话接口。
- 扩展 agent conversation repository，支持创建 seed turn 和题面 assistant message。
- 进入会话自动写入题面消息并设置进行中。

### 阶段三：聊天工作台前端

- 新增聊天页三段式布局。
- 实现工具栏、消息列表、输入框。
- 实现 Markdown 渲染和滚动策略。
- 实现 Review 记录预留抽屉。
- 实现题目已完成状态更新。

### 阶段四：AI 流式聊天

- 新增 practice 消息流接口。
- 接入现有 Agent runtime、LLM stream、AI governance 和 SSE mapper。
- 实现 `PracticePromptBuilder`。
- 持久化用户消息和 AI 回复。
- 补齐超时、错误降级、幂等重试和运行中冲突处理。

### 阶段五：体验打磨和验收

- 补齐后端和前端测试。
- 检查移动端布局。
- 检查日志脱敏和错误提示。
- 跑通从方案详情到 LeetCode 再回到完成状态的完整闭环。

## 验收标准

- 用户可以从方案列表进入方案详情页。
- 用户可以在方案详情页看到每道题的状态。
- 用户点击题目后进入聊天工作台，首屏包含题面消息。
- 首次进入未开始题目后，方案详情页状态变为进行中。
- 用户可以发送自然语言、代码或 LeetCode 反馈，并收到流式 AI 回复。
- 用户点击题目已完成后，方案详情页状态变为已完成。
- 刷新聊天页不会重复创建题面消息。
- 同一题目会话历史可以恢复。
- 第一版不出现内置 IDE、在线判题、结构化 Review 记录或快捷提示按钮。

## 待后续设计

- 结构化代码 Review 记录。
- 代码快照和长代码存储。
- 聊天历史摘要、长期记忆和基于错题的复盘。
- 单题复盘报告。
- 根据完成记录自动调整方案进度或推荐后续题目。
- 题目跳过、恢复和批量状态管理的更完整交互。
