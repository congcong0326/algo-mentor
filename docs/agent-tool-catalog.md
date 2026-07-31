# Agent Tool 当前状态清单

## 1. 文档范围

本文分析当前代码库中实现 `AgentTool` 接口、可通过 LLM function calling 进入 Agent loop 的生产工具。

本文是生产 Tool 现状的维护基线。任何新增、删除、重命名 Tool，或对 Tool 的业务白名单、默认开关、读写副作用和权限边界做出变更时，都必须在同一代码变更中同步更新本文。尚未实现的规划 Tool 不进入本文统计，统一记录在 `docs/agent-tool-development-requirements.md`。

统计时包含：

- `backend/agent-core` 中的通用运行时工具。
- `backend/mentor-api` 中的题库工具。
- `backend/mentor-application` 中的练习、代码 Review 和学习者记忆工具。

统计时不包含：

- 单元测试中的 `fake_lookup`、`lookup` 等替身工具。
- 只存在于设计文档、尚未实现的规划工具。
- 普通 Java `util`、前端工具栏和根目录 `tools/` 下的数据准备脚本。

截至当前代码，生产代码共有 **15 个 `AgentTool` 实现**。需要特别区分两个概念：

- **已注册**：Spring Bean 被收集进全局 `AgentToolRegistry`。
- **可调用**：某个 `AgentDefinition.allowedToolNames()` 把工具加入当前 run 的白名单，模型才会看到并执行它。

因此，工具已经实现或默认注册，并不代表当前用户场景一定能调用它。

按 `application.yml` 默认值并假设 PostgreSQL 等完整依赖均已装配：

- 默认实际暴露给业务 Agent 的工具有 7 个：`list_problem_filters`、`search_problems`、`read_tool_result`、`submit_practice_code_review`、`get_current_problem_learning_state`、`append_current_problem_note`、`get_problem_review_trajectory`。
- 通过可选能力开关可再暴露 7 个学习者记忆工具。
- `calculator` 和 `get_problem_statement` 虽然默认注册，但当前没有任何统一 Runtime Definition 将其加入白名单。

## 2. Tool 运行与隔离模型

当前工具调用链如下：

```text
Spring AgentTool Bean
  -> AgentToolRegistry 全局注册
  -> AgentDefinition.allowedToolNames() 选择当前场景白名单
  -> LLM 仅看到白名单内的 LlmToolSpec
  -> AgentToolPermissionGuard 执行权限判断
  -> AgentTool.execute(...)
  -> ToolResultCompactor 压缩或保存大结果
  -> 工具结果回填模型，继续下一步推理
```

主要边界：

- `AgentToolRegistry` 拒绝空名称和重名工具。
- 统一 Runtime 按 Definition 创建受限 Registry；模型调用全局已注册但未授权的工具时会得到 `TOOL_NOT_ALLOWED`。
- Definition 没有工具时，运行时强制使用 `tool_choice=none`，并将 loop 收敛为单步。
- 当前有工具的 Definition 使用 `tool_choice=auto`，由模型在白名单内自主选择。
- 所有生产 Tool 都声明严格 JSON Schema，禁止未声明字段，并在服务端再次校验参数。
- 工具执行统一经过权限门禁、生命周期事件、可观测记录和结果压缩。

## 3. 业务场景与 Tool 白名单

| Agent 业务场景 | 最大步骤 | 当前 Tool 白名单 | 默认状态 |
| --- | ---: | --- | --- |
| 学习计划草案 `LEARNING_PLAN_DRAFT` | 24 | `list_problem_filters`、`search_problems`、`read_tool_result` | 开启 |
| 学习计划修订 `LEARNING_PLAN_REVISION` | 24 | `list_problem_filters`、`search_problems`、`read_tool_result` | 开启 |
| 学习计划扩展 `LEARNING_PLAN_EXTENSION` | 24 | `list_problem_filters`、`search_problems`、`read_tool_result` | 开启 |
| 题目练习聊天 `PRACTICE_CHAT` | 8 | `submit_practice_code_review`、`get_current_problem_learning_state`、`append_current_problem_note`、`read_tool_result`、`get_problem_review_trajectory`，以及按开关加入的自述画像和记忆召回工具 | 开启 |
| Code Review 画像后台更新 `CODE_REVIEW_PROFILE_UPDATE` | 4 | `get_problem_review_trajectory`、`get_code_review_evidence`、`compare_submission_versions` | 默认关闭 |
| Practice Code Review 子 Agent | 1 | 无 | 随 Review 能力开启 |
| 学习者自述画像决策子 Agent | 1 | 无 | 默认关闭 |

