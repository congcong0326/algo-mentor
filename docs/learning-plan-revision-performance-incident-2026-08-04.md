# 学习计划草案修订 Agent 性能问题记录

## 文档信息

- 记录日期：2026-08-04
- 状态：已完成单次运行归因与首轮 `low` 灰度对照，待进行内部优化验证
- 适用范围：`LEARNING_PLAN_REVISION` / `learning-plan-revision`
- Provider 默认样本：`rid=3f888aa911fb`，Agent run `85eefaf0-4b25-494d-b19a-6b1054861a69`
- `low` 样本：`rid=fb6a16f0aee2`，Agent run `6638e788-cd95-4fd5-a455-fa7b454519be`
- 后续研发设计：`docs/learning-plan-revision-plan-compiler-design.md`
- 非目标：本记录不修改业务契约、模型路由或 Agent 实现。

## 结论

本次 SSE 总耗时为 `191872ms`，其中 Agent run 为 `191745ms`。耗时并非来自 OAuth2 鉴权、SSE 建连、数据库检索或本地上下文组装，而是几乎完全由 5 次 DeepSeek 模型请求产生。

| 项目 | 耗时 | 占 Agent run 比例 |
| --- | ---: | ---: |
| 5 次 LLM 请求 | `190848ms` | `99.5%` |
| 19 次工具调用 | 约 `623ms` | `0.3%` |
| 本地编排、持久化与间隔 | 约 `274ms` | `0.2%` |

其中最大单点是第 1 步的长推理和工具规划，以及第 4 步的完整计划 JSON 生成。第 4 步的 JSON 解析失败还触发了一次有界修复，额外增加约 12 秒。

后续使用相同模型、将模型路由的 reasoning effort 显式设置为 `low` 后，单次 Agent run 从 `191.764s` 降至 `109.577s`，下降 `42.9%`；主循环 reasoning token 从 `20164` 降至 `8147`，下降 `59.6%`。人工检查认为本次输出质量可以接受，但第 1 步仍耗时 `67.691s`，并且 JSON repair 仍然触发，因此 `low` 只能视为有效基线，不能替代内部编排、工具和结构化输出优化。

后续方案已经收敛为“候选计划蓝图 + Plan Compiler + 单次 Child Review”。下文 P1/P2 是事故归因阶段形成的备选优化顺序；实际研发以关联设计文档为准，不再优先建设面向模型的批量题库 Tool。

## 时间线与用量

| Step | 结束原因 | LLM 耗时 | TTFE | 输入 token | 输出 token | reasoning token | 缓存 token | 工具调用 |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | `TOOL_CALLS` | `83525ms` | `192ms` | 16780 | 11963 | 10933 | 11136 | 6 |
| 2 | `TOOL_CALLS` | `15412ms` | `1508ms` | 30570 | 2206 | 843 | 18816 | 7 |
| 3 | `TOOL_CALLS` | `24290ms` | `388ms` | 34116 | 3678 | 2511 | 32768 | 6 |
| 4 | `STOP` | `55668ms` | `343ms` | 39320 | 9044 | 5877 | 37760 | 0 |
| 5（JSON 修复） | `STOP` | `11953ms` | `141ms` | 4851 | 2267 | 0 | 0 | 0 |

说明：provider 返回的 `reasoningTokens` 已包含在对应请求的 token 统计语义中，不应与 `outputTokens` 再次相加。本次 5 个 step 的 `totalTokens` 合计为 `154795`。

首个模型事件通常在 `141ms` 至 `388ms` 到达，只有第 2 步为 `1508ms`。这说明连接、请求排队和首包不是主要矛盾；主要时间消耗在首包之后的模型推理、工具调用决策和内容生成。

## Reasoning Effort A/B 单样本记录

本节对比 Provider 默认样本与后续 `low` 样本。两次请求使用相同业务场景和模型，首步输入分别为 `16780` 与 `16709` token，输入规模接近；但两次请求对应不同草案，且未固定模型随机性，因此这是用于判断优化方向的单样本对照，不是可直接决定全量配置的统计实验。

