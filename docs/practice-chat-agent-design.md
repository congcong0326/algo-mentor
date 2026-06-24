# 题目聊天 Agent 研发设计

## 背景

`practice-chat-workbench-design.md` 已经定义了题目聊天工作台的页面闭环、题目状态和训练会话方向。本设计继续细化题目聊天框的 AI 侧实现，重点解决三个问题：

- 系统提示词和题目上下文如何组装。
- 第一条确定性题面消息在 UI、存储和模型上下文中分别使用什么角色。
- SSE 流式回复在聊天界面里如何展示，避免把产品体验做成调试台。

第一版题目聊天仍定位为 LeetCode 外部做题的 AI 教练空间，不做内置 IDE、在线评测、结构化代码 Review 或跨题推荐。

## 目标

- 用户从学习计划题目进入聊天页时，自动创建或复用训练会话。
- 后端确定性写入第一条题面消息，避免额外模型成本和题面幻觉。
- 用户可以输入思路、问题、代码片段或 LeetCode 反馈，AI 通过 SSE 流式回复。
- AI 回复围绕当前题和当前学习计划阶段，不主动发散到其他题。
- 默认采用教练式引导；当用户明确要求答案或代码时，直接给完整思路、复杂度和 Java 代码。
- 聊天页展示轻量流式状态，而不是完整 agent/tool 调试事件。

## 已确认决策

### Prompt 组装

模型请求按以下顺序组装：

```text
system: 稳定教学规则
system: 当前题目和学习计划上下文
system: 可选 active summary
history: 最近普通聊天消息
user: 当前用户消息
```

原因：

- 稳定规则和业务上下文分离，便于调试和后续版本迭代。
- 题目上下文不伪装成用户发言，也不伪装成模型历史输出。
- 历史聊天只保留真实 user/assistant 讨论，避免第一条题面 seed 影响模型判断。

### 第一条题面消息角色

第一条题面消息在不同边界的角色如下：

```text
UI 展示       assistant 气泡，标签为“教练”
数据库存储    agent_message.role = assistant
消息类型      agent_message.metadata.messageType = "PROBLEM_STATEMENT"
模型上下文    独立 system 上下文块，不作为 assistant 历史消息传入
```

这样既能让用户看到“教练先给出题面”的自然体验，又不会让模型误以为自己上一轮已经完成了某个回答。

### 工具调用范围

题目聊天 v1 开放题库工具给模型，一般模型也不需要调用因为题目内容一般在模型的训练数据里都有。

### 回复语言

默认跟随前端 locale。用户消息明显使用另一种语言时，AI 可以跟随用户消息语言。中文界面下默认中文回复。

### 历史范围

会话详情首版返回最近 50 条消息，不做分页。后续长会话再增加 `beforeMessageId` 和 `pageSize`。

## 系统提示词设计

`PracticePromptBuilder` 负责构造题目聊天提示词。稳定系统提示词建议如下：

```text
你是 algo-mentor 的算法刷题教练，正在帮助用户围绕当前 LeetCode 题目训练。

核心规则：
1. 只围绕当前题目、当前学习计划阶段、算法思路、复杂度、代码实现和 LeetCode 反馈进行回答。
2. 默认先引导用户理解关键观察、状态定义、边界条件和复杂度，不要一上来展开完整题解。
3. 如果用户明确要求“直接给答案”“给完整代码”“给 Java 解法”，直接给完整思路、复杂度和 Java 代码，不要再追问确认。
4. 不要编造题面、样例、约束、隐藏条件或 LeetCode 结果；如果上下文没有提供，就说明无法确认。
5. 用户粘贴代码时，先指出关键问题和最小修改建议，再给必要的修正版。
6. 用户粘贴 LeetCode 错误、WA/TLE/编译错误时，优先帮助定位原因，必要时给可执行的修复步骤。
7. 默认使用界面语言回复；如果用户明显使用另一种语言提问，则跟随用户语言。
8. 输出 Markdown，代码块标注语言；复杂度用 Big-O 表达。
```

该提示词属于跨请求公共契约，落地时应放到 practice 模块的 prompt builder 或常量类中，不在 controller 中硬编码。

## 题目上下文块

第二条 system message 只承载当前业务上下文，建议格式：

```text
当前训练上下文：

学习计划：
- planId: 12
- goal: 4 周内用 Java 准备后端算法面试
- level: INTERMEDIATE
- programmingLanguage: Java
- phaseIndex: 1
- phaseTitle: 哈希表基础
- phaseFocus: 建立哈希查找和频次统计模式

题目：
- slug: two-sum
- frontendId: 1
- title: Two Sum
- titleCn: 两数之和
- difficulty: EASY
- tags: Array, Hash Table
- leetcodeUrl: https://leetcode.com/problems/two-sum/

题面 Markdown：
---
# Two Sum
...
---
```

上下文块要求：

- 来自后端已校验的数据源，不能由前端提交任意题面。
- 题面为空时写入明确空态，例如 `题库暂未提供题面 Markdown。`
- 不包含 API key、Authorization、用户隐私内容或完整 LeetCode 提交历史。
- 后续如果题面过长，再加入截断策略和 metadata 标识；v1 先完整注入当前题面。

