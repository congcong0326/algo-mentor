# Agent Run 内工具结果压缩设计

## 背景

当前 `AgentLoopRunner` 在一次 run 内使用内存中的 `List<LlmMessage>` 维护活跃上下文。模型返回 tool call 后，运行时会追加 assistant tool calls，再执行工具并追加 tool result，然后进入下一次 LLM step。

这个闭环是 agent 能持续行动的基础，但也带来一个近端风险：同一次 run 内只要工具返回大结果，例如代码执行日志、检索结果、文件内容、批量评测输出，下一次 LLM 请求就可能直接超出上下文窗口。即使跨轮上下文还没有召回工具历史，run 内上下文也已经可能膨胀。

因此需要先在 run 内建立工具结果预算和压缩机制，再扩展到跨轮召回、artifact 摘要和长期记忆。

`learn-claude-code` 中的 `tool_result_budget`、`micro_compact`、`snip_compact` 可以作为分层设计参考：先处理大工具结果，再压缩旧工具结果，最后在整体上下文仍过大时裁剪中间历史。本项目不应逐字照搬其内存消息实现，因为这里还需要维护 run step、tool call、blob、request snapshot 和数据库审计记录之间的一致性。

## 设计目标

- 控制单次 run 内 tool result 对 LLM 请求上下文的占用。
- 保留完整工具执行事实，支持审计、调试、回放和成本分析。
- 让模型在看到预览后，可以通过受控工具读取完整结果的局部范围。
- 保持 assistant tool call 与 tool result 的 provider 协议配对关系合法。
- 优先使用确定性预算、截断、预览、引用和占位，LLM 级压缩作为后续兜底能力。
- 借鉴 `tool_result_budget`、`micro_compact`、`snip_compact` 的分层思路，但按本项目的 run step、tool call、snapshot 和持久化模型重新落地。
- 为跨轮工具摘要和上下文召回留下一致的数据模型和 source ref。

## 非目标

- 第一阶段不实现跨轮工具结果召回。
- 第一阶段不把 `agent_tool_call` 写入 `agent_message`，也不作为用户可见聊天消息展示。
- 第一阶段不要求自动生成 LLM 工具结果摘要；可以先保留确定性预览和占位。
- 第一阶段不引入对象存储，优先使用 PostgreSQL blob 表作为可替换实现。
- 第一阶段不让模型无预算地读取完整大结果。

## 核心原则

### 事实记录和活跃上下文分离

工具执行得到的完整结果属于事实记录。事实记录用于审计、回放、成本分析和后续摘要，不等于每次都要原样进入 LLM 上下文。

活跃上下文只放经过预算、脱敏和压缩后的版本：

- 小结果：可以 inline 回填。
- 大结果：回填预览、统计信息和引用。
- 旧结果：可以替换为摘要或占位。
- 完整结果：保存到 blob store，由受控读取工具按范围返回。

### run 内优先解决近端爆窗

跨轮召回发生在下一次用户请求前，run 内压缩发生在每个 LLM step 前。后者更靠近模型调用，必须先保证当前请求可发送。

### 工具协议配对不可破坏

如果某个 step 的 assistant message 包含 tool calls，后续 messages 中必须保留对应 tool result。可以压缩 tool result 的内容，但不能随意删除对应消息，避免 provider 拒绝请求或模型误判上下文。

### 压缩从便宜到昂贵

默认顺序：

1. 单个大结果预算化。
2. 单次请求工具结果总预算控制。
3. 旧工具结果微压缩。
4. 多工具调用范围占位或摘要。
5. 按消息组裁剪中间历史。
6. LLM 级 run context compact。

第一阶段优先实现 1-3；第 4-5 步在 run 内多 step 和工具调用变多后补齐。LLM 级 compact 成本高、行为不如确定性裁剪稳定，默认关闭。

### snip compact 必须按组执行

`snip_compact` 针对所有消息有效，不能只看 tool result。但在 agent loop 中不能按单条 message 粗暴删除，因为 assistant tool call 和 tool result 有协议配对关系。

run 内裁剪必须以消息组为单位：

- `system` 和当前任务约束组。
- 普通 user/assistant 对话组。
- `assistant tool_calls + 对应 tool result` 工具调用组。
- 已压缩摘要或占位组。

可以裁掉较早的完整组，不能裁掉工具调用组的一半。如果必须移除工具调用细节，应替换为一个 compact marker 或范围摘要，而不是留下孤儿 tool call 或孤儿 tool result。

