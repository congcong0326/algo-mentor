# AI 学习者画像 Agent 接入与记忆更新技术方向

更新时间：2026-07-20

状态：技术方向已收敛，可进入实施规划

> 上游存储设计：`docs/ai-learner-profile-data-model-and-storage-design.md`
>
> 相关设计：`docs/agent-loop-lifecycle-design.md`、`docs/agent-conversation-context-recall-design.md`、`docs/practice-chat-system-prompt-assembly-design.md`、`docs/practice-code-review-technical-design.md`。

## 一、文档目的

数据建模与存储文档已经说明画像条目、版本链和 `NO_CHANGE / REPLACE` 协议。本文不重复表结构，只记录画像如何接入 Agent 运行流程、用户自述和系统观察何时更新，以及后续扩展观察源时需要保留的技术边界。

本文记录已确认的技术方向，不替代完整实施计划。Java 接口命名、DTO 字段和 JSON Schema 语法等编码细节在实施计划或编码阶段按现有项目约定收敛。

## 二、核心边界

### 2.1 画像仍是学习业务能力

`learner_profile_entry` 是基于用户自述和学习业务事实形成的 AI 主观画像，不是通用原子记忆平台。题目、Review、复习和进度等业务表仍是事实来源，画像只保存模型形成的当前结论和历史版本。

画像是 AI 主观评价，不要求客观测量意义上的绝对正确。但它不能伪造业务事实，也不能覆盖用户当前明确表达和正式业务设置。

### 2.2 Agent loop 不感知学习者画像

`AgentLoopRunner` 继续只负责模型调用、工具执行、步数控制和生命周期事件。画像读取、场景选择和 Prompt 映射放在 `mentor-application` 外层，不向 `agent-core` 下沉学习业务语义。

暂定主链路：

```text
AgentConversationService
  -> 读取本轮相关画像
  -> Prompt Assembly 生成画像片段
  -> AgentRequest
  -> AgentLoopRunner
```

画像位于 loop 外围，不通过 `AgentLoopInterceptor` 在每个 step 重复读取或改写。

## 三、画像召回与 Prompt 注入

画像在进入 Agent loop 前读取一次，同一个 run 使用同一份画像快照。即使本轮工具产生了新 Review 或更新了用户自述，也不在 loop 中途重载画像；用户自述更新结果通过 tool result 供本轮模型继续使用，完整新画像从下一次 run 开始注入。

暂定复用已有 Prompt Assembly 扩展点：

- `AgentConversationService` 根据 `userId`、场景和当前题目等上下文读取画像；
- `LearnerProfilePromptSectionProvider` 将画像映射为可选 `PromptSection`；
- 片段暂定使用 `MEMORY_SUMMARY` slot，位于当前业务上下文之后、历史消息之前；
- 片段为非必需内容，受 Prompt 预算控制，画像查询失败时 Agent 仍可继续运行；
- 片段明确声明画像是参考性 AI 判断，当前用户消息和服务端校验事实优先。

第一版不引入向量召回。召回范围由服务端按场景、dimension 和可选题目标签确定。

### 3.1 主 Agent 与正式 Review 的场景隔离

第一版先明确以下场景边界：

- `practice_chat` 主 Agent 可以按场景 Policy 读取画像，用于调整讲解方式、关注薄弱点，并判断是否需要调用画像更新工具；
- `practice_code_review` 正式评分场景禁用画像召回，`LearnerProfilePolicyResolver` 对该场景返回 disabled policy；
- 普通非题目聊天、学习计划生成/修订/扩展、主题讲解和错题复述判定等其他 AI 调用场景，第一版均不注入画像；
- `PracticeCodeReviewService` 使用独立的 `PracticeCodeReviewPromptBuilder` 和独立的 `LlmGateway.complete()` 调用，不复用主 Agent 的 `AgentRequest` 或 system prompt；
- 正式 Review prompt 不主动查询或注入学习者画像、历史能力结论，评分只依据当前代码、当前题目、受信业务上下文和明确的评分规则。

