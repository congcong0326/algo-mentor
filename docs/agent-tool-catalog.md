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

截至当前代码，生产代码共有 **20 个 `AgentTool` 实现**。需要特别区分两个概念：

- **已注册**：Spring Bean 被收集进全局 `AgentToolRegistry`。
- **可调用**：某个 `AgentDefinition.allowedToolNames()` 把工具加入当前 run 的白名单，模型才会看到并执行它。

因此，工具已经实现或默认注册，并不代表当前用户场景一定能调用它。

按 `application.yml` 默认值并假设 PostgreSQL 等完整依赖均已装配：

- 默认实际暴露给业务 Agent 的工具有 9 个：`list_problem_filters`、`search_problems`、`read_tool_result`、`query_learning_plan_revision`、`compile_learning_plan_revision`、`submit_practice_code_review`、`get_current_problem_learning_state`、`propose_current_problem_coach_summary`、`get_problem_review_trajectory`。
- 通过可选能力开关可再暴露 7 个学习者记忆工具，以及 3 个历史正式提交 Tool。
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
| 学习计划修订 `LEARNING_PLAN_REVISION` | 24 | `query_learning_plan_revision`、`compile_learning_plan_revision` | 开启 |
| 学习计划扩展 `LEARNING_PLAN_EXTENSION` | 24 | `list_problem_filters`、`search_problems`、`read_tool_result` | 开启 |
| 题目练习聊天 `PRACTICE_CHAT` | 8 | `submit_practice_code_review`、`get_current_problem_learning_state`、`propose_current_problem_coach_summary`、`read_tool_result`、`get_problem_review_trajectory`，以及按开关加入的自述画像、记忆召回和历史正式提交工具 | 开启 |
| Code Review 画像后台更新 `CODE_REVIEW_PROFILE_UPDATE` | 4 | `get_problem_review_trajectory`、`get_code_review_evidence`、`compare_submission_versions` | 默认关闭 |
| Practice Code Review 子 Agent | 1 | 无 | 随 Review 能力开启 |
| 学习者自述画像决策子 Agent | 1 | 无 | 默认关闭 |

`calculator` 和 `get_problem_statement` 不在上表任何 Definition 的白名单中。它们目前属于“已实现、可注册、尚无统一 Runtime 业务消费者”的工具。

## 4. 学习计划修订工具

学习计划修订 Agent 不再直接使用通用题库搜索 Tool。草稿第一次形成完整计划时，服务端冻结来源无关的 `originBrief + originPlan`；每次修订再冻结 `baseBrief + basePlan`。模型读取和编译均绑定当前 revision、用户与业务场景，不能读取后来变化的 draft，也不能自行指定其他用户、draft、revision 或模板。

### 4.1 `query_learning_plan_revision`

**业务目的**

按需读取当前 revision 允许访问的冻结计划基线，避免把最多 149 题的计划全部放进首轮模型上下文，并支持精确恢复第一版草稿或撤销上次修订。

**输入与输出**

- `READ_BASELINE_OPTIONS`：返回 `CURRENT_REVISION`、`ORIGINAL_DRAFT`、`PREVIOUS_REVISION` 是否可用及语义说明。
- `READ_PHASE`：按 `phaseRef` 分页读取一个阶段，返回阶段标题、重点、题目数、难度统计，以及题目的 `slug`、当前语言标题、难度和推荐理由。
- `FIND_PROBLEMS`：按阶段引用、slug、难度或关键词筛选冻结计划中已经存在的题目。
- `READ_PHASE` 和 `FIND_PROBLEMS` 可选择 `CURRENT_REVISION`、`ORIGINAL_DRAFT` 或 `PREVIOUS_REVISION`；未指定时默认当前修订基线。
- 单页最多 30 题；cursor 绑定当前 revision ID、所选基线、操作和筛选条件，不能跨 revision、跨基线或更换条件复用。
- 输出中的 `phaseRef` 只在当前冻结快照内有效，不是持久化 `phaseIndex`。

**边界**

