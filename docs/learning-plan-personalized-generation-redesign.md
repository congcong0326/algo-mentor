# AI 学习计划个性化生成输入与上下文重构设计

## 文档信息

- 编写日期：2026-08-03
- 文档状态：待评审
- 适用范围：AI 学习计划草案、草案修订、计划扩展
- 兼容策略：项目尚未上线，不兼容现有草案、正式计划和前端 API 数据

## 一句话结论

保留当前“表单创建、草案预览、聊天修订”的交互和现有 Agent 底座；删除承担表单摘要职责的 `goal`，改为语义明确的 `objective`、`additionalConstraints` 和结构化规划字段，同时在模型调用前注入有界的学习者画像、能力、进度和复习负载摘要。

## 一、现状与痛点

### 1.1 当前生成链路

当前 AI 学习计划草案的主要链路是：

```text
前端表单
  -> 前端 buildLearningPlanGoal 拼接多行文本
  -> LearningPlanDraftCommand
  -> Prompt 同时写入 goal 和原始结构化字段
  -> 题库 Tool
  -> 模型输出完整 LearningPlanDraftPlan
  -> 服务端覆盖部分模型字段并校验
```

前端生成的 `goal` 实际包含：

- 训练场景。
- 周期和每周投入。
- 当前水平和编程语言。
- 难度比例。
- 专题偏好。
- 用户补充想法。

这些值又会作为独立字段发送给后端，因此 `goal` 不是单一业务概念，而是本地化的表单摘要。

### 1.2 痛点一：`goal` 语义过载

当前 `goal` 同时承担三种职责：

1. 用户学习目标。
2. 整份创建表单的展示摘要。
3. Prompt 中的自由文本输入。

这会带来以下问题：

- `goal` 与 `durationWeeks`、`weeklyHours`、`level` 等字段重复，存在冲突可能。
- 中文或英文展示文案进入后端业务数据，字段内容依赖前端 locale 和文案实现。
- 用户的目标与时间限制、题目限制等约束混在同一个字符串中，无法分别校验和修改。
- 正式计划中的 `goal` 过长，更像配置清单，不适合列表检索、详情展示和 Practice Chat 上下文。
- 草案结构化输出要求模型重复返回 `goal` 和其他输入字段，但服务端最终仍以命令字段为准，增加输出复杂度而没有业务收益。

### 1.3 痛点二：已有学习数据没有进入计划生成

项目已经具备以下数据：

- 用户明确自述的目标、时间约束和学习偏好。
- 基于正式 Code Review 的能力标签、分数和样本量。
- 当前激活计划、题目进度和计划节奏。
- 复习卡到期量和复习负载。

这些数据当前分别服务画像页、能力页、计划详情和复习中心。学习计划草案与修订 Agent 仍主要依赖本次表单，无法利用已有学习事实。

### 1.4 痛点三：模型与服务端职责不清

当前模型输出包含 `intent`、`goal`、周期、水平、时间预算、语言、难度偏好、专题和 `profileSummary`。其中大部分是服务端已经掌握的输入事实，不应该由模型重新决定。

结果是：

- JSON Schema 较大。
- 模型可能回传与请求不一致的值。
- `profileSummary` 看起来像真实画像，实际只是模型基于当前表单生成的摘要。
- Mapper 需要覆盖模型字段，增加理解和测试成本。

## 二、解决目标与预期收益

| 解决目标 | 直接收益 |
| --- | --- |
| 让学习目标只表达用户希望达成的结果 | 列表、详情、聊天上下文中的目标更简洁稳定 |
| 将时间、难度、专题和补充限制保持为结构化字段 | 消除重复和冲突，便于校验、修订和统计 |
| 让模型读取已有学习画像和训练事实 | 减少重复询问，使个性化不再只依赖用户自评 |
| 只让模型输出它负责生成的内容 | 缩小 Schema，降低结构化输出失败和字段漂移 |
| 保持当前创建和修订交互 | 用户路径不变，前端改动集中且可控 |

## 三、设计范围

### 3.1 本期目标

- 重构 AI 学习计划创建请求和领域输入。
- 用 `objective` 替代当前语义过载的 `goal`。
- 将用户补充限制独立为 `additionalConstraints`。
- 将前端难度滑块产生的精确比例作为结构化字段传入后端。
- 在草案、修订和扩展运行前组装有界的个性化上下文。
- 缩小初次草案的模型结构化输出，只保留模型生成内容。
- 保持草案修订继续通过自由文本聊天完成。

### 3.2 非目标

