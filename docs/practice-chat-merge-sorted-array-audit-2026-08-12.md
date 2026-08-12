# 题目聊天会话与上下文管理审计记录（LeetCode 88）

## 文档信息

- 审计日期：2026-08-12
- 文档性质：一次生产会话的只读审计记录
- 审计范围：`PRACTICE_CHAT` 中“88. 合并两个有序数组”的最近一次主会话、正式 Code Review、LLM 请求快照和 Prompt 组装链路
- 主会话：`agent_task.id=142`，用户 `1`，题目 `merge-sorted-array`，练习会话 `321`
- 时间范围：2026-08-12 03:16 至 03:46 UTC
- 审计数据：`agent_task`、`agent_message`、`agent_run`、`agent_run_step`、`agent_tool_call`、`agent_context_snapshot`、`practice_code_review`
- 非目标：本文不修改生产数据、Prompt、模型路由、上下文策略或业务代码；不记录用户完整代码、模型完整回复、密钥或数据库连接信息。

## 结论摘要

本次会话的教学结果和正式 Code Review 闭环整体良好：模型以引导式对话帮助用户从辅助数组双指针过渡到原地尾部双指针，用户在第二次提交中实现了正确解法；两次代码提交均按“模型调用正式 Review 工具，再基于结果回复”的预期链路执行。

短会话上下文的组成和消息顺序基本正确，但当前实现尚未达到“预算可控且可审计”的标准：每次请求都会重复发送当前用户消息；字符除以 4 的 token 估算对中文和工具上下文严重低估；持久化快照未正确记录 prompt 预算。这些问题会增加成本、干扰模型对当前意图的权重，并使长会话的裁剪行为无法可靠验收。

| 维度 | 结论 |
| --- | --- |
| 教学与代码反馈质量 | 良好，约 `8/10` |
| 正式 Code Review 闭环 | 符合预期，两次均完成工具调用和持久化 |
| 当前题目/计划上下文 | 符合预期，由服务端校验后注入 |
| 历史窗口顺序 | 符合预期，普通聊天历史按时间顺序保留，题面 seed 未混入历史 |
| 当前消息去重 | 审计时不符合预期；已于 2026-08-12 修复，待下次真实会话复核 |
| 8,000 token 预算执行 | 不符合预期，provider 实际输入最高达到 `8,762` token |
| 预算快照可观测性 | 不符合预期，`agent_context_snapshot.token_budget` 为 `0` |
| 长会话摘要/滑窗淘汰 | 本次未触发，不能据此验收 |

## 审计样本

主会话共产生 7 个成功 run、15 条用户可见消息、9 个 LLM 请求 step：

| Run | Step 数 | 结束原因 | 最高单 step 输入 token | 主要行为 |
| ---: | ---: | --- | ---: | --- |
| 208-212 | 1 | `STOP` | 4,873 | 思路引导、方向校正、代码骨架 |
| 213 | 2 | `TOOL_CALLS -> STOP` | 8,762 | 第一次代码提交与正式 Review |
| 215 | 2 | `TOOL_CALLS -> STOP` | 6,580 | 第二次代码提交与正式 Review |

所有 run 均以 `succeeded` 结束，模型为 `deepseek/deepseek-v4-flash`。第 213、215 两个 run 都调用 `submit_practice_code_review`；工具执行成功后，第 2 step 的消息顺序保留了 assistant tool call 与 tool result，符合 run 内工具交互的上下文要求。

## 模型回答质量

### 做得好的部分

1. 模型正确认可了“新建辅助数组 + 双指针”的可行性，同时把教学重点转到题目进阶要求所需的原地合并。
2. 用户提出“先将 `nums1` 有效段右移再正向合并”后，模型先澄清理解，再说明该方案可行但多了一次迁移，并引导用户发现更简洁的尾部写入方向。
3. 模型明确了三个核心指针：`m - 1`、`n - 1`、`m + n - 1`；并解释了为什么主循环结束后只需要补齐 `nums2` 剩余元素。
4. 第一次代码 Review 正确识别了两个根因：从前向后读却从后向前写的方向冲突，以及自增后读取导致的越界/跳过元素。持久化 Review 的静态结论与最终聊天回复一致。
5. 第二次代码 Review 正确认定尾部双指针实现可通过静态分析，复杂度结论为时间 `O(m+n)`、额外空间 `O(1)`；唯一扣分点是冗余的边界特判。

### 需要改进的部分

1. 最终回复称两次消息中都“贴了两遍同一份代码”，但持久化的两条用户代码消息各自只包含一份 `Solution`。这是缺乏证据的提醒，应避免。
2. “代码通过了”的表达应限定为“静态 Review 判定很可能通过”或“正式 Review 已通过”。本次没有真实 LeetCode 判题结果，不能将静态分析表述成在线评测已通过。
3. 部分引导回复偏长、问题较多；对于已明确展示理解的用户，可用更短的追问收束到循环不变量和边界条件。