## 总体流程

```text
tool.execute()
  -> raw result
  -> redaction
  -> persist full/small result
  -> build model-visible result
  -> messages.add(tool result preview or inline result)

before each LLM request
  -> run-local tool result budget
  -> old tool result micro compact
  -> optional range summary placeholder
  -> group-aware snip compact if still over budget
  -> optional LLM compact as last resort
  -> build LlmCompletionRequest
  -> save final request snapshot
  -> call LLM
```

## 组件设计

### ToolResultStore

负责保存完整工具结果，并返回模型可见的引用信息。第一版建议接口放在 `agent-core`，PostgreSQL 实现放在 `agent-persistence-postgres`。

建议职责：

- 判断结果是否 inline。
- 大结果写入 blob。
- 生成 `resultRef`。
- 提供按 offset、行号范围、JSON path 读取结果的能力。
- 记录 char count、line count、hash、content type、redaction policy version。

### ToolResultCompactor

负责把原始工具结果转换成可进入 LLM 上下文的 `JsonNode`。

建议输入：

- `runId`
- `stepIndex`
- `LlmToolCall`
- redacted raw result
- `ToolResultCompactionPolicy`

建议输出：

- 模型可见的 tool result JSON。
- storage metadata，例如 inline、blob id、result ref、preview char count。
- token/char 估算。

### RunMessageCompactor

负责在每次 LLM 请求前处理当前 run 内的 `messages`。

建议职责：

- 保留最近 N 个 tool result 的完整或预算版本。
- 将更旧的 tool result 替换为占位或摘要。
- 限制本次 request 中所有 tool result 的总字符数。
- 将 messages 解析成可裁剪的消息组。
- 在整体上下文仍超预算时，按组裁剪中间历史。
- 保证 assistant tool call 与 tool result 不断链。
- 产出 compaction metadata，写入 request snapshot metadata。

## run 内消息组模型

为了支持 `snip_compact`，`RunMessageCompactor` 不应只操作扁平 `List<LlmMessage>`。建议先把 messages 解析为 `RunMessageGroup`，再执行预算策略。

建议分组类型：

```text
SYSTEM_GROUP
  system prompt、任务约束、active summary。

PLAIN_EXCHANGE_GROUP
  普通 user/assistant 文本消息，不包含 tool call。

TOOL_INTERACTION_GROUP
  一个 assistant tool_calls message，以及其后所有匹配 tool_call_id 的 tool result message。

COMPACT_MARKER_GROUP
  snip、micro compact、range summary 或 LLM compact 产生的占位消息。
```

分组规则：

- assistant tool call message 和对应 tool result 必须在同一组。
- 一个 assistant message 同时包含多个 tool call 时，该组必须包含所有对应 tool result。
- 如果发现 tool result 找不到对应 tool call，默认不裁剪该区域，并在 metadata 记录 `orphanToolResultDetected=true`，避免进一步破坏上下文。
- 如果发现 assistant tool call 缺少 tool result，说明当前 run 状态不完整，该区域必须保留，并阻止 snip 裁剪穿过该区域。

裁剪优先级：

1. 已有摘要覆盖的旧 `PLAIN_EXCHANGE_GROUP`。
2. 已被后续 step 消费、且已有占位或摘要的旧 `TOOL_INTERACTION_GROUP`。
3. 中间较早的普通对话组。
4. 中间较早的工具组，先替换为 `COMPACT_MARKER_GROUP`，不直接删除。

默认保留：

- 最前面的 system/task 组。
- 最近 N 个组。
- 当前 step 刚产生、尚未被模型消费的工具组。
- 包含错误、最终结论、用户显式约束的组。

## 数据模型建议

### agent_tool_call 扩展

现有 `agent_tool_call` 继续保存工具调用审计信息。建议后续新增或预留以下字段：

```sql
result_storage_mode VARCHAR(32) NULL, -- inline | blob | omitted
result_blob_id BIGINT NULL,
result_preview_json JSONB NULL,
result_ref VARCHAR(128) NULL,
result_line_count INT NULL,
result_sha256 VARCHAR(128) NULL
```

第一阶段也可以不立即改表，把这些信息先放在 `metadata` 中，但正式实现建议显式字段化，便于查询和迁移。

### agent_content_blob

新增通用内容 blob 表，第一版使用 PostgreSQL 保存正文。未来可以替换为对象存储，表中只保留 URI、hash 和大小。