- 不重做创建页为多步骤向导或纯聊天交互。
- 不引入 Workflow、向量数据库或新的 Agent Runtime。
- 不向学习计划 Agent 开放原始代码、完整聊天记录或任意历史查询。
- 本期不新增详细 Review、跨题证据查询 Tool。
- 不自动修改学习者画像。
- 不调整题库 Tool、负载模型、周桶、进度和模板策展逻辑。
- 不兼容或迁移现有草案、正式计划 JSON 和前端旧字段。

## 四、核心数据设计

目标链路如下：

```text
前端表单 -> LearningPlanBrief -------------------------+
                                                       |
已有学习数据 -> LearningPlanPersonalizationContext ----+-> Prompt
                                                            -> 模型生成内容
                                                            -> 服务端合并 Brief
                                                            -> 校验并保存草案
```

### 4.1 `LearningPlanBrief`

新增 `LearningPlanBrief`，作为 AI 学习计划创建与修订的唯一规划输入：

```java
public record LearningPlanBrief(
    LearningPlanIntent intent,
    String objective,
    Integer durationWeeks,
    LearningPlanLevel level,
    Integer weeklyHours,
    String programmingLanguage,
    LearningPlanDifficultyDistribution difficultyDistribution,
    Boolean interviewOriented,
    List<String> topicPreferences,
    String additionalConstraints,
    boolean personalizationEnabled,
    LearningPlanContentLocale contentLocale
) {
}
```

字段语义：

| 字段 | 语义 |
| --- | --- |
| `intent` | 计划类型，例如面试冲刺、专题突破、错题复盘 |
| `objective` | 用户希望达成的具体结果，不包含时间、水平、难度等配置摘要 |
| `durationWeeks` | 计划周期 |
| `level` | 用户本次明确选择的当前水平 |
| `weeklyHours` | 每周可投入时间 |
| `programmingLanguage` | 主要练习语言 |
| `difficultyDistribution` | Easy、Medium、Hard 的目标比例 |
| `interviewOriented` | 是否面试导向 |
| `topicPreferences` | 用户本次明确选择的专题 |
| `additionalConstraints` | deadline、训练日安排、排除要求等补充限制 |
| `personalizationEnabled` | 是否允许本次生成参考已有学习数据 |
| `contentLocale` | 计划正文语言，由服务端根据请求 locale 确定 |

API 请求未提供 `personalizationEnabled` 时默认解析为 `true`，领域对象中不保留空值。

### 4.2 `objective` 规则

`objective` 在 API 请求中允许为空，进入应用层后必须解析为非空值：

1. 用户填写时，保留用户原文并进行 trim。
2. 用户未填写时，服务端根据 `intent` 和 locale 使用固定默认目标。
3. 默认目标由代码常量维护，不调用模型生成。
4. `objective` 最大 300 字符。
5. 周期、每周投入、难度比例和专题不得再次拼入 `objective`。

示例：

```text
合适：提升 Java 后端算法面试中等题的稳定性
合适：系统掌握动态规划的状态设计和转移方法
不合适：面试冲刺，4 周，每周 6 小时，中级，Java，难度 25/55/20
```

### 4.3 `additionalConstraints` 规则

- 可为空。
- 最大 1000 字符。
- 保存用户原始约束，不由前端拼接标签前缀。
- 不覆盖任何结构化字段。
- 与结构化字段冲突时，以结构化字段为准，并在 Prompt 中明确该优先级。

### 4.4 精确难度分布

新增值对象：

```java
public record LearningPlanDifficultyDistribution(
    int easyPercent,
    int mediumPercent,
    int hardPercent
) {
}
```

校验规则：

- 每项为 `0-100`。
- 三项之和必须等于 `100`。
- 前端继续使用现有滑块，只调整提交契约。
- 模型和负载计算直接读取比例，不再依赖 `goal` 中的自然语言描述。
- 该比例表达整体选题倾向，题目总数较少时不要求逐题严格命中百分比。

### 4.5 最终计划字段

`LearningPlanDraftPlan` 做以下调整：

- `goal` 重命名为 `objective`。
- 删除 `profileSummary`。
- `difficultyPreference` 替换为 `difficultyDistribution`。
- 增加 `additionalConstraints`。
- 其他计划字段和阶段结构保持不变。

正式计划中的 `objective`、周期、水平、时间预算、语言、难度比例、专题和补充限制均来自已校验的 `LearningPlanBrief`，不能由初次生成模型修改。

## 五、个性化上下文设计

### 5.1 上下文内容

新增 `LearningPlanPersonalizationContext`，只包含规划所需的聚合摘要：

```java
public record LearningPlanPersonalizationContext(
    List<String> declaredFacts,
    List<String> generalObservations,
    List<LearningPlanAbilityTagSummary> weakTags,
    List<LearningPlanAbilityTagSummary> strongTags,
    LearningPlanActiveProgressSummary activePlan,
    LearningPlanReviewLoadSummary reviewLoad,
    Instant generatedAt
) {
}
```