## 正式 Code Review 结果

| 版本 | Review ID | 总分 | 结论 | 关键依据 |
| ---: | ---: | ---: | --- | --- |
| 1 | 38 | `4.0/10` | 未通过 | `j++` 后访问 `nums2[j]` 会越界；从头读 `nums1`、从尾写入会破坏未处理数据 |
| 2 | 39 | `9.8/10` | 通过静态 Review | 尾部双指针避免覆盖；`m=0`、`n=0`、重复值和剩余元素均可处理 |

第二版的 `9.8` 分并非真实评测执行结果。Review 记录明确说明：未提供服务端执行结果，因此判定基于静态分析。

## 上下文管理核验

### 预期链路

Practice Chat 的设计顺序为：

```text
system: 稳定教学与安全规则
system: 当前题目、学习计划和题面事实
system: 可选 active summary
history: 最近普通聊天消息
user: 当前用户消息
```

实现使用最近 8 个 turn（最多 16 条消息）的历史窗口，并以 `PRACTICE_CHAT` 的 8,000 token Prompt 预算组装上下文。最终 LLM 请求由 `agent_context_snapshot` 持久化，用于事后复核。

### 已符合预期的部分

1. 每轮请求均包含服务端确认的计划、阶段、题号、题面、语言和教练风格；题面不能被历史或用户输入覆盖。
2. `PROBLEM_STATEMENT` seed message 会被排除在普通聊天历史之外，题面只通过服务端运行时上下文注入，没有污染 history。
3. 历史消息按 `sequence_no` 升序进入模型上下文，用户与 assistant 消息交替关系在本次样本中正确。
4. 两次代码提交的第 2 step 都保留了 tool call 和 tool result，模型的最终文字回复能够基于正式 Review 工具结果生成。
5. 学习者画像召回在本次样本中未产生内容，未向 Prompt 注入无关记忆。

### P1：当前用户消息重复发送（已修复，待生产复核）

每个请求快照都包含两份当前用户消息。例如首轮快照的第 6、7 条均为同一条用户输入；后续每个 step 也保持两份相同的当前消息。

根因是创建 run 时用户消息已经写入 `agent_message`，随后 `AgentConversationService` 查询最近历史时会取回该消息；组装 Prompt 时又将 `command.userMessage()` 作为 `CURRENT_USER_MESSAGE` 再次追加。

相关实现：

- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationService.java`
  - `recentMessages(... contextPolicy.recentTurns() * 2)`：第 339 行
  - `VARIABLE_HISTORY` 与 `VARIABLE_CURRENT_USER_MESSAGE` 同时赋值：第 367-368 行
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptSectionProvider.java`
  - history 后固定追加 current user section：第 61-63 行

影响：当前意图在上下文中被不必要加权；消息和 token 成本随每一轮增加；更重要的是，这破坏了 Prompt 设计中“history placeholder 与当前输入”互斥的语义。

修复记录（2026-08-12）：新增 `recentMessagesBeforeTurn(taskId, turnId, limit)` 查询契约，PostgreSQL 按 `agent_turn.sequence_no` 只返回当前 turn 之前的消息。Practice Chat 仅将该结果注入 history，当前用户输入继续只通过 `CURRENT_USER_MESSAGE` 追加。该实现按 turn 身份过滤，不按文本去重，因此不会错误删除历史中内容恰好相同的消息。

已验证：

- `AgentConversationServiceTest` 新增回归用例，模拟“最近消息”已包含刚落库的本轮输入，断言最终请求中本轮文本只出现一次。
- PostgreSQL 复核查询确认原审计会话的当前代码提交 turn 只会检索到其之前的 13 条消息。
- 定向 Maven 测试通过：会话服务、PostgreSQL repository、MyBatis mapper XML 及 API 自动配置测试。

### P1：token 预算严重低估且不是硬上限

Prompt 组装器以 `text.length() / 4` 估算 token，并以相同假设将 token 裁剪转换为字符截断。该估算不适用于中文，也没有计入 provider 归一化消息开销和工具 schema。

本次实际样本如下：

| Run / Step | 快照 token 估算 | Provider 实际输入 token | 实际/估算 |
| --- | ---: | ---: | ---: |
| 208 / 1 | 1,304 | 3,743 | 2.87x |
| 209 / 1 | 1,434 | 3,973 | 2.77x |
| 210 / 1 | 1,586 | 4,264 | 2.69x |
| 211 / 1 | 1,759 | 4,604 | 2.62x |
| 212 / 1 | 1,901 | 4,873 | 2.56x |
| 213 / 1 | 2,484 | 5,524 | 2.22x |
| 213 / 2 | 2,611 | 8,762 | 3.36x |
| 215 / 1 | 2,941 | 6,212 | 2.11x |
| 215 / 2 | 3,068 | 6,580 | 2.14x |

