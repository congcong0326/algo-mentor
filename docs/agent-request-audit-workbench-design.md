# Agent 请求审计工作台设计

## 文档信息

- 设计日期：2026-08-12
- 文档性质：功能需求与技术设计
- 适用范围：Agent 运行态、LLM 最终请求、工具调用、工具结果和上下文压缩的只读审计
- 首要场景：`PRACTICE_CHAT`
- 关联权限：复用现有 `ai-run:read`

## 1. 背景与问题

当前系统已经持久化 Agent 执行链路，但排查一次请求需要人工关联多张表：

```text
agent_task
  -> agent_turn
    -> agent_message
    -> agent_run
      -> agent_run_step
        -> agent_context_snapshot
        -> agent_tool_call
```

这使以下问题无法快速回答：

- 用户在哪个场景、哪个 turn 发起了什么请求；
- 最终发送给 provider 的完整脱敏 messages 和 tools schema 是什么；
- 模型在每个 step 调用了哪些工具，参数和结果是什么；
- 工具结果是否被 inline、preview、引用或占位替换；
- run-local loop 是否执行了压缩、旧结果微压缩或消息组裁剪；
- Prompt 估算预算、最终请求估算和 provider 实际用量之间是否偏差过大；
- cached input tokens 是否增加，模型侧 Prompt Cache 是否命中；
- 某次请求为何失败、超出 step、超出预算或被 provider 拒绝。

典型问题是 Practice Chat 配置了 `8,000` token Prompt 预算，但 Prompt Assembly 的内部估算没有覆盖工具 schema 和工具交互消息，最终 provider 输入可能达到 `8,762` token。没有审计页面时，定位该问题必须直接查询数据库并手工拼接请求快照、step、工具调用和 usage。

## 2. 目标

建设一个管理员只读的“Agent 请求审计工作台”，以 `task -> turn -> run -> step` 为主线展示每次真实出站 LLM 请求和完整工具交互。

目标包括：

1. 通过列表筛选快速定位异常 run，不依赖人工 SQL。
2. 通过时间线还原一次用户 turn 的执行过程。
3. 对每个最终出站请求展示脱敏后的 messages、tools、tool choice、生成参数和 usage。
4. 明确区分 Prompt Assembly 估算、最终出站请求估算和 provider 实际 usage。
5. 结构化展示裁剪、压缩、摘要、丢弃和工具结果预算化行为。
6. 支持观察模型侧 Prompt Cache：展示 `inputTokens`、`cachedTokens`、未缓存输入和缓存比例。
7. 保持用户原文、代码、工具结果和凭据的最小暴露原则，并记录管理员查看行为。

## 3. 非目标

第一版不包含：

- 修改 Prompt、工具权限、模型路由或预算策略；
- 在页面中重放、重试、取消或重新执行 Agent；
- 修改或删除运行态数据；
- 自动生成新的 LLM 摘要；
- 把工具调用和工具结果写入用户可见聊天消息；
- 建设通用 Prometheus/Grafana 替代品；
- 默认展示未脱敏的完整用户隐私或完整大工具结果。

## 4. 术语与展示主线

```text
task：整个会话容器，可包含多个用户 turn
turn：一次用户输入及其最终 assistant 回复
run：处理一个 turn 的一次 Agent 执行；重试可能产生多个 run attempt
step：run 内的一次 LLM 请求和响应
tool call：某个 step 中模型发出的工具调用
context snapshot：发送给 provider 前保存的最终脱敏请求快照
```

一个带工具的 run 的时间线：

```text
turn 开始
  -> Prompt Assembly（system/history/current user）
  -> step 1 request（messages + tools schema）
  -> step 1 response（assistant tool_calls）
  -> tool execution
  -> tool result compaction（inline/preview/ref/placeholder）
  -> step 2 request（messages + tool interaction + tools schema）
  -> step 2 final response
  -> turn/run 完成
```

## 5. 现有数据与缺口

### 5.1 已有数据

`agent_context_snapshot` 已保存：

- `task_id`、`run_id`、`step_index`、request id；
- provider、model、model selector；
- policy name/version；
- `token_budget`、`token_estimate`、reserved output tokens；
- 脱敏后的 `request_snapshot_json`；
- 脱敏后的 `messages_json`、`tools_json`、`tool_choice_json`、`generation_options`；
- request hash、redaction policy version、metadata。

`agent_run_step` 已保存：

- step 状态、provider、model、finish reason；
- provider usage；
- request snapshot 关联；
- 错误和时间。

`agent_tool_call` 已保存：

- tool call id、工具名称、参数；
- 工具状态、耗时、错误；
- 结果字符数和 token 估算；
- 结果存储模式、preview、blob id、result ref；
- redaction policy 和 metadata。

### 5.2 当前缺口

