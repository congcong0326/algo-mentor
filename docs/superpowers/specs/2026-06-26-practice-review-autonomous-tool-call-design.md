# 练习 Review 自主 Tool Call 阶段二设计

## 背景

阶段一已经完成 `submit_practice_code_review` 的工具执行前权限闭环：模型一旦发起 Review tool call，后端会先发出 `tool_permission_request`，用户允许后才执行真实 Review 工具；拒绝、超时和取消都会回填 synthetic tool result，且不会写入正式 Review 记录。

阶段二不继续推进 forced tool calling，也不恢复后端规则识别或旧的自动 Review capability。后续长期方向是让主模型基于题目聊天上下文自主判断是否应发起正式 Review tool call。

## 目标

- 强化 practice chat 系统 prompt，让模型在看到用户像是在粘贴当前题目的完整 LeetCode 解法时，积极主动调用 `submit_practice_code_review`。
- 强化 Review tool 描述，把工具语义定义为“记录一次正式代码提交，并委托 Review 流程抽取代码、分析、打分、写入 DB”。
- 保持阶段一权限机制作为最终安全边界：用户允许后才生成正式 Review 记录，拒绝或超时不写库。
- 允许用户拒绝正式 Review 后，主模型继续以普通聊天方式点评代码，但不得给出正式分数、不得声称保存 Review、不得声称影响完成状态。
- 清理或修正文档和测试中仍暗示 forced tool calling、后端自动识别或旧 capability 自动 Review 的残留表达。

## 非目标

- 不实现 `toolChoice=SPECIFIC(submit_practice_code_review)`。
- 不做后端代码提交规则识别、后端意图分类或显式 Review run。
- 不保证完整题解输入 100% 触发 Review tool。
- 不恢复 `PracticeTurnClassifier -> CodeReviewTurnCapability` 自动写库链路。
- 不新增前端“提交 Review”按钮。
- 不改权限 API、权限 SSE、coordinator 或 Review 数据库 schema。

## 核心语义

`submit_practice_code_review` 是正式提交记录工具，不是普通聊天点评开关。

工具执行后会启动独立 Review 流程，从服务端受信上下文读取当前用户、练习会话、题目、run 和本轮用户消息，抽取用户提交代码，完成结构化分析和评分，并写入 `practice_code_review`。主模型不能通过 tool arguments 传入 userId、sessionId、problemSlug、完整代码或 messageId；这些事实必须继续来自服务端 metadata 和 runtime repository。

权限确认保护的是正式记录、额外 Review 调用、数据库写入和完成门禁影响。用户拒绝权限并不代表拒绝普通帮助，因此主模型仍可基于当前消息做非正式建议。

## Prompt 规则

Practice chat prompt 应采用积极触发风格：

- 当当前用户消息看起来像是在粘贴当前题目的完整 LeetCode 解法时，主模型应优先调用 `submit_practice_code_review`。
- 即使用户没有明确说“帮我 Review”或“看看能不能通过”，只要像完整题解提交，也应调用 tool，让用户通过权限弹窗决定是否生成正式记录。
- “像完整题解”由主模型结合上下文判断，可以参考代码块、`class Solution`、函数实现、返回值、语言关键字、当前题意贴合度等信号。
- 如果不确定是否完整但确实像题解提交，偏积极触发；明显片段、明显伪代码、报错日志、局部 bug、语法问题、复杂度讨论和概念问题不调用 tool。
- tool 被允许后，主模型根据 tool result 总结正式 Review 结论。
- tool 被拒绝或超时后，主模型可以继续普通点评，但必须说明未生成正式 Review 记录，且不能给正式分数或完成资格判断。

需要清理会削弱触发的旧表述，例如“用户粘贴代码时先定位关键问题和最小修改”。普通点评规则应放在未调用 tool、tool 被拒绝或 tool 超时后的路径中。

## Tool 契约

保留工具名 `submit_practice_code_review`，避免扩大前后端契约迁移面。工具描述需要明确：

- 该工具记录一次正式练习代码提交。
- 该工具会委托 Review 流程抽取代码、分析、打分并保存 Review 记录。
- 当前用户消息像完整题解提交时应调用，即使用户未明确请求正式 Review。
- 片段、伪代码、报错日志、局部 bug 和普通概念问题不应调用。
- 模型不得传入用户、会话、题目、消息 ID 或完整代码等业务事实。

参数继续保持最小：

- `userIntent`：可空字符串，只描述模型为什么认为这是一次提交。
- `notes`：可空字符串，只放简短上下文备注，不包含完整代码、不包含隐私，不作为权限或写库事实。

## 拒绝与超时行为

用户拒绝或权限超时时，真实 Review 工具不会执行，也不会生成正式 Review 记录。主模型收到 synthetic tool result 后应：

- 明确说明本次没有生成正式 Review 记录。
- 可以给普通聊天级代码建议。
- 不给正式分数。
- 不声称代码已通过正式 Review。
- 不声称完成门禁已更新或题目可以标记完成。

## 清理范围

代码层面：

- 保留“纯粘贴代码不会被后端自动写库”的反回归测试，防止旧自动 capability 回来。
- 测试命名应表达“模型发起 tool call 后触发权限”，不要暗示后端 forced 或规则识别。
- 检查 `practiceCapabilities`、`CodeReviewTurnCapability`、`PracticeTurnCapability`、自动 Review capability registry 等旧残留；若仍存在且未使用，应删除。
- `PracticeChatMessageIntentClassifier` 若仅用于 prompt 中展示本轮意图，可以保留；prompt 不应依赖它决定是否调用 Review tool。

文档层面：

- 修正 `docs/agent-forced-tool-calling-design.md` 中“阶段二 forced tool calling”的未来方向，将其标注为废弃方向或改写为自主 tool calling。
- 修正 `docs/practice-code-review-product-design.md` 和 `docs/practice-code-review-technical-design.md` 中“后端自动识别并生成 Review”的旧说法。
- 新增或更新文档时明确：后续不追求 100% 触发，代码提交是否进入正式 Review 由主模型自主判断，服务端权限机制只负责执行前确认和安全边界。

## 测试策略

- Prompt 测试：断言 practice chat prompt 包含积极触发规则、拒绝/超时后的限制、禁止正式分数和禁止声称写库。
- Tool spec 测试：断言 tool description 表达正式提交记录、委托 Review 流程抽取/打分/写库，并继续禁止模型传 user/session/problem/code 等事实。
- 权限流测试：保留 fake LLM 返回 Review tool call 的后端 flow 测试，证明允许前不写库、允许后写库、拒绝或超时不写库。
- 反回归测试：保留纯粘贴代码不会由后端自动保存 Review 的测试。
- 文档/搜索检查：用 `rg` 检查明显误导的旧方向术语并修正。

## 验收标准

- 没有新增 forced tool calling、后端规则识别或旧自动 Review capability。
- Practice chat prompt 积极要求模型在完整题解提交场景调用 `submit_practice_code_review`。
- Review tool spec 清楚表达正式代码提交记录与 Review 流程职责。
- 用户拒绝或超时后，模型可以普通点评，但不得输出正式分数、不得声称保存 Review、不得影响完成状态。
- 权限 API、权限 SSE、coordinator、Review schema 和前端弹窗链路保持兼容。
- 文档不再把未来方向描述为 forced tool calling 或 100% 触发。