`calculator` 和 `get_problem_statement` 不在上表任何 Definition 的白名单中。它们目前属于“已实现、可注册、尚无统一 Runtime 业务消费者”的工具。

## 4. 题库工具

### 4.1 `list_problem_filters`

**业务目的**

让模型先获取本地题库真实支持的离散过滤值，避免凭自然语言猜测标签、公司、岗位、时间桶或排序枚举。

**适用场景**

- 生成、修订或扩展学习计划前，了解可用难度和标签。
- 用户提出“动态规划中等题”“某公司高频题”等模糊训练目标时，先把自然语言映射为题库精确值。
- 在搜索前确认当前题库是否存在分类、公司、岗位或时间桶数据。

**输入与输出**

- 输入：`includeCounts`、`locale`。
- 输出：题目总数、难度、标签、公司、岗位、时间桶、排序、分类及使用提示。
- `includeCounts` 默认视为 `true`；`locale` 默认中文题库视图。

**边界**

- 只读，不改变业务数据。
- 当前暴露给学习计划草案、修订和扩展 Agent。
- 配置：`AGENT_PROBLEM_FILTERS_TOOL_ENABLED`，默认 `true`。

### 4.2 `search_problems`

**业务目的**

按确定性过滤条件查询本地题库候选题，为学习计划选题提供事实来源。

**适用场景**

- 按主题、难度和数量为学习计划挑选题目。
- 按公司、岗位、时间桶及公司频度排序查找面试候选题。
- 用中文题名、英文题名、slug 或前端编号定位题目。

**输入与输出**

- 输入：`keyword`、`difficulty`、`tag`、`company`、`role`、`recencyBucket`、`sort`、`page`、`pageSize`、`locale`。
- 输出：轻量题目列表、总数、分页信息和 `appliedFilters`。
- 列表项包含 slug、题号、标题、难度、内容状态、公司频度信号和标签，不包含题面正文。

**边界**

- `difficulty` 仅允许 `EASY`、`MEDIUM`、`HARD`。
- `tag` 必须精确匹配 `list_problem_filters` 返回值。
- 分页大小受 `ProblemListRequest.MAX_PAGE_SIZE` 约束。
- 只读；当前暴露给学习计划草案、修订和扩展 Agent。
- 配置：`AGENT_PROBLEM_SEARCH_TOOL_ENABLED`，默认 `true`。

### 4.3 `get_problem_statement`

**业务目的**

按稳定 slug 读取一题的本地题面和必要元数据，为后续接入题目相关 Agent 能力提供项目内真实题面，避免模型仅依赖记忆。

**输入与输出**

- 输入：`slug`、`locale`。
- 命中时输出题号、标题、难度、内容状态、标签、题面 Markdown、样例测试和 LeetCode 链接。
- 未命中时返回 `found=false` 和原始 slug，不直接中断 Agent run。
- 不返回代码模板和内部导入字段。

**当前状态**

- 只读。
- 配置：`AGENT_PROBLEM_STATEMENT_TOOL_ENABLED`，默认 `true`。
- Spring 默认会注册该工具，但当前没有 Agent Definition 将其加入白名单，因此没有统一 Runtime 业务场景可以调用它。

## 5. Practice Chat 练习与状态工具

### 5.1 `submit_practice_code_review`

**业务目的**

把当前练习聊天中的完整解法提交转换为一次正式代码 Review，生成评分和 Review 记录，并参与题目完成状态判断。

**适用场景**

- 用户明确要求正式 Review。
- 当前消息看起来是针对当前练习题的完整解法，即使用户没有明确说“请 Review”。

**不适用场景**

- 代码片段、伪代码、错误日志。
- 局部 bug、语法问题、概念问题或复杂度讨论。
- 与当前练习题无关的代码。

**输入与可信上下文**

- 模型只可传 `userIntent` 和 `notes` 两个可空字符串。
- `userId`、练习 session、题目 slug、当前用户消息、代码正文和消息 ID 全部从服务端可信 metadata 与持久化记录读取。
- 工具会启动无工具的 Practice Code Review 子 Agent，完成代码提取、分析和结构化评分。