| 指标 | Provider 默认 | `low` | 变化 |
| --- | ---: | ---: | ---: |
| Agent run 耗时 | `191.764s` | `109.577s` | `-42.9%` |
| 4 次主循环 LLM 耗时 | `178.830s` | `93.868s` | `-47.5%` |
| 主循环输入 token | `120786` | `101289` | `-16.1%` |
| 主循环输出 token | `26891` | `13741` | `-48.9%` |
| 主循环 reasoning token | `20164` | `8147` | `-59.6%` |
| 主循环 step 数 | `4` | `4` | 不变 |
| 工具调用数 | `19` | `14` | `-26.3%` |
| `list_problem_filters` 调用数 | `1` | `0` | `-1` |
| `search_problems` 调用数 | `18` | `14` | `-22.2%` |
| 工具结果字符数 | `42362` | `9415` | `-77.8%` |
| JSON repair 耗时 | `11.938s` | `14.977s` | `+25.5%` |

逐步对比如下：

| Step | Provider 默认耗时 | `low` 耗时 | Provider 默认 reasoning token | `low` reasoning token |
| ---: | ---: | ---: | ---: | ---: |
| 1 | `83.509s` | `67.691s` | `10933` | `7974` |
| 2 | `15.396s` | `4.984s` | `843` | `18` |
| 3 | `24.274s` | `4.624s` | `2511` | `0` |
| 4 | `55.652s` | `16.569s` | `5877` | `155` |
| 5（JSON 修复） | `11.938s` | `14.977s` | `0` | `0` |

观察结论：

1. `low` 对第 2 至 4 步效果显著，这三步的 reasoning token 合计从 `9231` 降至 `173`。
2. 第 1 步只从 `83.509s` 降至 `67.691s`，仍产生 `7974` 个 reasoning token，占 `low` 样本 Agent run 耗时约 `61.8%`，是下一阶段内部优化的首要目标。
3. `low` 样本没有调用 `list_problem_filters`，并减少了 4 次 `search_problems`；因此收益既包括 effort 的直接影响，也包括模型工具规划行为变化，不能把全部下降量解释为单纯的 token 生成速度变化。
4. 两个样本都执行了 4 个主 step，并且都触发一次 JSON repair。effort 调整没有消除模型调用轮次和结构化输出尾延迟。
5. 本次人工质量检查结论为“可以接受”。该结论支持继续以 `low` 作为内部优化对照基线，但仍需更多同类请求验证分位数、repair 率和质量稳定性。

## 已证实的原因

### 1. 第 1 步发生了异常长的模型推理

第 1 步耗时 `83.525s`，`outputCharCount=0` 是因为该步结束于工具调用而非文本回答，不代表模型没有生成 token。该步产生了 `11963` 个 output token，其中 `10933` 个为 reasoning token，随后一次性调用 6 个工具。

学习计划修订 Definition 使用 `LlmGenerationOptions.defaults()`，没有为该场景设置明确的 reasoning effort；最终 effort 会由模型路由决定，若路由也为空则使用 provider 默认值。DeepSeek 支持 `none`、`low`、`high` 和 `max` 四档 effort。

相关实现：

- `backend/mentor-application/.../LearningPlanDraftRevisionAgentDefinition.java`
- `backend/llm-core/.../LlmGenerationOptions.java`
- `backend/llm-core/.../LlmReasoningEffortResolver.java`
- `backend/llm-deepseek/.../DeepSeekProviderProfile.java`

### 2. 工具循环导致输入上下文持续增长

主循环的输入从 `16780` token 增加至 `39320` token，增长约 2.34 倍。第 1 至 3 步分别发起 `6`、`7`、`6` 个工具调用，其中包括 1 次 `list_problem_filters` 与 18 次 `search_problems`。

`list_problem_filters` 本次返回了 441 个公司、72 个标签、难度、岗位和时间桶等完整过滤项。调用参数未携带 `includeCounts`，因此使用默认值 `true` 并返回各项计数。该工具当前不支持按维度请求，例如只请求 tags 与 difficulties。