```sql
CREATE TABLE agent_content_blob (
  id BIGSERIAL PRIMARY KEY,
  scope_type VARCHAR(64) NOT NULL,
  scope_id BIGINT NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  storage_mode VARCHAR(32) NOT NULL,
  content_text TEXT NULL,
  content_bytes BYTEA NULL,
  uri TEXT NULL,
  sha256 VARCHAR(128) NOT NULL,
  char_count INT NULL,
  byte_count BIGINT NULL,
  line_count INT NULL,
  redaction_policy_version VARCHAR(64) NULL,
  metadata JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL
);
```

约束建议：

```sql
CREATE INDEX idx_agent_content_blob_scope ON agent_content_blob(scope_type, scope_id);
CREATE UNIQUE INDEX uk_agent_content_blob_hash_scope ON agent_content_blob(scope_type, scope_id, sha256);
```

第一版 `scope_type` 可以使用：

- `tool_result`
- `context_snapshot`
- `artifact_source`

run 内工具结果主要使用 `scope_type=tool_result`，`scope_id=agent_tool_call.id`。

## 模型可见结果格式

### 小结果 inline

当结果低于单结果预算时，保持当前语义，直接返回工具原始 JSON：

```json
{
  "value": 42,
  "explanation": "..."
}
```

### 大结果 preview

当结果超过单结果预算时，回填给 LLM 的 tool result 使用稳定结构：

```json
{
  "type": "tool_result_preview",
  "resultRef": "tool-result:12345",
  "toolCallId": "call_abc",
  "toolName": "code_runner",
  "contentType": "application/json",
  "charCount": 89432,
  "lineCount": 2100,
  "preview": "...前 2000 字符...",
  "truncated": true,
  "readHint": "Use read_tool_result with resultRef and offset/limit or line range if more detail is needed."
}
```

注意：

- preview 必须是脱敏后的内容。
- `resultRef` 不应暴露数据库真实表结构，可以是稳定 opaque ref。
- `preview` 长度受配置控制。
- JSON 工具结果可以保留顶层字段摘要，例如 keys、数组长度、错误字段。

### 旧结果占位

当某个 tool result 已经被后续 step 消费过，并且不属于最近保留窗口时，可以替换为：

```json
{
  "type": "tool_result_compacted",
  "resultRef": "tool-result:12345",
  "toolCallId": "call_abc",
  "toolName": "search_problem",
  "summary": "Earlier tool result compacted. It contained 18 retrieved records about binary search boundary cases.",
  "truncated": true
}
```

第一阶段如果没有 summary，可以使用确定性占位：

```json
{
  "type": "tool_result_compacted",
  "resultRef": "tool-result:12345",
  "message": "Earlier tool result compacted. Re-read a range if needed.",
  "truncated": true
}
```

## read_tool_result 工具

### 用途

当模型看到大结果 preview 后，如果确实需要更多内容，可以调用 `read_tool_result` 获取局部范围。这个工具只读取当前 run、当前 task 或权限允许范围内的 blob 内容。

### 输入 schema

建议第一版支持两种读取方式：

```json
{
  "resultRef": "tool-result:12345",
  "offset": 4000,
  "limit": 2000
}
```

```json
{
  "resultRef": "tool-result:12345",
  "lineStart": 120,
  "lineEnd": 180
}
```

后续可扩展：

```json
{
  "resultRef": "tool-result:12345",
  "jsonPath": "$.errors[0:10]"
}
```

### 输出格式

```json
{
  "type": "tool_result_range",
  "resultRef": "tool-result:12345",
  "contentType": "text/plain",
  "range": {
    "offset": 4000,
    "limit": 2000
  },
  "content": "...",
  "charCount": 2000,
  "hasMoreBefore": true,
  "hasMoreAfter": true
}
```

### 安全限制

- 每次读取必须有最大字符数。
- 只能读取同一 task/run 权限范围内的结果。
- 输出仍要经过 redaction。
- 不允许模型通过 resultRef 枚举其他用户或其他 task 的内容。
- 对过期、删除或脱敏降级的 blob 返回明确错误。

## 压缩策略

run 内压缩管线分为四类确定性策略和一个兜底策略：

```text
tool_result_budget
  控制单个工具结果和工具结果总预算。

micro_compact
  压缩旧 tool result 的内容，保留 tool result 消息和 tool_call_id。

group-aware snip_compact
  当整体 messages 仍超预算时，按消息组裁剪中间历史。

range_summary_placeholder
  对连续工具调用保留范围说明，第一阶段可先使用确定性占位。

llm_compact
  最后兜底。默认关闭，后续阶段再引入。
```