**输出与副作用**

- 输出状态、Review ID、版本号、总分、是否通过、题目和 session 等摘要。
- 会持久化正式 Review，可能改变题目完成资格，因此属于有业务副作用的写工具。
- 通过幂等键避免同一练习 turn 重复产生 Review。

**权限**

- 当前配置专用人在回路权限 Hook 的写工具之一。
- 模型请求调用后，前端会收到确认请求；只有用户允许后才执行真实 Review。
- 用户拒绝、超时或取消时不会进入工具实现，也不会生成 Review 记录。
- 配置：`PRACTICE_CODE_REVIEW_ENABLED`，默认 `true`；权限总开关 `AGENT_TOOL_PERMISSION_ENABLED` 默认 `true`。

### 5.2 `get_current_problem_learning_state`

**业务目的**

当用户询问当前题的完成情况、最近正式 Review、复习安排或既有笔记时，读取当前题的最新学习事实，避免依赖聊天历史猜测。

**输入与可信上下文**

- 模型只可传严格布尔参数 `includeNoteBody`。
- `userId`、practice session、plan、phase、题目 slug 和当前 run 全部来自服务端可信 metadata；工具会再次校验 session 与当前题上下文一致。
- 不提供跨题、跨计划或任意 session 查询参数。

**输出**

- 当前 practice progress 状态，以及是否已完成或跳过。
- 当前题最新一条正式 Review 摘要，包括版本、时间、总分、是否通过、扣分原因、改进建议和受影响标签；不返回代码或完整 Review Markdown。
- 当前题复习卡的到期时间、最近复习时间、评级和有限调度状态；不返回内部来源详情。
- 默认返回题目笔记是否存在、修订号和结构化提纲。

**笔记正文边界**

- 默认 `includeNoteBody=false`，生产查询不会选择 `note_markdown` 正文列。
- 只有当前用户消息明确要求查看笔记正文、全文或完整内容，并且模型传入 `includeNoteBody=true` 时，才执行完整笔记查询。
- 只询问是否有笔记或查看提纲时不读取正文；不满足显式请求时返回 `EXPLICIT_REQUEST_REQUIRED`，不返回 Markdown。
- 工具只读取当前 turn 的用户消息来校验正文意图，不读取或返回完整聊天历史。

**边界与开关**

- 只读，无业务写副作用，不触发人在回路确认。
- 多版本 Review 的持续、已解决和新增问题继续使用 `get_problem_review_trajectory`。
- 配置：`PRACTICE_CHAT_LEARNING_STATE_TOOL_ENABLED`，默认 `true`。

### 5.3 `append_current_problem_note`

**业务目的**

当用户明确要求“保存到笔记”或“把这个记下来”时，把当前对话中已经整理好的内容追加到当前练习题目的用户笔记。

**输入与可信上下文**

- 模型只可传严格字符串参数 `contentMarkdown`，内容必须是准备追加的确切、自包含 Markdown。
- `userId`、practice session、plan、phase 和题目 slug 全部来自服务端可信 metadata；工具执行前再次校验 session 与当前题上下文一致。
- 不接受模型声明的用户、session、plan、phase、题目或预期修订号。

**确认与写入语义**

- 每次调用都由专用权限 Hook 返回 `ASK`，前端展示规范化后的确切 `contentMarkdown`；只有用户允许后才执行真实写入。
- 用户拒绝、取消或确认超时不会进入工具实现，也不会更新题目笔记。
- PostgreSQL 使用单条原子 upsert：首次追加以空提纲创建笔记；已有笔记只追加 `note_markdown` 并递增 `revision`，不修改 `outline_json`。
- 非空已有正文与新内容之间使用两个换行符分隔；追加后仍受 10,000 字符总长度约束。

**边界与开关**

- 只允许追加，不覆盖、清空或删除已有笔记，不修改结构化解题提纲。
- 只有用户明确表达保存意图时才调用；普通讲解、代码 Review 或正式 Review 后不得自动写入。
- 工具返回 `APPENDED` 后才能声称保存成功；失败、拒绝或超时不得声称笔记已更新。
- 配置：`PRACTICE_CHAT_NOTE_APPEND_TOOL_ENABLED`，默认 `true`；权限总开关 `AGENT_TOOL_PERMISSION_ENABLED` 默认 `true`。为保证每次写入都经过确认，权限总开关关闭时该工具不会注册或暴露给 Practice Chat。

