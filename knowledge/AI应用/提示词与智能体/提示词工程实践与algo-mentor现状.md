# 提示词工程实践与 algo-mentor 现状

更新时间：2026-06-29

## 核心结论

提示词工程不是单纯“把提示词写得更像模板”，而是把模型行为约束、业务上下文、工具调用边界、结构化输出、测试与发布流程一起纳入工程管理。

OpenAI 官方给出的 `Identity / Instructions / Examples / Context` 不是强制标准，而是组织 `developer message` 的推荐范式。它适合用作默认骨架，但具体内容和顺序仍要根据模型、任务、上下文长度、工具调用方式和评估结果调整。

结合当前 `algo-mentor` 项目，练习聊天的 Prompt Assembly 设计是合理的，已经比简单的大段字符串 prompt 更工程化；学习计划生成和代码 Review 的提示词当前可用，但还存在统一到 Prompt Assembly、增加评估用例和更好适配 OpenAI `developer message` 语义的优化空间。

## OpenAI 官方建议怎么理解

OpenAI 文档里有几个重点值得沉淀。

第一，`developer` message 和 `user` message 可以类比为“函数定义”和“函数参数”。`developer` message 承载应用开发者定义的规则和业务逻辑，`user` message 承载终端用户的输入和配置。这个类比对工程设计很重要：业务规则不应该和用户输入混在同一层级里，用户输入也不应该被提升成系统规则。

第二，OpenAI 建议生产提示词以代码方式管理。也就是把 prompt builder 放在业务附近的小模块里，用类型化参数或 schema 注入动态值，走代码评审、测试、评估和部署流程。对后端项目来说，这意味着提示词不是配置后台里的一段散文本，而应该像 service、DTO、数据库迁移一样可审查、可版本化、可回滚。

第三，OpenAI 建议用 Markdown 标题、列表和 XML 标签等方式标记边界。典型 `developer message` 可以包含：

- `Identity`：说明助手身份、目标、沟通风格。
- `Instructions`：说明必须做什么、不能做什么、工具如何调用、输出格式如何约束。
- `Examples`：给出输入与期望输出样例。
- `Context`：放置模型完成任务所需的额外背景、私有数据或业务上下文。

这个顺序背后的原则是：稳定身份和规则在前，动态上下文在后。动态上下文经常随请求变化，放在靠后位置也更符合 prompt caching 和阅读维护习惯。

第四，OpenAI 强调提示词需要实验和评估。官方文档明确把 prompting 称为 art and science，建议每次发布前运行 prompt tests 和 evaluation cases。对于 `algo-mentor` 这类学习产品，不能只凭一次主观对话判断 prompt 好坏，而应沉淀代表性样例，例如“用户要提示”“用户要完整代码”“用户贴 WA 反馈”“用户贴完整题解”“用户贴片段但不是提交”等。

第五，对于 reasoning models，OpenAI 当前建议更直接：developer message 是新的 system message；提示应简单、明确；不要要求模型展示 chain-of-thought；先尝试 zero-shot，再在复杂输出要求上补充 few-shot；使用分隔符和 section title 帮助模型理解边界。

参考资料：

- OpenAI Prompt engineering: https://developers.openai.com/api/docs/guides/prompt-engineering
- OpenAI Prompting: https://developers.openai.com/api/docs/guides/prompting
- OpenAI Reasoning best practices: https://developers.openai.com/api/docs/guides/reasoning-best-practices

## 在 algo-mentor 里，提示词工程应该怎么做

对当前项目来说，提示词工程至少包含以下几层。

第一层是稳定规则。包括助手身份、安全边界、隐私规则、业务范围、输出语言、是否允许编造题面、是否允许输出正式分数等。这一层应该由后端代码控制，不能被用户输入覆盖。

第二层是场景策略。不同场景的策略不同：练习聊天要围绕当前题目教学；学习计划生成要调用题库工具并输出结构化计划；代码 Review 要判断是否为当前题目的完整 LeetCode 解法并输出 schema JSON。

第三层是服务端校验过的业务上下文。比如当前学习计划、阶段、题目 slug、题面事实、题库候选、用户选择的教练风格和响应语言。这些内容可以影响模型回答，但不能覆盖稳定规则。

