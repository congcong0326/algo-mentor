# Agent Tool 后续研发需求

## 1. 文档定位

本文记录 Agent Tool 后续研发需求，只描述业务目标、优先级和范围边界，不展开参数 Schema、类设计、存储方案、调用预算或测试实现。

当前已经实现并可在代码中找到的 Tool，以 `docs/agent-tool-catalog.md` 为唯一现状基线。规划项完成开发后，必须同步更新现状清单，并在本文中调整对应需求状态。

## 2. 研发方向

近期 Tool 建设围绕两个目标展开：

- 补齐现有 Agent 已经产生的结果和学习记录读取能力。
- 缩短 Practice Chat 中“练习、复盘、沉淀笔记”的操作路径。

不为尚未立项的 Agent 场景提前建设 Tool，也不把本应由后端固定提供的必要上下文改为模型自主查询。

## 3. 需求优先级

| 优先级 | 类型 | 需求 | 状态 |
| --- | --- | --- | --- |
| P0 | 调整现有 Tool | 学习计划 Agent 可继续读取被压缩的工具结果 | 已完成 |
| P0 | 调整现有 Tool | Practice Chat 可读取当前题目的正式 Review 轨迹 | 已完成 |
| P1 | 新增 Tool | Practice Chat 可按需读取当前题目的学习状态 | 已完成 |
| P2 | 新增 Tool | 用户可在 Practice Chat 中确认后追加题目笔记 | 已完成 |
| 暂缓 | 新增 Tool | 全局学习进度快照 | 等待全局学习教练对话立项 |

## 4. 调整现有 Tool

### 4.1 学习计划结果续读

学习计划草案、修订和扩展 Agent 已能查询题库，但在工具结果被压缩后无法继续读取完整结果。

已完成：三个学习计划 Agent 已使用现有 `read_tool_result`，题库过滤项或搜索结果较大时可在当前 Agent run 内继续读取；结果不会跨 run 暴露。

### 4.2 Practice Chat Review 轨迹

现有 `get_problem_review_trajectory` 只服务后台画像更新，Practice Chat 无法利用正式 Review 历史回答用户关于进步、持续问题和已解决问题的追问。

已完成：复用现有 `get_problem_review_trajectory`，Practice Chat 仅能读取当前用户正在练习题目的正式 Review 轨迹。前台能力通过独立的 `PRACTICE_CHAT_REVIEW_TRAJECTORY_TOOL_ENABLED` 开关启停，不依赖后台画像消费者。

### 4.3 题面读取能力保持现状

现有 `get_problem_statement` 暂不加入当前 Agent 白名单。Practice Chat 已固定获得当前题面，学习计划选题也不需要批量读取完整题面。

只有在独立讲题 Agent 或“搜索题目后直接讲解”的产品场景立项后，才重新评估其业务入口。

## 5. 新增 Tool

### 5.1 当前题目学习状态

已完成：新增 `get_current_problem_learning_state`，服务 Practice Chat。

业务需求：当用户询问题目完成状态、最近正式 Review、复习安排或既有题目笔记时，Agent 能读取当前题目的最新学习记录，而不是依赖聊天历史猜测。

范围边界：

- 只读取当前 Practice Chat 对应的题目，不提供跨题或跨计划查询。
- 默认提供状态和笔记提纲；只有用户明确要求时才读取笔记正文。
- 不读取源代码、完整聊天历史或其他与问题无关的用户数据。
- 详细的多版本 Review 变化继续使用现有 `get_problem_review_trajectory`。

落地结果：身份、session、plan、phase 和题目均来自服务端可信上下文；默认笔记摘要查询不读取 Markdown 正文，只有当前用户消息明确要求且工具参数同步开启时才读取正文。能力通过 `PRACTICE_CHAT_LEARNING_STATE_TOOL_ENABLED` 独立开关启停。

### 5.2 追加当前题目笔记

已完成：新增 `append_current_problem_note`，服务 Practice Chat。

业务需求：用户明确要求“保存到笔记”或“把这个记下来”时，Agent 能把本次对话中整理出的内容追加到当前题目的用户笔记。

范围边界：

- 每次写入都需要用户确认，并展示将要追加的内容。
- 只允许追加，不覆盖、清空或删除用户已有笔记。
- 不自动在每次讲解或正式 Review 后写入。
- 第一阶段不修改结构化解题提纲。

落地结果：模型只提交待追加的 `contentMarkdown`；身份、session、plan、phase 和题目来自服务端可信上下文。权限 Hook 会在执行前把规范化后的确切 Markdown 展示给用户，只有用户允许后才进入工具实现；全局工具权限机制关闭时该写工具不会注册。持久化使用 PostgreSQL 原子 upsert，只追加正文并递增修订号，已有结构化提纲保持不变；拒绝、超时、非法参数和长度超限均不会写入。能力通过 `PRACTICE_CHAT_NOTE_APPEND_TOOL_ENABLED` 独立开关启停。

## 6. 暂缓需求

### 6.1 全局学习进度快照

`get_learning_progress_snapshot` 暂缓研发。

当前项目没有全局学习教练对话，今日题包、计划进度、训练节奏和下一训练内容也已有确定性后端服务和页面入口。现阶段新增该 Tool 缺少明确消费者，并会扩大 Practice Chat 的上下文范围。

当全局学习教练对话正式立项后，再评估该 Tool 是否需要提供当前激活计划、近期活动、节奏状态和能力标签等事实。

## 7. 推荐研发顺序

1. 先补齐 `read_tool_result` 和 `get_problem_review_trajectory` 的现有场景接入。
2. 已完成 `get_current_problem_learning_state`。
3. 已完成需要用户确认的 `append_current_problem_note`。
4. 全局学习进度快照保持暂缓，不继续展开其他候选 Tool。