### 5.4 `update_learner_declared_profile`

**业务目的**

将用户明确表达的长期稳定信息同步为学习者画像 Claim，用于后续个性化辅导。

**适用场景**

- 学习背景和目标。
- 稳定的时间或资源约束。
- 学习与交互偏好。
- 用户自我能力评估。
- 用户明确纠正上述长期事实。

**不适用场景**

- 单次做题表现、短期情绪和临时困惑。
- 模型自行猜测出的偏好或能力。
- 用户未明确表达的推断性结论。

**输入与输出**

- 输入为 `updates` 批次，每项包含 `dimension`、`statement`、`intent`。
- 多个相关维度要求一次批量提交。
- 输出 `UPDATED`、`NO_CHANGE` 或 `FAILED`，并给出每个维度的处理摘要。

**边界与副作用**

- 只能在 `PRACTICE_CHAT` 可信上下文执行，身份和父 run 从服务端 metadata 获取。
- 会创建画像更新 run，并可能写入 Claim、版本和消息证据。
- 工具失败时返回 `FAILED`，不会把内部异常细节暴露给模型。
- 当前没有单独的确认弹窗；安全边界依赖用户明确表达、Prompt 规则、可信消息校验和原子写入服务。
- 配置：`LEARNER_MEMORY_DECLARED_UPDATE_ENABLED`，默认 `false`。

## 6. Practice Chat 记忆召回工具

这三个工具只读取当前 Practice Chat run 启动时创建的学习者记忆快照，不直接遍历用户的全部长期记忆。默认开关关闭。

### 6.1 `search_learner_memory`

- 业务目的：按文本、主题 section 和题目 tag 在当前快照中搜索相关长期记忆。
- 输入：`query`、可选 `sectionRef`、`tagValues`、`limit`、`cursor`。
- 输出：匹配 statement 的引用、Claim 文本、来源摘要、所在主题和是否命中当前题目。
- 无匹配只表示当前 run 快照未命中，不能推断用户没有相关经验。

### 6.2 `read_learner_memory_section`

- 业务目的：在模型已知某个记忆主题可能相关时，按固定投影顺序读取该主题。
- 输入：`sectionRef`、`limit`，以及继续翻页时使用的 `afterStatementRef`。
- 输出：主题标题、statement 列表和下一页位置。
- 只能使用当前快照提供的 section/statement 引用，不能自行构造任意数据库查询。

### 6.3 `get_learner_memory_evidence`

- 业务目的：核验某条记忆 statement 的证据类型和时间，帮助模型区分用户自述与正式 Review 观察。
- 输入：`statementRef`、`limit`、`cursor`。
- 输出：证据来源 `FORMAL_REVIEW` 或 `USER_MESSAGE`、证据角色和记录时间。
- 不返回用户消息正文、代码正文或完整 Review Markdown。

### 6.4 共享安全预算

- 配置：`LEARNER_MEMORY_RECALL_PRACTICE_CHAT_ENABLED`，默认 `false`。
- 三项业务工具合计每个 run 最多调用 3 次。
- 单次结果最多向模型暴露 8,000 字符。
- 三项工具及其后续范围读取合计最多暴露 24,000 字符。
- 大结果通过 `read_tool_result` 继续读取时，最多允许 2 次范围读取。
- 快照通过不可枚举的 run-local capability ref 访问，默认 15 分钟过期；run 释放后不能重新打开。

## 7. Code Review 画像后台工具

这三个工具服务于后台 `CODE_REVIEW_PROFILE_UPDATE` Agent，用来从多次正式 Review 中提炼长期能力、错误模式和恢复轨迹。`get_problem_review_trajectory` 也可由 Practice Chat 在独立前台开关开启时读取当前训练题；其余两项不面向前台对话直接开放，默认随 Code Review 画像消费者一起关闭。

### 7.1 `get_problem_review_trajectory`

