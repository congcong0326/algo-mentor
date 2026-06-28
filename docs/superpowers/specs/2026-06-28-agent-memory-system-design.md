# Agent Core 记忆系统设计

## 背景

`algo-mentor` 已经具备 agent loop、Prompt Assembly、上下文快照、run trace、工具结果压缩和 PostgreSQL 持久化基础。下一步需要在不耦合具体学习业务的前提下，为 `agent-core` 设计长期记忆系统，让 agent 能把用户稳定偏好、反复出现的问题、长期学习习惯和工作流经验带入后续对话。

本设计先不展开具体业务能力，只定义通用长期记忆底座。业务侧可以在后续通过 extractor/provider 插件写入“偏好 Java”“喜欢先看思路”“经常漏边界条件”等学习相关记忆。

## 参考做法

- Codex Memories：记忆可由配置启用，启用后由后台从合格历史线程抽取，做 secret redaction，并支持线程级控制；记忆是辅助召回层，必须规则仍应放在 `AGENTS.md` 或受控文档中。
- Claude Code memory：通过 `CLAUDE.md` 等持久指令文件分层加载项目、用户和本地规则，并支持 import 其他文件，体现“索引常驻、内容按需加载”的 progressive disclosure 思路。
- learn-claude-code 的 s09_memory：强调上下文压缩会丢细节，长期记忆应作为独立层存在，低频抽取整理，高频按需召回。
- Anthropic Memory tool：记忆读写由应用实现并暴露为受控工具或接口，模型不直接拥有无限制持久化权限。

落到本项目，应借鉴成熟产品的治理与 progressive disclosure，而不是照搬文件式存储。服务端多用户系统更适合用 PostgreSQL 表达记忆状态、审计事件、来源证据和用户隔离。

## 已确认范围

- 记忆系统采用通用长期记忆底座，不只做会话摘要。
- 长期记忆默认开启，但必须支持用户关闭、清空、删除和审计。
- v1 使用结构化检索，接口预留向量召回；不在第一版引入 pgvector 或 embedding 作业。
- 记忆写入自动进行，不逐条打断用户确认。
- v1 记忆作用域为用户全局；每条记忆仍记录 task、turn、run、message 等来源，便于追踪和删除。
- 控制面放在前端“我的”界面下。后端 API 和审计能力应先完整，前端可以分阶段接入。

## 非目标

- 不把完整聊天历史当作长期记忆。
- 不在 `agent-core` 中引入题目、学习计划、LeetCode、知识点等业务语义。
- 不把记忆当成不可覆盖的系统规则。系统规则仍由 Prompt Assembly 的 `STATIC_INSTRUCTION` 和项目文档承载。
- 不在 v1 建设复杂可视化记忆管理后台，只在“我的”界面提供必要设置、列表、来源和清空入口。
- 不在 v1 实现向量召回、embedding 重建、跨用户共享记忆或团队级知识库。
- 不让前端提交 `userId` 来读写记忆，用户身份只能来自服务端认证上下文。

## 总体架构

```text
Agent run 成功结束
  -> MemoryExtractionScheduler / observer
  -> AgentMemoryExtractor
  -> MemorySafetyFilter
  -> MemoryClassifier
  -> AgentMemoryWriter
  -> PostgreSQL agent_memory / source / event

下一轮业务 command
  -> 读取用户 memory settings
  -> AgentMemoryRetriever
  -> AgentMemorySectionProvider
  -> PromptAssembler
  -> AgentRequest
```

### agent-core

`agent-core` 定义通用契约、模型和策略，不依赖 Spring、PostgreSQL 或业务模块：