- 只读，不搜索全局题库，不读取最新 mutable draft；`ORIGINAL_DRAFT` 读取草稿第一次完整落库的冻结快照，不重新查询可能已变化的模板。
- 不返回题目 `frontendId`、双语标题、tags、`sortOrder`、阶段 `phaseIndex`、阶段 `durationWeeks` 或 metadata。
- `userId`、`draftId`、`revisionId` 和场景标识来自服务端可信 metadata；模型参数中没有身份字段，也不接受 `templateId`。
- 只对 `LEARNING_PLAN_REVISION` 白名单开放，无需用户确认。

### 4.2 `compile_learning_plan_revision`

**业务目的**

把模型提交的语义 Patch 编译为完整 canonical 计划，并在 revision 仍为 `GENERATING` 时暂存 `proposedBrief + proposedPlan`。编译基线可选择当前修订、第一版完整草稿或上一次修订前快照；模型最终只返回该产物的 `artifactRef`，不再复制完整计划。

**输入能力**

- 基线选择：`CURRENT_REVISION` 用于普通修订；`ORIGINAL_DRAFT` 精确恢复第一版完整草稿，对模板和 AI 来源通用；`PREVIOUS_REVISION` 用于撤销上一轮修订。
- Brief Patch：可修改规划字段；缺失字段保持基线，`personalizationEnabled` 和 `contentLocale` 无条件从基线恢复。
- 计划 Patch：可修改标题、摘要，对阶段执行 `UPDATE`、`ADD`、`REMOVE`、`MOVE`。
- 题目 Patch：可执行 `ADD`、`REMOVE`、`MOVE`、`REPLACE`；可直接指定 slug，也可通过 `replacementSelection` 让服务端按难度、关键词和主题提示选择本地题库候选。
- 删除和移动必须显式表达，字段缺失不会被解释为删除。

**恢复与校验**

- 从本地题库按 slug 回填 `frontendId`、双语标题、difficulty 和 tags，只保留模型负责的 reason。
- 按最终列表顺序重建 `phaseIndex` 和 `sortOrder`；阶段周数不交给模型，在总周期或阶段结构变化后由服务端兼容分配。
- 从合并后的 Brief 回填计划中的重复约束字段，保留 metadata provenance，并重算学习负载。
- 模板计划内容变化后失效旧 `matchedProblemCount` 断言；149 题模板计划不会套用 AI 生成计划“每阶段最多 5 题”的限制。
- 编译失败返回 `NEEDS_REVISION + diagnostics`，不覆盖已存产物；成功返回 `PASS + artifactRef + baseline + changed + changeSummary`。
- `changeSummary.problemCountBefore` 和修订前难度统计始终描述当前 revision 基线，修订后统计描述编译结果，因此 18→4 后按 `ORIGINAL_DRAFT` 恢复会明确返回 4→18。

**副作用与权限**

- 会更新当前 revision 的 proposed 快照，但不会直接把 revision 标记为 `READY`，也不会直接覆盖 draft。
- Agent 最终输出 artifactRef 后，流服务重新读取产物并执行过期检查、READY 转换和 draft 更新。
- `userId`、`revisionId` 和场景标识来自服务端可信 metadata，只对 `LEARNING_PLAN_REVISION` 白名单开放。
- 该写入是一次修订请求内部的受限暂存，不代表对外部系统执行不可逆动作，因此无需额外用户确认。

## 5. 题库工具

### 5.1 `list_problem_filters`

**业务目的**

让模型先获取本地题库真实支持的离散过滤值，避免凭自然语言猜测标签、公司、岗位、时间桶或排序枚举。

**适用场景**

- 生成或扩展学习计划前，了解可用难度和标签。
- 用户提出“动态规划中等题”“某公司高频题”等模糊训练目标时，先把自然语言映射为题库精确值。
- 在搜索前确认当前题库是否存在分类、公司、岗位或时间桶数据。

**输入与输出**

- 输入：`includeCounts`、`locale`。
- 输出：题目总数、难度、标签、公司、岗位、时间桶、排序、分类及使用提示。
- `includeCounts` 默认视为 `true`；`locale` 默认中文题库视图。

**边界**

- 只读，不改变业务数据。
- 当前暴露给学习计划草案和扩展 Agent。
- 配置：`AGENT_PROBLEM_FILTERS_TOOL_ENABLED`，默认 `true`。