- 业务目的：查看同一题最近最多 5 个正式 Review 的纵向变化。
- 输入：`problemSlug`。
- 输出：各版本的时间、评分、是否通过、扣分原因、改进建议、受影响标签，以及分数变化、持续问题、已解决问题和新增问题。
- 不返回源代码或完整 Review Markdown。
- 后台画像更新中，同一 run 对同一题最多调用一次，并且题目必须在后台任务预先授权的 Review 窗口内。
- Practice Chat 中，只能通过当前 run 的不可枚举 capability 读取当前用户、当前训练题，且每个 run 最多调用一次；不会读取源代码或完整 Review Markdown。
- 前台开关：`PRACTICE_CHAT_REVIEW_TRAJECTORY_TOOL_ENABLED`，默认 `true`，不依赖 `LEARNER_MEMORY_CODE_REVIEW_CONSUMER_ENABLED`。

### 7.2 `get_code_review_evidence`

- 业务目的：读取某条正式 Review 的受限证据详情，支持画像 Claim 的证据化判断。
- 输入：`reviewId`。
- 输出：Review 评分事实、上下文摘要和检测证据。
- 不返回源代码或完整 Review Markdown。
- `reviewId` 必须属于当前用户且位于本次后台 run 的授权范围内。

### 7.3 `compare_submission_versions`

- 业务目的：比较同题前后两个正式提交版本，判断问题是持续、修复还是新出现。
- 输入：`fromReviewId`、`toReviewId`。
- 输出：分数变化、持续/已解决/新增问题和有界 unified diff。
- 两个 Review 必须属于同一用户、同一题，且版本严格递增。
- 每个 run 最多使用一次 diff；该结果含代码差异，敏感度高于另外两个后台只读工具，但仍受 run-local scope 和结果长度限制。

### 7.4 共享安全预算

- 配置：`LEARNER_MEMORY_CODE_REVIEW_CONSUMER_ENABLED`，默认 `false`。
- 三项工具合计每个后台 run 最多调用 3 次。
- 单个结果最多 8,000 字符。
- 工具只能使用随机 capability ref 访问任务预先授权的 Review 集合，默认 15 分钟过期。
- 轨迹按题去重，diff 全 run 只能一次，越权 Review ID 返回结构化失败结果。

## 8. 通用运行时工具

### 8.1 `read_tool_result`

**业务目的**

当某个工具结果超过内联阈值、完整内容被保存为 blob 时，让模型通过 `resultRef` 按范围继续读取，而不是把大结果一次性塞入上下文。

**输入与输出**

- 输入：`resultRef`，以及字符范围 `offset`/`limit` 或行范围 `lineStart`/`lineEnd`。
- 输出：内容类型、实际范围、内容、字符数和前后是否还有数据。

**边界**

- 只允许读取当前 Agent run 产生的结果 blob，不能跨 run 使用 `resultRef`。
- 对学习者记忆工具结果额外执行专用读取预算；其他工具结果使用通用压缩策略上限。
- 当前只被 Practice Chat Definition 加入白名单。
- 没有独立功能开关；存在 `ToolResultStore` 时注册。完整 PostgreSQL 装配会提供 `PostgresToolResultStore`。

### 8.2 `calculator`

**业务目的**

提供确定性的基础算术计算，避免模型在简单数值运算中产生错误。当前更接近 Agent Runtime 示例、测试和储备能力。

**输入与输出**

- 输入：长度不超过 200 的 `expression`。
- 仅支持数字、括号和 `+ - * /`，支持正负号。
- 使用 `BigDecimal` 和 20 位精度计算，输出原表达式和规范化结果。
- 除零、非法 token 和括号不匹配会映射为工具执行失败。

**当前状态**

- 无外部副作用。
- 配置：`AGENT_CALCULATOR_TOOL_ENABLED`，默认 `true`。
- 默认会进入全局 Registry，但没有任何当前 Agent Definition 将其加入白名单，因此统一 Runtime 业务流不会调用它。

## 9. 默认配置汇总