这不是新增一层 Prompt 隔离机制，而是把现有独立调用链写成显式架构约束。主 Agent 的画像片段不会自动传递给工具内部发起的 Review 模型调用，避免历史画像影响当前 Review，再由当前 Review 反向强化同一结论。

`LearnerProfilePolicyResolver` 第一版采用白名单语义：只有 `PRACTICE_CHAT` 返回 enabled policy，未知或未配置场景统一返回 disabled policy。disabled 只表示不查询 `learner_profile_entry`、不生成画像 `PromptSection`，不会关闭对应 Agent、模型调用或业务工具。

用户自述同步更新工具和异步画像消费者不受召回白名单限制。它们按更新协议显式读取或写入画像，其中异步消费者读取当前画像是为了判断 `NO_CHANGE / REPLACE`，不属于向业务 Agent 自动注入画像。

### 3.2 标签能力画像的召回范围

`PRACTICE_CHAT` 召回 `TAG_ASSESSMENT / TAG_MASTERY` 时，只查询当前题目 `problem_tag_assignment` 中受信标签对应的有效画像条目，不注入其他标签的能力评价。

当前题目没有标签、标签没有对应画像，或者标签画像查询失败时，不生成标签能力片段，练习聊天继续正常运行。召回过程不能根据自然语言临时扩展标签，也不允许模型自行选择数据库中其他标签；后续如果需要跨标签迁移提示，再单独扩展场景 Policy。

### 3.3 用户自述画像的召回范围

`PRACTICE_CHAT` 召回用户全部有效 `DECLARED_FACT`。第一版五个固定 dimension 各自最多读取一条当前 `ACTIVE` 版本：

```text
LEARNER_BACKGROUND
GOALS_AND_INTENTS
TIME_AND_RESOURCE_CONSTRAINTS
LEARNING_AND_INTERACTION_PREFERENCES
SELF_ABILITY_ASSESSMENT
```

不存在当前版本的 dimension 直接跳过；`SUPERSEDED`、`SUPPRESSED` 和历史版本不进入 Prompt。用户自述数量由固定 dimension 自然限制，第一版不再为不同练习题配置自述维度过滤规则。

### 3.4 通用观察画像的召回范围

`PRACTICE_CHAT` 第一版只召回以下两个 `GENERAL_OBSERVATION` dimension 的当前 `ACTIVE` 版本：

```text
PROBLEM_SOLVING_APPROACH
IMPLEMENTATION_AND_ERROR_PATTERN
```

这与第一版 Code Review 画像消费者允许更新的通用观察白名单保持一致。`LEARNING_INTERACTION_AND_INDEPENDENCE`、`REVIEW_AND_GROWTH_PERFORMANCE` 等其他 dimension 即使后续由新观察源生成，也不会自动进入练习 Prompt；需要使用时必须显式调整场景 Policy。

### 3.5 Prompt 预算

第一版保留 `PRACTICE_CHAT` 当前总输入预算 `8,000`，学习者画像片段单独限制为最多 `800`。两个值均作为 Spring 配置提供默认值，不写死在画像业务逻辑中。

`800` 是画像片段上限，不要求每轮填满。画像内容仍应保持简短；预算不足时按确定性规则裁剪，不额外调用模型压缩画像。第一版不根据模型 context window 动态计算预算，也不因为模型支持更长上下文而自动扩大画像片段。

当前 Prompt Assembly 使用字符数近似估算 token，因此预算值属于内部容量控制，不代表 provider 的精确 token 数。运行时至少记录总 Prompt 估算量、画像片段估算量、画像是否发生裁剪，并结合 AI 调用台账中的实际输入 token 观察偏差；后续根据真实使用数据调整默认值。

画像片段超过 `800` 预算时，三类内容按以下顺序保留：

```text
DECLARED_FACT
  > 当前题目相关 TAG_ASSESSMENT
  > GENERAL_OBSERVATION
```

用户明确自述优先于 AI 生成的主观结论；当前题目相关标签比跨题目的通用观察更贴近本轮任务。第一版不根据模型临时判断优先级。

## 四、双轨更新时机

### 4.1 用户自述：同步立即更新

用户明确表达长期事实、偏好或纠正时，由当前主 Agent 判断是否需要更新，并自主调用同步工具 `update_learner_declared_profile`。第一版不在 Agent loop 前增加独立的自述分类模型调用。