- `AgentMemory`：长期记忆条目。
- `AgentMemoryType`：记忆类型枚举。
- `AgentMemoryStatus`：`ACTIVE`、`DISABLED`、`DELETED`。
- `AgentMemorySourceRef`：来源引用，指向 task、turn、run、message 或 trace。
- `AgentMemoryRetrievalRequest`：召回请求，包含 `userId`、查询文本、标签、预算、排除类型等。
- `AgentMemoryRecallResult`：召回结果，包含索引摘要、具体记忆、丢弃原因、token 估算和 metadata。
- `AgentMemoryRetriever`：按预算召回记忆。
- `AgentMemoryWriter`：写入、合并、禁用、删除记忆。
- `AgentMemoryExtractor`：从 run、turn、message、metadata 中抽取候选记忆。
- `AgentMemoryPolicy`：默认开关、召回数量、写入阈值、敏感内容处理、策略版本。
- `AgentMemorySectionProvider`：把召回结果转换为 `PromptSection`。

`agent-core` 不做 SQL、不做 HTTP、不读取认证上下文，也不定义算法学习领域的记忆类型。

### agent-persistence-postgres

`agent-persistence-postgres` 实现存储、检索和审计：

- Flyway migration 新增 `agent_memory`、`agent_memory_source`、`agent_memory_event`、`agent_memory_settings`。
- MyBatis mapper 支持用户隔离查询、结构化检索、merge、tombstone 删除和事件写入。
- v1 使用 PostgreSQL JSONB、数组/全文检索或 `ILIKE` 组合实现结构化召回。
- 预留 `embedding_ref`、`embedding_model` 或 metadata 字段，但不实现向量召回。

### mentor-application

`mentor-application` 提供业务 extractor 和业务 query context：

- 从 practice chat、学习计划、普通 agent 对话中提取候选记忆。
- 将题目标签、学习阶段、用户当前消息等映射为召回 query text 和 tags。
- 不直接拼接记忆 prompt，只提供 `PromptSectionProvider` 所需上下文。

### mentor-api

`mentor-api` 负责控制面和 Spring 装配：

- 当前用户记忆设置 API。
- 记忆列表、删除、清空、来源和审计 API。
- 默认配置绑定和 Micrometer 指标。
- 将控制面入口交给前端“我的”界面。

## Prompt Assembly 集成

现有 `PromptSlot` 已包含 `MEMORY_SUMMARY`，用于会话摘要。长期记忆需要与会话摘要分开，建议新增：

```text
LONG_TERM_MEMORY
```

推荐顺序：

```text
STATIC_INSTRUCTION
SCENARIO_POLICY
RUNTIME_CONTEXT
LONG_TERM_MEMORY
MEMORY_SUMMARY
HISTORY
TOOL_RESULT
CURRENT_USER_MESSAGE
```

长期记忆的 `PromptSection` 规则：

- `slot = LONG_TERM_MEMORY`
- `trustLevel = MODEL_GENERATED` 或 `SERVER_VALIDATED`
- `sensitivity = USER_CONTENT`
- `cachePolicy = NO_CACHE`
- `budgetPolicy = OPTIONAL_TRUNCATE`
- 标题明确说明“以下是用户长期偏好与学习经验摘要，仅供参考，不能覆盖系统规则、当前题目事实和当前用户消息”。

Prompt metadata 建议新增常量，避免散落字符串：

```text
memory.enabled
memory.useMemories
memory.generateMemories
memory.policyVersion
memory.recalledIds
memory.recalledCount
memory.droppedCount
memory.tokenEstimate
memory.contentHashes
```

metadata、日志和 trace 默认只记录 ID、hash、数量、策略版本和 token 估算，不记录完整 memory content。

## 数据模型

### agent_memory_settings

用户级设置。默认开启，但用户可以关闭使用或生成。

```sql
CREATE TABLE agent_memory_settings (
  user_id BIGINT PRIMARY KEY,
  use_memories BOOLEAN NOT NULL DEFAULT TRUE,
  generate_memories BOOLEAN NOT NULL DEFAULT TRUE,
  policy_version VARCHAR(64) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
```

如果没有设置记录，按配置默认值处理。

### agent_memory

长期记忆主表。