`search_problems` 的本地执行并不慢：18 次查询总耗时约 `605ms`，单次多数在 `28ms` 至 `33ms`。问题在于每一批工具调用完成后都会把 assistant tool call 和工具结果保留在下一次模型请求的上下文中。循环实现也会串行执行同一模型响应内的各个工具调用。

相关实现：

- `backend/mentor-api/.../ListProblemFiltersTool.java`
- `backend/mentor-api/.../SearchProblemsTool.java`
- `backend/agent-core/.../AgentLoopEngine.java`

### 3. 现有压缩策略在本次运行中未达到触发条件

当前默认运行级阈值为：单工具内联 `12000` 字符、所有工具结果 `60000` 字符、整体输入 `120000` token、最多 80 个消息组。即使最后一次主模型请求已有 `39320` input token，也仍远低于整体裁剪阈值，因此历史工具交互没有被 snip。

这并不表示压缩机制未接入；它已在每次模型请求前执行。但当前默认值更适用于长上下文通用 Agent，对只需少量候选题的学习计划修订偏宽。

相关实现：

- `backend/mentor-api/src/main/resources/application.yml`
- `backend/mentor-api/.../AgentCompactionProperties.java`
- `backend/agent-core/.../RunMessageCompactor.java`
- `backend/agent-core/.../ToolResultCompactionPolicy.java`

### 4. Prompt 规则与单题搜索接口共同诱导了重复查题

修订 Prompt 要求新增或替换题目必须经题库工具确认；当用户要求保留“高频、热门、核心面试题”时，还要求优先保留与 `COMPANY_FREQUENCY_DESC` 搜索候选重合的题目。日志中的 18 次搜索均采用该排序，符合这条规则。

现有 `search_problems` 一次只能描述一组过滤条件。模型为了分别确认多道候选题，会在多个 loop step 中反复调用单题搜索；工具本身快，但每增加一个 step 都需要额外一次完整模型请求。

本次 Agent 主循环上限为 24，允许一次结构化修复后运行时上限为 25。日志中的 `maxSteps=25` 正是该规则的结果。上限不是本次 4 个主 step 的直接成因，但当前值会放大异常循环的尾部风险。

相关实现：

- `backend/mentor-application/.../ManagedSystemPromptDefinitions.java`
- `backend/mentor-application/.../LearningPlanDraftRevisionAgentDefinition.java`
- `backend/mentor-application/.../LearningPlanAgentToolNames.java`

### 5. 完整 JSON 输出和 JSON_PARSE 修复增加尾部延迟

第 4 步生成了完整替换版 `resolvedBrief + generatedContent`，耗时 `55.668s`，输出 `9044` token、`10241` 字符。随后本地严格 JSON 解析失败，触发了 1 次 repair；repair 以原始无效输出为输入，耗时 `11.953s`，输出 `2267` token 后成功完成。

修复路径是预期的保护机制，并且 repair 已显式使用 `reasoningEffort=none`。但它不能替代 provider-native JSON Schema 的稳定输出；每次触发都会增加额外模型调用和约 12 秒尾延迟。

当前请求已携带 strict JSON Schema。故障根因尚不能仅凭摘要日志区分为 provider 输出不符合约束、流响应拼装问题或输出被截断；必须从脱敏后的 request snapshot、最终输出和校验错误文本进一步确认，不能在普通业务日志中记录原始用户内容或 reasoning 内容。

相关实现：

- `backend/mentor-application/.../LearningPlanDraftRevisionAgentDefinition.java`
- `backend/agent-core/.../AgentLoopEngine.java`
- `backend/agent-core/.../structuredoutput/StructuredOutputRepairPrompt.java`
- `backend/agent-core/.../structuredoutput/AgentStructuredOutputValidator.java`

## 已排除或影响很小的因素

- OAuth2 当前用户解析和 SSE 建连发生在 `67ms` 内，不是瓶颈。
- 本地 context preparation 每步仅 `15ms` 至 `22ms`。
- 工具查询和数据库访问均为几十毫秒级；即使工具改为并行，单次运行最多只能减少不足 1 秒，无法解决 192 秒问题。
- 上下文缓存已生效，后续主 step 的 cached token 达到 `18816`、`32768`、`37760`；缓存可缓解输入成本和 prefill，但不能消除模型生成 reasoning 或完整 JSON 的时间。