需要通过查询聚合或新增结构化 metadata 补齐：

- Prompt Assembly 估算与最终消息估算的分项数据；
- tools schema 估算；
- provider 消息封装开销估算；
- 最终请求总估算和预算状态；
- 实际 input/cached/output/reasoning/total token 的统一展示；
- compaction 是否执行及执行动作列表；
- 被丢弃、截断、摘要或替换的 section/message group；
- 跨 step 的上下文增长和压缩前后对比；
- 缓存命中比例和按 step 的变化趋势。

现有 `token_budget` 快照值可能为 `0`，且通用脱敏规则会误将包含 `token` 的观测字段隐藏。该问题应在审计功能的后端准备阶段修复，否则页面无法可信展示预算。

## 6. 页面需求

### 6.1 页面入口

在管理员 AI 平台下新增“请求审计”页面：

```text
AI 平台
  模型资源
  成本治理
  请求审计
  系统 Prompt
```

权限使用现有 `ai-run:read`。若后续需要更细的原文读取权限，再新增独立权限；第一版不复用写权限作为读取依据。

### 6.2 Run 列表

页面默认展示最近 24 小时，按最近请求时间倒序分页。支持筛选：

- from/to 时间；
- user id；
- scenario/purpose/source；
- task id、turn id、run id；
- provider、model；
- run status、finish reason；
- 是否有 tool call；
- 是否发生 compaction；
- 是否预算超限；
- 是否有 provider error；
- cached token 或 cache ratio 范围。

列表列：

| 列 | 说明 |
| --- | --- |
| 时间 | run 最后更新时间或最后一个 step 请求时间 |
| 场景 | `PRACTICE_CHAT` 等业务场景 |
| 用户 | 用户 ID；展示名称按现有管理员用户摘要规则脱敏/展示 |
| task/turn/run | 可点击进入详情 |
| provider/model | 实际路由结果 |
| steps | run step 数及成功/失败摘要 |
| tools | 工具调用总数 |
| 估算/预算 | 最终请求估算与预算，例如 `7,420 / 8,000` |
| 实际输入 | provider `inputTokens` |
| cached | provider `cachedTokens` 与比例 |
| 压缩 | 是否执行及动作数量 |
| 状态 | succeeded、failed、cancelled、超步等 |

列表必须突出预算异常：

```text
最终估算 7,920 / 8,000
provider 实际 8,762
状态：实际输入超预算 762
```

### 6.3 Run 详情

详情顶部展示：

- 用户、场景、task/turn/run、run attempt；
- 开始/结束时间、总耗时；
- provider/model、finish reason；
- 总 input/output/cached/reasoning/total tokens；
- 总工具调用数、失败工具数；
- 预算状态、压缩状态、缓存比例。

主体使用 step 时间线：

```text
Step 1  request -> tool call -> tool result
Step 2  request -> final output
```

每个 step 显示：

- 请求开始、结束、耗时；
- messages 数量和角色统计；
- messages/token 估算；
- tools 数量和 schema 估算；
- 最终请求总估算、预算、剩余预算；
- provider usage 和 cached token；
- finish reason；
- compaction 前后字符/token 估算；
- 压缩动作：`tool_result_preview`、`old_tool_result_compacted`、`group_snip`、`section_drop`、`section_truncate`、`summary`；
- request snapshot、tool calls、错误详情入口。

### 6.4 请求快照视图

请求快照默认提供以下标签页：

1. **概要**：provider、model、budget、estimate、usage、cache、hash。
2. **Messages**：按消息顺序展示 role、tool call id、字符数、token 估算和脱敏正文。
3. **Tools**：展示工具名称、描述、参数 schema、工具数量和 schema 估算。
4. **Compaction**：展示压缩前后统计、动作列表和被影响的 section/group。
5. **Raw JSON**：展示最终脱敏 provider 请求 JSON，仅对具备审计读取权限的管理员开放。

消息展示必须标记来源：

```text
SYSTEM_STATIC
SERVER_VALIDATED
USER_INPUT
MODEL_GENERATED
TOOL_OUTPUT
```

工具结果展示：

- inline：显示有限长度内容；
- preview：显示 preview 和 result ref；
- compacted：显示占位摘要和原始结果引用；
- blob：默认不加载完整内容，按需读取且受权限、范围和 retention 限制。

### 6.5 会话/Turn 视图

Run 详情应提供“所在会话”入口，展示同一 task 的 turn 列表：

- 每个 turn 的用户消息和最终 assistant 回复；
- 关联的 run attempts；
- 每个 turn 是否调用工具；
- 每个 turn 的总 usage、缓存比例和预算异常；
- 当前 turn 使用了哪些历史消息，但不默认恢复上一 turn 的完整 tool call/result 链。

## 7. API 设计

### 7.1 列表