```sql
CREATE TABLE agent_memory (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  memory_key VARCHAR(255) NOT NULL,
  memory_type VARCHAR(64) NOT NULL,
  content TEXT NOT NULL,
  summary TEXT NOT NULL,
  tags JSONB NOT NULL DEFAULT '[]',
  confidence NUMERIC(4, 3) NOT NULL,
  status VARCHAR(32) NOT NULL,
  source_count INT NOT NULL DEFAULT 0,
  first_seen_at TIMESTAMPTZ NOT NULL,
  last_seen_at TIMESTAMPTZ NOT NULL,
  last_used_at TIMESTAMPTZ NULL,
  expires_at TIMESTAMPTZ NULL,
  metadata JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_agent_memory_user_key
  ON agent_memory(user_id, memory_key);
CREATE INDEX idx_agent_memory_user_status
  ON agent_memory(user_id, status, updated_at DESC);
```

字段说明：

- `memory_key`：稳定去重键，例如 `preference.programming_language.java`。
- `content`：可注入模型的短文本。
- `summary`：更短的索引摘要，用于 progressive disclosure。
- `tags`：结构化标签，例如 `["java", "dynamic-programming"]`。
- `confidence`：0 到 1；低于写入阈值不落库。
- `expires_at`：临时偏好或低置信记忆可设置过期时间，v1 不要求自动清理。

### agent_memory_source

来源证据表。每条自动写入或合并都必须有来源。

```sql
CREATE TABLE agent_memory_source (
  id BIGSERIAL PRIMARY KEY,
  memory_id BIGINT NOT NULL REFERENCES agent_memory(id),
  task_id BIGINT NULL,
  turn_id BIGINT NULL,
  run_id BIGINT NULL,
  message_id BIGINT NULL,
  source_kind VARCHAR(64) NOT NULL,
  evidence_hash VARCHAR(128) NOT NULL,
  evidence_preview TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_agent_memory_source_memory
  ON agent_memory_source(memory_id, created_at DESC);
```

`evidence_preview` 必须是脱敏短预览，不能保存完整用户隐私或密钥。

### agent_memory_event

审计事件表。

```sql
CREATE TABLE agent_memory_event (
  id BIGSERIAL PRIMARY KEY,
  memory_id BIGINT NULL,
  user_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  actor_type VARCHAR(32) NOT NULL,
  reason VARCHAR(255) NULL,
  before_json JSONB NOT NULL DEFAULT '{}',
  after_json JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_agent_memory_event_user
  ON agent_memory_event(user_id, created_at DESC);
```

事件类型：

```text
CREATED
MERGED
UPDATED
USED
DISABLED
DELETED
CLEARED
REDACTED
SKIPPED
```

## 记忆类型

v1 使用有限枚举，避免过早泛化：

```text
PREFERENCE   用户明确偏好，例如常用语言、讲解风格
WORKFLOW     用户习惯流程，例如先提示再给完整代码
PROFILE      稳定画像，例如正在准备算法面试
PITFALL      反复出现的问题，例如边界条件容易漏
FACT         用户明确陈述的长期事实
SKILL_HINT   系统总结出的学习建议
```

类型边界：

- `PREFERENCE` 和 `FACT` 优先来自用户明确陈述。
- `PITFALL` 和 `SKILL_HINT` 可以由系统推断，但需要更高来源数量或较低注入权重。
- `PROFILE` 容易过度推断，写入阈值应高于普通偏好。

## 写入链路

长期记忆写入异步进行，不阻塞 SSE 回复。

```text
Agent run 成功结束
  -> 判断 generate_memories 是否开启
  -> 收集本轮脱敏输入
  -> AgentMemoryExtractor 生成候选
  -> MemorySafetyFilter 过滤 secret、隐私和短期状态
  -> MemoryClassifier 归类 key/type/tags/confidence
  -> MemoryMergeService upsert 或 merge
  -> 写 source 和 event
```

### v1 抽取策略