### 5.2 `search_problems`

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
- 只读；当前暴露给学习计划草案和扩展 Agent。学习计划修订使用冻结快照查询与编译器内部的受约束候选选择。
- 配置：`AGENT_PROBLEM_SEARCH_TOOL_ENABLED`，默认 `true`。

### 5.3 `get_problem_statement`

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

## 6. Practice Chat 练习与状态工具

### 6.1 `submit_practice_code_review`

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

- 工具不接受模型参数；模型仅决定是否调用，调用时使用空对象。
- `userId`、练习 session、题目 slug、当前用户消息、代码正文和消息 ID 全部从服务端可信 metadata 与持久化记录读取。
- 工具会启动无工具的 Practice Code Review 子 Agent，完成代码提取、分析和结构化评分。

**输出与副作用**

- 输出状态、Review ID、版本号、总分、是否通过、题目和 session 等摘要。
- 会持久化正式 Review，可能改变题目完成资格，因此属于有业务副作用的写工具。
- 通过幂等键避免同一练习 turn 重复产生 Review。

**权限**

- 在 `PRACTICE_CHAT` 场景，模型识别当前消息可能是完整题解提交后直接执行，不展示权限确认弹窗。
- 自动授权同时要求受信的 Runtime `agentKey=practice-chat` 和精确工具名；不会放开该场景的其他工具，也不影响其他场景继续使用权限链。
- 工具未保存或执行失败时不会生成正式 Review 记录。
- 配置：`PRACTICE_CODE_REVIEW_ENABLED`，默认 `true`；权限总开关 `AGENT_TOOL_PERMISSION_ENABLED` 默认 `true`。

### 6.2 `get_current_problem_learning_state`

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
- 只有当前用户消息明确要求查看笔记正文、全文、完整内容，或要求生成/更新教练总结，并且模型传入 `includeNoteBody=true` 时，才执行完整笔记查询。
- 只询问是否有笔记或查看提纲时不读取正文；不满足显式请求时返回 `EXPLICIT_REQUEST_REQUIRED`，不返回 Markdown。
- 工具只读取当前 turn 的用户消息来校验正文意图，不读取或返回完整聊天历史。

**边界与开关**

- 只读，无业务写副作用，不触发人在回路确认。
- 多版本 Review 的持续、已解决和新增问题继续使用 `get_problem_review_trajectory`。
- 配置：`PRACTICE_CHAT_LEARNING_STATE_TOOL_ENABLED`，默认 `true`。

### 6.3 `propose_current_problem_coach_summary`

**业务目的**

当用户明确要求生成、更新、替换或保存当前题的教练总结时，生成一份完整候选稿，并把它作为当前 assistant 消息正文展示，等待用户通过消息末尾的一次性按钮采纳。

**输入与可信上下文**

- 模型只可传严格字符串参数 `summaryMarkdown`，内容必须是准备展示和采纳的完整替代稿。
- `userId`、practice session、plan、phase 和题目 slug 全部来自服务端可信 metadata；工具执行前再次校验 session 与当前题上下文一致。
- `sourceRunId` 和 `sourceToolCallId` 来自 Agent runtime 可信上下文，用于幂等创建和关联最终 assistant 消息。
- 不接受模型声明的用户、session、plan、phase、题目、正式总结 revision 或保存状态。

**候选与采纳语义**

- Tool 只创建 `PENDING` proposal，不修改 `user_problem_note.note_markdown`，因此不进入权限弹窗或倒计时流程。
- 聊天消息正文使用 proposal 中的确切 Markdown，消息末尾按钮只提交 `proposalId`，不把 Markdown 从浏览器回传给服务端。
- `POST /api/practice-sessions/{sessionId}/coach-summary-proposals/{proposalId}/apply` 在服务端校验当前用户、session、题目和 proposal 状态后，原子创建或替换正式总结。
- 正式总结使用独立 `coach_summary_revision`；结构化提纲的 `revision` 和 `outline_json` 在总结采纳时保持不变。
- 同一用户同一题只允许一个 `PENDING` proposal；新 proposal 自动使旧候选进入 `SUPERSEDED`。
- apply 对已 `APPLIED` proposal 幂等；若正式总结 revision 已变化，则候选转为 `SUPERSEDED`，不覆盖较新的总结。