数据来源：

| 内容 | 来源 |
| --- | --- |
| 用户目标、时间约束、学习偏好和自评 | 当前 ACTIVE 学习者记忆 Claim |
| 通用解题与错误模式 | 有证据的 ACTIVE 系统观察 Claim |
| 强弱标签、分数和样本量 | 现有能力画像确定性计算 |
| 当前计划进度和节奏 | 当前激活计划、PracticeProgress 和负载服务 |
| 到期与逾期复习量 | 现有复习队列聚合结果 |

### 5.2 上下文边界

- 不包含原始代码。
- 不包含完整 Review Markdown。
- 不包含完整聊天记录和用户消息正文。
- 不包含任意用户 ID 或可供模型扩大查询范围的参数。
- 画像中的自由文本按有界数据块渲染，只能作为学习参考，不能被解释为系统指令。
- 单次 Prompt 中个性化上下文预算上限为 1000 token。
- 超出预算时优先保留用户明确自述、弱项、当前计划节奏和复习负载。
- 任一数据源读取失败时忽略对应部分，不阻断计划生成。
- 每个草案、修订或扩展 run 只组装一次上下文，并在该 run 内保持不变；下一次 run 重新读取最新聚合数据。

### 5.3 优先级

模型必须遵循以下优先级：

```text
本次 LearningPlanBrief
  > 当前确定性进度与复习事实
  > 用户明确自述
  > 有证据的 AI 观察
```

例如，长期画像记录“每周可投入 5 小时”，但本次表单选择 8 小时，当前计划使用 8 小时。

### 5.4 本期不提供历史 Tool

首期只注入聚合上下文，不向学习计划 Agent 增加 `search_learner_memory`、Review 证据或原始历史 Tool。

原因：

- 计划生成主要需要强弱项、样本量、负载和约束，不需要逐条审阅证据。
- 聚合摘要已经可以验证个性化收益。
- 先避免增加 Agent step、Token 成本和权限范围。

只有后续评测证明模型必须读取具体 Review 才能稳定规划时，再单独设计受限 Tool。

## 六、模型输入与输出

### 6.1 初次草案 Prompt

Prompt 保持三部分，顺序固定：

```text
system：学习计划固定规则
system：可选的 LearningPlanPersonalizationContext，明确标记为参考数据
user：服务端校验后的 LearningPlanBrief
```

本次 Brief 始终作为最后一条 user message。个性化上下文不能覆盖本次规划输入。

### 6.2 初次草案结构化输出

新增仅包含模型负责内容的输出：

```java
public record LearningPlanGeneratedContent(
    String title,
    String summary,
    List<LearningPlanPhaseDraft> phases,
    Map<String, Object> metadata
) {
}
```

初次草案 JSON Schema 删除：

- `intent`
- `goal`
- `durationWeeks`
- `level`
- `weeklyHours`
- `programmingLanguage`
- `difficultyPreference`
- `interviewOriented`
- `topicPreferences`
- `profileSummary`

`LearningPlanDraftStructuredOutputMapper` 使用服务端 Brief 与模型生成内容组装最终 `LearningPlanDraftPlan`。

### 6.3 草案修订

草案修订继续使用当前自由文本输入。修订 Agent 接收：

- 当前 `LearningPlanBrief`。
- 当前完整草案。
- 用户修订要求。
- 本次重新生成的个性化上下文。

修订结果包含：

```text
resolvedBrief：模型根据用户明确修订要求解析后的完整 Brief
generatedContent：新的完整计划内容
```

服务端对 `resolvedBrief` 执行与创建请求相同的校验，再组装完整新草案。Prompt 明确要求：用户没有要求修改的 Brief 字段保持不变。

这使以下聊天修订继续成立：

- “把周期延长到 6 周。”
- “减少 Hard，多安排二分。”
- “目标改成准备 Java 后端面试。”
- “每周只能投入 4 小时。”

### 6.4 计划扩展

计划扩展保持“只能追加阶段”的现有语义：

- 当前正式计划的 `objective` 和 Brief 不被扩展 Agent 修改。
- 扩展 Prompt 增加最新个性化上下文。
- 用户扩展要求继续作为独立 instruction，不写回原计划 objective。

## 七、前端交互

当前创建交互保持不变：

```text
选择场景与规划参数
  -> 输入可选目标和补充限制
  -> 生成草案
  -> 预览
  -> 通过聊天修订
```

最小改动如下：

