# Agent 多轮上下文召回与压缩研发设计

## 背景

`backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentLoopRunner.java` 当前以一次性执行为主：每次 run 在内存中通过 `List<LlmMessage>` 维护当前消息，模型返回 tool call 后追加 assistant tool call 与 tool result，再进入下一步。这个设计适合单次主题讲解，但不适合长期多轮对话。

算法学习场景中，用户会围绕同一道题、同一个知识点或一条学习计划持续追问。系统需要在每轮请求时召回历史上下文，同时避免将完整历史、所有工具调用和大体积工具结果无差别塞入模型上下文。后续还需要支持上下文压缩、工具结果摘要、长期记忆、调试回放和成本控制。

因此，多轮对话不应只是在 `AgentLoopRunner` 内部追加历史消息，而应在应用层建立稳定的会话、消息、运行轨迹、压缩产物和上下文快照模型。

## 设计目标

- 支持同一任务下的多轮用户对话。
- 保留完整原始消息、工具调用和模型运行轨迹，方便审计、调试和学习过程沉淀。
- 支持按策略组装上下文，而不是简单拼接全部历史。
- 支持滑动窗口、滚动摘要、范围摘要、工具结果摘要、大上下文再压缩等多种压缩策略。
- 每次真实 LLM 调用记录最终 `LlmCompletionRequest` 快照，保证问题可追踪、结果可回放。
- 将上下文召回和压缩逻辑放在 application 层，保持 `agent-core` 作为通用 agent 内核底座，只负责 agent loop 编排。
- 明确 `agent-core` 只消费通用 `messages`、`options`、`tools`、`metadata`，不依赖 `LearningTopic`、题目、学习计划等学习业务模型。
- 由 `mentor-application` 负责把题目、学习计划、`LearningTopic` 等业务上下文转换成 `LlmMessage` 和 metadata。
- 为后续引入向量检索、用户画像记忆、学习状态记忆预留扩展点。

## 非目标

- 第一阶段不实现复杂向量记忆和语义检索。
- 第一阶段不要求所有压缩都实时完成，可以先支持同步关键摘要和异步补充摘要。
- 不把 tool call、tool result 当作普通聊天消息直接展示给用户。
- 不让 `AgentLoopRunner` 直接依赖数据库、HTTP、Spring Repository 或具体存储实现。
- 不在 `agent-core` 的长期请求模型中引入 `LearningTopic`、题目、学习计划、课程进度等业务字段。
- 不要求第一阶段立刻删除现有 topic explanation 兼容入口，但它只能停留在业务 adapter 或兼容层。

## 总体设计原则

### 原始事实不可变

用户消息、assistant 最终回复、tool call 参数、tool result、LLM step、usage 和错误信息都属于事实记录。事实记录不应因为压缩策略变化而被覆盖或删除。

这里的不可变指审计语义上的追加、版本化和可解释，不等于永远保留明文。tool call 参数、tool result、final request snapshot、错误信息和用户提交的代码都可能包含隐私、密钥、Authorization、访问令牌或业务敏感内容；进入持久化前应按字段做 redaction，确需保留原文时应加密存储、限制访问并设置 retention。

上下文压缩只生成新的派生产物，例如摘要、裁剪版、替代说明或长期记忆。原始数据在 retention 周期内保留；超过周期后可以归档、加密冷存储、脱敏降级或删除明文并留下 tombstone、hash 和必要引用，派生产物可以重建、替换或废弃。

### 安全、脱敏与留存优先

对话上下文和运行轨迹默认按敏感数据处理：

- 持久化前过滤 API key、Authorization、cookie、JWT、数据库连接串、邮箱、手机号、真实用户标识和高风险代码片段中的密钥。
- request snapshot、tool 参数、tool result、错误堆栈和调试 metadata 应支持字段级 redaction；脱敏规则需要可版本化，便于解释历史记录为什么被遮盖。
- 明文 snapshot 和大工具结果应使用服务端加密或数据库加密能力，并按用户、任务、运维角色区分读取权限；普通业务查询不应默认返回完整 trace。
- retention 策略需要区分在线排障期、归档期和删除期。删除明文时保留 tombstone、`request_hash`、时间、范围和删除原因，避免误以为记录从未存在。

### 模型上下文是选择结果

数据库里的历史消息不等同于本轮送入模型的上下文。本轮上下文应由 `ContextAssembler` 按预算和策略选择：

- 哪些消息保留原文。
- 哪些消息范围使用摘要。
- 哪些工具结果使用摘要。
- 哪些内部 trace 不进入模型上下文。
- 哪些内容只保留引用或占位说明。

### 压缩产物可追溯

每个摘要或压缩结果都需要记录来源范围、生成模型、prompt 版本、token 估算和元数据。这样可以判断摘要覆盖到哪里，也可以在摘要质量不佳时重建。

### 每轮上下文可回放

每次真实调用模型前，应保存 `agent_context_snapshot`。这里的快照不是 application 预组装出的粗略文本列表，而是某个 run step 最终送入 `llmGateway.stream` 的 `LlmCompletionRequest`。

保存点应位于 `AgentLoopRunner` 每个 step 构造 `LlmCompletionRequest` 之后、所有 request interceptor 处理之后、调用 `llmGateway.stream` 之前。这样快照能覆盖第 2 步及后续 step 中由 agent loop 动态追加的 assistant tool calls、tool results 和其他运行时消息。

application 层仍然可以记录候选上下文、来源选择和预算决策，用于策略分析；但真实回放应以 core 生命周期观测点保存的 final request snapshot 为准。

这对排查“模型为什么这样回答”非常关键，也能支撑后续 prompt 评测和回归测试。

## 核心概念

```text
AgentTask
  一条长期任务或会话线程，例如“学习动态规划”或“讲解某道题”。

AgentTurn
  用户的一轮意图。一次 turn 只表达用户想解决的问题，不等同于某一次执行。

AgentMessage
  用户可见消息，只包含 user / assistant，用于聊天界面展示。user message 归属于 turn，assistant message 归属于被接受的 run。

AgentRun
  一次 AgentLoopRunner 执行尝试。同一个 turn 可以有多次 run attempt，例如失败重试、用户重新生成、系统自动重试。

AgentRunStep
  一次 LLM 调用。一个 run 内可能因为 tool call 存在多个 step。

AgentToolCall
  模型内部工具调用，包括参数、结果、状态、耗时和错误。

AgentArtifact
  派生产物，例如会话摘要、消息范围摘要、工具结果摘要、长期记忆、上下文压缩结果。artifact 通过 scope 表达归属范围，不默认绑定到单个 task。

AgentContextSnapshot
  某个 run step 实际送入 LLM 的最终 LlmCompletionRequest 快照。

CoreAgentRequest / AgentRequest
  `agent-core` 消费的通用执行请求。它不表达算法学习领域语义，只包含 run/request 标识、已经组装好的 LLM messages、metadata、可选执行参数和工具配置。
```