**边界与开关**

- 生成前必须先调用 `get_current_problem_learning_state(includeNoteBody=true)`，读取既有总结和当前题学习状态。
- 只有用户明确表达教练总结意图时才调用；普通讲解或代码 Review 后不得自动生成候选。
- Tool 返回 `PROPOSED` 只代表候选已创建，不能声称正式总结已保存；只有 apply 返回 `APPLIED` 才完成写入。
- 撤销替换暂不实现。
- 配置：`PRACTICE_CHAT_COACH_SUMMARY_TOOL_ENABLED`，默认 `true`。

### 6.4 `update_learner_declared_profile`

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

### 6.5 历史正式提交 Tool

`get_practiced_problem_overview`、`list_practice_problem_submissions` 和 `read_practice_submission_detail` 只服务于当前 Practice Chat run 的历史正式代码提交。它们均为只读能力，不触发权限确认，也不改变 Review、练习进度或用户资料。

- `get_practiced_problem_overview` 输入一个本 run 签发的 `problemRef`，返回跨计划正式提交次数、通过次数、首次时间、题目展示字段和最新提交的紧凑结论；不返回源码、逐次反馈或完整 Review Markdown。
- `list_practice_problem_submissions` 输入 `problemRef`、可选的本 run keyset `cursor` 与最多 5 条的 `limit`，返回按 `created_at DESC, id DESC` 排列的受限 Review 时间线，并为每条记录签发仅本 run 有效的 `submissionRef`。
- `read_practice_submission_detail` 只接受已签发的 `submissionRef`。仅当服务端从当前用户消息确认“查看旧代码、代码级复盘或与当前代码比较”的明确意图时，返回一次归一化代码和定义字段内的结构化 Review；普通的“我之前哪里错了”不会授权读取代码。

**隔离与预算**

- 模型参数中不接受用户、裸 `problemSlug`、Review ID、计划或 session。所有查询先由不可枚举的 run-local ref 解析出服务端保存的 `userId + problemSlug (+ reviewId)`；伪造、跨题、跨 run、过期或已释放 ref 在查询前返回 `UNAVAILABLE`。
- scope 仅驻留应用节点内存，默认 15 分钟过期，并随 run 正常结束、取消、超时、准备失败或异常释放。幂等 replay 不打开新 scope，也不能复用旧 ref。
- overview 与 list 合计每个 run 最多两次，且同题 overview 最多一次；detail 最多一次。`USER_INTENT_REQUIRED` 不查询代码，也不消耗 detail 次数或字符预算。
- 详情大结果经 `read_tool_result` 续读时，最多两次范围读取；首次 preview 加两次续读合计最多向模型暴露 16,000 字符。该 guard 只拦截 `read_practice_submission_detail` 的 result provenance，不影响其他 Tool。
- 代码详情 blob 沿用 Agent run 的诊断留存与管理员审计访问控制：诊断数据和 `tool_result` blob 在 run 终态后最多保留 30 天，清理任务会删除 blob 并脱敏相关 Tool trace；正式 `practice_code_review` 事实不受此短期诊断留存影响。

**开关**

- `PRACTICE_CHAT_SUBMISSION_HISTORY_TOOL_ENABLED` 默认 `true`。关闭时不创建 scope、不注册三个 Tool 到 Practice Chat 白名单，Phase 1 Prompt 索引仍照常注入。
- `PRACTICE_CHAT_SUBMISSION_HISTORY_CODE_DETAIL_ENABLED` 默认 `true`，且以总开关为前置条件。关闭源码详情开关时只暴露 overview 与 list。

## 7. Practice Chat 记忆召回工具

这三个工具只读取当前 Practice Chat run 启动时创建的学习者记忆快照，不直接遍历用户的全部长期记忆。默认开关关闭。

### 7.1 `search_learner_memory`

