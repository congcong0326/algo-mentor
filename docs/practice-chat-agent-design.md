# 题目聊天 Agent 研发设计

## 背景

`practice-chat-workbench-design.md` 已定义题目聊天工作台、题目状态和训练会话方向。`practice-chat-system-prompt-assembly-design.md` 已进一步把题目聊天 prompt 从专用 `PracticePromptBuilder` 调整为通用 Prompt Assembly 底座，并且阶段 2 到阶段 3 的部分能力已经落到 `agent-core` 与 `mentor-application/practice`。

本设计重新收敛题目聊天 Agent 的产品闭环，目标是把“学习计划中的某一道题”打通为稳定的聊天训练线程：

- `practice_session` 是题目聊天的产品入口。
- `learning_plan_problem_progress` 是计划题目进度事实。
- `agent_task`、`agent_turn`、`agent_message`、`agent_run` 继续作为底层 Agent runtime。
- Prompt Assembly 负责模型上下文，不再新增独立 practice prompt builder。

第一版题目聊天仍定位为 LeetCode 外部做题的 AI 教练空间，不做内置 IDE、在线评测、结构化代码 Review 或跨题推荐。

## 目标

- 用户从学习计划题目进入聊天页时，自动创建或复用训练会话。
- 后端确定性写入第一条题面 seed 消息，避免额外模型成本和题面幻觉。
- 用户可以输入思路、问题、代码片段或 LeetCode 反馈，AI 通过 SSE 流式回复。
- AI 回复围绕当前题和当前学习计划阶段，不主动发散到其他题。
- 默认采用教练式引导；当用户明确要求答案或代码时，直接给完整思路、复杂度和 Java 代码。
- 聊天页展示轻量流式状态，而不是完整 agent/tool 调试事件。
- 前端通过 practice session 专用 API 完成创建、恢复、发送消息和标记完成。

## 已确认决策

### 产品入口

题目聊天的产品入口是 practice session 专用 API，不让前端直接使用通用 `/api/agent/conversations/stream` 承载题目训练。

```text
POST /api/learning-plans/{planId}/phases/{phaseIndex}/problems/{slug}/practice-session
GET  /api/practice-sessions/{sessionId}
POST /api/practice-sessions/{sessionId}/messages/stream
PATCH /api/practice-sessions/{sessionId}/progress-status
```

通用 `AgentConversationService`、`AgentConversationRunCoordinator` 和 `AgentLoopRunner` 作为底层运行能力复用；practice session 负责产品校验、题目上下文、进度和消息恢复。

### Prompt 组装

题目聊天直接使用 Prompt Assembly profile，模型请求按以下 canonical 顺序组装：

```text
system: STATIC_INSTRUCTION    平台与安全基线
system: SCENARIO_POLICY       题目聊天教学策略
system: RUNTIME_CONTEXT       当前训练上下文
system: MEMORY_SUMMARY        可选 active summary
history: HISTORY              最近普通聊天消息
user: CURRENT_USER_MESSAGE    当前用户消息
```

原因：

- 稳定规则和业务上下文分离，便于调试和后续版本迭代。
- 题目上下文不伪装成用户发言，也不伪装成模型历史输出。
- 历史聊天只保留真实 user/assistant 讨论，避免第一条题面 seed 影响模型判断。
- Prompt profile、section version、token estimate、裁剪记录和 content hash 写入 `AgentRequest.metadata()`。

### 第一条题面消息角色

第一条题面消息在不同边界的角色如下：

```text
UI 展示       assistant 气泡，标签为“教练”
数据库存储    agent_message.role = assistant
消息类型      agent_message.metadata.messageType = "PROBLEM_STATEMENT"
模型上下文    独立 RUNTIME_CONTEXT system 片段，不作为 assistant 历史消息传入
```

这样既能让用户看到“教练先给出题面”的自然体验，又不会让模型误以为自己上一轮已经完成了某个回答。

### 工具调用范围

题目聊天 v1 不开放题库工具给模型。当前题目、学习计划阶段和题面由后端服务端校验后注入 prompt。后续如果增加读取当前题、查相似题或读取历史复盘工具，应通过 `TOOL_RESULT` 片段接入 Prompt Assembly。

### 回复语言

默认跟随前端 locale。用户消息明显使用另一种语言时，AI 可以跟随用户消息语言。中文界面下默认中文回复。