虽然配置的 Prompt token budget 为 8,000，但第 213 的 Review 工具后续 step 已达到 8,762 个 provider 输入 token。该预算当前只能理解为基于粗略字符估算的软目标，不能作为实际请求上限或成本控制承诺。

相关实现：

- `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/prompt/DefaultPromptRenderer.java`
  - token 估算：第 124-128 行
  - token 到字符的截断换算：第 57-68 行
- `backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfiguration.java`
  - 将 Practice Chat 的 Prompt 预算配置为上下文策略预算：第 155-160 行

### P2：快照无法正确审计预算

本次 9 条 `agent_context_snapshot` 的 `token_budget` 均为 `0`。Prompt Assembly metadata 实际写入的是 `promptTokenBudget`，但 trace observer 读取的是通用 `tokenBudget`；此外通用脱敏规则会将任何包含 `token` 的 metadata 字段替换为 `[REDACTED]`，导致 `promptTokenBudget` 和 `promptTokenEstimate` 在持久化快照中不可读。

因此即使运行时完成了 Prompt 裁剪，也无法通过快照直接判断：采用了哪个预算、裁剪了哪些 section、最终预算估算为何值。

相关实现：

- `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/observer/PersistentAgentTraceObserver.java`
  - 写入 `token_budget` 时读取 `AgentRuntimeMetadataKeys.TOKEN_BUDGET`：第 79-102 行
- `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/observer/AgentTraceRedactor.java`
  - 将所有包含 `token` 的字段一律脱敏：第 55-72 行

### 未覆盖的上下文能力

本次主会话只有 7 个 run，未达到最近 8 个 turn 的历史窗口上限；`agent_task.active_summary_artifact_id` 为空，且库中尚无 `agent_artifact` 表。因此本次不能验证：

1. 历史超过 16 条消息时的滑动窗口淘汰是否保持完整的 user/assistant turn 边界。
2. active summary 的生成、持久化、恢复、优先级和失效机制。
3. 在长会话或多工具循环中，Prompt 裁剪是否会误删关键历史、工具结果或最近未完成 interaction group。

这些能力不应因为本次短会话未出现异常而默认视为已验收。

## 修复优先级与验收建议

### P1：修复当前消息去重（已完成）

组装 history 时排除本 run 所属 turn 的 user message，或在 repository 查询中只读取当前 turn 之前的已接受消息。保留 `CURRENT_USER_MESSAGE` 作为唯一的当前输入来源。

已按此方式实现。后续验收应在新的真实 Practice Chat run 中检查 `agent_context_snapshot`：本轮用户文本应只在最终请求 messages 中出现一次；带工具的后续 step 也不应重新出现第二份本轮输入。

### P1：替换字符除以 4 的预算模型

使用面向目标 provider/model 的 tokenizer 或保守的 Unicode/中文估算，并将 system message、工具 schema、tool call 和 tool result 一并纳入总输入预算。若无法精确估算，应预留足够安全边际并在提交前设置可观测的硬阈值。

验收：中文题面、长代码提交和工具调用后的请求均不超过配置输入预算；快照估算与 provider 实际输入的偏差应在预设范围内。

### P2：统一运行时与快照的预算字段

将 `promptTokenBudget`、`promptTokenEstimate`、裁剪 section 列表映射到结构化快照字段；脱敏策略应区分认证 token 与 token 计数/预算，不能屏蔽非敏感数值观测字段。

验收：每个 `agent_context_snapshot` 可读出预算、估算、裁剪结果与最终 provider 用量，且不泄露密钥、认证令牌或用户原文。

### P2：收敛教学回复的证据边界

对“重复粘贴”“在线通过”“评测结果”等结论建立来源要求：只有持久化消息、工具结果或真实判题回传明确支持时才可陈述；静态 Review 与真实执行结果在文案和结构化状态上分开表达。

验收：针对无重复代码、无真实判题结果的测试样本，模型不会生成无依据提醒或将静态通过表述为线上通过。

## 后续验证建议

在修复前后使用相同题目与相同中文会话脚本做对照，至少覆盖：

1. 首轮思路讨论、连续 9 轮以上追问、代码提交和 Review 工具调用。
2. 长代码、长题面、中文为主且混有 Java 标识符的 Prompt。
3. 工具调用前后每一个 final request snapshot 的消息去重、角色顺序、预算估算、实际 provider 用量和裁剪 metadata。
4. 超窗口后摘要尚不可用时的明确降级行为；摘要实现接入后再单独验证生成、恢复和淘汰。