暂定时序：

```text
loop 前读取本轮画像快照
  -> Agent 读取当前用户消息
  -> 判断存在明确的长期自述或纠正
  -> 调用 update_learner_declared_profile
  -> LearnerProfileUpdateService.updateNow
  -> tool result 返回 NO_CHANGE / REPLACE 和当前内容
  -> Agent 使用 tool result 继续本轮回答
  -> 下一次 run 注入最新画像
```

工具使用批量结构化参数，一次可以更新多个用户自述维度：

```json
{
  "updates": [
    {
      "dimension": "TIME_AND_RESOURCE_CONSTRAINTS",
      "content": "工作日每天通常只能投入约 30 分钟学习。"
    }
  ]
}
```

工具只允许更新 `DECLARED_FACT` 对应的五个 dimension，不能修改 `GENERAL_OBSERVATION` 或 `TAG_ASSESSMENT`。不将短期情绪、当前题目的一次性表现和普通对话自动升格为长期自述；模型不确定是否属于长期信息时不调用工具。

复用现有 `agent_tool_start / agent_tool_end` SSE 事件展示工具状态，不向 `agent-core` 增加记忆专用事件。前端按工具名映射以下状态文案：

```text
agent_tool_start  -> 正在更新学习记忆...
agent_tool_end    -> 已更新学习记忆 / 学习记忆无需更新 / 学习记忆更新失败
```

状态提示只表示同步工具执行过程，不要求用户离开当前对话或手动刷新。

画像更新不是当前回答的核心依赖。工具内部捕获校验、数据库和画像更新异常，将其转换为正常 tool result，不向 `AgentLoopRunner` 抛出 `TOOL_EXECUTION_FAILED`：

```json
{
  "status": "FAILED",
  "message": "学习记忆暂时未能更新"
}
```

工具结果状态至少区分 `UPDATED / NO_CHANGE / FAILED`。返回 `FAILED` 时仍发布 `agent_tool_end`，前端显示“学习记忆更新失败”，主 Agent 使用该结果继续回答且不得声称记忆已保存。服务端记录异常日志和指标，但不在 tool result 中暴露数据库错误、堆栈或隐私内容；第一版不在同一个 run 内自动重试。

### 4.2 系统观察：异步批量更新

系统观察在受信业务事实提交后触发，不阻塞当前 Agent 回复，也不影响 Code Review 等主业务操作的成功状态。

用户仍然逐次提交业务事实，批处理发生在异步消费侧：消费者从数据库读取用户近期的多条相关事实，交给画像模型判断是否返回 `NO_CHANGE` 或 `REPLACE`。不要求用户批量提交，也不在 SQL 中实现自然语言模式匹配。

## 五、第一个系统观察源：Code Review

### 5.1 只消费有效正式 Review

第一版系统观察只接入 Code Review。技术语义上，`practice_code_review` 应只保存已形成有效评分和点评的正式 Review。

Agent 调用 `submit_practice_code_review` 不等于必然形成正式 Review。如果 Review 内部判断为非完整代码、非当前题目，或模型调用失败，应通过工具结果、日志、指标和 AI 调用台账记录，不写入 `practice_code_review`，也不发布正式 Review 已保存的观察事件。

在该语义下，`PracticeCodeReviewObserver.onReviewSaved` 可以稳定表示“有效正式 Review 已提交”。当前保存 rejected attempt 的实现需在画像接入前另行收敛。

### 5.2 Review 是 AI 评价事实

Code Review 不执行在线编译和判题。画像可以基于 Review 分数、扣分原因、改进建议和多版本变化形成主观结论，但不应将 `passed` 表述为 LeetCode 真实 AC，也不应声称某段代码已通过客观测试。

画像本身是 AI 主观评价，第一版不为每条结论增加独立 `confidence` 字段。

### 5.3 在消费侧聚合历史 Review

每次有效 Review 只负责发布一个新的业务事实。画像消费者决定何时调用模型，并在调用时查询用户近期多道不同题目的最新有效 Review。

暂定原则：