1. 在训练场景附近增加可选“具体目标”输入框。
2. 将“补充想法”改名为“其他限制”，提交为 `additionalConstraints`。
3. 增加“参考我的学习数据”复选框，默认开启。
4. 难度滑块提交精确比例对象。
5. 草案顶部只展示简洁 `objective`，不再展示整份表单拼接文本。
6. 修订输入框和聊天式调整流程保持不变。

模板创建入口不增加个性化开关，继续保持确定性生成。

## 八、后端落点

### 8.1 `mentor-application`

- 用 `LearningPlanBrief` 替换 `LearningPlanDraftCommand`。
- 新增 `LearningPlanDifficultyDistribution`。
- 新增 `LearningPlanPersonalizationContextService`。
- 定义一个聚合数据端口，由现有画像、能力、进度和复习实现提供数据。
- 调整草案、修订和扩展 Agent input。
- 调整 Prompt Builder、JSON Schema、StructuredOutputMapper 和 Validator。
- 将 Practice Chat 中的计划上下文字段从 `goal` 改为 `objective`。

### 8.2 `mentor-api`

- 更新 `LearningPlanCreateDraftRequest` 和响应 DTO。
- 组合现有 repository/service，实现个性化上下文数据端口。
- 更新 MyBatis JSON 序列化模型。
- 不新增个性化上下文数据库表。

### 8.3 持久化

- 草案命令继续保存在 `command_json`。
- 草案和正式计划继续保存在 JSONB snapshot。
- 项目未上线，直接使用新 JSON 结构，不做旧字段读取、双写和回填。
- 本地与测试环境删除旧草案和正式计划数据后重新验证。
- 模板内部 `goal` 暂不纳入本次重命名；模板转草案时映射为正式计划的 `objective`。

## 九、校验与降级

### 9.1 Brief 校验

- `intent`、周期、水平和每周投入继续必填。
- `objective` 为空时使用固定默认值。
- `additionalConstraints` 只做长度和空白规范化。
- 难度比例必须合法且总和为 100。
- 专题突破仍要求至少一个专题。

### 9.2 模型结果校验

- 阶段周数之和等于 Brief 周期。
- 每阶段题数和题库事实继续按现有规则校验。
- 计划总负载继续使用现有负载服务校验。
- 最终计划中的 Brief 字段只来自服务端已校验值。

### 9.3 个性化降级

- 用户关闭个性化时，不读取也不注入学习数据。
- 用户没有画像或 Review 时，使用空上下文正常生成。
- 任一上下文数据源异常时，只省略该部分并记录低敏指标。
- 个性化不可用不改变 API 状态和草案确认流程。

## 十、测试范围

### 10.1 后端单元测试

- `objective` 显式值和默认值解析。
- `additionalConstraints` 不再混入 objective。
- 难度比例校验。
- 初次输出 Schema 不包含服务端输入字段和 `profileSummary`。
- Mapper 使用 Brief 组装最终计划。
- 个性化开启、关闭、空数据和部分失败。
- Brief 与画像冲突时，本次 Brief 生效。
- 修订可以更新周期、时间、目标和难度，并保持未修改字段。
- 扩展不会修改正式计划 objective。

### 10.2 前端测试

- 删除 `buildLearningPlanGoal` 及其测试。
- 创建请求提交 objective、additionalConstraints、难度比例和个性化开关。
- 空 objective 仍可提交。
- 草案页只展示 objective。
- 修订聊天交互保持现有行为。

### 10.3 集成验证

- AI 草案 SSE 能使用新请求生成并保存草案。
- 确认草案后正式计划 JSON 使用新字段。
- 草案修订能更新 Brief 并生成新版本。
- Practice Chat 能读取正式计划 objective。
- 模板创建计划仍能正常生成、确认和练习。

## 十一、验收标准

1. 前端和后端不再存在 `buildLearningPlanGoal` 生成链路。
2. AI 创建 API 不再接收 `goal`，改为 `objective` 和 `additionalConstraints`。
3. 初次模型输出不再包含任何服务端已有规划字段和 `profileSummary`。
4. 最终计划 objective 为简洁目标，不包含表单摘要。
5. 个性化开启时，Prompt 包含有界的画像、能力、进度和复习摘要。
6. 个性化关闭或数据不可用时，计划仍可正常生成。
7. 不向计划 Agent 暴露原始代码、完整聊天或详细 Review Tool。
8. 创建、预览、聊天修订和确认保存的用户路径保持不变。
9. 不新增 Workflow、Agent 基础设施或个性化上下文数据库表。

## 十二、待评审决策

本设计需要确认以下三项产品决策：

1. `objective` 允许为空，并由服务端按 intent 生成固定默认目标。
2. “参考我的学习数据”默认开启，用户可以在创建页关闭。
3. 首期只使用聚合上下文，不开放详细历史和 Review Tool。