### 单结果预算

配置项示例：

```yaml
agent:
  tool-result:
    inline-max-chars: 12000
    preview-max-chars: 2000
    range-read-max-chars: 8000
```

规则：

- `resultCharCount <= inlineMaxChars`：完整 inline。
- `resultCharCount > inlineMaxChars`：完整内容写 blob，tool result 返回 preview。
- 如果工具本身已经返回结构化摘要，可以优先 inline 摘要，并将 raw body 写 blob。

### 请求总预算

配置项示例：

```yaml
agent:
  run-context:
    tool-results-total-max-chars: 60000
    keep-recent-tool-results: 3
```

规则：

- 每次 LLM 请求前统计 messages 中 tool result 的模型可见字符数。
- 超过总预算时，从最旧的 tool result 开始压缩。
- 最近 N 个 tool result 默认保留预算版本。
- 当前 step 刚产生、尚未被 LLM 消费的 tool result 不做进一步压缩，只受单结果预算限制。

### 旧结果微压缩

适用条件：

- tool result 已经过至少一次后续 LLM step 消费。
- tool result 不在最近保留窗口内。
- tool result 内容超过占位阈值。

处理方式：

- 有 summary artifact：替换为 summary。
- 无 summary artifact：替换为确定性占位和 resultRef。

### 范围摘要

后续阶段可以针对同类连续工具调用生成范围摘要，例如：

```text
step 2-4 executed 7 code_runner calls. The final failing case is n=0, expected 0 but got 1. Earlier failures were fixed.
```

第一阶段可以先不做 LLM 摘要，只保留接口和 metadata：

- source run id
- step range
- tool call ids
- original token estimate
- compacted token estimate

### 按消息组裁剪

`group-aware snip_compact` 处理的是整体 messages 预算，而不是单个工具结果预算。

触发条件：

- 经过单结果预算、请求总预算和旧工具结果微压缩后，整体 token/char 估算仍超过 `input-token-budget`。
- 或者 messages 数量超过 `max-message-groups`。

建议策略：

```text
keep head:
  system/task 组和必要 compact marker。

keep tail:
  最近 N 个 message group，尤其是最近工具交互组和当前用户意图。

snip middle:
  中间较早且已被摘要覆盖的组优先删除。
  没有摘要的工具组替换为 compact marker，不直接删除。
```

compact marker 示例：

```json
{
  "type": "run_context_snip",
  "snippedGroupCount": 8,
  "source": {
    "runId": "agent-run-id",
    "fromStepIndex": 2,
    "toStepIndex": 4
  },
  "message": "Earlier run context was snipped after tool results were compacted. Refer to request snapshots and tool result refs for debugging."
}
```

注意：

- marker 应作为 system 或 assistant 可读的上下文说明进入 messages，具体 role 需要结合 provider 协议选择。
- 如果 provider 不接受非标准 tool 内容，优先用普通 assistant/system 文本消息承载 marker。
- snip 只影响活跃上下文，不删除 `agent_run_step`、`agent_tool_call`、`agent_content_blob` 或 request snapshot。

### LLM 级 run context compact

当确定性压缩后仍超预算时，可以把当前 run 内较早的消息组交给压缩模型生成摘要，再替换为 `COMPACT_MARKER_GROUP`。

第一版默认不启用，原因：

- 增加额外模型成本。
- 摘要质量会影响后续推理。
- 当前阶段的主要风险通常可以通过大结果预览、旧结果微压缩和按组裁剪解决。

后续启用时必须记录：

- compact prompt version。
- source group range。
- source hash。
- compression model。
- 压缩前后 token/char 估算。
- 是否覆盖工具结果、普通消息或二者都有。

## AgentLoopRunner 集成点

### 工具执行后

当前流程：

```java
var result = executeTool(...);
messages.add(LlmMessage.toolResult(toolCall.id(), result));
```

建议变为：

```text
rawResult = executeTool(...)
visibleResult = toolResultCompactor.compactForModel(context, stepIndex, toolCall, rawResult)
messages.add(LlmMessage.toolResult(toolCall.id(), visibleResult))
```

持久化 observer 仍记录 raw/redacted result。为了避免 observer 和 compactor 各自写一份结果，后续可以让 compactor 产出 storage metadata，observer 在 `onToolEnd` 写入同一份 metadata。

### LLM 请求前

当前流程：