- Code Review 画像消费者的 `batchSize` 固定为 `5`，以 `(topic, userId)` 为批次边界；
- 每累计五条有效正式 Review 消息触发一次消费者，队列只统计消息数量，不理解题目、Review 或画像业务；
- 通用观察不把同一道题的多个版本当成多道题；
- 当前表现按每道题最新有效 Review 聚合；
- 每次模型调用最多输入最近 10 道不同题目的最新有效 Review，当前批次涉及的题目计算在这 10 道之内；
- 当前批次不足 10 道不同题目时，从更早的 Review 中补足；用户总题量不足 10 道时使用现有全部题目；
- 同题多版本变化只用于后续的成长表现判断；
- 模型输入优先使用分数维度、扣分原因、改进建议和必要上下文，不默认传入完整代码和完整 Review Markdown；
- 消费者查询和去重历史 Review、控制 10 道题窗口以及选择可更新的 dimension 都属于画像业务，队列框架不感知。

### 5.4 第一版包含标签能力画像

第一版不仅更新 `GENERAL_OBSERVATION`，也生成和更新 `TAG_ASSESSMENT / TAG_MASTERY`。标签判断属于模型的主观评价，但模型不能自由创建标签。

标签归因分为两个阶段：

1. 正式 Code Review 模型接收当前题目来自 `problem_tag_assignment` 的受信候选标签，至少包含 `tagId`、稳定 `value` 和展示名称；
2. Review 模型结合完整代码和题目上下文，从候选中返回本次解法实际影响的 `affectedTagIds`，并随正式 Review 持久化；
3. 异步画像消费者聚合最近 10 道题的 Review 及其 `affectedTagIds`，不根据题目全部标签重新猜测归因；
4. 画像模型再判断哪些已归因标签需要执行 `NO_CHANGE / REPLACE`。

Code Review 模型和画像模型都只能选择服务端提供的受信 `tagId`。服务端丢弃候选集合之外的 ID 并记录日志和指标，但不因此拒绝正式 Review。Review 阶段负责“本次代码实际涉及什么”，画像阶段负责“这些表现是否足以改变长期标签能力评价”。

`affectedTagIds` 使用规范化关联表持久化：

```sql
CREATE TABLE practice_code_review_tag (
  review_id BIGINT NOT NULL
    REFERENCES practice_code_review(id) ON DELETE CASCADE,
  tag_id BIGINT NOT NULL
    REFERENCES problem_tag(id) ON DELETE RESTRICT,
  PRIMARY KEY (review_id, tag_id)
);

CREATE INDEX idx_practice_code_review_tag_tag_review
  ON practice_code_review_tag (tag_id, review_id);
```

正式 Review 和合法标签关联必须在同一事务中保存。应用层在写入前校验每个 `tagId` 都属于当前题目的 `problem_tag_assignment`，对重复 ID 去重并丢弃非法 ID；画像消费者通过该关联表读取已归因标签，不解析 JSON 数组。

`affectedTagIds` 允许为空。模型未返回标签、只返回非法标签或标签关联写入为空时，仍保存有效正式 Review，并继续发布画像观察消息；该 Review 仍可用于 `GENERAL_OBSERVATION`，但不为 `TAG_ASSESSMENT` 提供标签证据。

画像模型使用一个批量结构化工具提交本次所有更新，概念参数如下，命名和字段细节待实施时收敛：

```json
{
  "generalObservations": [
    {
      "dimension": "IMPLEMENTATION_AND_ERROR_PATTERN",
      "action": "NO_CHANGE"
    }
  ],
  "tagAssessments": [
    {
      "tagId": 123,
      "action": "REPLACE",
      "content": "能够识别二分查找场景，但边界不变量仍不稳定。"
    }
  ]
}
```

工具一次提交多个通用观察和标签能力决策，避免为每个标签分别发起工具调用。服务端仍负责校验 `entryKind`、`dimension`、`tagId`、`action` 和正文，并按既有 `NO_CHANGE / REPLACE` 协议更新画像版本；模型不直接操作数据库。

第一版 Code Review 画像消费者只允许更新以下 `GENERAL_OBSERVATION` dimension：