## 后端设计

### 数据模型

#### 表职责边界

`learning_plan_problem_progress` 是题目进度事实表。它回答的是“某个用户在某个学习计划的某个阶段里，这道题当前做到什么状态”。方案详情页、完成率统计、阶段复盘、后续错题推荐都应该读取这张表。它不关心聊天内容，也不依赖是否已经创建聊天会话；例如用户未来在方案详情页直接把题目标记为跳过或完成，也只需要更新这张表。

`practice_session` 是题目训练聊天线程锚点表。它回答的是“这个用户围绕这道计划题的聊天线程是哪一个”。聊天记录本身继续存放在现有 Agent runtime 的 `agent_message` 中，模型运行轨迹继续存放在 `agent_run` 和 trace 表中；`practice_session` 只负责把学习计划题目和 `agent_task_id` 关联起来，并记录题面 seed 消息、最后消息时间和会话状态。

两张表不合并，是因为进度和聊天生命周期不同：

- 进度是学习计划的稳定业务事实，可以独立于聊天存在。
- 聊天是围绕题目的交互线程，负责上下文恢复、消息历史和 SSE 运行关联。
- 后续即使增加“方案详情页直接跳过题目”“归档某次聊天线程”“重建聊天上下文”等能力，也不会让进度状态和聊天运行态互相污染。

简化理解：

```text
learning_plan_problem_progress = 这道题做没做、做到哪一步
practice_session               = 这道题的聊天会话是哪一个
```

新增 `learning_plan_problem_progress`：

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
updated_at TIMESTAMPTZ NOT NULL,
UNIQUE (user_id, plan_id, phase_index, problem_slug),
CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED'))
```

新增 `practice_session`：

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
updated_at TIMESTAMPTZ NOT NULL,
UNIQUE (user_id, plan_id, phase_index, problem_slug),
UNIQUE (agent_task_id),
CHECK (status IN ('ACTIVE', 'ARCHIVED'))
```

`agent_task.metadata` 写入 practice 关联信息：

```json
{
  "scenario": "PRACTICE_CHAT",
  "practiceSessionId": 100,
  "planId": 12,
  "phaseIndex": 1,
  "problemSlug": "two-sum"
}
```

`agent_message.metadata` 使用以下契约：

```json
{
  "messageType": "PROBLEM_STATEMENT"
}
```

普通聊天消息使用：

```json
{
  "messageType": "CHAT"
}
```

这些字符串应放入常量类或枚举中，避免散落在 SQL、service 和前端 mapper 中。

### API 契约

创建或复用题目会话：

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

刷新会话：

```text
GET /api/practice-sessions/{sessionId}
```

发送消息并流式回复：

```text
POST /api/practice-sessions/{sessionId}/messages/stream
Idempotency-Key: <client-generated-key>
Content-Type: application/json

{
  "message": "我想先用哈希表做。"
}
```

更新进度：

```text
PATCH /api/practice-sessions/{sessionId}/progress-status

{
  "status": "COMPLETED"
}
```

v1 聊天页只暴露 `COMPLETED` 操作；方案详情页如果需要跳过题目，可另设计划题目维度接口。

### 创建会话流程

```text
POST create-or-reuse
  -> 解析当前登录用户
  -> 校验 plan 属于当前用户
  -> 校验 phaseIndex + slug 存在于 plan snapshot
  -> 读取题库 ProblemDetail
  -> upsert learning_plan_problem_progress
  -> upsert practice_session
  -> 创建或复用 agent_task
  -> 如无题面 seed，创建 seed turn + assistant PROBLEM_STATEMENT message
  -> NOT_STARTED/SKIPPED 更新为 IN_PROGRESS，COMPLETED 不回退
  -> 返回 session、problem、progress、最近 50 条 messages
```

该流程必须在事务内保证幂等。并发打开两个标签页时，唯一约束应保证只生成一个 session、一个 progress 记录和一条题面 seed。

### 发送消息流程

```text
POST messages/stream
  -> 校验 session 属于当前用户
  -> 校验同一 task 没有运行中 run，或同一 Idempotency-Key 复用已有 run
  -> 写入 user agent_message，messageType = CHAT
  -> 用 PracticePromptBuilder 组装 system 规则和题目上下文
  -> 读取最近普通聊天历史，排除 PROBLEM_STATEMENT
  -> 调用 AgentLoopRunner stream
  -> SSE 转发 content_delta/message_end/agent_run_end/error
  -> run 成功后持久化 assistant message，messageType = CHAT
  -> 更新 practice_session.last_message_at
```

当前 `AgentConversationService` 的 `ContextAssembler` 只有 `system + summary + history + current user`。实现 practice 时需要扩展上下文组装能力，或新增 practice 专用 assembler，以支持额外的题目上下文 system message 和消息类型过滤。

### AI 治理

`purpose` 继续使用 `AiPurpose.LEARNING_CHAT`，减少治理配置扩散。`source` 使用新增或专用值 `PRACTICE_CHAT`，并在 admission metadata 中写入：