v1 先实现规则抽取，模型抽取只预留接口。

规则抽取适合这些模式：

- “以后都用 Java”
- “我喜欢先看思路”
- “不要一开始直接给完整答案”
- “我在准备算法面试”
- “我经常忘记处理空数组”

模型抽取接口：

- `LlmAgentMemoryExtractor` 以后可以在后台低频运行。
- 抽取模型输出必须走结构化 schema，并通过 safety filter 二次检查。
- 抽取失败不影响主 run。

### 自动写入规则

- 低于 `min-write-confidence` 的候选不写入。
- SECRET 级内容直接拒绝，写 `SKIPPED` 或 `REDACTED` 事件。
- 短期状态不写入，例如“今天在做第 3 题”。
- 模型推断不能当作高置信事实；需要多来源合并后才提升权重。
- 每次写入、合并、禁用或删除都必须写审计事件。

### 合并与冲突

- 同 `memory_key`：更新 `last_seen_at`、增加 `source_count`、追加 source，必要时提升 `confidence`。
- 明确冲突偏好：新偏好明确且置信度高时，旧记忆置为 `DISABLED`，新记忆置为 `ACTIVE`，事件 reason 写 `superseded_by_conflicting_memory`。
- 长期未使用记忆：v1 不自动删除，只在排序时降权。

## 召回链路

每次准备 `AgentRequest` 之前召回用户全局记忆。

```text
业务 command
  -> 读取 memory settings
  -> 构造 AgentMemoryRetrievalRequest
  -> AgentMemoryRetriever 返回 AgentMemoryRecallResult
  -> AgentMemorySectionProvider 生成 PromptSection
  -> PromptAssembler 输出 canonical messages
```

召回请求字段：

- `userId`
- `queryText`：当前用户消息、任务标题、题目标签、少量业务上下文组合。
- `tags`
- `maxMemories`
- `tokenBudget`
- `excludedTypes`
- `metadata`

召回结果：

- `indexSummary`：短索引摘要，例如“用户偏好 Java，喜欢先看思路，近期常卡在 DP 状态定义”。
- `selectedMemories`：top N 具体记忆。
- `droppedReasons`：预算不足、状态不可用、敏感过滤、低分等。
- `tokenEstimate`
- `metadata`

### 排序策略

v1 可使用结构化打分：

```text
score =
  keywordMatch * 0.35
  + tagMatch * 0.20
  + confidence * 0.25
  + recency * 0.10
  + typeWeight * 0.10
```

类型权重建议：

```text
PREFERENCE  1.00
WORKFLOW    0.95
FACT        0.85
PROFILE     0.75
PITFALL     0.70
SKILL_HINT  0.65
```

召回时只读取 `ACTIVE`。`DISABLED` 和 `DELETED` 不进入 prompt。

### Progressive Disclosure

召回不是全量塞入 prompt：

- 先构造短 `indexSummary`。
- 再选择 3 到 8 条具体记忆。
- 长期记忆使用独立 token 上限，例如总 prompt budget 的 8% 到 12%，默认 `max-memory-tokens = 800`。
- 超预算时优先保留高置信偏好和工作流，丢弃低置信 skill hint。

### 降级规则

- 用户关闭 `use_memories`：不召回。
- 查询失败：跳过长期记忆，不阻断对话。
- 召回为空：不生成空 section。
- 敏感过滤命中：跳过该条并写 `REDACTED` 或 `SKIPPED` event。
- 当前 run metadata 明确禁用记忆：本轮覆盖用户默认设置。

## API 与“我的”界面

后端 API 放在 `mentor-api`，前端入口放在“我的”界面下。建议页面分为“记忆设置”“我的记忆”“来源记录”三个区域。

### API 草案

```text
GET    /api/agent/memories/settings
PATCH  /api/agent/memories/settings
GET    /api/agent/memories
GET    /api/agent/memories/{id}
DELETE /api/agent/memories/{id}
POST   /api/agent/memories/clear
GET    /api/agent/memories/{id}/sources
GET    /api/agent/memories/{id}/events
```