```text
PROBLEM_SOLVING_APPROACH
IMPLEMENTATION_AND_ERROR_PATTERN
```

`LEARNING_INTERACTION_AND_INDEPENDENCE` 需要聊天求助、提示使用等交互事实，`REVIEW_AND_GROWTH_PERFORMANCE` 需要更明确的时间跨度、复习和迁移事实，第一版不允许 Code Review 单独更新。画像工具 Schema 和服务端校验必须使用相同白名单；`TAG_ASSESSMENT / TAG_MASTERY` 不受该通用观察白名单影响。

## 六、持久化生产者/消费者队列

系统观察已呈现明确的生产者/消费者语义：Code Review 等业务模块产生事实，指定的画像消费者异步处理。第一版只支持单播消息，一条持久化消息只交给一个明确消费者。

技术方向上确定第一版使用 PostgreSQL 实现一套通用的持久化消费队列，而不是在 `practice_code_review` 上增加全局 `consumed` 标记。出队状态属于定向队列消息，Code Review 业务事实本身不感知消息是否已出队。

### 6.1 PostgreSQL 作为第一版持久化载体

第一版不引入 Kafka、RabbitMQ 等外部消息中间件。队列使用 PostgreSQL 事务和 MVCC 可见性保证生产、消费并发安全，使正式 Code Review 与对应队列消息可以在同一数据库事务中提交。

通用性体现在消息模型、消费者注册、消费状态和单条/批量派发语义，不要求第一版支持多种队列存储 Provider 的运行时切换。

### 6.2 通用消息模型

参考 Kafka 的基础概念，队列消息至少对上层暴露以下字段，具体存储字段待后续详细设计：

```text
messageId
topic
key
value
createdAt
```

字段语义暂定如下：

- `topic` 表示消息主题，负责将消息路由到唯一消费者；
- `key` 是队列不解析的不透明业务键，也是批量消费的分组键；
- `value` 是使用项目统一 Jackson 配置序列化的 JSON 文本，PostgreSQL 使用 `TEXT` 保存，队列不查询其内部字段；
- Code Review 画像消息使用 `userId` 作为 `key`，避免将不同用户的 Review 混入同一次画像聚合。

`value` 按 Jackson 序列化后的 UTF-8 字节数限制大小，配置名暂定为：

```yaml
algo-mentor:
  queue:
    message:
      max-value-bytes: 65536
```

超过上限时发布失败，不截断内容。队列不维护 payload schema 或版本兼容协议，具体 JSON 字段演进由发布方和对应消费者共同维护。Code Review 画像消息只携带 `reviewId` 等轻量定位信息，不传完整代码或完整 Review 内容，例如：

```json
{"reviewId": 123}
```

`consumerName` 不属于消息字段，只作为消费者 Bean、日志和监控指标的内部标识。

### 6.3 单条与批量消费接口

队列同时支持单条消费和严格满批消费。消费者实现相应接口并注册为 Spring Bean，由队列框架在启动时发现、校验和调度。

概念接口如下，命名和 DTO 结构待实施时收敛：

```java
public interface QueueConsumer {

  Set<String> topics();

  void consume(QueueMessage message);
}

public interface BatchQueueConsumer {

  Set<String> topics();

  BatchConsumerPolicy policy();

  void consume(List<QueueMessage> messages);
}
```

注册和调度规则暂定如下：

- 一个 `topic` 只能映射到一个消费者 Bean；
- 同一个 `topic` 不能同时注册单条消费者和批量消费者；
- 多个 Bean 重复注册同一 `topic` 时，应用启动失败；
- 一个消费者 Bean 可以注册多个 `topic`；
- 每个运行中的队列框架实例为每个 `topic` 创建一个独立的单线程 worker；
- 同一 `topic` 下的所有 `key` 由该 worker 串行消费，不同 `topic` 的 worker 可以并行；
- 一个消费者 Bean 注册多个 `topic` 时，可能被多个 topic worker 并发调用，因此消费者实现应保持无共享可变状态或自行保证线程安全；
- 批量消费者通过 `BatchConsumerPolicy` 等配置声明批量阈值；
- 队列以 `(topic, key)` 作为批次边界，只有同一边界内的待处理消息达到阈值时，才按消息顺序选取恰好一批并派发；
- 不满一批的消息继续保留在队列中，不进行部分派发；无需聚合的场景使用单条消费者。