```http
GET /api/admin/ai/audit/runs
```

查询参数：

```text
from, to
page, pageSize
userId
scenario
taskId, turnId, runId
provider, model
status, finishReason
hasTools, hasCompaction, overBudget
minCachedTokens, maxCachedTokens
```

响应：

```json
{
  "items": [
    {
      "runId": 213,
      "runUuid": "...",
      "taskId": 142,
      "turnId": 321,
      "userId": 1,
      "scenario": "PRACTICE_CHAT",
      "provider": "deepseek",
      "model": "deepseek-v4-flash",
      "status": "SUCCEEDED",
      "stepCount": 2,
      "toolCallCount": 1,
      "promptTokenBudget": 8000,
      "finalEstimateMax": 7920,
      "actualInputTokens": 8762,
      "cachedTokens": 0,
      "overBudgetTokens": 762,
      "compactionApplied": false,
      "startedAt": "...",
      "endedAt": "..."
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 20
}
```

列表响应不得包含完整 messages、tools schema 或工具结果正文。

### 7.2 Run 详情

```http
GET /api/admin/ai/audit/runs/{runId}
```

响应包含：

- run 概要；
- turn user/assistant 摘要；
- steps 时间线；
- 每个 step 的 usage、预算和压缩摘要；
- 工具调用摘要；
- 是否存在可查看的 snapshot/blob。

### 7.3 Step 详情

```http
GET /api/admin/ai/audit/runs/{runId}/steps/{stepIndex}
```

响应包含：

- 脱敏后的最终请求 snapshot；
- messages、tools、tool choice、generation options；
- 对应的 tool calls 和 tool results；
- compaction metadata；
- provider usage；
- redaction policy 和 retention 信息。

### 7.4 工具结果按需读取

```http
GET /api/admin/ai/audit/runs/{runId}/tool-results/{toolCallId}
```

第一版默认只返回 preview、hash、字符数、存储模式和 result ref。完整内容必须：

- 校验管理员具备 `ai-run:read`；
- 校验 run/tool call 归属；
- 应用 retention 和最大读取范围；
- 返回审计访问记录；
- 继续执行敏感字段脱敏。

## 8. 后端分层与查询实现

推荐新增通用查询端口，不让 controller 直接依赖 MyBatis：

```text
agent-core
  runtime/audit/
    AgentAuditQuery.java
    AgentAuditRunSummary.java
    AgentAuditRunDetail.java
    AgentAuditStepDetail.java
    AgentAuditToolCall.java
    AgentAuditRepository.java

agent-persistence-postgres
  repository/PostgresAgentAuditRepository.java
  mapper/AgentAuditMapper.java
  mapper/AgentAuditMapper.xml

mentor-api
  controller/admin/ai/AdminAiAuditController.java
  controller/admin/ai/model/*AuditResponse.java
```

列表查询应以 `agent_run` 为主表，聚合：

- `agent_turn` / `agent_message` 获取用户和最终回复摘要；
- `agent_run_step` 获取 step 数、状态和 usage；
- `agent_context_snapshot` 获取预算、估算、tools/message snapshot 元数据；
- `agent_tool_call` 获取工具调用数量、失败数量和结果压缩信息。

详情查询再按 run/step 分批读取 JSON，避免列表接口加载大字段。

所有列表查询必须：

- 使用分页；
- 限制最大 page size；
- 按 `started_at DESC, id DESC` 稳定排序；
- 对 user input、assistant output、tool result 做长度限制；
- 不把原始 prompt 拼到日志或异常消息中。

## 9. 预算与压缩观测契约

运行时应统一使用以下非敏感数值 metadata。字段名不得被通用 secret redaction 误处理：

```text
promptTokenBudget
assemblyTokenEstimate
messageTokenEstimate
toolsTokenEstimate
providerOverheadTokenEstimate
finalRequestTokenEstimate
actualInputTokens
cachedTokens
outputTokens
reasoningTokens
totalTokens
budgetStatus
overBudgetTokens
compactionApplied
compactionPolicyVersion
compactionActions
compactionBeforeChars
compactionAfterChars
compactionBeforeTokenEstimate
compactionAfterTokenEstimate
truncatedSectionIds
droppedSectionIds
snippedMessageGroupCount
toolResultPreviewCount
toolResultCompactedCount
```

推荐的 `budgetStatus`：

```text
WITHIN_ESTIMATE
ESTIMATE_OVER_BUDGET
PROVIDER_ACTUAL_OVER_BUDGET
REQUIRED_CONTENT_OVER_BUDGET
UNKNOWN_PROVIDER_USAGE
```

说明：