### 历史范围

会话详情首版返回最近 50 条消息，不做分页。后续长会话再增加 `beforeMessageId` 和 `pageSize`。

## Prompt Assembly 接入

### Profile 与片段

practice chat 使用 `PRACTICE_CHAT_V1` profile。片段来源位于 `mentor-application` 的 practice 包，底层结构和算法位于 `agent-core`。

核心片段：

- `practice.base.identity`：平台与安全基线，`STATIC_INSTRUCTION`，`SYSTEM_STATIC`。
- `practice.strategy.coach`：教练式教学策略，`SCENARIO_POLICY`，`SYSTEM_STATIC`。
- `practice.context.problem`：学习计划、阶段、题目和题面，`RUNTIME_CONTEXT`，`SERVER_VALIDATED`。
- `practice.memory.active-summary`：可选会话摘要，`MEMORY_SUMMARY`，`MODEL_GENERATED`。
- `practice.history.*`：普通聊天历史，`HISTORY`，保持原 user/assistant 角色。
- `practice.current-user-message`：当前用户消息，`CURRENT_USER_MESSAGE`，最后一条 user message。

### 当前训练上下文

`RUNTIME_CONTEXT` 只使用后端已校验的数据源，建议渲染格式：

```text
学习计划：
- planId: 12
- goal: 4 周内用 Java 准备后端算法面试
- level: INTERMEDIATE
- programmingLanguage: Java
- locale: zh-CN

阶段：
- phaseIndex: 1
- title: 哈希表基础
- focus: 建立哈希查找和频次统计模式

题目：
- slug: two-sum
- frontendId: 1
- title: Two Sum
- titleCn: 两数之和
- difficulty: EASY
- tags: Array, Hash Table
- leetcodeUrl: https://leetcode.com/problems/two-sum/

题面：
<problem_statement>
# Two Sum
...
</problem_statement>
```

要求：

- 题面为空时写入明确空态，例如 `题库暂未提供题面 Markdown。`
- 题面和结构化字段分开渲染，避免大段题面覆盖题号、难度、标签等关键事实。
- 不包含 API key、Authorization、用户隐私内容或完整 LeetCode 提交历史。
- 用户输入中的伪 `system:`、XML 闭合标签、Markdown fence escape 只能作为文本处理。

### 历史过滤

模型历史只包含普通聊天消息：

- 排除 `messageType = PROBLEM_STATEMENT` 的题面 seed。
- 保留 `messageType = CHAT` 的 user/assistant 消息。
- 按 sequenceNo 排序后取最近 N 条。
- 当前用户消息不从 history 重复注入，必须作为最后一条 `CURRENT_USER_MESSAGE`。

## 后端设计

### 数据模型

#### 表职责边界

`learning_plan_problem_progress` 是题目进度事实表。它回答的是“某个用户在某个学习计划的某个阶段里，这道题当前做到什么状态”。方案详情页、完成率统计、阶段复盘、后续错题推荐都应该读取这张表。它不关心聊天内容，也不依赖是否已经创建聊天会话。

`practice_session` 是题目训练聊天线程锚点表。它回答的是“这个用户围绕这道计划题的聊天线程是哪一个”。聊天记录本身继续存放在 Agent runtime 的 `agent_message` 中，模型运行轨迹继续存放在 `agent_run` 和 trace 表中；`practice_session` 只负责把学习计划题目和 `agent_task_id` 关联起来，并记录题面 seed 消息、最后消息时间和会话状态。

简化理解：

```text
learning_plan_problem_progress = 这道题做没做、做到哪一步
practice_session               = 这道题的聊天会话是哪一个
agent_task                     = 底层 Agent runtime 线程
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

迁移脚本放在 `backend/mentor-api/src/main/resources/db/migration`，版本号使用当前 Flyway 全局序列的下一个版本，例如 `V12__practice_session_schema.sql`。

### Metadata 契约

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
  "messageType": "PROBLEM_STATEMENT",
  "scenario": "PRACTICE_CHAT",
  "practiceSessionId": 100,
  "planId": 12,
  "phaseIndex": 1,
  "problemSlug": "two-sum"
}
```

普通聊天消息使用：

```json
{
  "messageType": "CHAT",
  "scenario": "PRACTICE_CHAT",
  "practiceSessionId": 100,
  "planId": 12,
  "phaseIndex": 1,
  "problemSlug": "two-sum"
}
```