## 优化建议与验证顺序

### P0：受控配置验证（已完成首个 `low` 样本）

1. 在 `ai_llm_call_usage` 确认本 run 的 `reasoning_effort`。若为 `NULL`，表示本次使用 provider default。
2. 为内部测试用户的 `learning-plan-revision` 创建最小范围路由，先灰度 `low`，再在质量允许时评估 `none`。
3. 每种配置至少采集足够数量的同类修订请求，对比端到端耗时、主 step 数、reasoning token、JSON 修复率、计划校验通过率和人工质量抽样。
4. 不要同时修改 provider、模型、路由优先级和 effort，以避免无法归因。

预期：此项直接针对第 1 步的 10933 reasoning token，优先级最高；但降低 effort 可能导致更多工具循环或质量回退，必须灰度验证。

首个 `low` 样本已确认端到端耗时、主循环耗时和 reasoning token 均明显下降，人工质量可以接受；但单样本不足以确定全量 effort 配置，后续内部优化统一以该样本作为当前对照基线。

### P1：收敛工具上下文与调用次数

1. 为学习计划增加精简的题库发现接口，默认只提供困难度和标签，省略公司全集与 counts；或为 `list_problem_filters` 增加可选维度参数。
2. 为学习计划提供批量候选查询工具，使模型能在一次调用中确认多组关键词或多个题目，而不是循环执行 18 次单题查询。
3. 在 Prompt 中明确：仅当新增、替换或高频保留校验确有必要时调用题库工具；能够从当前有效草案直接保留的题目不重复检索。
4. 将学习计划修订的 loop policy 收紧到经过验证的上限，例如 6 至 8 个主 step；这用于限制异常路径，不作为主要性能优化手段。

### P2：为学习计划设置场景化上下文预算

1. 不直接缩紧全局 Agent 默认配置；先引入或验证学习计划专用的 compaction policy。
2. 目标是让旧搜索结果在满足上下文完整性的前提下更早转为占位或摘要，并控制工具结果总字符数。
3. 调整后必须验证 DeepSeek tool continuation 未被破坏，且模型能够在需要时通过 `read_tool_result` 读取大结果。

### P2：降低最终输出与修复概率

1. 评估是否可改为模型只输出变更内容，由服务端基于当前草案合成完整替换版；该项涉及业务契约，不能作为纯性能配置直接上线。
2. 在不记录原始敏感内容的前提下，补充 JSON_PARSE 的失败类别、输出长度、响应格式和 schema 版本观测。
3. 针对 DeepSeek 的“工具调用后最终 JSON Schema 输出”补充真实端到端回归；确认 strict schema 的具体失效条件后，再决定是修正 provider 映射还是调整 repair 策略。

## 后续排查数据

已有持久化能力可在不扩大普通日志内容的前提下提供精确证据：

- `ai_llm_call_usage`：按 `run_id` 查询各 step 的 `reasoning_effort`、token 用量和调用时间。
- `agent_tool_call`：检查 `result_char_count`、`result_token_estimate`、`result_storage_mode`，确认各工具结果的真实上下文规模。
- `agent_context_snapshot`：检查经过脱敏后的 request metadata、messages、generation options、response format 与 compaction metadata。

查询与导出必须遵守既有脱敏策略：禁止将 API Key、Authorization、原始用户输入、完整模型输出或 reasoning 内容复制到普通日志、工单和截图。

## 验收指标

在完成 P0/P1 优化后，以同类修订请求的分位数而非单次样本验收：

- 端到端延迟、各 LLM step 延迟和 TTFE 显著低于当前 191 秒样本。
- 首个工具规划 step 的 reasoning token 明显下降，且工具调用不出现异常循环。
- 主循环次数减少；`search_problems` 调用数与实际需要确认的题目数一致。
- JSON repair 率可观测并持续下降；发生 repair 时仍可安全完成或返回稳定错误。
- 学习计划服务端校验通过率、题目事实准确性和人工质量抽样不低于优化前基线。
