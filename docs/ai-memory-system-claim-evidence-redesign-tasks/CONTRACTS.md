# AI 记忆系统重构固定契约

本文件是 `AMR-00` 至 `AMR-14` 的实现期固定契约摘要。任务实施时优先遵守这里的约束，不在单个任务内重新发散产品设计。

## 1. 产品与成本边界

- Code Review 仍按用户严格积累 5 条正式 Review 后消费；1 至 4 条可以长期等待。
- 每个满批使用稳定业务幂等键；队列成功确认采用至少一次语义，允许有限技术失败重试，不增加 DLQ 或最大等待时间。
- 横向窗口最多 10 道不同题目，每题只放最新正式 Review；同题多版本只能通过历史工具按需读取。
- 不引入向量数据库、图数据库、通用 Memory 平台或读取时 LLM 整篇改写。
- 旧 `learner_profile_entry` 数据和 v1 topic 数据不回填、不转换、不双写。
- 第一阶段只展示依据，不实现用户 `SUPPRESSED / REJECTED` 操作 UI；状态和 `claimKey` 先保留。

## 2. Claim 范围

Claim kind 与 dimension 固定如下：

| kind | dimension | 来源 |
| --- | --- | --- |
| `DECLARED_FACT` | `LEARNER_BACKGROUND` | 用户明确陈述 |
| `DECLARED_FACT` | `GOALS_AND_INTENTS` | 用户明确陈述 |
| `DECLARED_FACT` | `TIME_AND_RESOURCE_CONSTRAINTS` | 用户明确陈述 |
| `DECLARED_FACT` | `LEARNING_AND_INTERACTION_PREFERENCES` | 用户明确陈述 |
| `DECLARED_FACT` | `SELF_ABILITY_ASSESSMENT` | 用户明确陈述 |
| `GENERAL_OBSERVATION` | `PROBLEM_SOLVING_APPROACH` | 正式 Review |
| `GENERAL_OBSERVATION` | `IMPLEMENTATION_AND_ERROR_PATTERN` | 正式 Review |
| `GENERAL_OBSERVATION` | `LEARNING_INTERACTION_AND_INDEPENDENCE` | 首版禁止 Code Review Agent 生成 |
| `GENERAL_OBSERVATION` | `REVIEW_AND_GROWTH_PERFORMANCE` | 多版本或跨时间正式 Review |
| `TAG_ASSESSMENT` | `TAG_MASTERY` | 绑定受信 `tag_id` 的正式 Review |

一个 claim 只表达一个主要判断。多个判断必须拆成多个 claim，即使它们属于同一 dimension 或 tag。

容量边界：

- declared dimension ACTIVE 最多 10 条。
- general dimension ACTIVE 最多 10 条。
- 单 tag `TAG_MASTERY` ACTIVE 最多 5 条。
- 用户 ACTIVE 500 条为软上限，只记录治理状态并提示既有批次优先合并、修订和退役。
- 用户 ACTIVE 1000 条为硬上限，只拒绝 `ADD`；`CONFIRM / REVISE / RETIRE` 继续允许。
- claim 文本硬上限 600 字符，模型生成目标不超过 300 字符。
- 历史和终态 revision 不计入 ACTIVE 上限，也不自动删除。

## 3. Revision 与 Operation

逻辑 claim 使用服务端 UUID `claim_key`。同一 `claim_key` 的 `revision_no` 从 1 递增，最多一个当前 revision。

状态：

- `ACTIVE`：当前可召回。
- `SUPERSEDED`：历史版本，不召回。
- `RETIRED`：当前终态，新证据表明判断不再成立。
- `SUPPRESSED`：当前终态，用户暂不允许个性化使用。
- `REJECTED`：当前终态，用户认为判断错误。

Agent 只允许输出：

- `ADD`：创建新 `claim_key` 和 revision 1。
- `CONFIRM`：文本不变，使用完整证据集生成下一 ACTIVE revision。
- `REVISE`：scope 不变，更新文本并生成下一 ACTIVE revision。
- `RETIRE`：复制原文本，生成新的 RETIRED 当前 revision。
- 空 operations：`NO_CHANGE`。

`CONFIRM / REVISE / RETIRE` 只能引用当前 ACTIVE revision。模型不能输出状态、grade、provider、model、用户 ID 或时间。

## 4. Evidence

Review role：`OBSERVED / PERSISTED / RESOLVED / REGRESSED / CONTRADICTS`。

Message role：`DECLARED / CORRECTED`。

Evidence pattern：