这些字符串应放入常量类或枚举中，避免散落在 SQL、service 和前端 mapper 中。

### Repository 与端口

practice 业务不直接写 `agent_*` 表。建议新增 practice repository 管理业务表，并新增或拆分 Agent runtime 端口管理 task/message：

```java
public interface PracticeSessionRepository {
  PracticeSession upsertAndLockSession(...);
  PracticeProgress upsertAndAdvanceProgress(...);
  Optional<PracticeSession> findSessionForUser(...);
  PracticeSession attachAgentTask(...);
  PracticeSession attachProblemStatementMessage(...);
  PracticeProgress updateProgressStatus(...);
  void touchLastMessageAt(...);
}
```

```java
public interface AgentTaskMessageRepository {
  AgentTaskRef createTask(AgentTaskCreationRequest request);
  AgentMessage createAssistantSeedMessage(AgentAssistantSeedMessageRequest request);
  List<AgentMessage> messages(long taskId, int messageLimit);
}
```

也需要扩展现有 run preparation 能力：

- 为指定 `agent_task_id` 创建 user turn/message/run。
- user message 支持写入 metadata。
- 幂等键重放时不重复写 user message。
- running run 冲突时返回 `AGENT_RUN_IN_PROGRESS`。

SQL 仍留在 `agent-persistence-postgres`，`mentor-api` 不直接拥有 agent runtime SQL。

## API 契约

### 创建或复用题目会话

```text
POST /api/learning-plans/{planId}/phases/{phaseIndex}/problems/{slug}/practice-session?locale=zh-CN
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

### 刷新会话

```text
GET /api/practice-sessions/{sessionId}
```

返回同 `PracticeSessionResponse`，方便前端创建后渲染和刷新恢复复用同一套 mapper。

### 发送消息并流式回复

```text
POST /api/practice-sessions/{sessionId}/messages/stream
Idempotency-Key: <client-generated-key>
Content-Type: application/json

{
  "message": "我想先用哈希表做。"
}
```

### 更新进度

```text
PATCH /api/practice-sessions/{sessionId}/progress-status

{
  "status": "COMPLETED"
}
```

v1 聊天页只暴露 `COMPLETED` 操作；方案详情页如果需要跳过题目，可另设计划题目维度接口。

## 服务流程

### 创建会话流程

```text
POST create-or-reuse
  -> 解析当前登录用户
  -> 校验 plan 属于当前用户
  -> 校验 phaseIndex + slug 存在于 plan snapshot
  -> 读取题库 ProblemDetail
  -> upsert learning_plan_problem_progress
  -> upsert practice_session 并锁定业务键
  -> 如 session 无 agent_task_id，创建 agent_task 并回写
  -> 如 session 无题面 seed，创建 seed turn + assistant PROBLEM_STATEMENT message 并回写
  -> NOT_STARTED/SKIPPED 更新为 IN_PROGRESS，COMPLETED 不回退
  -> 返回 session、problem、progress、最近 50 条 messages
```

该流程必须在事务内保证幂等。并发打开两个标签页时，唯一约束和锁定策略应保证只生成一个 session、一个 progress 记录、一条 `agent_task` 绑定和一条题面 seed。

seed 内容要求：

- 使用后端已校验的题面 Markdown。
- 题面为空时写 `题库暂未提供题面 Markdown。`
- 作为 assistant 气泡展示。
- 不进入模型 assistant history。

### 发送消息流程

```text
POST messages/stream
  -> 解析当前登录用户
  -> 查询并锁定 practice_session，确认属于当前用户且 ACTIVE
  -> 读取 progress、plan、phase、problem detail，重建 PracticeChatContext
  -> 校验同一 agent_task 没有运行中 run，或同一 Idempotency-Key 复用已有 run
  -> 写入 user agent_message，messageType = CHAT
  -> 用 PromptAssembler + PRACTICE_CHAT_V1 profile 组装上下文
  -> 读取最近普通聊天历史，排除 PROBLEM_STATEMENT
  -> 调用 AgentLoopRunner stream
  -> SSE 转发 content_delta/message_end/agent_run_end/error
  -> run 成功后持久化 assistant message，messageType = CHAT
  -> 更新 practice_session.last_message_at