每个 topic worker 使用 key 轮转派发：

1. 查询当前 topic 下所有满足派发条件的 key，并按每个 key 最早的待处理消息 `id` 排序；
2. 依次为每个 key 出队并派发一批消息，批量消费者取 `batchSize` 条，单条消费者取 1 条；
3. 当前轮所有 key 派发完成后立即重新查询，继续处理仍满足条件的 key；
4. 只有一轮没有发现任何可派发 key 时，worker 才按配置等待下一次轮询。

该规则保证积压较多的 key 不会长期阻塞其他 key。每次业务回调前只将当前 key 的这一批消息更新为 `SUCCEEDED`，不提前出队后续 key 的消息。

业务消费者只接收消息或同一分组的消息列表，不感知 PostgreSQL 查询和状态流转。队列框架捕获业务消费者抛出的异常并记录日志和指标，避免异常终止 topic worker；业务处理失败不改变队列消息状态，也不触发队列重试。

### 6.4 节点级消费开关

第一版不实现多节点之间的 topic worker 自动选主。通过 Spring 配置控制当前节点是否启动消费线程，配置名暂定为：

```yaml
algo-mentor:
  queue:
    consumer:
      enabled: true
      poll-interval: 10s
```

- 配置为 `true` 时，当前节点为已注册的每个 `topic` 创建单线程 worker；
- 配置为 `false` 时，当前节点不创建任何消费 worker；
- topic worker 有可派发消息时连续轮转；没有任何可派发 key 时，默认等待 10 秒再查询；
- 消费开关不影响消息发布能力，关闭消费的节点仍可向 PostgreSQL 队列发布消息；
- 多节点部署时，通过部署配置手动保证只有一个节点开启消费；
- 切换消费节点时，必须先停止旧节点消费，再开启新节点；
- 第一版不提供分布式 topic 所有权、自动选主和自动故障转移。

消费节点停机期间，新消息仍能正常入库并等待后续消费。

### 6.5 最多一次派发与状态流转

第一版采用最多一次派发语义，状态链路为：

```text
PENDING -> SUCCEEDED
```

worker 在一个短事务中查询已提交且满足派发条件的 `PENDING` 消息，并按本次选定的 `messageId` 将单条消息或整批消息更新为 `SUCCEEDED`。事务提交成功后，才在事务外调用业务消费者；数据库事务失败时不派发消息。

`SUCCEEDED` 只表示消息已经成功出队，不表示业务消费者执行成功。如果节点在事务提交后、调用业务消费者前退出，或者业务消费者执行失败，消息不会重新派发。第一版明确接受该丢失窗口，不提供至少一次或 exactly-once 保证。

生产者和消费者并发时遵循以下规则：

- 生产者在独立事务中插入 `PENDING` 消息；
- 消费者只读取已经提交的消息；
- 出队事务执行期间新提交的消息不加入当前已选定的单条或批次，留到后续轮询处理；
- 出队事务只按本次选定的 `messageId` 更新状态；
- 唯一消费节点和每个 `topic` 的单线程 worker 共同保证同一消息不会被两个消费者同时选中。

第一版不增加 `PROCESSING`、租约或消费所有权字段，也不要求业务消费者为队列重复派发实现幂等保护。

出队成功的消息不立即物理删除，而是保留为 `SUCCEEDED`，便于短期排查和确认队列处理结果。队列框架通过定时清理任务删除超过保留期的 `SUCCEEDED` 数据，业务消费者不参与清理。

清理策略默认值和配置名暂定为：

```yaml
algo-mentor:
  queue:
    cleanup:
      succeeded-retention: 7d
      fixed-delay: 1h
      batch-size: 1000
```

清理任务只在 `algo-mentor.queue.consumer.enabled=true` 的节点运行，使用固定延迟调度。每次执行一个短事务，按 `succeeded_at, id` 顺序最多删除 1,000 条超过 7 天的 `SUCCEEDED` 数据，不在单次调度中循环清空积压。