```java
LlmCompletionRequest request = AgentLlmRequestFactory.build(messages, tools, ...)
lifecycle.llmRequestReady(context, stepIndex, request)
llmGateway.stream(request)
```

建议变为：

```text
messages = runMessageCompactor.compactBeforeRequest(context, stepIndex, messages)
request = build(messages, tools, ...)
onLlmRequestReady(...)
stream(request)
```

注意：

- compactor 应返回新的 list 或可追踪的 immutable copy，避免难以解释的原地突变。
- final request snapshot 必须记录压缩后的真实 messages。
- snapshot metadata 应记录压缩前后字符数、被压缩的 resultRef 和策略版本。

## 与 observer 的关系

### PersistentAgentRunTraceObserver

继续负责：

- step start/end。
- tool start/end。
- usage/error。
- 参数和结果脱敏。
- result char/token 估算。

后续增强：

- 写入 `result_storage_mode`。
- 写入 `result_blob_id`。
- 写入 `result_ref`。
- 写入 `result_preview_json`。

### PersistentAgentTraceObserver

继续保存 final request snapshot。run 内压缩后，snapshot 里看到的是模型真实收到的 tool result preview、占位或摘要。

这样可以解释：

- 模型当时是否看到了完整工具结果。
- 哪些结果被压缩。
- 如果模型误答，是否因为 preview 信息不足。

## 回放语义

回放分两种：

### 请求回放

使用 `agent_context_snapshot.request_snapshot_json` 原样重放。它应该包含当时真实发送给模型的压缩后 messages。

### 调试回放

使用 `agent_run_step`、`agent_tool_call` 和 `agent_content_blob` 还原完整执行轨迹。调试界面可以展示：

- 模型真实看到的 preview。
- 完整工具结果 blob。
- 压缩策略和阈值。
- 后续是否调用过 `read_tool_result`。

两者不能混淆。请求回放回答“模型当时看到了什么”，调试回放回答“系统当时完整发生了什么”。

## 安全与留存

- blob 内容默认按敏感数据处理。
- 写入 blob 前执行 redaction，记录 redaction policy version。
- resultRef 必须是 opaque ref，不暴露真实路径或数据库细节。
- `read_tool_result` 必须校验 task/run/user 权限。
- 对超出 retention 的 blob，可以删除正文并保留 tombstone、sha256、char count、删除时间和删除原因。
- 日志中不得输出完整 tool result、resultRef 对应正文或敏感参数。

## 配置建议

第一版默认值建议保守：

```yaml
agent:
  tool-result:
    inline-max-chars: 12000
    preview-max-chars: 2000
    range-read-max-chars: 8000
    blob-enabled: true
  run-context:
    input-token-budget: 120000
    tool-results-total-max-chars: 60000
    keep-recent-tool-results: 3
    compact-old-tool-results: true
    max-message-groups: 80
    snip-keep-head-groups: 2
    snip-keep-tail-groups: 24
    group-aware-snip-enabled: true
    llm-compact-enabled: false
```

说明：

- `inline-max-chars` 控制单个工具结果是否完整进入上下文。
- `preview-max-chars` 控制大结果首次展示给模型的长度。
- `range-read-max-chars` 控制模型通过 `read_tool_result` 追加读取的最大长度。
- `tool-results-total-max-chars` 控制一次 LLM 请求中所有工具结果的总可见字符数。
- `max-message-groups`、`snip-keep-head-groups`、`snip-keep-tail-groups` 控制按组裁剪。
- `group-aware-snip-enabled` 控制是否启用 run 内整体消息裁剪。
- 第一版关闭 LLM compact，避免引入额外模型成本和摘要质量风险。

## 分阶段落地

### 阶段 1：确定性大结果预算

- 新增 `ToolResultCompactionPolicy`。
- 新增 `ToolResultCompactor`。
- 工具执行后，将大结果转换为 preview + resultRef。
- 小结果保持 inline。
- final request snapshot 能看到 preview。

验收标准：

- 单个大工具结果不会完整进入下一次 LLM 请求。
- 小结果行为不变。
- assistant tool call 与 tool result 配对合法。

### 阶段 2：PostgreSQL blob store

- 新增 `agent_content_blob`。
- 新增 blob mapper/repository。
- `agent_tool_call` 记录 result storage metadata。
- 大结果完整保存到 blob。

验收标准：

- 能通过 tool call id 查到完整 blob。
- blob 有 hash、char count、line count 和 redaction policy version。
- run snapshot 只包含 preview，不包含完整大结果。