```

建议新增 `PracticeMessageStreamService` 承接产品校验、上下文构建、AI governance 和 `practiceSessionId` metadata。它可以复用底层 run coordinator，但不要让通用 `AgentConversationService` 反向依赖 practice session。

### AI 治理

`purpose` 继续使用 `AiPurpose.LEARNING_CHAT`，减少治理配置扩散。`source` 使用专用 `PRACTICE_CHAT`，并在 admission metadata 中写入：

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
- 首版可以用 AbortController 断开前端流；服务端取消和续跑能力后续补强。
- 如果用户刷新页面，已写入的 user message 仍通过会话详情恢复。

### 工具栏

工具栏展示：

- 返回方案。
- 题号、题名、难度。
- 当前进度状态。
- LeetCode 外链。
- 题目已完成按钮。

点击完成后调用 progress status API。重复点击应返回当前 `COMPLETED` 状态，不报错。

## 前端设计

### 加载流程

`PracticeChatWorkbench` 从题面展示页升级为真正聊天页：

```text
进入 /learning-plans/{planId}/phases/{phaseIndex}/problems/{slug}/chat
  -> POST createOrReusePracticeSession
  -> 渲染 toolbar/session/problem/progress/messages
  -> composer 启用
```

前端不要再单独调用 `getProblemDetail` 来构造题面气泡。题面由 create/get session 返回，确保 UI 展示和模型上下文来自同一份后端校验数据。

### 前端类型

新增类型集中在 `frontend/src/types/api.ts`：

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

### 前端状态

```text
idle        可输入
loading     创建/刷新 session
streaming   已发送，等待或接收 assistant 流
error       session 加载或发送失败
blocked     AGENT_RUN_IN_PROGRESS
```

发送消息时：

- 立即乐观追加 user `CHAT` 气泡。
- 创建 pending assistant 气泡。
- `content_delta` 持续追加到 pending assistant content。
- `message_end` 标记生成完成。
- `agent_run_end` 释放输入框。
- 错误时 pending assistant 显示失败状态和重试入口。

## 边界与错误处理

- plan 不存在或不属于当前用户：返回业务错误，不暴露其他用户资源信息。
- phase/slug 不在计划快照中：返回题目不属于该计划。
- 题库没有对应 slug：会话不创建，提示题库题目不存在。
- 题面为空：允许创建会话，seed 消息显示题面暂不可用。
- 同一 session 有运行中 run：返回 `AGENT_RUN_IN_PROGRESS` 或复用相同幂等键的 run。
- SSE 断开：用户消息已保存；刷新后通过会话详情恢复，若 assistant 尚未持久化则显示无回复或允许重试。
- 完成题目后再次进入：状态保持 `COMPLETED`，不回退为 `IN_PROGRESS`。

## 测试计划

### 后端单元测试

- `PracticeSessionServiceTest`：验证会话创建幂等、题面 seed 只写一次、状态流转正确。
- `PracticeMessageStreamServiceTest`：验证发送消息写入 user message、排除 `PROBLEM_STATEMENT` 历史、构造 agent request metadata。
- `PracticeProgressServiceTest`：验证完成操作可重复。
- `PracticeChatPromptSectionProviderTest`：验证稳定片段、题目上下文、语言、intent 和题面空态。

### 后端 mapper/集成测试

- practice 表唯一约束和 upsert 行为。
- `agent_message.metadata.messageType` 读写。
- 最近 50 条消息排序和过滤。
- `PracticeSessionController` 创建、读取、发送和完成接口。

### 前端测试

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

## 实施顺序

1. 新增 practice 数据迁移、repository 和 Agent runtime task/message 端口。
2. 实现 create/get practice session API。
3. 实现 practice message stream API，接入 AI governance 与 Prompt Assembly。
4. 前端 `PracticeChatWorkbench` 接入专用 API 和 SSE 状态。
5. 补齐后端、前端测试并运行最小相关验证。

## 后续演进

- 消息分页：增加 `GET /api/practice-sessions/{sessionId}/messages?beforeMessageId=&pageSize=`。
- 代码 Review：从普通聊天中沉淀结构化 review 记录。
- 工具能力：按 session 限权开放读取当前题、查相似题或读取历史复盘。
- 上下文压缩：长会话后为 practice session 增加 active summary 和题面引用策略。
- 进度看板：基于 `learning_plan_problem_progress` 汇总计划完成率、错题复盘和阶段复盘建议。