`PATCH /settings` 请求：

```json
{
  "useMemories": true,
  "generateMemories": true
}
```

`GET /memories` 查询参数：

```text
status=ACTIVE
type=PREFERENCE
tag=java
page=1
pageSize=20
```

`POST /clear` 请求：

```json
{
  "reason": "user_requested_clear"
}
```

### “我的”界面交互

第一版前端不需要复杂管理后台，但应包含：

- 两个开关：使用已有记忆、从后续对话生成新记忆。
- 记忆列表：类型、摘要、标签、置信度、最近使用时间。
- 单条删除。
- 清空所有记忆。
- 来源抽屉：展示脱敏 evidence preview、来源时间、事件类型。
- 空态文案说明：记忆用于个性化学习体验，可随时关闭或清空。

## 安全与隐私

长期记忆默认开启，因此安全治理是 v1 必需能力。

- 所有 API 只能访问当前登录用户自己的记忆。
- `user_id` 只能来自 `CurrentUserIdProvider` 或认证 principal。
- extractor 输入先脱敏，写入前再过滤。
- SECRET 级内容不得进入 `agent_memory.content`、`summary`、`evidence_preview`、日志或 metadata。
- 删除使用 tombstone：清空 `content`、`summary` 和可识别 metadata，保留 id、删除时间、删除原因、hash 和事件。
- `agent_context_snapshot` 只记录 recalled memory ids/hash，不记录完整记忆内容，除非以后有受控 debug 开关。
- 普通日志只输出 id、type、status、数量、policyVersion，不输出 content。
- 清空记忆应批量 tombstone 并写 `CLEARED` 事件。

## 配置

建议配置：

```yaml
algo-mentor:
  agent:
    memory:
      enabled: true
      use-memories-default: true
      generate-memories-default: true
      max-recalled-memories: 6
      max-memory-tokens: 800
      min-write-confidence: 0.72
      policy-version: memory-policy-v1
```

配置语义：

- `enabled=false`：全局禁用记忆读写，API 仍可返回禁用状态。
- `use-memories-default`：无用户设置记录时是否召回记忆。
- `generate-memories-default`：无用户设置记录时是否从新 run 生成记忆。
- `max-recalled-memories`：单次最多注入的具体记忆数。
- `max-memory-tokens`：长期记忆 prompt 预算。
- `min-write-confidence`：自动写入阈值。
- `policy-version`：召回、写入和审计记录中的策略版本。

## 观测指标

Micrometer 指标建议：

```text
agent.memory.recall.count
agent.memory.recall.latency
agent.memory.write.count
agent.memory.write.skipped
agent.memory.redacted.count
agent.memory.injected.tokens
agent.memory.extract.failure
agent.memory.clear.count
```

标签建议：

- `memoryType`
- `status`
- `reason`
- `policyVersion`
- `adapter`

不要把 `memoryKey`、用户输入或 content 作为指标标签。

## 测试策略

### agent-core 单元测试

- `AgentMemoryPolicy` 默认开关和预算。
- `AgentMemoryRetriever` 排序、过滤和预算裁剪。
- `AgentMemorySectionProvider` 生成 `LONG_TERM_MEMORY` section。
- Prompt slot 顺序正确，长期记忆不覆盖当前用户消息。
- metadata 只包含 ids/hash/数量，不包含完整 content。

### persistence 测试

- 用户隔离：用户 A 不能读写用户 B 的记忆。
- `ACTIVE` 才能召回。
- `DISABLED` 和 `DELETED` 不召回。
- `DELETE` 使用 tombstone。
- `clear` 批量 tombstone 并写事件。
- `memory_key` merge 不重复插入。

### application 测试

- 规则 extractor 能识别明确偏好。
- 低置信候选不写入。
- secret、token、Authorization、cookie 被拒绝。
- 冲突偏好禁用旧记忆并启用新记忆。