- 业务目的：按文本、主题 section 和题目 tag 在当前快照中搜索相关长期记忆。
- 输入：`query`、可选 `sectionRef`、`tagValues`、`limit`、`cursor`。
- 输出：匹配 statement 的引用、Claim 文本、来源摘要、所在主题和是否命中当前题目。
- 无匹配只表示当前 run 快照未命中，不能推断用户没有相关经验。

### 7.2 `read_learner_memory_section`

- 业务目的：在模型已知某个记忆主题可能相关时，按固定投影顺序读取该主题。
- 输入：`sectionRef`、`limit`，以及继续翻页时使用的 `afterStatementRef`。
- 输出：主题标题、statement 列表和下一页位置。
- 只能使用当前快照提供的 section/statement 引用，不能自行构造任意数据库查询。

### 7.3 `get_learner_memory_evidence`

- 业务目的：核验某条记忆 statement 的证据类型和时间，帮助模型区分用户自述与正式 Review 观察。
- 输入：`statementRef`、`limit`、`cursor`。
- 输出：证据来源 `FORMAL_REVIEW` 或 `USER_MESSAGE`、证据角色和记录时间。
- 不返回用户消息正文、代码正文或完整 Review Markdown。

### 7.4 共享安全预算

- 配置：`LEARNER_MEMORY_RECALL_PRACTICE_CHAT_ENABLED`，默认 `false`。
- 三项业务工具合计每个 run 最多调用 3 次。
- 单次结果最多向模型暴露 8,000 字符。
- 三项工具及其后续范围读取合计最多暴露 24,000 字符。
- 大结果通过 `read_tool_result` 继续读取时，最多允许 2 次范围读取。
- 快照通过不可枚举的 run-local capability ref 访问，默认 15 分钟过期；run 释放后不能重新打开。

## 8. Code Review 画像后台工具

这三个工具服务于后台 `CODE_REVIEW_PROFILE_UPDATE` Agent，用来从多次正式 Review 中提炼长期能力、错误模式和恢复轨迹。`get_problem_review_trajectory` 也可由 Practice Chat 在独立前台开关开启时读取当前训练题；其余两项不面向前台对话直接开放，默认随 Code Review 画像消费者一起关闭。

### 8.1 `get_problem_review_trajectory`

- 业务目的：查看同一题最近最多 5 个正式 Review 的纵向变化。
- 输入：`problemSlug`。
- 输出：各版本的时间、评分、是否通过、扣分原因、改进建议、受影响标签，以及分数变化、持续问题、已解决问题和新增问题。
- 不返回源代码或完整 Review Markdown。
- 后台画像更新中，同一 run 对同一题最多调用一次，并且题目必须在后台任务预先授权的 Review 窗口内。
- Practice Chat 中，只能通过当前 run 的不可枚举 capability 读取当前用户、当前训练题，且每个 run 最多调用一次；不会读取源代码或完整 Review Markdown。
- 前台开关：`PRACTICE_CHAT_REVIEW_TRAJECTORY_TOOL_ENABLED`，默认 `true`，不依赖 `LEARNER_MEMORY_CODE_REVIEW_CONSUMER_ENABLED`。

### 8.2 `get_code_review_evidence`

- 业务目的：读取某条正式 Review 的受限证据详情，支持画像 Claim 的证据化判断。
- 输入：`reviewId`。
- 输出：Review 评分事实、上下文摘要和检测证据。
- 不返回源代码或完整 Review Markdown。
- `reviewId` 必须属于当前用户且位于本次后台 run 的授权范围内。

### 8.3 `compare_submission_versions`

- 业务目的：比较同题前后两个正式提交版本，判断问题是持续、修复还是新出现。
- 输入：`fromReviewId`、`toReviewId`。
- 输出：分数变化、持续/已解决/新增问题和有界 unified diff。
- 两个 Review 必须属于同一用户、同一题，且版本严格递增。
- 每个 run 最多使用一次 diff；该结果含代码差异，敏感度高于另外两个后台只读工具，但仍受 run-local scope 和结果长度限制。

### 8.4 共享安全预算

- 配置：`LEARNER_MEMORY_CODE_REVIEW_CONSUMER_ENABLED`，默认 `false`。
- 三项工具合计每个后台 run 最多调用 3 次。
- 单个结果最多 8,000 字符。
- 工具只能使用随机 capability ref 访问任务预先授权的 Review 集合，默认 15 分钟过期。
- 轨迹按题去重，diff 全 run 只能一次，越权 Review ID 返回结构化失败结果。