### 阶段 3：read_tool_result 工具

- 新增 `read_tool_result` tool spec。
- 支持 offset/limit 和 line range。
- 强制最大读取长度和权限校验。
- 返回内容再次进入普通 tool result 预算流程。

验收标准：

- 模型可以按范围读取大结果。
- 读取结果仍受 `range-read-max-chars` 控制。
- 不能读取其他 task/run 的结果。

### 阶段 4：run-local request 前微压缩

- 新增 `RunMessageCompactor`。
- 每次 LLM 请求前压缩旧 tool result。
- 记录 compaction metadata 到 snapshot。

验收标准：

- 连续多个工具调用不会让 messages 无限增长。
- 最近 N 个 tool result 保持可用。
- 旧结果被替换为占位或摘要且协议合法。

### 阶段 5：按消息组裁剪

- 将 messages 解析为 `RunMessageGroup`。
- 实现 group-aware snip compact。
- 支持 head/tail 保留和中间组裁剪。
- 工具交互组只能整体替换为 compact marker，不能只删 tool call 或 tool result。
- snapshot metadata 记录 snipped group 数量、来源范围和策略版本。

验收标准：

- 整体 messages 超预算时可以裁剪中间历史。
- 不产生孤儿 assistant tool call 或孤儿 tool result。
- system/task 组、最近 N 组和当前未消费工具结果保留。
- request snapshot 能解释哪些组被裁剪。

### 阶段 6：范围摘要和 LLM compact

- 引入工具调用范围摘要 artifact。
- 支持按 run/step/tool call 范围生成摘要。
- cheap compaction 后仍超预算时，才触发 LLM 级 compact。

验收标准：

- 摘要记录来源范围、策略版本、模型和 token 估算。
- 原始工具结果不被覆盖。
- compact 后仍保存 final request snapshot。

## 测试策略

### agent-core 单元测试

- 小结果 inline。
- 大结果变成 preview。
- resultRef 为空或非法时拒绝。
- 旧 tool result 压缩后仍保留 toolCallId。
- assistant tool calls 和 tool result 配对顺序不被破坏。
- request 前 compaction 不改变非 tool 消息内容。
- messages 解析为 group 时，多 tool call assistant message 和多个 tool result 保持同组。
- group-aware snip 不裁掉 system/task 组和最近 tail 组。
- group-aware snip 不产生孤儿 tool call 或孤儿 tool result。
- 中间工具组被裁剪时替换为 compact marker，而不是直接删除一半消息。

### persistence 测试

- 大结果写入 `agent_content_blob`。
- `agent_tool_call` storage metadata 正确。
- blob hash、char count、line count 正确。
- redaction 后再写入 blob。

### 集成测试

- 一次 run 内工具返回大结果，下一步 LLM request snapshot 只包含 preview。
- 模型调用 `read_tool_result` 后，只读取指定范围。
- 连续多次工具调用后，旧结果被压缩，最近结果保留。
- 整体 messages 超预算时触发 group-aware snip，snapshot 记录被裁剪组和策略版本。
- 超预算时不会产生孤儿 tool result。

## 与跨轮控制的边界

run 内控制只解决同一次 run 中 messages 增长的问题。跨轮控制仍由 `ContextAssembler` 和后续 artifact 体系负责。

跨轮时不建议默认恢复完整 `assistant tool_calls + tool_result` 消息链。更合理的方式是从 `agent_tool_call`、`agent_content_blob` 和摘要 artifact 中选择上下文片段：

- 当前问题需要的工具摘要。
- 少量关键原始结果。
- 大结果引用和占位。

因此 run 内实现产出的 `resultRef`、blob、char/token 估算、source metadata，会成为跨轮工具上下文召回的基础素材。

## 开放问题

- `resultRef` 是否使用 `tool-result:{blobId}`，还是完全 opaque 的 signed token。
- JSON 大结果是否第一版支持 JSON path，还是先只支持文本 offset/line。
- blob 正文是否需要加密字段或依赖数据库透明加密。
- FileRead 类工具是否需要特殊策略，避免“读 blob 后再次落 blob”的循环。
- `ToolResultCompactor` 和 `PersistentAgentRunTraceObserver` 谁负责最终写 blob，避免重复序列化和事务不一致。

第一版建议先选择简单路径：opaque `resultRef`、文本 offset/line、PostgreSQL blob、确定性 preview，不做 LLM 摘要。