第四层是低可信上下文。包括用户原始输入、历史消息、模型生成的摘要、工具返回内容和 LeetCode 反馈。它们对回答很重要，但必须被标记为参考或输入，不能变成系统规则。

第五层是运行治理。工具权限、配额、并发锁、审计、结构化输出 schema、数据库写入权限等不应该只靠 prompt 约束。Prompt 可以引导模型，但最终安全边界必须由服务端代码强制执行。

所以，一个合格的 `algo-mentor` prompt 不只是包含“你是算法教练”这句话，还应该能回答这些问题：

- 哪些规则是稳定且不可被用户覆盖的？
- 哪些上下文是服务端校验事实，哪些只是用户声称？
- 当前场景允许调用哪些工具，何时调用，何时不能调用？
- 结构化输出由 prompt 描述，还是由 JSON Schema 强约束？
- prompt 修改后如何测试回归？
- prompt 内容如何版本化、观察和回滚？

## 当前项目的提示词现状

### 练习聊天

练习聊天目前是项目里最成熟的 prompt 设计。

相关代码：

- `../backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptSectionProvider.java`
- `../backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptProfileResolver.java`
- `../backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/prompt/PromptSlot.java`
- `../backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/prompt/DefaultPromptAssembler.java`

当前练习聊天大致按以下顺序组装：

```text
STATIC_INSTRUCTION   平台与安全基线
SCENARIO_POLICY      教练风格、回复语言、题目聊天策略、Review tool 边界
RUNTIME_CONTEXT      当前训练上下文
MEMORY_SUMMARY       会话摘要
HISTORY              最近聊天历史
CURRENT_USER_MESSAGE 当前用户消息
```

这个结构与 OpenAI 推荐的 `Identity / Instructions / Context` 基本一致，而且更细。它的优点是：

- 稳定规则、场景策略、上下文、历史和当前用户输入分层明确。
- 每个 section 有 `slot`、`role`、`trustLevel`、`sensitivity`、`cachePolicy` 和 `budgetPolicy`。
- 用户选择的教练风格是受控枚举，不允许用户注入任意 system prompt。
- 当前用户消息、历史消息、摘要和题面都被明确限制为不能覆盖系统规则。
- `submit_practice_code_review` 的调用边界写得比较具体：何时积极调用、何时普通答疑、用户拒绝或超时后不能声称已保存正式记录。
- Prompt 不是唯一安全边界，代码里已有工具权限确认、服务端 metadata 和 Review 写库校验。

从提示词工程角度看，这套设计是合理的。它不是机械套用 OpenAI 四段式，而是把四段式拆成了更适合后端系统维护的 section 模型。

### 学习计划生成

学习计划生成目前使用 `LearningPlanDraftPromptBuilder` 手写 `system + user` prompt。

相关代码：

- `../backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/stream/LearningPlanDraftPromptBuilder.java`

当前 prompt 已经包含关键规则：

- 先使用 `list_problem_filters`，再用 `search_problems` 搜索候选题。
- 推荐题必须来自本地题库候选，不能编造 slug、标题、难度或标签。
- 候选不足时允许少推荐，并在 metadata 中标记。
- 阶段数、周期、每阶段题目数量和输出 JSON 都有明确约束。

这对一个窄任务来说是可用的。但问题是它还没有接入通用 Prompt Assembly，缺少统一的 section、版本、快照、预算、敏感级别和测试组织方式。项目文档 `../docs/agent-conversation-code-review-notes.md` 里已经指出这个问题：核心业务提示词入口并不统一，学习计划后续适合迁移为 `LearningPlanDraftPromptSectionProvider` 和 `LearningPlanDraftPromptProfileResolver`。

### 代码 Review

代码 Review 使用 `PracticeCodeReviewPromptBuilder` 手写 `system + user` prompt，并配合 structured output。

相关代码：

- `../backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewPromptBuilder.java`

当前设计比较适合结构化任务：

- system prompt 明确身份、任务和安全隐私规则。
- user prompt 提供当前题目、学习计划上下文、本轮消息、提取代码、摘要和评分规则。
- 输出由 JSON Schema/structured output 约束，不只是靠自然语言要求。
- 服务端会重新归一化 total 和 passed，避免完全信任模型评分。