## 9. 通用运行时工具

### 9.1 `read_tool_result`

**业务目的**

当某个工具结果超过内联阈值、完整内容被保存为 blob 时，让模型通过 `resultRef` 按范围继续读取，而不是把大结果一次性塞入上下文。

**输入与输出**

- 输入：`resultRef`，以及字符范围 `offset`/`limit` 或行范围 `lineStart`/`lineEnd`。
- 输出：内容类型、实际范围、内容、字符数和前后是否还有数据。

**边界**

- 只允许读取当前 Agent run 产生的结果 blob，不能跨 run 使用 `resultRef`。
- 对学习者记忆工具和历史提交详情结果额外执行各自的专用读取预算；其他工具结果使用通用压缩策略上限。
- 当前只被 Practice Chat Definition 加入白名单。
- 没有独立功能开关；存在 `ToolResultStore` 时注册。完整 PostgreSQL 装配会提供 `PostgresToolResultStore`。

### 9.2 `calculator`

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

## 10. 默认配置汇总

| 环境变量 | 默认值 | 影响的 Tool |
| --- | --- | --- |
| `AGENT_CALCULATOR_TOOL_ENABLED` | `true` | `calculator`，仅控制注册 |
| `AGENT_PROBLEM_FILTERS_TOOL_ENABLED` | `true` | `list_problem_filters` |
| `AGENT_PROBLEM_SEARCH_TOOL_ENABLED` | `true` | `search_problems` |
| `AGENT_PROBLEM_STATEMENT_TOOL_ENABLED` | `true` | `get_problem_statement`，仅控制注册 |
| 无独立开关 | 条件装配 | `query_learning_plan_revision`、`compile_learning_plan_revision`；存在学习计划提案仓储时注册，仅修订 Agent 可见 |
| `PRACTICE_CODE_REVIEW_ENABLED` | `true` | `submit_practice_code_review` 及 Review 子 Agent |
| `PRACTICE_CHAT_LEARNING_STATE_TOOL_ENABLED` | `true` | Practice Chat 的 `get_current_problem_learning_state` |
| `PRACTICE_CHAT_COACH_SUMMARY_TOOL_ENABLED` | `true` | Practice Chat 的 `propose_current_problem_coach_summary` |
| `PRACTICE_CHAT_REVIEW_TRAJECTORY_TOOL_ENABLED` | `true` | Practice Chat 的当前题 `get_problem_review_trajectory` |
| `PRACTICE_CHAT_SUBMISSION_HISTORY_TOOL_ENABLED` | `true` | `get_practiced_problem_overview`、`list_practice_problem_submissions`，并创建 run-local 历史 scope |
| `PRACTICE_CHAT_SUBMISSION_HISTORY_CODE_DETAIL_ENABLED` | `true` | `read_practice_submission_detail`；必须同时开启历史提交总开关 |
| `LEARNER_MEMORY_DECLARED_UPDATE_ENABLED` | `false` | `update_learner_declared_profile` |
| `LEARNER_MEMORY_RECALL_PRACTICE_CHAT_ENABLED` | `false` | 三个 Practice Chat 记忆召回工具 |
| `LEARNER_MEMORY_CODE_REVIEW_CONSUMER_ENABLED` | `false` | 三个 Code Review 画像后台工具的 Agent Definition |
| `AGENT_TOOL_PERMISSION_ENABLED` | `true` | 工具执行前权限链；Practice Chat 的正式 Review 使用受限自动 `ALLOW`，教练总结 proposal 不使用该弹窗 |
| `AGENT_TOOL_RESULT_INLINE_MAX_CHARS` | `12000` | 大结果转 preview/ref 的内联阈值 |
| `AGENT_TOOL_RESULT_PREVIEW_MAX_CHARS` | `2000` | 大工具结果预览长度 |
| `AGENT_TOOL_RESULT_RANGE_READ_MAX_CHARS` | `8000` | `read_tool_result` 单次通用读取上限 |