| pattern | 服务端最小要求 |
| --- | --- |
| `USER_DECLARATION` | 当前用户至少一条消息 |
| `USER_CORRECTION` | 当前纠正消息；新 revision 需要时带旧声明证据 |
| `SINGLE_REVIEW` | 一条正式 Review；只允许标签评价 |
| `SAME_PROBLEM_PERSISTENCE` | 同题至少两个不同版本 |
| `SAME_PROBLEM_RECOVERY` | 同题至少 `OBSERVED + RESOLVED` |
| `SAME_PROBLEM_REGRESSION` | 同题至少三个有序版本，改善后再次出现 |
| `CROSS_PROBLEM_RECURRENCE` | 至少两个不同 problem slug |
| `CROSS_PROBLEM_LONGITUDINAL` | 至少两个不同题目，且至少一题含多版本轨迹 |
| `TAG_BREADTH` | 至少两个不同题目，且 Review 都关联目标 tag |

Evidence grade 只由服务端计算：

- `LIMITED`：单题单版本或跨度有限。
- `SUPPORTED`：同题多版本或至少两个不同题目。
- `STRONG`：至少两个不同题目且包含纵向轨迹，或跨批次持续确认。
- `USER_AUTHORED`：用户明确声明或纠正。

Grade 不是正确概率，UI 不展示 confidence 百分比。每个 revision 的 evidence 必须自包含；不能只存相对上一版本的增量。

## 5. 物理数据模型

最终存在五张新表：

- `learner_memory_update_run`
- `learner_memory_update_run_review`
- `learner_memory_claim_revision`
- `learner_memory_claim_review_evidence`
- `learner_memory_claim_message_evidence`

`learner_memory_update_run` 固定字段至少包括：用户、trigger、status、唯一幂等键、Agent run、Prompt/schema 版本、输入/operation/tool 数、低敏失败码和开始/完成时间。

`learner_memory_claim_revision` 固定字段至少包括：`claim_key`、用户、kind、dimension、tag、revision、status、`claim_text`、规范化 hash、origin、pattern、grade、decision reason、update run、前一 revision、生效区间和审计时间。

核心数据库约束：

- 同一 `claim_key` 最多一个 `status <> 'SUPERSEDED'` 的当前 revision。
- 同一 `claim_key + revision_no` 唯一。
- `supersedes_revision_id` 唯一且不能指向自身。
- 当前 revision `valid_to IS NULL`；`SUPERSEDED` 必须有 `valid_to`。
- kind/dimension/tag 使用数据库 check 固定合法矩阵。
- 同一用户和 scope 下 ACTIVE 的规范化文本 hash 唯一。
- Review evidence FK `ON DELETE RESTRICT`；消息 evidence 同样不得静默失源。
- 整用户物理删除顺序为 memory -> practice/agent -> identity；首版不新增用户删除产品功能。

`learner_memory_update_run_review` 只记录触发满批的固定 5 条 Review；工具探索但未成为最终 evidence 的 Review 只留在 Agent tool trace。

## 6. 事务、并发与幂等

- 所有模型和只读工具调用发生在数据库事务外。
- 应用阶段开启短事务：锁用户行 -> 重读 ACTIVE 集合 -> 校验 snapshot token -> 复核 evidence 和 scope -> 稳定排序应用全部 operation -> 写 evidence 和 run 终态。
- 任一 operation 非法时整个批次零写入。
- STALE 最多重新计算一次；第二次仍 STALE 则失败，不部分应用。
- Code Review 批次幂等键由 `user_id + sorted(reviewIds)` 的 SHA-256 生成。
- declared 更新幂等键继续绑定父 run、父 step 和规范化工具请求；消息 ID 从受信 run/turn 查询，不接受模型传入。
- `agent_run_id` 记录最终采用的 Agent run；全部失败时记录最后一次已创建 run，Agent 自身 retry 链保留各 attempt。

## 7. Code Review 更新 Agent

初始输入只包含：横向窗口、相关可更新 scope 的 ACTIVE claim、用户级 snapshot token 和容量状态。

允许更新：

- `PROBLEM_SOLVING_APPROACH`
- `IMPLEMENTATION_AND_ERROR_PATTERN`
- `REVIEW_AND_GROWTH_PERFORMANCE`
- 横向窗口受影响 tag 的 `TAG_MASTERY`

禁止更新 declared claim 和 `LEARNING_INTERACTION_AND_INDEPENDENCE`。

只读工具：

- `get_problem_review_trajectory(problemSlug)`：仅横向窗口 slug，最近 5 个版本，升序，无完整代码。
- `get_code_review_evidence(reviewId)`：仅当前用户和允许 slug，返回受限 evidence/context/findings。
- `compare_submission_versions(fromReviewId,toReviewId)`：同用户、同题、有序，返回受限 unified diff 和 finding/score 变化。

更新 Agent 最大 4 个模型 step、最多 3 次工具调用、每个 slug 最多一次 trajectory、diff 最多一次。输出根对象只有 `operations`，最多 12 项，严格 `additionalProperties=false`。

## 8. 用户自述更新