业务消费者失败后的处理属于业务自身职责，第一版队列不提供自动重试、退避、死信或人工回放，也不允许将 `SUCCEEDED` 重置为 `PENDING`。确需补偿时，由业务重新发布一条具有新 `messageId` 的消息。

### 6.6 第一版只支持单播

每个 `topic` 在应用启动时绑定唯一消费者 Bean，因此每条消息只会被一个消费者处理。队列不实现广播、Consumer Group 或“同一消息为每个消费者维护独立投递状态”的能力。

因此第一版不为广播拆分通用消息表和消费投递表。如果未来需要让同一业务事实被多个异步消费者处理，再单独讨论显式发布到多个 `topic` 或升级为广播投递模型。

### 6.7 最小队列表与索引

第一版只使用一张 `queue_message` 表。`id` 同时作为对外的 `messageId` 和 topic 内的稳定消息顺序，不单独保存业务发生时间；`created_at` 表示消息入队时间。

表结构草案如下：

```sql
CREATE TABLE queue_message (
  id BIGSERIAL PRIMARY KEY,
  topic VARCHAR(128) NOT NULL,
  message_key VARCHAR(256) NOT NULL,
  message_value TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  succeeded_at TIMESTAMPTZ NULL,
  CONSTRAINT ck_queue_message_topic CHECK (BTRIM(topic) <> ''),
  CONSTRAINT ck_queue_message_key CHECK (BTRIM(message_key) <> ''),
  CONSTRAINT ck_queue_message_status CHECK (
    status IN ('PENDING', 'SUCCEEDED')
  ),
  CONSTRAINT ck_queue_message_succeeded_at CHECK (
    (status = 'PENDING' AND succeeded_at IS NULL)
    OR (status = 'SUCCEEDED' AND succeeded_at IS NOT NULL)
  )
);
```

`message_value` 的大小由发布接口按配置校验，不在数据库增加固定长度约束。第一版不增加 `updated_at`、`PROCESSING`、重试次数、租约、消费者名称、业务结果或 payload 版本字段。

只建立三类实际查询所需的局部索引：

```sql
-- 单条消费者按 topic 和入队顺序读取。
CREATE INDEX idx_queue_message_pending_topic
  ON queue_message (topic, id)
  WHERE status = 'PENDING';

-- 批量消费者按 topic + key 判断满批并按入队顺序读取。
CREATE INDEX idx_queue_message_pending_topic_key
  ON queue_message (topic, message_key, id)
  WHERE status = 'PENDING';

-- 定时任务按成功时间分批清理。
CREATE INDEX idx_queue_message_succeeded_cleanup
  ON queue_message (succeeded_at, id)
  WHERE status = 'SUCCEEDED';
```

消息出队时在同一短事务内选定 `id` 并更新对应记录的 `status='SUCCEEDED'`、`succeeded_at=NOW()`。由于第一版只允许一个消费节点且每个 topic 只有一个线程，不增加多消费者抢占索引或锁字段。

后续队列设计至少需要解决：

- 业务事实事务提交后的可靠发布；
- 每条单播消息的消费状态和进度；
- 按用户和画像目标批量聚合多条事实；
- 消费者的启用和停用；
- 业务消费者失败后的日志和指标；
- 画像模型返回 `NO_CHANGE` 或调用失败时，不回写或回滚队列状态。

队列状态只保存在 `queue_message`，不在业务事实表中引入临时消费字段。

## 七、暂定设计模式

### 7.1 Policy + Resolver

画像是否在某个场景启用、召回哪些 entry kind/dimension、可使用多少预算，由轻量场景 Policy 表达，由 Resolver 根据场景选择。屏蔽某场景的画像时返回 disabled policy，不需要修改 Agent loop。

### 7.2 Observer + Composite

Code Review 保存后的应用内扩展仍可使用 Observer + Composite，让错题等现有同步适配器和画像事件发布适配器相互隔离。画像 observer 只向 PostgreSQL 队列的目标 `topic` 发布一条单播消息，并以 `userId` 作为 `key`；应用内存在多个 observer 不等于持久化队列支持广播。