它的主要改进空间不是“写更多规则”，而是把 Review prompt 的事实来源、摘要可信度、输出约束和评估样例沉淀得更系统。

### OpenAI adapter 角色映射

当前 `llm-core` 里是 `LlmMessage.Role.SYSTEM / USER / ASSISTANT / TOOL`，OpenAI adapter 会把 `SYSTEM` 映射成 OpenAI 请求里的 system role。

相关代码：

- `../backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/request/LlmMessage.java`
- `../backend/llm-openai/src/main/java/org/congcong/algomentor/llm/openai/OpenAiResponsesMapper.java`

这在当前抽象下可以工作。但 OpenAI 对 reasoning models 的文档已经更强调 `developer message` 是新的 system message。后续如果项目主要面向 OpenAI Responses API 和 reasoning/tool-calling 模型，可以考虑在 `llm-core` 增加 `DEVELOPER` role，或者在 OpenAI provider 内把业务规则类 message 映射到 developer/instructions 语义。

## 是否合理

整体判断：合理，但还没有完全统一。

合理的地方：

- Practice Chat 的分层结构符合 OpenAI 推荐的逻辑边界，并且有更强的工程元数据。
- 重要业务事实来自服务端上下文，而不是信任用户输入。
- 工具调用引导和工具执行权限分离，避免把 prompt 当安全边界。
- 结构化输出场景已经使用 JSON Schema，而不是只要求“请输出 JSON”。
- 代码里已有 prompt profile、section、token budget、snapshot 和 metadata，为后续评估与排查提供基础。

不足的地方：

- 学习计划和代码 Review 仍是手写 builder，和 Practice Chat 的 Prompt Assembly 不统一。
- 缺少系统化 prompt eval。当前更多是单元测试断言 prompt 包含规则，缺少代表性输入输出样例的行为评估。
- `system` 与 OpenAI `developer` 语义尚未对齐，未来多 provider 抽象下需要明确不同 provider 的角色映射策略。
- few-shot examples 使用较少。对确定性很强的结构化输出可以继续 zero-shot，但对“是否调用 Review tool”这类边界判断，可以增加少量高价值例子。
- 部分 prompt 仍用自然语言承载业务约束，需要持续区分“模型行为引导”和“服务端强制校验”。

## 优化空间

### 1. 统一业务 prompt 到 Prompt Assembly

建议优先把学习计划生成迁移为：

```text
LEARNING_PLAN_DRAFT_V1
  STATIC_INSTRUCTION   平台、安全和输出基线
  SCENARIO_POLICY      计划生成规则、题库工具使用规则
  RUNTIME_CONTEXT      用户目标、周期、水平、语言、偏好
  TOOL_RESULT          题库筛选和搜索结果
  CURRENT_USER_MESSAGE 当前生成请求
```

这样可以让学习计划和练习聊天共享同一套版本、预算、快照和测试机制。

代码 Review 也可以后续迁移，但优先级可以低于学习计划，因为 Review 当前任务更窄，且 structured output 已经提供了强约束。

### 2. 为关键行为建立 eval fixtures

建议为 Practice Chat 至少沉淀这些用例：

- 用户问“给我一点提示”：应引导思路，不直接提交 Review。
- 用户问“直接给 Java 解法”：应给完整思路、复杂度和代码。
- 用户贴 WA 反馈：应分析失败路径，不调用正式 Review tool。
- 用户贴完整 `class Solution`：应优先调用 `submit_practice_code_review`。
- 用户贴代码片段或辅助函数：应普通点评，并提醒粘贴完整解法才能生成正式 Review。
- 用户拒绝 Review 权限：可以继续普通点评，但不得给正式分数，不得声称保存记录。

学习计划也应有 eval：

- 候选题充足时，所有推荐题必须来自工具返回。
- 候选不足时，允许少推荐并标记 incomplete。
- 用户目标、周期、每周时间变化时，阶段数和题量符合规则。
- 不允许编造 slug、标题、难度和标签。

### 3. 增加少量 few-shot，不要滥用