| 环境变量 | 默认值 | 影响的 Tool |
| --- | --- | --- |
| `AGENT_CALCULATOR_TOOL_ENABLED` | `true` | `calculator`，仅控制注册 |
| `AGENT_PROBLEM_FILTERS_TOOL_ENABLED` | `true` | `list_problem_filters` |
| `AGENT_PROBLEM_SEARCH_TOOL_ENABLED` | `true` | `search_problems` |
| `AGENT_PROBLEM_STATEMENT_TOOL_ENABLED` | `true` | `get_problem_statement`，仅控制注册 |
| `PRACTICE_CODE_REVIEW_ENABLED` | `true` | `submit_practice_code_review` 及 Review 子 Agent |
| `PRACTICE_CHAT_LEARNING_STATE_TOOL_ENABLED` | `true` | Practice Chat 的 `get_current_problem_learning_state` |
| `PRACTICE_CHAT_NOTE_APPEND_TOOL_ENABLED` | `true` | Practice Chat 的 `append_current_problem_note` |
| `PRACTICE_CHAT_REVIEW_TRAJECTORY_TOOL_ENABLED` | `true` | Practice Chat 的当前题 `get_problem_review_trajectory` |
| `LEARNER_MEMORY_DECLARED_UPDATE_ENABLED` | `false` | `update_learner_declared_profile` |
| `LEARNER_MEMORY_RECALL_PRACTICE_CHAT_ENABLED` | `false` | 三个 Practice Chat 记忆召回工具 |
| `LEARNER_MEMORY_CODE_REVIEW_CONSUMER_ENABLED` | `false` | 三个 Code Review 画像后台工具的 Agent Definition |
| `AGENT_TOOL_PERMISSION_ENABLED` | `true` | 工具执行前权限链，当前保护正式 Review 和题目笔记追加工具 |
| `AGENT_TOOL_RESULT_INLINE_MAX_CHARS` | `12000` | 大结果转 preview/ref 的内联阈值 |
| `AGENT_TOOL_RESULT_PREVIEW_MAX_CHARS` | `2000` | 大工具结果预览长度 |
| `AGENT_TOOL_RESULT_RANGE_READ_MAX_CHARS` | `8000` | `read_tool_result` 单次通用读取上限 |

部署环境可能覆盖这些默认值，因此判断线上实际能力时，应同时检查 Spring 条件装配和运行环境变量。

## 10. 当前实现观察

### 10.1 题面工具已实现但没有业务消费者

普通 Mentor 会话和算法主题讲解业务已移除，不再有对应的 Agent Definition 或 Tool 白名单。`get_problem_statement` 的实现、配置和设计仍然存在，但当前学习计划只允许过滤项和搜索工具，Practice Chat 则由后端固定注入当前题面。由于没有任何 Definition 将该工具加入白名单，统一 Runtime 当前没有它的业务消费者。

Practice Chat 已由后端确定性注入当前题面，因此不会通过统一 Runtime 按需调用该工具。

### 10.2 计算器当前没有业务消费者

`calculator` 默认注册但不在任何 Definition 白名单中，当前统一 Runtime 业务流不会调用它。

### 10.3 学习计划可继续读取被压缩的大工具结果

学习计划草案、修订和扩展 Agent 都已将 `read_tool_result` 加入白名单。过滤项或搜索结果超过压缩阈值时，模型可基于同一 run 的 `resultRef` 按范围续读；ToolResultStore 不允许跨 run 访问结果。

当前搜索结果通常可通过较小 `pageSize` 控制，但公司过滤项等集合增长后仍可通过范围续读完整结果。

### 10.4 长期记忆能力默认均为关闭状态

自述画像写入、Practice Chat 记忆召回和 Code Review 画像后台更新都已经实现，但 `application.yml` 默认关闭。因此开发或产品验收时不能仅根据类和 Bean 是否存在判断功能已上线，应检查对应环境变量、Definition 注册和实际工具事件。

### 10.5 写工具的确认策略不同

`submit_practice_code_review` 和 `append_current_problem_note` 都有明确的 `ASK` 权限流程，真实副作用只在用户允许后发生；`update_learner_declared_profile` 没有独立确认弹窗，依赖“用户明确陈述长期事实”的 Prompt 契约和服务端可信消息校验。三者当前采用两种不同的产品授权语义。

## 11. 主要代码依据

- Tool 抽象与注册：`backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentTool.java`、`AgentToolRegistry.java`。
- 场景白名单：各业务模块的 `*AgentDefinition.java`。
- 全局 Tool 装配：`backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorAiConfiguration.java`。
- Practice Chat 与记忆装配：`backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfiguration.java`。
- 当前题学习状态工具：`backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/GetCurrentProblemLearningStateAgentTool.java`。
- 当前题笔记追加工具与确认 Hook：`backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/AppendCurrentProblemNoteAgentTool.java`、`AppendCurrentProblemNotePermissionHook.java`。
- 正式 Review 装配：`backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/PracticeCodeReviewConfiguration.java`。
- 默认配置：`backend/mentor-api/src/main/resources/application.yml`。