术语约定：

- `AgentRequest` 和 `CoreAgentRequest` 在本文中表达同一类通用 core 输入模型；后续实现可二选一命名，但不应重新引入 `LearningTopic` 等业务字段。
- `candidate_context` 指 application 层 `ContextAssembler` 选出的候选上下文、预算决策和 source refs。它用于策略分析，不等同于真实送入模型的请求。
- `request snapshot` 泛指 LLM 请求快照；`final request snapshot` 专指经过 request interceptor 之后、调用 `llmGateway.stream` 之前的最终 `LlmCompletionRequest`，并由 `agent_context_snapshot` 保存。
- `request_snapshot_json` 是 final request snapshot 的主要存储字段。生产链路第一阶段优先保存完整脱敏 JSON；归档或 hash-only 只属于后续降级/留存策略。

## 推荐表模型

### agent_task

会话或任务主表，使用自增 ID 作为主键。

```sql
CREATE TABLE agent_task (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NULL,
  title VARCHAR(255) NULL,
  status VARCHAR(32) NOT NULL,
  system_prompt TEXT NULL,
  active_summary_artifact_id BIGINT NULL,
  context_policy JSONB NOT NULL DEFAULT '{}',
  metadata JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

字段说明：

- `system_prompt`：当前任务使用的系统提示词或任务级提示词。
- `active_summary_artifact_id`：当前 task scope 下有效的会话摘要，指向 `agent_artifact`，不用于长期用户记忆或全局记忆。
- `context_policy`：任务级上下文策略配置，例如最大保留轮次、摘要阈值、token 预算。
- `metadata`：题目 ID、学习计划 ID、知识点标签等扩展信息。

为什么这样设计：

- 任务主表只保存稳定的会话级状态，不承载完整历史。
- 当前摘要使用 artifact 指针，而不是直接内嵌文本，方便摘要版本管理和回滚。
- `context_policy` 放在任务级别，便于不同场景采用不同召回策略，例如刷题讲解、错题复盘、学习计划对话。

### agent_turn

用户一轮意图。`AgentTurn` 表达“用户这一次想让 agent 处理什么”，不表达某一次具体执行。失败重试、用户点击重新生成、系统自动重试都应在同一个 turn 下新增 `agent_run`，而不是重复插入相同 user message。

```sql
CREATE TABLE agent_turn (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  sequence_no BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  user_message_id BIGINT NULL,
  assistant_message_id BIGINT NULL,
  accepted_run_id BIGINT NULL,
  current_run_id BIGINT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

字段说明：

- `user_message_id`：本轮用户输入，只写入一次。重试不应重复插入同一轮 user message。
- `assistant_message_id`：当前对用户生效的 assistant 回复，应与 `accepted_run_id` 或 `current_run_id` 对应的成功 run 一致。
- `accepted_run_id`：当前已被接受并对用户生效的 run。用户重新生成并接受新结果后更新该指针。
- `current_run_id`：当前最新执行中的 run，可用于 SSE 状态查询和“正在重新生成”展示。run 完成并被接受后可同步到 `accepted_run_id`。

约束建议：

- 对 `(task_id, sequence_no)` 建唯一索引。
- `accepted_run_id`、`current_run_id` 均应指向同一个 turn 下的 `agent_run`。

为什么这样设计：

- `turn` 将用户意图、用户消息、当前有效 assistant 回复和多个 run attempt 串起来。
- 聊天界面可按 `turn.sequence_no` 展示稳定对话轮次，内部执行可按 `agent_run.attempt_no` 排查。
- 避免把“重新执行同一意图”和“用户发起新意图”混在一起，保证重试不会污染消息历史。

### agent_message

用户可见消息表。

```sql
CREATE TABLE agent_message (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  turn_id BIGINT NOT NULL,
  run_id BIGINT NULL,
  role VARCHAR(32) NOT NULL,
  content TEXT NOT NULL,
  sequence_no BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  token_estimate INT NULL,
  metadata JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

约束建议：

- `role` 第一阶段只允许 `user`、`assistant`。
- `run_id` 对 user message 为空；对 assistant message 指向生成该回复的 `agent_run`。
- `sequence_no` 在同一个 task 内递增。
- 对 `(task_id, sequence_no)` 建唯一索引。

为什么这样设计：

- 聊天界面查询简单，避免 tool trace 污染用户可见消息。
- 原始用户输入和最终 assistant 输出完整保留，压缩策略不会破坏 UI 展示和审计。
- 重试时不重复插入 user message；成功 run 生成新的 assistant message 后，再由 `agent_turn.assistant_message_id` 指向当前生效回复。
- `metadata` 可存前端消息状态、引用题目、附件信息等。

### agent_run

一次 agent 执行尝试记录。同一个 turn 可以有多次 run attempt。

```sql
CREATE TABLE agent_run (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  turn_id BIGINT NOT NULL,
  run_uuid VARCHAR(64) NOT NULL,
  attempt_no INT NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  parent_run_id BIGINT NULL,
  retry_of_run_id BIGINT NULL,
  trigger_type VARCHAR(64) NOT NULL,
  retry_reason VARCHAR(255) NULL,
  status VARCHAR(32) NOT NULL,
  provider VARCHAR(64) NULL,
  model VARCHAR(128) NULL,
  max_steps INT NOT NULL,
  finish_reason VARCHAR(64) NULL,
  input_token_estimate INT NULL,
  output_token_estimate INT NULL,
  usage JSONB NOT NULL DEFAULT '{}',
  error JSONB NOT NULL DEFAULT '{}',
  started_at TIMESTAMP NOT NULL,
  ended_at TIMESTAMP NULL
);
```

字段说明：

- `attempt_no`：同一 turn 内从 1 开始递增的执行序号，用于排序和唯一定位某次尝试。
- `idempotency_key`：请求幂等键。API 重放、网络重试或 SSE 断线恢复时，使用同一个 key 返回已有 run，避免重复创建执行。
- `parent_run_id`：可选父 run，用于表达从某次结果派生的新执行，例如基于上一版回复继续改写。
- `retry_of_run_id`：被重试的 run。失败重试、用户重新生成、自动重试都应指向原 run 或最近一次失败 run。
- `trigger_type`：触发类型，例如 `user_request`、`user_regenerate`、`system_retry`、`timeout_retry`。
- `retry_reason`：重试原因或简短错误分类，例如 `provider_timeout`、`tool_error`、`user_requested_regenerate`。

约束建议：

- 对 `(turn_id, attempt_no)` 建唯一约束。
- 对 `idempotency_key` 建唯一约束；如果幂等键只在 task 或 user 范围内保证唯一，也可以使用 `(task_id, idempotency_key)`。
- 对 `run_uuid` 建唯一约束，保持和 agent core 运行标识一一对应。

为什么这样设计：

- 数据库主键使用自增 ID，便于关联和分页；`run_uuid` 保留当前 `AgentLoopRunner` 的运行标识。
- run 记录模型、provider、usage、错误信息，用于成本统计和问题排查。
- 一个 turn 可以有多个 run attempt，明确区分真实用户新一轮输入和同一轮意图的重试。
- 幂等键让 API 层、SSE 恢复和系统自动重试不会意外生成重复 run 或重复 assistant message。

### agent_run_step

run 内的一次 LLM 调用。

```sql
CREATE TABLE agent_run_step (
  id BIGSERIAL PRIMARY KEY,
  run_id BIGINT NOT NULL,
  step_index INT NOT NULL,
  finish_reason VARCHAR(64) NULL,
  tool_call_count INT NOT NULL DEFAULT 0,
  request_snapshot_id BIGINT NULL,
  usage JSONB NOT NULL DEFAULT '{}',
  metadata JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMP NOT NULL
);
```

为什么这样设计：

- agent loop 可能因为工具调用进入多步执行，step 表能准确表达一次 run 内部结构。
- `request_snapshot_id` 记录该 step 经过 interceptor 后、调用 `llmGateway.stream` 前的最终 `LlmCompletionRequest` 快照，后续可以回放和分析。

### agent_tool_call

模型内部工具调用记录。

```sql
CREATE TABLE agent_tool_call (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  run_id BIGINT NOT NULL,
  step_index INT NOT NULL,
  tool_call_index INT NOT NULL,
  provider_tool_call_id VARCHAR(128) NOT NULL,
  tool_name VARCHAR(128) NOT NULL,
  arguments JSONB NOT NULL DEFAULT '{}',
  result JSONB NULL,
  status VARCHAR(32) NOT NULL,
  result_token_estimate INT NULL,
  result_summary_artifact_id BIGINT NULL,
  error JSONB NOT NULL DEFAULT '{}',
  started_at TIMESTAMP NOT NULL,
  ended_at TIMESTAMP NULL
);
```

为什么这样设计：

- tool call 是内部 trace，不直接进入 `agent_message`。
- `arguments` 和 `result` 保留结构化数据，方便统计、调试和后续二次处理。
- `result_summary_artifact_id` 指向工具结果摘要。该 artifact 一般是 task 或 run scope：只服务当前执行排障和后续 step 的摘要用 run scope，会在同一 task 后续轮次复用的摘要用 task scope。上下文召回时优先使用摘要，而不是大体积原始 result。

如果后续工具结果可能非常大，可以把 `result` 拆到 `agent_content_blob` 或对象存储，`agent_tool_call` 只保留引用。第一阶段可以先用 JSONB，等实际体积超过阈值再拆。

### agent_artifact

摘要、压缩结果和长期记忆的统一派生产物表。artifact 不应强制绑定到单个 task；会话摘要属于 task scope，用户偏好和学习进度记忆可能属于 user scope，系统级规则或公共知识可能属于 global scope。

```sql
CREATE TABLE agent_artifact (
  id BIGSERIAL PRIMARY KEY,
  scope_type VARCHAR(32) NOT NULL,
  scope_id BIGINT NULL,
  artifact_type VARCHAR(64) NOT NULL,
  source_type VARCHAR(64) NOT NULL,
  source_id BIGINT NULL,
  from_message_sequence BIGINT NULL,
  to_message_sequence BIGINT NULL,
  source_range JSONB NOT NULL DEFAULT '{}',
  source_hash VARCHAR(128) NULL,
  content TEXT NOT NULL,
  token_estimate INT NULL,
  model VARCHAR(128) NULL,
  prompt_version VARCHAR(64) NULL,
  supersedes_artifact_id BIGINT NULL,
  status VARCHAR(32) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT FALSE,
  metadata JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

`scope_type` 建议：

```text
task
run
user
global
```

`artifact_type` 建议：

```text
conversation_summary
message_range_summary
tool_result_summary
tool_range_summary
context_compression
task_state
user_preference_memory
learning_progress_memory
```

`source_type` 建议：

```text
task
message
message_range
tool_call
tool_call_range
context_snapshot
```

字段说明：

- `scope_type` / `scope_id`：artifact 的生效范围。`task` scope 的 `scope_id` 指向 `agent_task.id`，`run` scope 的 `scope_id` 指向 `agent_run.id`，`user` scope 的 `scope_id` 指向用户 ID，`global` scope 可为空。
- `from_message_sequence` / `to_message_sequence`：摘要类 artifact 覆盖的消息序号范围，便于判断摘要是否覆盖当前对话段。
- `source_range`：保存更复杂的来源范围，例如多个 message 区间、tool call 区间、snapshot item 引用或跨 run 来源。
- `source_hash`：基于来源原文或规范化来源引用生成，用于判断摘要是否仍匹配原始范围，避免来源变化后误用旧摘要。
- `supersedes_artifact_id`：新版本 artifact 替代的旧版本，用于摘要重生成、压缩策略调整和回滚。
- `status`：artifact 生命周期状态，例如 `draft`、`active`、`superseded`、`failed`。
- `active`：同一 scope、同一 artifact 类型、同一覆盖范围下是否当前生效。对滚动会话摘要，通常只有 task scope 的最新摘要为 active。

为什么这样设计：

- 压缩结果不覆盖原文，而是作为可追溯的派生产物保存。
- 同一段历史可以有多个不同版本摘要，支持不同 prompt 版本和模型生成。
- `scope_type` 避免把长期用户偏好、学习进度记忆强行塞进某个 task，减少跨 task 召回时的归属歧义。
- `source_hash`、`supersedes_artifact_id`、`active` 和 `status` 共同表达摘要版本替换关系，避免多份摘要同时生效。
- `agent_tool_call.result_summary_artifact_id` 通常指向 task 或 run scope 的 `tool_result_summary` / `tool_range_summary`。单次执行内只服务排障或后续 step 的摘要可以使用 run scope；会在同一 task 后续轮次复用的工具结论使用 task scope。
- `agent_task.active_summary_artifact_id` 只指向 task scope 的 active `conversation_summary`，不指向工具摘要、范围摘要、user/global memory 或 run scope artifact。
- 长期 memory 第一阶段可以先作为 user scope artifact 保存；后续如果生命周期、权限、人工确认、过期或召回策略明显不同，再从 `agent_artifact` 拆出独立 `agent_memory`。

### agent_context_snapshot

一次真实 LLM request 的快照。每条记录对应 `AgentLoopRunner` 内一个 step 调用 `llmGateway.stream` 前的最终 `LlmCompletionRequest`。

`ContextAssembler` 生成的候选上下文只能说明 application 当时准备了什么；它不能代表最终请求，因为 core 可能在第 2+ step 追加 assistant tool calls、tool results，interceptor 也可能调整 messages、tools、tool choice、模型选择或生成参数。因此 `agent_context_snapshot` 必须保存完整 request 语义，不能只保存 `role + content TEXT` 列表。

```sql
CREATE TABLE agent_context_snapshot (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  run_id BIGINT NOT NULL,
  step_index INT NOT NULL,
  request_id VARCHAR(128) NULL,
  provider VARCHAR(64) NULL,
  model VARCHAR(128) NULL,
  model_selector VARCHAR(128) NULL,
  policy_name VARCHAR(64) NOT NULL,
  policy_version VARCHAR(64) NOT NULL,
  token_budget INT NOT NULL,
  token_estimate INT NULL,
  reserved_output_tokens INT NULL,
  snapshot_storage_mode VARCHAR(32) NOT NULL DEFAULT 'inline',
  request_snapshot_json JSONB NOT NULL,
  request_snapshot_blob_ref VARCHAR(512) NULL,
  messages_json JSONB NOT NULL,
  tools_json JSONB NOT NULL DEFAULT '[]',
  tool_choice_json JSONB NULL,
  generation_options JSONB NOT NULL DEFAULT '{}',
  request_hash VARCHAR(128) NOT NULL,
  redaction_policy_version VARCHAR(64) NULL,
  retention_expires_at TIMESTAMP NULL,
  metadata JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMP NOT NULL
);
```

字段说明：

- `snapshot_storage_mode`：snapshot 存储模式。第一阶段生产链路默认 `inline`，表示 `request_snapshot_json` 保存经过脱敏后的完整 final request JSON；后续归档可使用 `archived_blob`，表示完整 JSON 已移动到受控 blob/object storage；极端受限环境或过期删除后才使用 `hash_only_tombstone`。
- `request_snapshot_json`：`inline` 模式下保存完整 `LlmCompletionRequest` 序列化结果，是真实回放和差异分析的准据；`archived_blob` 或 `hash_only_tombstone` 模式下仍保持 NOT NULL，但只保存归档 manifest 或删除 tombstone，不再声称可单独回放。
- `request_snapshot_blob_ref`：当完整 request JSON 因归档、加密冷存储或合规要求移出数据库时，保存受控 blob/object storage 引用。读取时仍需校验权限、`request_hash` 和 retention 状态。
- `messages_json`、`tools_json`、`tool_choice_json`、`generation_options`：从完整 request 中拆出的常用查询字段，方便排查 messages、工具定义、工具选择和生成参数变化。这些拆分字段同样需要脱敏和留存控制；在 `hash_only_tombstone` 模式下不应继续保存明文消息内容，`NOT NULL` 字段应写入空结构或 tombstone manifest。如果实现希望物理清空这些字段，应在迁移中显式改为可空。
- `model_selector`：记录请求进入 gateway 前使用的模型选择策略或逻辑名；`provider`、`model` 记录最终解析出的供应商和模型。
- `request_hash`：基于规范化后的完整 request 生成，用于去重、回归测试、归档完整性校验和 `hash_only_tombstone` 模式下的存在性证明。
- `redaction_policy_version`：记录 snapshot 入库时使用的脱敏规则版本，便于解释历史记录中哪些字段被遮盖。
- `retention_expires_at`：在线明文或可回放归档的留存截止时间；过期后可按策略转为归档、hash-only tombstone 或删除 blob。
- `run_id` 和 `step_index`：定位到一次真实 LLM 调用。application 候选上下文或组装 trace 不应混入这张表，可以单独作为策略 trace、artifact 或 metadata 保存。
- `metadata`：保存 trace id、上下文策略摘要、interceptor 版本等非业务强依赖信息。

### agent_context_snapshot_item

快照中的来源分析条目。该表用于解释最终 request 中的消息或片段来自哪里、使用了原文还是摘要、对应哪个 artifact/tool/message；真实回放仍以 inline 或 archived blob 中的完整 final request snapshot 为准。

```sql
CREATE TABLE agent_context_snapshot_item (
  id BIGSERIAL PRIMARY KEY,
  snapshot_id BIGINT NOT NULL,
  ordinal INT NOT NULL,
  item_type VARCHAR(64) NOT NULL,
  source_type VARCHAR(64) NULL,
  source_id BIGINT NULL,
  source_refs JSONB NOT NULL DEFAULT '[]',
  message_index INT NULL,
  content_path VARCHAR(255) NULL,
  role VARCHAR(32) NULL,
  content TEXT NULL,
  token_estimate INT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'
);
```

`item_type` 建议：

```text
system_prompt
active_summary
raw_message
message_range_summary
raw_tool_result
tool_result_summary
tool_range_summary
context_compression
placeholder
current_user_message
assistant_tool_calls
tool_result_message
```

为什么这样设计：

- `agent_context_snapshot` 记录本次真实送入模型的完整 request，而不是事后推测。
- `agent_context_snapshot_item` 保留来源引用、message index 和内容路径，便于解释“这段上下文为什么进入了 request”。
- item 表可以保留片段文本、摘要文本或占位文本，也可以只保留 source refs；它是分析索引，不是回放的唯一数据源。
- 在线留存期内，后续模型异常、用户反馈、测试回归都可以基于完整 final request snapshot 复现。
- 策略调整后，也能比较不同策略对上下文组成和 token 成本的影响。

## 上下文组装流程

新增 application 层组件 `AgentConversationService` 和 `ContextAssembler`。推荐流程：

```text
用户发起请求
  -> 创建或读取 agent_task
  -> 根据幂等键创建或读取 agent_turn
  -> 首次请求写入 user agent_message，重试或重新生成不重复写入
  -> 创建或读取 agent_run attempt，写入 attempt_no / idempotency_key / trigger_type
  -> 更新 agent_turn.current_run_id
  -> ContextAssembler 组装候选上下文和 source refs
  -> 构造通用 AgentRequest / CoreAgentRequest
  -> 调用 AgentLoopRunner.stream(...)
  -> AgentLoopRunner 每个 step 构造 LlmCompletionRequest
  -> request interceptor 处理最终 request
  -> 保存 final request snapshot 到 agent_context_snapshot
  -> 调用 llmGateway.stream(...)
  -> AgentLoopObserver 持久化 run / step / tool / assistant message
  -> run 结束后更新 turn、task summary 或派发异步压缩任务
```

application 层可以在 `ContextAssembler` 之后保存候选上下文、source refs、预算决策或策略降级结果，供后续分析使用。但这类记录只能视为 `candidate_context` 或策略 trace，不能替代 final request snapshot。final request snapshot 应由 `agent-core` 暴露的生命周期观测点或 mandatory observer 保存。若第一阶段尚未落 `agent_run_step` 表，可先用 `agent_context_snapshot.run_id + step_index` 定位；第二阶段引入 step 表后，再通过 `agent_run_step.request_snapshot_id` 建立显式关联。

重试与重新生成流程应复用同一个 `agent_turn`：

- API 层收到相同 `idempotency_key` 时，直接返回已有 `agent_run` 的状态或结果，不创建新的 turn、run、message。
- 用户点击重新生成时，创建新的 `agent_run`，`trigger_type=user_regenerate`，`retry_of_run_id` 指向当前 `accepted_run_id` 或最近失败 run，`attempt_no` 在该 turn 内递增。
- 系统自动重试时，创建新的 `agent_run`，`trigger_type=system_retry` 或更具体的 `timeout_retry`，并记录 `retry_reason`。
- 当前用户消息始终复用 `agent_turn.user_message_id`；只有 run 成功且结果被接受时，才写入或激活对应 assistant message，并更新 `agent_turn.assistant_message_id` 与 `accepted_run_id`。
- 未被接受的 assistant 输出可以保留为带 `run_id` 的历史版本，或只保存在 run trace 中；聊天界面默认只展示 `agent_turn.assistant_message_id` 指向的回复。

第一阶段应把 `AgentRequest` 或等价的 core request 收敛为通用请求模型：

```java
public record AgentRequest(
    String runId,
    String requestId,
    List<LlmMessage> messages,
    Map<String, Object> metadata,
    AgentExecutionOptions options
) {}
```

其中 `messages` 是 `ContextAssembler` 的初始产物，可以包含 system prompt、当前用户消息、历史摘要、最近消息和工具结果摘要。`metadata` 只承载通用追踪、业务引用 ID、策略版本等扩展信息，不要求 `agent-core` 理解其中的业务语义。`options` 可包含模型、温度、最大 step、超时、工具启用策略等执行参数；如果第一阶段已有独立配置对象，也可以复用现有命名。进入 `AgentLoopRunner` 后，后续 step 的 messages 还会包含模型产生的 assistant tool calls 和工具执行返回的 tool results，因此最终快照不能只依赖 application 初始 messages。

`LearningTopic`、题目、学习计划、错题复盘状态等学习业务对象，应由 `mentor-application` 在调用 `agent-core` 前转换成 prompt messages 或 metadata。例如 topic prompt 可以由 application adapter 生成一条 system/user message，再随通用请求传入。

现有 `AgentLlmRequestFactory.initialMessages(request)` 中基于 topic 拼接 prompt 的做法，可以作为兼容旧 topic explanation 用法的业务 adapter 保留，但不应作为 `agent-core` 的长期方向。长期方向是 `AgentLoopRunner` 不负责查历史、不拼接学习业务 prompt，只消费已经组装好的通用 `messages`。

## 压缩策略

### 1. 滑动窗口策略

保留最近 N 轮原始消息，例如最近 6 到 10 轮。最近消息通常和当前问题相关性最高，应优先保留原文。

适用场景：

- 第一阶段默认策略。
- 对话较短或中等长度时。
- 用户连续追问上一轮回答细节时。

设计原因：

- 实现简单、行为稳定。
- 不依赖额外模型调用。
- 能保证最近语义和指代关系完整。

### 2. 头尾保留策略

保留最早若干轮和最近若干轮，中间部分使用摘要替代。

适用场景：

- 早期几轮包含任务目标、题目背景、学习约束。
- 中间过程较长，但当前问题仍依赖最初目标。

设计原因：

- 很多任务的第一轮定义了长期约束，例如“用 Java 讲解”“不要直接给答案”“我正在学二分查找”。
- 只保留尾部可能丢失任务目标，头尾结合能降低这种风险。

### 3. 滚动会话摘要

当消息超过阈值后，将较早历史合并到 task scope 的 `conversation_summary`，记录 `from_message_sequence`、`to_message_sequence`、`source_hash` 和版本替换关系，并将当前有效摘要设置为 `agent_task.active_summary_artifact_id`。

适用场景：

- 长期学习会话。
- 用户围绕同一主题持续追问。

设计原因：

- 摘要稳定占用较少 token。
- 每轮上下文不需要重复读取大量早期消息。
- `active_summary_artifact_id` 让系统快速找到当前 task 的有效会话摘要。它不用于 user/global scope 的长期 memory。

### 4. 消息范围摘要

对某一段连续消息生成 `message_range_summary`，记录 `from_message_sequence`、`to_message_sequence`、`source_hash`。如果新摘要覆盖同一范围并替代旧摘要，应设置 `supersedes_artifact_id`，并将旧摘要标记为 `superseded` 或 `active=false`。

适用场景：

- 中间历史过长，但还不适合合并进全局滚动摘要。
- 需要保留某一阶段讨论结果，例如“已经比较过 BFS 和 DFS 的差异”。

设计原因：

- 范围摘要比全局摘要更局部，便于按需召回。
- 后续可以根据用户问题选择相关范围摘要，而不是只依赖一份全局摘要。

### 5. 工具结果摘要

工具返回结果超过阈值时，原始 `result` 保留在 `agent_tool_call.result`，上下文中使用 run scope 或 task scope 的 `tool_result_summary`。只服务当前 run 后续 step 或排障的摘要使用 run scope；会在同一 task 后续轮次复用的工具结论使用 task scope。摘要通过 `source_type=tool_call`、`source_id=agent_tool_call.id` 和 `source_hash` 绑定原始结果。

适用场景：

- 搜索结果、题目解析结果、代码执行日志、评测输出较大。
- 工具结果包含大量结构化字段，但模型只需要结论。

设计原因：

- 工具结果通常 token 密度高，直接塞入上下文成本大。
- 原始结果保留，摘要进入上下文，可以兼顾可追踪和成本。

### 6. 工具调用范围摘要

当同一 run 或相邻多个 run 有大量 tool call 时，将一组工具调用压缩为 `tool_range_summary`，并在 `source_range` 中记录 run、step 和 tool call 区间。

适用场景：

- agent 多次搜索、评测、读取材料。
- 当前问题只需要知道工具链最终结论，不需要每次调用细节。

设计原因：

- 工具 trace 对调试重要，但对模型后续回答未必都重要。
- 按范围压缩可以避免上下文被内部执行过程淹没。

### 7. 占位引用策略

对于不需要进入模型上下文但需要保留存在感的内容，使用短占位说明。

示例：

```text
此前调用过 code_runner 工具，完整执行日志已归档，当前上下文仅保留错误摘要。
```

适用场景：

- 工具结果极大。
- 内容对当前推理低相关。
- 只需让模型知道某些信息已经存在但不展开。

设计原因：

- 比完全删除更可控。
- 可以减少模型误以为没有历史动作的概率。

### 8. LLM 上下文再压缩

当滑动窗口、摘要、工具压缩后仍超出预算时，将候选上下文整体交给压缩模型，生成 task scope 的 `context_compression`，并记录覆盖的消息范围、引用的 artifact ID 和 `source_hash`。

适用场景：

- 用户长时间对话后突然要求总结或继续复杂任务。
- 多类上下文叠加后仍超过模型窗口。

设计原因：

- 作为最后兜底策略，保证请求可继续执行。
- 压缩结果保存为 artifact，后续可复用或人工检查。

注意事项：

- 该策略会带来额外模型成本，不应作为第一优先级。
- 生成时必须要求模型保留用户目标、关键结论、未解决问题、约束条件和必要引用。

### 9. 重要性记忆提取

从历史对话中提取长期有效信息，例如用户偏好、学习进度、薄弱知识点，保存为 user scope 或 global scope 的 memory 类型 artifact。长期 memory 可能跨 task 生效，不应依赖 `agent_artifact.task_id NOT NULL` 这类单 task 归属，也不应写入 `agent_task.active_summary_artifact_id`。

适用场景：

- “以后都用 Java 示例”。
- “用户已经掌握数组和哈希表，但动态规划较弱”。
- “该用户偏好先提示思路，再给完整解法”。

设计原因：

- 长期记忆不应依赖完整聊天历史。
- 学习类产品需要持续理解用户状态。
- 长期用户偏好和学习进度通常跨多个 task 复用，使用 `scope_type=user` 更符合召回边界。

第一阶段可以只设计 artifact 类型和 scope，不实现自动提取。后续如果 memory 的权限、过期策略、人工确认流程或向量召回需求独立增长，可以拆出 `agent_memory` 表。

## 上下文预算策略

建议引入 `ContextBudget`，避免上下文组装无限膨胀。

示例预算：

```text
模型最大上下文: 128k tokens
预留输出: 8k tokens
可用输入: 120k tokens

system prompt: 不超过 10%
active summary / task state: 10% - 20%
最近原始消息: 30% - 40%
历史范围摘要: 10% - 20%
工具结果摘要: 10% - 20%
安全余量: 5% - 10%
```

第一阶段可以先用字符数估算 token，后续再接入 tokenizer。表中保留 `token_estimate` 字段，方便平滑替换估算方式。

## 召回优先级

默认优先级：

1. 系统提示词和安全约束。
2. 当前用户消息。
3. 当前任务状态和 task scope active conversation summary。
4. 明确相关的 user scope memory，例如用户偏好或学习进度。
5. 最近几轮原始 user / assistant 消息。
6. 与当前任务相关的工具结果摘要。
7. 中间历史范围摘要。
8. 早期原始消息。
9. 低相关工具 trace 占位。

如果超预算，降级顺序：

```text
早期原始消息 -> 消息范围摘要 -> 工具原始结果 -> 工具结果摘要 -> 工具范围摘要 -> 占位引用 -> LLM 上下文再压缩
```

## 与现有 AgentLoopRunner 的关系

`AgentLoopRunner` 应继续保持通用内核编排职责：

- 调用 LLM。
- 收集 tool call。
- 执行工具。
- 发布生命周期事件。
- 在每个 step 构造最终 `LlmCompletionRequest` 后、调用 `llmGateway.stream` 前暴露 request snapshot 观测点。
- 根据通用 request 中的 messages、options、tools 和 metadata 执行，不感知学习业务模型。

多轮对话的持久化和上下文召回应放在外层：

```text
mentor-application
  AgentConversationService
    -> ContextAssembler
    -> ConversationRepository
    -> ArtifactService
    -> AgentLoopRunner
    -> AgentLoopObserver 持久化运行轨迹
```

原因：

- `agent-core` 可以继续作为通用 agent 内核底座，不绑定数据库，也不绑定 `LearningTopic`、题目或学习计划。
- API、异步任务、测试用例都可以复用同一个 `AgentLoopRunner`。
- 上下文策略和业务 prompt 可以在 application 层按业务场景替换。
- final request snapshot 由 core 生命周期事件或 mandatory observer 产生，既不要求 `agent-core` 依赖数据库，也能保证记录到 tool call 后续 step 的真实 messages。

## 运行轨迹持久化

建议新增一个 `AgentLoopObserver` 实现，例如 `PersistentAgentTraceObserver`：

```text
onRunStart
  校验 application 已创建或读取的 agent_run attempt
  在缺少预创建 run 的测试、批处理或兼容入口中兜底创建
  更新 agent_run.started_at / status，并校验 agent_turn.current_run_id

onStepStart
  创建 agent_run_step

onLlmRequestReady
  保存 final request snapshot 到 agent_context_snapshot
  如果 agent_run_step 已存在，则回填 agent_run_step.request_snapshot_id

onLlmEvent
  聚合 assistant delta、tool call delta、usage

onToolStart
  创建 agent_tool_call

onToolEnd
  更新 agent_tool_call.result / status / ended_at

onRunEnd
  写入带 run_id 的 assistant agent_message
  按接受策略更新 agent_turn.assistant_message_id / accepted_run_id / current_run_id
  更新 agent_run 状态

onError
  更新 agent_run 错误状态
  保留 agent_turn.user_message_id，不重复生成 user message
```

`onLlmRequestReady` 的触发时机必须在 `AgentLoopRunner` 构造 `LlmCompletionRequest` 且 request interceptor 完成之后、`llmGateway.stream` 调用之前。该事件应携带最终 request、run id、step index、模型选择结果、上下文策略标识和 source refs。为了避免调用模型却没有快照，生产链路中这个 observer 应作为 mandatory observer 或等价的强制持久化钩子启用。

生产 API 链路中，`agent_run` attempt 的创建和幂等处理由 `AgentConversationService` 负责，observer 不应在 `onRunStart` 再按 `idempotency_key` 重复创建同一个 run。observer 的职责是校验并更新已有 run、补写运行状态，以及为绕过 application service 的测试或后台兼容入口提供兜底。

注意：流式 assistant 文本需要在 observer 或 API 层聚合 `LlmStreamEvent.ContentDelta`，在 `message_end` 或 `run_end` 后落库为最终 assistant message。

## 索引建议

```sql
CREATE UNIQUE INDEX uk_agent_turn_task_seq ON agent_turn(task_id, sequence_no);
CREATE INDEX idx_agent_turn_current_run ON agent_turn(current_run_id);
CREATE INDEX idx_agent_turn_accepted_run ON agent_turn(accepted_run_id);
CREATE UNIQUE INDEX uk_agent_message_task_seq ON agent_message(task_id, sequence_no);
CREATE INDEX idx_agent_message_run ON agent_message(run_id);
CREATE INDEX idx_agent_run_task_turn ON agent_run(task_id, turn_id);
CREATE UNIQUE INDEX uk_agent_run_turn_attempt ON agent_run(turn_id, attempt_no);
CREATE UNIQUE INDEX uk_agent_run_idempotency_key ON agent_run(idempotency_key);
CREATE INDEX idx_agent_run_retry_of ON agent_run(retry_of_run_id);
CREATE INDEX idx_agent_run_step_run_step ON agent_run_step(run_id, step_index);
CREATE INDEX idx_agent_tool_call_run_step ON agent_tool_call(run_id, step_index);
CREATE INDEX idx_agent_artifact_scope_type ON agent_artifact(scope_type, scope_id, artifact_type);
CREATE INDEX idx_agent_artifact_active ON agent_artifact(scope_type, scope_id, artifact_type, active);
CREATE INDEX idx_agent_artifact_source ON agent_artifact(source_type, source_id);
CREATE INDEX idx_agent_context_snapshot_run ON agent_context_snapshot(run_id, step_index);
CREATE INDEX idx_agent_context_snapshot_item_snapshot ON agent_context_snapshot_item(snapshot_id, ordinal);
```

active artifact 的唯一性约束不建议用一条泛化索引覆盖所有类型。`conversation_summary`、`message_range_summary` 这类有消息范围的 artifact，可按 `scope_type`、`scope_id`、`artifact_type`、`from_message_sequence`、`to_message_sequence` 做部分唯一约束；长期 memory 可能需要按 memory key、确认状态或过期时间单独建约束。`scope_id` 允许为空时，PostgreSQL 唯一索引不会把 NULL 视为相同值，global scope 可使用哨兵值、表达式索引或专门 partial index。

如果使用 PostgreSQL JSONB 查询 metadata，可按实际查询需求增加 GIN 索引，不建议第一阶段过早添加大量 JSONB 索引。

## 分阶段落地

### 第一阶段：基础链路 + 最小 final request snapshot 

- 新增 `agent_task`、`agent_turn`、`agent_message`、`agent_run`。
- 明确 `AgentTurn` 表达用户意图，`AgentRun` 表达执行尝试；同一 turn 下支持多个 `attempt_no`。
- 引入 `idempotency_key`，保证 API 重放、SSE 恢复和系统自动重试不会重复创建 user message 或 run。
- 扩展 application service，支持根据 task ID 继续对话。
- `ContextAssembler` 实现 system prompt + 最近 N 轮消息；如果已有兼容摘要来源，可以预留 active summary 位置，但不要求第一阶段引入 `agent_artifact`。
- 引入或调整通用 `AgentRequest` / core request，只包含 `runId/requestId`、`List<LlmMessage> messages`、`metadata` 和可选 execution/options。
- 将 `LearningTopic`、题目、学习计划到 messages/metadata 的转换放在 `mentor-application`；现有 topic prompt 拼接逻辑先作为兼容 adapter，不继续扩散到 `agent-core`。
- 在 `AgentLoopRunner` 调用 LLM 前保存最小 final request snapshot。生产链路优先以 `snapshot_storage_mode=inline` 写入完整脱敏 `request_snapshot_json`，同时保存 `request_hash`、messages/tools/tool choice/generation options 拆分字段和基础 metadata。
- 第一阶段不要堆太多表：可以先只落主链路表和 `agent_context_snapshot`，`agent_run_step`、`agent_tool_call`、`agent_artifact`、`agent_context_snapshot_item` 后移到后续阶段。
- 保存带 `run_id` 的 assistant 最终回复，并由 `agent_turn.accepted_run_id`、`assistant_message_id` 指向当前对用户生效的版本。

### 第二阶段：运行轨迹与工具持久化

- 新增 `agent_run_step`、`agent_tool_call`。
- 使用 `AgentLoopObserver` 持久化 step、tool call、usage、error，并把 `agent_run_step.request_snapshot_id` 关联到第一阶段已有的 final request snapshot。
- 大工具结果先保留原文，增加 token 或字符数估算。
- 对 tool 参数、result、error 做字段级 redaction；大结果可以先 JSONB 保存，超过阈值后再拆 blob/object storage。

### 第三阶段：摘要与压缩产物

- 新增 `agent_artifact`。
- 使用 `scope_type` / `scope_id` 表达 task、run、user、global 等归属范围，避免工具摘要、会话摘要和长期 memory 混用归属。
- 实现 conversation summary、message range summary、tool result summary。
- 摘要类 artifact 记录 `from_message_sequence`、`to_message_sequence`、`source_hash`、`supersedes_artifact_id`、`active` / `status`。
- task 表维护 `active_summary_artifact_id`，仅指向 task scope 的 active conversation summary。

### 第四阶段：上下文快照增强与策略回放

- 在第一阶段最小 request snapshot 基础上补齐 `agent_context_snapshot_item` 来源分析。
- 强化 request diff、策略版本、token 预算和降级记录。
- 增加围绕完整 `request_snapshot_json` 的回放、对比和评测辅助能力。
- 引入 snapshot 归档策略：在线期保留 `inline` 完整 JSON，归档期可转为 `archived_blob` 并保留 blob 引用，删除期只保留 `hash_only_tombstone`、source refs 和删除原因。

### 第五阶段：长期记忆与相关性召回

- 基于 user scope artifact 增加用户偏好、学习进度、知识点薄弱项。
- 评估是否将长期 memory 从 `agent_artifact` 拆到独立 `agent_memory`，尤其是当 memory 需要独立权限、确认、过期和召回策略时。
- 按需引入 pgvector 或外部向量库。
- 将相关性召回作为 ContextAssembler 的一个可插拔步骤。

## 这样设计的收益

### 可维护性

聊天消息、内部运行轨迹、压缩产物和上下文快照职责清晰。turn 表达用户意图，run 表达执行尝试，后续改重试策略或压缩策略时，不需要重构消息表或 agent loop。

### 可追溯性

每次真实 LLM request 都有 final request snapshot。模型异常回答、用户投诉、成本异常和 prompt 回归都可以还原现场，包括第 2+ step 中的 assistant tool calls 和 tool results。

同一 turn 下的多个 run attempt 使用 `attempt_no`、`retry_of_run_id` 和 `idempotency_key` 串联，可以解释当前回复来自哪次尝试，以及失败重试、用户重新生成和系统自动重试之间的关系。

### 成本可控

上下文预算和多级压缩策略可以避免历史消息无限膨胀。工具结果默认摘要进入上下文，可以显著降低 token 消耗。

### 质量稳定

最近消息保留原文，早期历史通过摘要保留关键信息。相比简单截断，这种方式更不容易丢失任务目标和长期约束。

### 扩展性

`agent_artifact` 可以承载摘要、压缩、长期记忆和后续向量召回结果。`scope_type` / `scope_id` 让 task 摘要、用户长期偏好和 global 记忆具备不同归属边界。第一阶段不做复杂记忆，也不会阻塞后续升级。

### 与现有架构匹配

`agent-core` 继续保持通用 agent 内核底座，`mentor-application` 负责业务会话、上下文策略和学习业务对象到 messages/metadata 的转换，`mentor-api` 负责 HTTP/SSE 传输。这符合当前模块边界。

## 主要风险与应对

### 摘要丢失关键信息

应对：

- 原始消息永不覆盖。
- 摘要记录来源范围。
- 对关键约束使用 task state 或 memory 单独保存。
- 必要时允许重新生成摘要。

### 重试导致消息重复或展示错乱

应对：

- user message 只随 turn 创建一次，重试只新增 run attempt。
- 使用 `idempotency_key` 吸收 API 重放和网络重试。
- 聊天界面以 `agent_turn.assistant_message_id` 和 `accepted_run_id` 为准，不直接展示所有 run 输出。
- 对 `(turn_id, attempt_no)` 和 `idempotency_key` 建唯一约束，避免并发重试生成重复执行。

### artifact scope 或版本关系混乱

应对：

- task 摘要使用 `scope_type=task`，长期用户记忆使用 `scope_type=user`，不混用 `active_summary_artifact_id`。
- 摘要类 artifact 必须记录覆盖范围、`source_hash` 和替换关系。
- 同一 scope、同一类型、同一覆盖范围只保留一个 active 版本；旧版本标记为 `superseded` 或 `active=false`。

### 上下文快照占用存储

应对：

- 第一阶段生产链路至少保存最小 final request snapshot，优先以 `snapshot_storage_mode=inline` 保存完整脱敏 `request_snapshot_json`，并同步保存 `request_hash`、关键 request JSON 拆分字段和基础 metadata。
- 存储压力不应在同一张表语义下直接降级成“只保存 hash + source refs”。如果确需降级，应显式进入 `archived_blob` 或 `hash_only_tombstone` 模式：前者保留受控 blob 引用，后者只用于特殊环境或过期删除后的存在性证明。
- 后续可对旧 snapshot 做归档、加密冷存储或明文删除；删除后保留 tombstone、`request_hash`、必要引用、删除时间和删除原因，避免与 `request_snapshot_json NOT NULL` 的表结构语义冲突。

### 工具结果过大

应对：

- 设置工具结果大小阈值。
- 超阈值结果生成摘要。
- 更大结果拆到 blob 表或对象存储。

### 压缩链路增加延迟

应对：

- 最近窗口策略同步执行。
- 非关键摘要异步生成。
- 只有超预算时才触发同步 LLM 上下文再压缩。

## 第一版推荐默认策略

第一版建议使用保守策略：

```text
Context Policy: sliding-window-with-active-summary

1. 放入 system prompt。
2. 放入 active conversation summary，如果存在。
3. 放入最近 8 轮 user / assistant 原始消息。
4. 放入当前用户消息。
5. 工具结果默认不回灌，除非被标记为 current-task-relevant。
6. 如果 token 超预算，减少最近消息到 4 轮。
7. 如果仍超预算，触发 message_range_summary 或 context_compression。
```

这个策略实现成本低，能先解决多轮对话主链路，同时为后续工具摘要、范围摘要和长期记忆留下明确扩展点。