OpenAI 对 reasoning models 的建议是先 zero-shot，再按需要 few-shot。当前项目可以遵循这个策略。

适合补 examples 的地方：

- Review tool 调用边界。
- 学习计划候选不足时的输出形态。
- 代码 Review 中“不是当前题目 / 不是完整解法 / 只是报错日志”的结构化字段。

不建议给太多 examples。例子过多会增加 token 成本，也可能让模型过度模仿某些题型。

### 4. 明确 developer/system role 策略

后续可以在 `llm-core` 做一次角色语义梳理：

- 平台和业务规则：映射到 OpenAI `developer` 或 Responses API `instructions`。
- 用户输入：保持 `user`。
- 历史 assistant：保持 `assistant`。
- 工具结果：保持 tool/function output。
- 服务端校验上下文：可以作为 developer/instructions 的 context section，也可以作为独立 system/developer message，取决于 provider 能力。

这一步不一定马上做，但需要在文档中明确：项目里的 `SYSTEM` 抽象目前表达的是“开发者控制的规则和上下文”，不等于终端用户可控的 system prompt。

### 5. 把 prompt 修改纳入发布纪律

建议约定：

- 每个 prompt profile 有版本号。
- 修改稳定规则、工具边界或输出格式时必须更新测试。
- 关键 prompt 的 rendered snapshot 可以在测试中做局部断言，而不是全文快照。
- AI trace metadata 保留 profile、section version、token estimate、truncated sections 和 content hash。
- prompt 修改说明里写清楚影响范围、验证命令、风险和回滚方式。

### 6. 继续坚持“prompt 不是安全边界”

特别是以下能力必须由服务端保证：

- 用户身份、会话、题目、学习计划归属。
- Review tool 执行前权限确认。
- 正式 Review 写库。
- AI 配额、并发运行限制和审计。
- 结构化输出解析失败处理。
- 密钥、Authorization、隐私字段脱敏。

Prompt 可以告诉模型“不要做”，但不能替代服务端的强制校验。

## 推荐落地顺序

1. 短期：保留当前 Practice Chat 设计，为 Review tool 调用边界补充少量 eval fixtures。
2. 短期：给学习计划生成补最小行为测试，覆盖题库工具使用和禁止编造题目。
3. 中期：将 `LearningPlanDraftPromptBuilder` 迁移到 Prompt Assembly。
4. 中期：梳理 OpenAI provider 中 `system/developer/instructions` 的映射策略。
5. 长期：建立一套 prompt eval 数据集，覆盖练习聊天、学习计划、代码 Review 和错题复盘。

## 可复述的面试表达

如果面试中被问“你们项目里的提示词工程怎么做”，可以这样回答：

> 我们没有把 prompt 当成一段随手拼接的字符串，而是把它当成应用代码管理。练习聊天里，我们把提示词拆成稳定系统规则、场景策略、服务端校验上下文、摘要、历史和当前用户输入几个 section，每个 section 都有可信级别、敏感级别、缓存策略和预算策略。这样做的目的是防止用户输入覆盖系统规则，也方便测试、审计和排查模型行为。
>
> OpenAI 官方建议 developer message 通常可以按 Identity、Instructions、Examples、Context 组织，我们项目里的 Practice Chat 基本遵循这个思想，但做了更工程化的拆分。比如代码 Review tool 的触发规则写在场景策略里，但真正的权限确认和写库由服务端强制执行，不依赖模型自觉。
>
> 当前不足是学习计划和代码 Review 还有一部分是手写 `system + user` builder，后续会迁移到统一 Prompt Assembly，并补充 eval fixtures，避免 prompt 修改只靠主观体验判断。

## 后续可继续深入的方向

- OpenAI Responses API 中 `instructions`、`developer message` 和多消息输入的差异。
- Prompt caching 对 section 顺序和静态前缀设计的影响。
- Tool calling prompt 与工具 schema、权限 hook 的职责边界。
- Structured Outputs 与自然语言 prompt 的分工。
- Prompt eval 如何设计：规则断言、golden case、LLM-as-judge 和线上观测。
- 多 provider 抽象下 system/developer/tool role 的兼容策略。