```json
{
  "scenario": "PRACTICE_CHAT",
  "practiceSessionId": 100,
  "planId": 12,
  "phaseIndex": 1,
  "problemSlug": "two-sum"
}
```

request size 至少统计当前用户消息字节数；如后续治理需要更精确成本，可把题面上下文估算纳入 metadata。

## SSE 与前端体验

### 展示策略

SSE 不直接展示完整 agent 调试日志。用户可见层只展示：

- 当前 assistant 气泡内的 Markdown 增量。
- 首 token 前的轻量状态条，例如 `正在组织思路...`。
- 完成状态，例如 `回复完成`，通常不需要长期保留。
- 错误状态，例如 `回复失败，请重试`。

推荐事件映射：

```text
connection_open / agent_run_start  -> 创建 pending assistant 气泡，显示“正在组织思路...”
message_start                      -> 状态改为“正在回复...”
content_delta                      -> 追加到 pending assistant 气泡
message_end                        -> 标记 assistant 气泡生成完成
agent_run_end                      -> 结束发送状态，释放 composer
error / agent_error                -> pending 气泡显示错误和重试入口
heartbeat                          -> 不展示
tool_call_* / agent_tool_*         -> v1 不开放工具，前端可忽略
```

### 自动滚动

消息列表只在用户接近底部时自动滚动到底部。用户主动上滑查看历史时，不强制拉回。可用阈值：

```text
scrollHeight - scrollTop - clientHeight < 96px
```

### Composer

输入框使用多行 textarea，支持粘贴代码和 LeetCode 反馈。发送中：

- 禁用重复发送。
- 保留停止按钮或取消行为。
- 如果用户刷新页面，已写入的 user message 仍通过会话详情恢复。

### 工具栏

工具栏展示：

- 返回方案。
- 题号、题名、难度。
- 当前进度状态。
- Review 记录占位。
- LeetCode 外链。
- 题目已完成按钮。

点击完成后调用 progress status API。重复点击应返回当前 `COMPLETED` 状态，不报错。

## 前端类型

新增类型建议集中在 `frontend/src/types/api.ts`：

```ts
export type PracticeProgressStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';
export type PracticeMessageRole = 'USER' | 'ASSISTANT';
export type PracticeMessageType = 'PROBLEM_STATEMENT' | 'CHAT';

export interface PracticeSessionResponse {
  session: PracticeSessionSummary;
  problem: PracticeProblemSummary;
  messages: PracticeMessage[];
}
```

API 封装放在 `frontend/src/services/api.ts`：

- `createOrReusePracticeSession`
- `getPracticeSession`
- `streamPracticeMessage`
- `updatePracticeProgressStatus`

`streamPracticeMessage` 可复用现有 `readEventStream`，但路径和请求体使用 practice 专用契约。

## 边界与错误处理

- plan 不存在或不属于当前用户：返回业务错误，不暴露其他用户资源信息。
- phase/slug 不在计划快照中：返回题目不属于该计划。
- 题库没有对应 slug：会话不创建，提示题库题目不存在。
- 题面为空：允许创建会话，seed 消息显示题面暂不可用。
- 同一 session 有运行中 run：返回 `AGENT_RUN_IN_PROGRESS` 或复用相同幂等键的 run。
- SSE 断开：用户消息已保存；刷新后通过会话详情恢复，若 assistant 尚未持久化则显示无回复或允许重试。
- 完成题目后再次进入：状态保持 `COMPLETED`，不回退为 `IN_PROGRESS`。

## 测试计划

后端单元测试：

- `PracticePromptBuilderTest`：验证系统规则、题目上下文、语言和计划字段。
- `PracticeSessionServiceTest`：验证会话创建幂等、题面 seed 只写一次、状态流转正确。
- `PracticeMessageStreamServiceTest`：验证发送消息写入 user message、排除 `PROBLEM_STATEMENT` 历史、构造 agent request metadata。
- `PracticeProgressServiceTest`：验证完成操作可重复。

后端 mapper/集成测试：

- practice 表唯一约束和 upsert 行为。
- `agent_message.metadata.messageType` 读写。
- 最近 50 条消息排序和过滤。

前端测试：

- 进入聊天页调用创建会话接口并渲染题面 seed。
- 发送消息后乐观展示用户气泡。
- `content_delta` 追加到 pending assistant 气泡。
- `agent_run_end` 后释放 composer。
- `error/agent_error` 显示错误状态和重试入口。
- 点击题目已完成后更新状态 badge。

建议验证命令：

```bash
make backend-test
make frontend-test
```

## 后续演进

- 消息分页：增加 `GET /api/practice-sessions/{sessionId}/messages?beforeMessageId=&pageSize=`。
- 代码 Review：从普通聊天中沉淀结构化 review 记录。
- 工具能力：按 session 限权开放读取当前题、查相似题或读取历史复盘。
- 上下文压缩：长会话后为 practice session 增加 active summary 和题面引用策略。
- 进度看板：基于 `learning_plan_problem_progress` 汇总计划完成率、错题复盘和阶段复盘建议。