### API 测试

- 未登录返回 401。
- 只能访问当前用户记忆。
- 关闭 `useMemories` 后后续 run 不召回。
- 关闭 `generateMemories` 后成功 run 不生成新记忆。
- 清空后列表为空，审计事件存在。

### 前端测试

- “我的”界面能读取和切换记忆设置。
- 列表、删除、清空、来源抽屉状态正确。
- API 失败时不误显示已删除或已关闭。

## 分阶段落地

### 阶段 1：agent-core 契约与 Prompt slot

- 新增 memory 模型、枚举、策略、retrieval request/result。
- 新增 `LONG_TERM_MEMORY` slot 并调整 canonical order。
- 新增 memory metadata key 常量。
- 实现内存版 retriever/writer 供单元测试和后续 wiring 使用。

### 阶段 2：PostgreSQL 存储

- 新增 Flyway migration。
- 新增 MyBatis mapper、repository 实现和 repository 测试。
- 实现 settings、query、merge、delete、clear、event/source 写入。

### 阶段 3：召回注入

- 在 application service 准备 `AgentRequest` 前调用 retriever。
- 实现 `AgentMemorySectionProvider`。
- metadata 写入 recalled ids/hash/token estimate。
- 查询失败降级为不注入。

### 阶段 4：后台自动写入

- 从成功 run 结束事件触发 extraction job。
- 实现规则 extractor、安全过滤、classifier、merge service。
- 写 source 和 event。
- 增加指标和失败日志。

### 阶段 5：控制面和“我的”界面

- 后端 settings/list/delete/clear/source/event API。
- “我的”页新增记忆设置、记忆列表、来源抽屉和清空入口。
- 文案说明默认开启和可关闭/清空。

### 阶段 6：模型抽取与向量召回预留

- 增加 `LlmAgentMemoryExtractor` 实现，但默认可关闭。
- 评估 pgvector 和 embedding 重建策略。
- 在不改变 `AgentMemoryRetriever` 调用方的前提下增加 semantic retriever。

## 风险与取舍

- 默认开启带来隐私风险，因此 v1 必须同时交付关闭、删除、清空和审计。
- 自动写入可能误记，因此 v1 先以规则抽取为主，模型抽取预留但不强依赖。
- 用户全局作用域简单，但可能跨场景误召回。通过 type、tag、queryText、预算和排序降低风险，后续可再扩展 scope。
- 不做向量召回会降低语义匹配能力，但 v1 可解释性更强，成本和治理复杂度更低。
- 独立 `agent_memory` 表比复用 `agent_artifact` 多一些工作量，但记忆需要独立设置、删除、审计、merge 和召回状态，长期更清晰。

## 验收标准

- 用户默认可以使用和生成长期记忆。
- 用户可以在“我的”界面关闭使用、关闭生成、删除单条、清空全部，并查看来源预览。
- `agent-core` 不依赖业务模块或数据库。
- 长期记忆通过 Prompt Assembly 注入，不在 service 中拼接 prompt。
- 关闭 use memory 后，本轮 prompt metadata 不包含 recalled memory ids。
- 关闭 generate memory 后，成功 run 不新增 `agent_memory`。
- secret、Authorization、cookie、JWT、token、password 不会进入 memory content、summary、preview、日志或 metadata。
- 所有写入、合并、删除和清空都有审计事件。
- 结构化召回失败不影响主对话。

## 参考链接

- Codex Memories：https://developers.openai.com/codex/memories
- Codex AGENTS.md 与 progressive guidance：https://developers.openai.com/codex/guides/agents-md
- Claude Code memory：https://code.claude.com/docs/en/memory
- Anthropic Memory tool：https://platform.claude.com/docs/en/agents-and-tools/tool-use/memory-tool
- learn-claude-code s09_memory：https://github.com/shareAI-lab/learn-claude-code/tree/main/s09_memory