- `finalRequestTokenEstimate` 必须覆盖 messages、tools schema 和 provider overhead 的估算；
- `actualInputTokens` 和 `cachedTokens` 只在 provider 返回 usage 后写入；
- provider 实际用量不能用于回溯裁剪已经发送的请求，只用于审计、告警和估算校准；
- 原始完整工具结果仍保存于既有 tool result storage，审计页面展示的是脱敏后的模型可见版本和引用信息。

## 10. 安全、隐私与留存

审计页面属于高敏感管理员能力：

- 默认权限：`ai-run:read`；
- 不新增“重放/执行”能力；
- 列表仅展示摘要和计数；
- 详情默认展示脱敏内容和有限长度 preview；
- API key、Authorization、cookie、密码、JWT、secret 等凭据永不展示；
- 用户代码、题面、工具结果遵循现有 retention 和 redaction policy；
- 完整 blob 按需读取，限制最大范围，禁止批量导出；
- 记录管理员查看 run、step、raw JSON 和完整工具结果的审计事件；
- 不在 metrics tag、普通日志或 URL 中放 user id 以外的高基数原文、prompt、response 或 result ref 正文。

如果后续需要把审计能力开放给普通用户，应另建“本人 run 查询”接口，不能复用管理员接口。

## 11. 分阶段实施

### 阶段 1：后端只读查询与观测修复

- 修复 snapshot `token_budget=0` 的字段映射；
- 调整 redactor，使 token 计数、预算和 usage 数值可观测；
- 定义 audit query/domain DTO；
- 新增列表、run 详情、step 详情 API；
- 覆盖分页、权限、脱敏、run/step/tool 关联测试。

验收：不查数据库即可定位某个 run 的场景、step、工具调用、预算、估算和 provider usage。

### 阶段 2：前端审计工作台

- 在 AI 平台新增“请求审计”导航；
- 实现筛选栏、run 列表、预算异常标记；
- 实现 run 时间线和 step 详情；
- 实现 Messages/Tools/Compaction/Raw JSON 标签页；
- 适配中英文资源和移动端基本可读性。

验收：使用审计样本可以在页面中复原 `step 1 -> tool call -> tool result -> step 2`，并看到 `8,000` 与 `8,762` 的差异。

### 阶段 3：完整预算和压缩可观测性

- 在最终出站请求前计算 messages + tools schema 的完整估算；
- 将 run-local compaction metadata 结构化写入 snapshot/step；
- 展示估算与实际 usage 偏差；
- 增加超预算和缓存命中率筛选、排序和统计。

验收：中文、长代码、工具调用和多 step 样本均能显示预算分项、压缩动作和 provider 实际结果。

### 阶段 4：告警与长期趋势

- 增加按场景/provider/model 的超预算率、缓存命中率趋势；
- 对 provider 实际输入超过预算、usage 缺失和估算偏差异常提供告警；
- 与现有 AI usage 页面形成跳转关系。

## 12. 测试要求

后端：

- repository 查询结果正确关联 task/turn/run/step/tool；
- 分页、过滤、排序和最大 page size；
- 无权限访问返回统一错误；
- redaction 不泄露 secret，但保留 token budget/usage 数值；
- 大 JSON、空 tool result、失败 tool call、缺失 usage 和重试 run；
- 预算状态和 compaction action 映射。

前端：

- 列表筛选和分页状态流转；
- run 详情时间线；
- step 标签页切换；
- 预算超限、压缩、缓存命中和错误状态展示；
- 长消息、长工具 schema、中文内容不溢出；
- 权限不足时不显示入口、不发起接口请求。

## 13. 风险与回滚

主要风险：

- 详情 JSON 较大导致查询和页面加载慢；
- 管理员误查看用户隐私；
- 历史快照字段缺失或旧版本 metadata 不兼容；
- provider usage 口径不同，缓存比例不可直接跨 provider 比较。

控制措施：

- 列表和详情分离，详情 JSON 延迟加载；
- 统一脱敏、长度和 retention；
- 所有字段允许 `null/unknown`，不把旧数据伪装成准确值；
- 缓存指标按 provider/model 分组展示；
- 页面为只读功能，可通过权限或配置关闭；
- 后端查询和前端页面可独立回滚，不修改既有 run/tool 数据。

## 14. 完成标准

当以下条件全部满足时，第一版审计工作台视为完成：

1. 管理员可按场景、用户、时间、provider、model 和异常状态筛选 run。
2. 不查数据库即可看到一次 turn 的 run/step/tool 时间线。
3. 每个 step 能查看最终脱敏 messages、tools schema 和工具结果 preview。
4. 页面同时展示 Prompt Assembly 估算、最终请求估算、预算和 provider 实际 input/cached tokens。
5. 页面能明确标记压缩、截断、丢弃、preview、引用和超预算状态。
6. `8,000` 预算与 `8,762` 实际输入的历史样本可以被复现和解释。
7. 权限、脱敏、retention 和管理员查看审计均通过测试。