### 7.3 Strategy + Registry 按需引入

只有 Code Review 一个系统观察源时，不提前建设通用策略注册中心。当 Recall Evaluation 等第二个观察源实际接入时，再抽取不同观察源的 Strategy 和 Registry。

### 7.4 不引入 MemoryFactory

当前问题的核心是场景策略、业务事实通知和异步消费，不是动态创建不同类型的记忆引擎。Bean 创建和装配由 Spring DI 承担，第一版不新增 `MemoryFactory`、运行时插件发现和多存储 Provider 切换。

## 八、暂定模块职责

```text
agent-core
  保持不变，只消费组装好的 AgentRequest

mentor-application
  LearnerProfileQueryService
  LearnerProfileUpdateService
  LearnerProfilePolicyResolver
  LearnerProfilePromptSectionProvider
  Code Review 画像观察适配器

mentor-api
  Spring Bean 装配
  PostgreSQL/MyBatis 画像存储适配
  配置绑定与场景开关

持久化消费队列
  第一版使用 PostgreSQL
  topic 在启动时绑定唯一消费者 Bean
  每个 topic 使用一个单线程 worker，同 topic 按 key 轮转、不同 topic 并行
  节点级配置控制消费开关和 10 秒轮询间隔，关闭消费的节点仍可发布消息
  封装消息持久化、单条/满批派发和出队状态
  对业务暴露单条与批量消费接口
  默认每小时清理最多 1000 条超过 7 天的 SUCCEEDED 数据
```

## 九、实施阶段收敛项

当前业务需求、数据边界、Agent 接入方式、同步与异步更新时机、Code Review 观察流程、标签归因和 PostgreSQL 队列语义已经足以支持第一版落地。以下内容不再作为设计讨论的前置阻塞项，在实施计划或编码阶段按现有项目约定确定：

1. Java 接口、DTO、配置项和 JSON Schema 的最终命名；
2. Flyway 迁移版本号和 MyBatis SQL 组织方式；
3. 画像模型的具体型号、超时以及 AI governance purpose/source 等参数；
4. 指标名称、日志字段和测试类的最终命名。

如果实施过程中发现现有代码与本文边界冲突，再针对具体冲突补充设计，不继续进行无实现反馈的穷举式预设计。

## 十、第一版已确认结论

```text
画像读取：loop 前、application 层、每个 run 一次快照
召回白名单：第一版只有 PRACTICE_CHAT 注入画像，其他 AI 调用场景默认 disabled
标签召回：只读取当前题目受信标签对应的有效 TAG_ASSESSMENT
用户自述召回：五个固定 DECLARED_FACT dimension 全部读取当前 ACTIVE 版本
通用观察召回：只读取 PROBLEM_SOLVING_APPROACH 和 IMPLEMENTATION_AND_ERROR_PATTERN
Prompt 预算：PRACTICE_CHAT 总预算默认 8000，画像片段上限默认 800，均可配置
画像裁剪优先级：DECLARED_FACT > 当前题目 TAG_ASSESSMENT > GENERAL_OBSERVATION
用户自述：主 Agent 自主调用同步工具更新，并通过 tool 生命周期事件展示状态
自述更新失败：返回 FAILED tool result，不终止当前 Agent run
系统观察：异步批量更新
首个观察源：有效正式 Code Review
Code Review 触发：同一用户每 5 条正式 Review 消息触发一次消费者，模型窗口最多 10 道不同题目
画像产物：第一版同时包含 GENERAL_OBSERVATION 和 TAG_ASSESSMENT
标签归因：Code Review 模型从受信候选中确定 affectedTagIds，画像模型负责聚合更新
Code Review 通用观察：只更新 PROBLEM_SOLVING_APPROACH 和 IMPLEMENTATION_AND_ERROR_PATTERN
扩展方式：Policy/Resolver + Observer/Composite
异步基础：PostgreSQL 单播持久化队列，topic 唯一路由，每个 topic 单线程消费，JSON/TEXT value 默认上限 64 KiB，最多一次派发，SUCCEEDED 默认保留 7 天
明确不做：通用 MemoryFactory、向量召回、运行时插件平台
```