部署环境可能覆盖这些默认值，因此判断线上实际能力时，应同时检查 Spring 条件装配和运行环境变量。

## 11. 当前实现观察

### 11.1 题面工具已实现但没有业务消费者

普通 Mentor 会话和算法主题讲解业务已移除，不再有对应的 Agent Definition 或 Tool 白名单。`get_problem_statement` 的实现、配置和设计仍然存在，但当前学习计划只允许过滤项和搜索工具，Practice Chat 则由后端固定注入当前题面。由于没有任何 Definition 将该工具加入白名单，统一 Runtime 当前没有它的业务消费者。

Practice Chat 已由后端确定性注入当前题面，因此不会通过统一 Runtime 按需调用该工具。

### 11.2 计算器当前没有业务消费者

`calculator` 默认注册但不在任何 Definition 白名单中，当前统一 Runtime 业务流不会调用它。

### 11.3 学习计划草案和扩展可继续读取被压缩的大工具结果

学习计划草案和扩展 Agent 已将 `read_tool_result` 加入白名单。过滤项或搜索结果超过压缩阈值时，模型可基于同一 run 的 `resultRef` 按范围续读；ToolResultStore 不允许跨 run 访问结果。学习计划修订不使用通用结果续读，专用查询 Tool 自带最多 30 题的分页 cursor。

当前搜索结果通常可通过较小 `pageSize` 控制，但公司过滤项等集合增长后仍可通过范围续读完整结果。

### 11.4 长期记忆能力默认均为关闭状态

自述画像写入、Practice Chat 记忆召回和 Code Review 画像后台更新都已经实现，但 `application.yml` 默认关闭。因此开发或产品验收时不能仅根据类和 Bean 是否存在判断功能已上线，应检查对应环境变量、Definition 注册和实际工具事件。

### 11.5 副作用能力的确认策略不同

`submit_practice_code_review` 在 `PRACTICE_CHAT` 中由“模型识别疑似完整提交 + 精确工具名与受信 agentKey 自动授权 + 服务端可信上下文校验”组成闭环，不展示确认弹窗；`propose_current_problem_coach_summary` 本身无正式写副作用，先在聊天中展示候选，再由用户点击一次性 apply 按钮写入；`update_learner_declared_profile` 没有独立确认弹窗，依赖“用户明确陈述长期事实”的 Prompt 契约和服务端可信消息校验。三者采用与业务风险匹配的不同授权语义。

### 11.6 历史提交能力默认开启

历史正式提交 Tool 已实现，总开关与源码详情二级开关在 `application.yml` 中均默认开启。源码详情仍需通过当前消息的服务端意图判定和专用 `read_tool_result` 预算；需要收紧暴露范围时，可先关闭二级开关而不影响正式 Review 数据。

## 12. 主要代码依据

- Tool 抽象与注册：`backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentTool.java`、`AgentToolRegistry.java`。
- 场景白名单：各业务模块的 `*AgentDefinition.java`。
- 学习计划修订投影、查询与编译：`LearningPlanRevisionModelViewProjector.java`、`QueryLearningPlanRevisionAgentTool.java`、`CompileLearningPlanRevisionAgentTool.java`、`LearningPlanRevisionCanonicalRestorer.java`。
- 全局 Tool 装配：`backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorAiConfiguration.java`。
- Practice Chat 与记忆装配：`backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfiguration.java`。
- 当前题学习状态工具：`backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/GetCurrentProblemLearningStateAgentTool.java`。
- 当前题教练总结候选：`backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/ProposeCurrentProblemCoachSummaryAgentTool.java`、`practice/coachsummary/CoachSummaryProposalService.java`。
- 历史正式提交 Tool：`PracticeSubmissionHistoryRunScopeRegistry.java`、`GetPracticedProblemOverviewAgentTool.java`、`ListPracticeProblemSubmissionsAgentTool.java`、`ReadPracticeSubmissionDetailAgentTool.java`、`PracticeSubmissionHistoryToolResultReadGuard.java`。
- 正式 Review 装配：`backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/PracticeCodeReviewConfiguration.java`。
- 默认配置：`backend/mentor-api/src/main/resources/application.yml`。