- 根 Practice Chat 工具仍由服务端读取当前用户、父 run 和父 step。
- 当前用户消息 ID、原文和归属通过 `AgentTurnMessageLookupRepository` 或等价受信端口查询。
- declared Agent 只允许 `ADD / REVISE / RETIRE / no-op`，不允许 `CONFIRM`、系统观察或 tag claim。
- 每个有效 operation 必须关联当前消息 evidence；纠正时使用 `CORRECTED`，新声明使用 `DECLARED`。
- `SELF_ABILITY_ASSESSMENT` 不得覆盖系统 `TAG_MASTERY`。
- 更新失败继续返回工具失败状态，不阻断 Practice Chat 主回复。

## 9. Practice Chat 召回

每个 run 创建只读 `LearnerMemoryRecallSnapshot`：固定可见 ACTIVE revision 集合、自然主题索引、当前题 slug/tag 和 `documentRevision`。并发更新只从下一个 run 可见。

Bootstrap：

- 配置名 `bootstrap-token-budget`，默认 1000，硬上限 1500。
- 注入工具使用边界、自然主题索引和 3 至 8 条完整直接命中 claim；最多 8 条。
- 优先级：相关用户自述 -> 当前题 tag -> 当前问题相关通用观察 -> grade -> 最近确认时间。
- 不注入全量 claim、Review ID、原始代码、完整 evidence、decision reason 或自由生成的整篇摘要。
- 以完整索引项或完整 claim 为裁剪单位，禁止截断半句。

只读记忆工具：

- `search_learner_memory`
- `read_learner_memory_section`
- `get_learner_memory_evidence`

统一预算：单次最多 20 项、每 run 最多 3 次业务记忆工具、单次模型可见结果最多 8000 字符、合计最多 24000 字符；记忆结果额外 `read_tool_result` 最多 2 次，每次仍受通用 8000 字符上限。

工具参数不接受 user ID、任意 revision ID 或任意 document revision。`sectionRef / statementRef / cursor / resultRef` 由服务端生成并按当前用户、当前 run 和 snapshot 校验。

## 10. 文档投影与 API

`LearnerProfileDocumentProjector` 读取 ACTIVE claim 和 evidence 摘要，确定性生成受限文档 AST；投影不持久化，也不能反向写回 claim。

固定主题：学习背景与目标、学习方式与条件、解题与实现、复盘与成长、知识点表现。空主题不渲染。

API：

- `GET /api/me/learner-profile`
- `GET /api/me/learner-profile/statements/{statementRef}/evidence?cursor=...&limit=20`

主响应：

- `format=MARKDOWN_DOCUMENT_V1`
- `projectorVersion=v1`
- `locale`
- `documentRevision`
- `title`
- `blocks`
- `citationMap`
- `updatedAt`

block 只允许 `HEADING / PARAGRAPH`；span 只允许 `TEXT / SUPPORTED_TEXT`。每个 `SUPPORTED_TEXT` 只对应一个 claim revision 和 citation；`TEXT` 只能放空格、标点或受信模板文本。

每个 citation 主响应最多返回 2 条 preview evidence 和总数；完整 evidence 使用 cursor 分页，每页最多 20 条。`documentRevision` 由 `projectorVersion + locale + 有序 ACTIVE revision ID` 计算，并用于 ETag。

## 11. 前端与深链

- `/me` 展示一篇连续画像，不显示 declared/general/tag tab、entry count、dimension、kind、revision 或 evidence grade。
- 引用编号始终可见；桌面 hover/focus 显示预览，点击、键盘和移动端轻触打开依据抽屉。
- 抽屉懒加载完整 evidence，不能把 preview 当作完整列表。
- Review 深链格式：`/learning-plans/{planId}/phases/{phaseIndex}/problems/{slug}/submissions?review={reviewId}&from=learner-profile&profileAnchor={anchorId}`。
- 前端只用受控 path helper 构造 URL；`profileAnchor` 只接受固定前缀和字符集，不能作为任意返回地址。
- 返回 `/me` 后恢复原句锚点和滚动位置。

## 12. 安全、隐私与观测

- 用户身份只来自认证或受信 Agent context；模型和前端都不能传 user ID 扩大范围。
- API 和记忆工具不返回 raw/normalized code、完整 Review Markdown 或完整用户消息；消息只返回受限摘录。
- claim 作为纯文本转义，不允许任意 HTML、脚本、URL 或模型 citation token；前端不得使用 `dangerouslySetInnerHTML`。
- 日志只记录数量、kind、dimension、tag ID、operation、工具名、字符数和低敏失败码，不记录 claim、代码、Review 或消息正文。
- 指标 label 不使用 user ID、claim key、review ID、tag 或自由文本。
- 最终 LLM 请求诊断快照仍按现有 30 天策略治理；重构必须确保这里只进入 bootstrap/按需读取内容，而不是全量画像或完整证据。
- 工具大结果继续复用 `ToolResultCompactor + ToolResultStore + read_tool_result`，并保持同 run 读取校验。
