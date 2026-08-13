# AI 学习者画像第一版研发计划任务清单

更新时间：2026-07-20

状态：任务拆分完成，待逐项编写研发实施计划

依据：

- `docs/ai-learner-profile-data-model-and-storage-design.md`
- `docs/ai-learner-profile-agent-integration-technical-direction.md`

关联前置：

- `docs/problem-tag-modeling-spec.md`
- `docs/problem-tag-modeling-implementation-plan.md`
- `docs/practice-code-review-technical-design.md`
- `docs/practice-chat-system-prompt-assembly-design.md`

## 一、文档目的

本文不直接给出编码步骤，而是把 AI 学习者画像第一版拆成可分别编写研发实施计划、独立评审和独立验收的任务单。

后续原则上每个任务单对应一份实施计划。实施计划需要继续细化目标代码边界、数据库迁移、接口契约、测试用例、配置、指标、发布顺序和回滚方式，不能把本文直接当成编码清单。

第一版最终闭环包括：

```text
受信业务事实
  -> 用户自述同步更新 / Code Review 异步观察
  -> learner_profile_entry 版本更新
  -> PRACTICE_CHAT 按场景召回
  -> Prompt Assembly 注入本轮画像快照
```

## 二、拆分原则

任务按以下标准拆分：

1. 每个任务有单一主要交付目标，可以单独形成实施计划和验收结论；
2. 通用基础设施与画像业务分离，持久化队列不感知 Code Review 和画像正文；
3. 画像存储内核与 Agent 接入分离，先稳定版本更新协议，再接同步工具和异步消费者；
4. Code Review 事实生产、标签归因和画像消费分离，避免一次计划同时改评分、队列、画像模型和 Prompt；
5. 前端只承担同步工具状态反馈，不把第一版扩展为画像管理产品；
6. 已由题目标签建模计划覆盖的迁移、seed 双写和读取切换不重复拆任务。

## 三、当前实现基线

截至 2026-07-20，当前仓库与目标设计之间的关键差异如下：

| 范围 | 当前状态 | 对应任务 |
| --- | --- | --- |
| 题目标签 | `V33`、`problem_tag`、`problem_tag_assignment` 和规范化读取代码已存在 | 外部前置门禁，不重复实施 |
| 正式 Review 语义 | `PracticeCodeReviewService` 会把不可 Review 或 LLM 失败保存为零分记录，并触发 `onReviewSaved` | `LP-01` |
| Review 标签归因 | Review Schema、领域模型和持久化均无 `affectedTagIds` / `practice_code_review_tag` | `LP-02` |
| 画像存储 | 尚无 `learner_profile_entry`、画像 repository 和版本服务 | `LP-03`、`LP-04` |
| 持久化队列 | 尚无 `queue_message`、发布接口、消费者注册和 worker | `LP-05` 至 `LP-07` |
| Review 事实发布 | Observer 已存在，但当前在 repository 事务返回后调用并吞掉异常，无法保证 Review 与消息原子提交 | `LP-08` |
| 系统观察 | 尚无按用户满 5 条消息触发、最多聚合 10 道题的画像消费者 | `LP-09` |
| 用户自述 | 尚无 `update_learner_declared_profile` 工具 | `LP-10` |
| Prompt 召回 | Prompt Assembly 已存在，`PRACTICE_CHAT` 总预算当前为 8,000，但尚无画像 Policy、查询和 800 预算片段 | `LP-11` |
| 前端反馈 | 练习工作台已消费通用 tool 生命周期事件，但只对 Review 工具有专用状态映射 | `LP-12` |
| 联调发布 | 尚无画像闭环配置、数据库级集成测试和发布门禁 | `LP-13` |

### 3.1 外部前置门禁：题目标签建模

题目标签规范化不再为画像单独编写实施计划。开始 `LP-02`、`LP-03` 或 `LP-11` 前，需要确认：

- `problem_tag` 和 `problem_tag_assignment` 已完成目标环境迁移与数据校验；
- 题目读取已经以规范化关系为权威来源；
- 可以按题目稳定读取 `tagId`、`value` 和展示名称；
- 旧数组的保留或删除继续服从题目标签建模计划，不由画像任务改变。

如果上述门禁未完成，应先继续执行 `docs/problem-tag-modeling-implementation-plan.md`，不得在画像代码中增加数组解析或临时字符串标签身份。

## 四、任务总览

建议后续实施计划统一放在：

```text
docs/ai-learner-profile-implementation-plans/
```

| ID | 任务 | 主要交付 | 直接依赖 | 可并行 |
| --- | --- | --- | --- | --- |
| `LP-01` | 正式 Code Review 事实语义收敛 | 非完整、非当前题和失败尝试不再写正式 Review | 无 | 可与 `LP-03`、`LP-05` 并行 |
| `LP-02` | Review 受信标签归因与关联持久化 | `affectedTagIds` 受信候选、校验和 `practice_code_review_tag` | 标签门禁、`LP-01` | 可与画像/队列基础并行 |
| `LP-03` | 画像领域契约与 PostgreSQL 存储 | 画像枚举、领域模型、迁移、Mapper/Repository | 标签门禁 | 可与 `LP-01`、`LP-05` 并行 |
| `LP-04` | 画像查询、版本更新与并发一致性 | `NO_CHANGE / REPLACE`、查询、版本切换、冲突处理 | `LP-03` | 可与队列链路并行 |
| `LP-05` | 持久化队列契约、表结构与发布能力 | 通用消息模型、`queue_message`、Publisher | 无 | 可与 Review/画像存储并行 |
| `LP-06` | 队列消费者注册与单条/满批派发 | topic 唯一路由、key 轮转、成功确认的至少一次出队 | `LP-05` | 可与 `LP-04` 并行 |
| `LP-07` | 队列节点运行、清理与可观测性 | worker 生命周期、开关、轮询、清理、指标 | `LP-06` | 可与画像 Agent 接入准备并行 |
| `LP-08` | Code Review 画像消息原子发布 | 正式 Review、标签关联和队列消息同事务提交 | `LP-01`、`LP-02`、`LP-05` | 可与 `LP-04`、`LP-06` 并行 |
| `LP-09` | Code Review 画像批量消费者 | 五条触发、十题窗口、画像模型批量更新 | `LP-02`、`LP-04`、`LP-06`、`LP-07`、`LP-08` | 与同步工具/召回可并行开发 |
| `LP-10` | 用户自述同步更新 Agent 工具 | `update_learner_declared_profile` 和失败降级 | `LP-04` | 可与 `LP-09`、`LP-11` 并行 |
| `LP-11` | `PRACTICE_CHAT` 画像召回与 Prompt 注入 | 场景 Policy、画像快照、800 预算和确定性裁剪 | 标签门禁、`LP-04` | 可与 `LP-09`、`LP-10` 并行 |
| `LP-12` | 前端画像工具状态反馈 | 更新中、已更新、无需更新、失败状态 | `LP-10` | 可在工具契约稳定后独立实施 |
| `LP-13` | 画像闭环联调、配置与发布门禁 | PostgreSQL IT、端到端验证、灰度与回滚清单 | `LP-07` 至 `LP-12` | 最终收口任务 |

## 五、依赖与实施波次

### 5.1 依赖主链

```text
题目标签门禁
  -> LP-02 Review 标签归因
  -> LP-08 Review 消息原子发布
  -> LP-09 异步画像消费者

LP-03 画像存储
  -> LP-04 画像查询与版本更新
     -> LP-09 异步画像消费者
     -> LP-10 用户自述同步工具
     -> LP-11 练习聊天画像召回

LP-05 队列表与发布
  -> LP-06 消费者注册与派发
  -> LP-07 worker、清理和观测
  -> LP-09 异步画像消费者

LP-01 正式 Review 语义
  -> LP-02 Review 标签归因
  -> LP-08 Review 消息原子发布

LP-10
  -> LP-12 前端工具状态

LP-07 + LP-08 + LP-09 + LP-10 + LP-11 + LP-12
  -> LP-13 闭环联调与发布
```

### 5.2 推荐波次

| 波次 | 可同时编写/实施的计划 | 阶段出口 |
| --- | --- | --- |
| A | `LP-01`、`LP-03`、`LP-05` | 三条基础链路的边界稳定 |
| B | `LP-02`、`LP-04`、`LP-06` | Review 标签、画像内核、队列派发可独立测试 |
| C | `LP-07`、`LP-08`、`LP-10`、`LP-11` | 队列可运行，两个更新入口和读取入口具备接线条件 |
| D | `LP-09`、`LP-12` | 异步系统观察和同步用户反馈闭环完成 |
| E | `LP-13` | 第一版整体达到发布门禁 |

波次只表达依赖关系，不要求所有任务合并为同一 PR。涉及共享 Flyway 版本空间的计划必须在实际实施前重新分配唯一版本号。

## 六、任务单

### `LP-01`：正式 Code Review 事实语义收敛

建议计划文件：`docs/ai-learner-profile-implementation-plans/LP-01-formal-code-review-semantics.md`

**目标**

让 `practice_code_review` 只保存已经形成有效评分和点评的正式 Review，使 `PracticeCodeReviewObserver.onReviewSaved` 稳定代表可供画像消费的业务事实。

**计划应覆盖**

- 删除或替换当前 `saveRejectedAttempt` 写零分正式 Review 的行为；
- 明确 `NOT_COMPLETE_SUBMISSION`、`NOT_CODE_LIKE`、结构化输出失败和 LLM 失败的返回契约；
- 检查完成 gate、Review 历史、错题观察者、统计指标和 tool result 是否依赖零分尝试记录；
- 保留日志、低基数指标和 AI 调用台账作为失败尝试诊断入口；
- 明确 replay/idempotency 在没有正式 Review 时的行为。

**主要代码落点**

- `PracticeCodeReviewService`
- `PracticeReviewResult` / `PracticeReviewStatus`
- `PracticeCodeReviewRepository` 及测试替身
- Review 工具结果映射、完成 gate、Review 历史和相关测试

**验收出口**

- 只有有效正式 Review 执行 repository save 和 `onReviewSaved`；
- 失败或不可 Review 不新增 `practice_code_review` 行；
- 当前 Agent run 仍能得到明确的失败/不可 Review 结果并继续回答；
- 现有完成门槛和 Review 历史不把失败尝试识别为正式版本。

**不包含**

- 不增加 `affectedTagIds`；
- 不发布画像队列消息；
- 不修改画像表和 Prompt。

### `LP-02`：Review 受信标签归因与关联持久化

建议计划文件：`docs/ai-learner-profile-implementation-plans/LP-02-review-affected-tags.md`

**目标**

让正式 Review 模型只能从当前题目的规范化受信标签中选择本次解法实际影响的标签，并与正式 Review 原子保存。

**计划应覆盖**

- 为 Review 上下文补充 `tagId`、稳定 `value` 和展示名称，不再只传字符串 label；
- 扩展 Review prompt、JSON Schema、结构化输出 mapper 和领域模型以承载 `affectedTagIds`；
- 新增 `practice_code_review_tag` 迁移、Mapper、Repository 写入和历史查询；
- 服务端按当前题目的 `problem_tag_assignment` 校验、去重并丢弃非法 ID；
- `affectedTagIds` 为空时仍允许保存正式 Review；
- 为非法 ID、重复 ID、空关联增加日志和低基数指标。

**关键事务边界**

正式 Review 主表与合法标签关联必须在同一事务中提交。画像消费者只能读取 `practice_code_review_tag`，不得解析 Review JSON 或把题目全部标签当成本次归因。

**验收出口**

- 模型无法创建自由文本标签或选择题目候选集合外的标签；
- 非法标签不导致有效 Review 保存失败；
- 正式 Review 与合法关联不会出现部分提交；
- 可以按 Review 和按标签双向查询归因结果。

**不包含**

- 不更新 `learner_profile_entry`；
- 不实现异步消费者；
- 不改变能力雷达“一题贡献全部题目标签”的既有语义。

### `LP-03`：画像领域契约与 PostgreSQL 存储

建议计划文件：`docs/ai-learner-profile-implementation-plans/LP-03-profile-domain-and-storage.md`

**目标**

落地 `learner_profile_entry` 统一版本表和画像领域契约，为后续查询、同步工具和异步消费者提供稳定存储边界。

**计划应覆盖**

- `entry_kind`、`dimension`、`status`、`origin_type`、更新 action 等强类型枚举；
- `DECLARED_FACT`、`GENERAL_OBSERVATION`、`TAG_ASSESSMENT` 的合法 dimension/tag 映射；
- `learner_profile_entry` Flyway 迁移、约束、外键和局部唯一索引；
- MyBatis Mapper、行模型、Repository 端口和 PostgreSQL 适配；
- 按业务身份读取当前 `ACTIVE`、历史版本和锁定当前版本的基础 SQL；
- 内容非空、长度和敏感信息校验所需的配置入口或领域端口。

**模块边界**

画像领域模型和 Repository 端口属于 `mentor-application` 学习业务；PostgreSQL/MyBatis 实现和迁移由 API 持久化侧承载。不得把画像语义下沉到 `agent-core`。

**验收出口**

- 数据库硬约束能够拒绝非法 kind/dimension/tag/status 组合；
- 同一业务身份数据库层最多存在一条 `ACTIVE`；
- 历史 revision 唯一，`supersedes_entry_id` 不会被多个新版本复用；
- Repository 能覆盖 dimension 身份和 tag 身份两类查询。

**不包含**

- 不调用模型；
- 不实现 `NO_CHANGE / REPLACE` 编排；
- 不实现 API、Agent tool 或 Prompt 注入。

### `LP-04`：画像查询、版本更新与并发一致性

建议计划文件：`docs/ai-learner-profile-implementation-plans/LP-04-profile-query-and-update-services.md`

**目标**

实现可被同步工具、异步消费者和 Prompt 召回复用的 `LearnerProfileQueryService` 与 `LearnerProfileUpdateService`。

**计划应覆盖**

- `ProfileUpdateDecision` 的 `NO_CHANGE / REPLACE` 契约和完整正文校验；
- 首次创建、旧版本 `SUPERSEDED`、新版本 `ACTIVE` 和 revision 递增；
- `SUPPRESSED`、物理删除和用户纠正所需的内部服务边界，但不要求第一版公开管理 API；
- 模型结果产生后重新读取/锁定当前版本，检测陈旧结果并基于最新正文重新处理；
- 按用户、kind、dimension 和 tag 批量读取当前画像；
- `origin_type`、provider、model、prompt version 等追踪字段写入；
- 更新结果、校验失败、并发冲突和版本切换指标。

**并发原则**

版本切换必须是单事务操作。计划必须选择并验证明确的并发策略，例如行锁、业务身份 advisory lock 或可证明正确的重试机制，不能只依赖先查后写和唯一索引报错。

**验收出口**

- `NO_CHANGE` 不写数据库；
- `REPLACE` 保留旧正文并形成连续版本链；
- 并发更新不会产生双 `ACTIVE`、revision 冲突或静默覆盖；
- 查询服务只返回目标范围内的当前 `ACTIVE`，默认排除历史和 `SUPPRESSED`。

**不包含**

- 不决定某个业务场景该召回哪些画像；
- 不实现具体画像 prompt 和 LLM 调用；
- 不实现队列。

### `LP-05`：持久化队列契约、表结构与发布能力

建议计划文件：`docs/ai-learner-profile-implementation-plans/LP-05-persistent-queue-storage-and-publisher.md`

**目标**

落地与画像业务无关的 PostgreSQL 单播持久化队列最小内核：消息契约、表结构、Repository 和发布接口。

**计划应先收敛**

- 队列是否建立独立 Maven 模块；推荐按通用基础设施独立于 `mentor-application`，最终名称由实施计划确定；
- 业务发布方依赖的端口位置，以及 PostgreSQL/MyBatis 适配的装配位置；
- topic、配置 key、状态值和 metadata key 的公共常量归属。

**计划应覆盖**

- `QueueMessage`、Publisher、Repository、状态枚举和批量策略等基础契约；
- `queue_message` 迁移和三类局部索引；
- topic/key 非空、Jackson JSON/TEXT value 和 UTF-8 64 KiB 默认上限；
- 发布操作可加入调用方现有 Spring 事务，关闭消费时仍可正常发布；
- 按 ID、topic、key 查询待处理消息所需的持久化能力；
- Publisher 成功、校验失败和数据库失败的指标与安全日志。

**验收出口**

- Publisher 不解析业务 payload，只负责序列化、大小校验和入库；
- 超限消息拒绝发布且不截断；
- 新消息只以 `PENDING` 入库，`succeeded_at` 为空；
- 调用方可以在同一数据库事务中保存业务事实和消息。

**不包含**

- 不启动线程；
- 不注册消费者；
- 不实现重试、死信、广播、租约或多 Provider 切换。

### `LP-06`：队列消费者注册与单条/满批派发

建议计划文件：`docs/ai-learner-profile-implementation-plans/LP-06-persistent-queue-dispatch.md`

**目标**

实现消费者 Bean 注册校验、单条消费、严格满批消费、按 key 公平轮转和成功确认的至少一次出队语义。

**计划应覆盖**

- `QueueConsumer`、`BatchQueueConsumer` 和 `BatchConsumerPolicy` 最终接口；
- topic 到唯一消费者 Bean 的启动期注册与冲突校验；
- 同一 topic 禁止同时注册单条和批量消费者；
- 以 `(topic, key)` 为批次边界，未满批消息保持 `PENDING`；
- 按每个 key 最早消息 ID 排序轮转，单次只派发当前 key 的一条或一批；
- 在短事务内先把选定消息更新为 `SUCCEEDED`，提交后在事务外调用业务消费者；
- 消费异常只记录日志和指标，不回退队列状态、不终止 dispatcher。

**关键语义**

`SUCCEEDED` 表示成功出队，不表示业务处理成功。第一版接受数据库提交后到回调前的丢失窗口，不得在实施中悄然改成至少一次语义。

**验收出口**

- 重复 topic 注册使应用启动失败；
- 批量消费者只收到同 topic、同 key、恰好 `batchSize` 条有序消息；
- 积压 key 不会长期阻塞其他满足条件的 key；
- 业务异常后消息保持 `SUCCEEDED`，后续 key 仍能继续派发。

**不包含**

- 不实现 worker 启停和定时轮询；
- 不实现清理任务；
- 不实现画像消费者。

### `LP-07`：队列节点运行、清理与可观测性

建议计划文件：`docs/ai-learner-profile-implementation-plans/LP-07-persistent-queue-runtime.md`

**目标**

把 dispatcher 装配为可由配置控制的 topic worker，并补齐成功消息清理、优雅停止和运行观测。

**计划应覆盖**

- `algo-mentor.queue.consumer.enabled` 和默认 10 秒轮询间隔；
- 每 topic 一个单线程 worker，不同 topic 可并行；
- 有可派发 key 时连续轮转，空轮次后再等待；
- 节点关闭时停止取新批次并有界等待当前回调；
- 默认每小时删除最多 1,000 条超过 7 天的 `SUCCEEDED`；
- 清理任务只在消费节点运行，单次短事务且不循环清空积压；
- worker 活跃状态、PENDING 数、出队数、消费异常、清理数和耗时指标；
- 多节点部署只允许人工保证单消费节点的配置与运维说明。

**验收出口**

- `enabled=false` 不创建 worker 和清理任务，但 Publisher 仍可用；
- 每个 topic 的消费线程符合串行约束；
- worker 异常不会永久退出且不会高频空轮询；
- 清理只删除满足保留期的 `SUCCEEDED`，不触碰 `PENDING`。

**不包含**

- 不实现自动选主、分布式锁、故障转移或人工回放 API；
- 不增加 `PROCESSING`、重试次数或消费者所有权字段。

### `LP-08`：Code Review 画像消息原子发布

建议计划文件：`docs/ai-learner-profile-implementation-plans/LP-08-code-review-profile-event-publishing.md`

**目标**

在有效正式 Review 提交后，向唯一画像 topic 发布轻量消息，并保证 Review 主表、标签关联和队列消息原子提交。

**计划应覆盖**

- 固定 topic、payload 字段和 `userId` key 的常量契约；
- payload 第一版只携带 `reviewId` 等定位信息，不携带完整代码和 Review Markdown；
- Review 保存事务如何同时包含主表、`practice_code_review_tag` 和 Queue Publisher；
- Observer + Composite 中画像发布适配器与错题等既有观察者的隔离方式；
- replay 或幂等命中已有 Review 时不得重复发布消息；
- 发布失败时回滚正式 Review 保存，并向当前调用链返回可诊断但不泄露内部信息的失败结果；
- 正式 Review 成功、消息发布成功/失败和 payload 大小指标。

**当前代码冲突必须解决**

当前 `PracticeCodeReviewService` 在 `repository.save` 返回后才调用 Observer，并捕获 Observer 异常。这一边界不能满足原子提交。实施计划必须明确移动 Spring 事务边界，不能只给现有 Observer 增加 `@Transactional` 或继续吞掉画像发布异常。

**验收出口**

- Review、合法标签关联和画像消息要么全部提交，要么全部回滚；
- 每个新正式 Review 恰好发布一条目标 topic 消息；
- 不可 Review、失败尝试和 replay 不产生新消息；
- 队列关闭消费不影响 Review 与消息入库。

**不包含**

- 不在 Review 请求内调用画像模型；
- 不等待画像更新完成；
- 不实现批量聚合。

### `LP-09`：Code Review 画像批量消费者

建议计划文件：`docs/ai-learner-profile-implementation-plans/LP-09-code-review-profile-consumer.md`

**目标**

实现第一版系统观察源：同一用户每累计 5 条有效正式 Review 消息，异步聚合近期事实并批量更新通用观察和标签能力画像。

**计划应覆盖**

- 注册唯一 Code Review 画像 topic 的 `BatchQueueConsumer`，`batchSize=5`；
- 校验一批消息属于同一 `userId` key，并安全解析 `reviewId`；
- 从当前批次题目出发，查询用户最近最多 10 道不同题目的最新有效 Review；
- 同题只使用最新有效 Review 表现，多版本信息仅作为后续成长维度事实；
- 读取分数维度、扣分原因、改进建议和 `affectedTagIds`，默认不传完整代码或完整 Markdown；
- 只允许更新 `PROBLEM_SOLVING_APPROACH`、`IMPLEMENTATION_AND_ERROR_PATTERN` 和已归因标签的 `TAG_MASTERY`；
- 使用一个批量结构化提交契约返回所有 `NO_CHANGE / REPLACE` 决策；
- 服务端重新校验 kind、dimension、tagId、action 和正文，再调用画像更新服务；
- 接入 AI governance 的 purpose/source、模型、超时和调用台账；
- 消费失败、模型失败、非法输出和部分无变化的日志与指标。

**关键边界**

- 队列只按消息数量判断满批，不理解 Review 或画像；
- Review 模型负责“本次解法涉及哪些标签”，画像模型负责“长期评价是否变化”；
- 画像模型失败由队列回写 `PENDING` 并按有限退避重试，达到上限转 `FAILED`、告警并停止 topic；
- Code Review 消费者不得更新另外两个通用观察 dimension。

**验收出口**

- 4 条消息不会触发，5 条同用户消息恰好触发一次；
- 不同用户不会进入同一批画像调用；
- 题目窗口按不同题目去重且不超过 10 道；
- `NO_CHANGE` 不写版本，`REPLACE` 形成合法版本链；
- 空或非法 `affectedTagIds` 不阻断通用观察更新。

**不包含**

- 不接入错题复述、学习计划进度和聊天求助等第二观察源；
- 不引入 Strategy Registry；
- 不实现补偿重放后台。

### `LP-10`：用户自述同步更新 Agent 工具

建议计划文件：`docs/ai-learner-profile-implementation-plans/LP-10-declared-profile-agent-tool.md`

**目标**

提供 `update_learner_declared_profile` 批量结构化工具，让 `PRACTICE_CHAT` 主 Agent 在识别到明确长期自述或纠正时同步更新画像。

**计划应覆盖**

- 工具名、参数名、结果字段和状态值的公共常量；
- 一次更新多个 `DECLARED_FACT` dimension 的 JSON Schema 和服务端校验；
- 只允许五个自述维度，禁止修改通用观察和标签评价；
- 区分首次自述、普通补充和用户纠正的 `origin_type`；
- 调用画像更新模型前读取当前正文，并使用 `NO_CHANGE / REPLACE` 协议；
- tool result 至少返回 `UPDATED / NO_CHANGE / FAILED` 和当前内容摘要；
- 校验、数据库和模型异常转换为正常 `FAILED` tool result，不抛出 `TOOL_EXECUTION_FAILED`；
- Prompt 明确长期自述、一次性题目表现和短期情绪的边界；
- 复用通用 `agent_tool_start / agent_tool_end`，不修改 `agent-core` 生命周期事件。

**验收出口**

- 工具无法修改服务端未允许的 kind/dimension/tag；
- 多维度更新有明确的部分失败或整体失败语义，实施计划必须二选一并测试；
- `FAILED` 不终止当前 run，主 Agent 不得声称已经保存；
- 同一 run 不重载 Prompt 画像，本轮通过 tool result 使用新结果，下一 run 才读取新快照。

**不包含**

- 不从所有聊天消息中自动抽取画像；
- 不在 loop 前额外调用自述分类模型；
- 不新增画像专用 SSE 事件。

### `LP-11`：`PRACTICE_CHAT` 画像召回与 Prompt 注入

建议计划文件：`docs/ai-learner-profile-implementation-plans/LP-11-practice-chat-profile-recall.md`

**目标**

在 Agent loop 前按场景读取一次画像快照，并通过 Prompt Assembly 向 `PRACTICE_CHAT` 注入受预算控制的参考性画像片段。

**计划应覆盖**

- `LearnerProfilePolicy` 和白名单 `LearnerProfilePolicyResolver`；
- 第一版仅 `PRACTICE_CHAT` enabled，未知场景和正式 `PRACTICE_CODE_REVIEW` disabled；
- 读取五个当前 `DECLARED_FACT`、两个允许的 `GENERAL_OBSERVATION`；
- 只读取当前题目 `problem_tag_assignment` 对应的当前 `TAG_ASSESSMENT`；
- `AgentConversationService` 在 loop 前完成查询，同一 run 不二次刷新；
- `LearnerProfilePromptSectionProvider` 使用 `MEMORY_SUMMARY` slot，并与会话摘要保持稳定顺序和独立 section identity；
- `PRACTICE_CHAT` 总预算默认 8,000、画像片段默认上限 800，均通过 Spring 配置注入；
- 确定性裁剪优先级：自述大于当前题目标签评价大于通用观察；
- 查询失败和无画像时不生成片段，不阻断 Agent；
- 记录总 Prompt 估算、画像估算和是否裁剪，避免记录完整画像正文。

**计划必须验证**

- 正式 Review 的独立 `PracticeCodeReviewPromptBuilder` 不查询、不注入画像；
- 普通聊天、学习计划、主题讲解和复述判定等场景默认不读取画像；
- 当前用户消息和服务端事实优先级高于模型生成画像；
- 800 预算不因模型 context window 变大而自动扩张。

**验收出口**

- 每个 practice run 最多查询一次画像；
- 当前题目外的标签评价不会进入 Prompt；
- 超预算按固定优先级裁剪且结果可重复；
- 画像查询异常时原练习聊天链路仍正常运行。

**不包含**

- 不实现向量召回、模型压缩画像或跨标签语义扩展；
- 不修改 Agent loop；
- 不把正式业务设置迁移到画像正文。

### `LP-12`：前端画像工具状态反馈

建议计划文件：`docs/ai-learner-profile-implementation-plans/LP-12-profile-tool-frontend-status.md`

**目标**

让练习聊天工作台基于现有通用 tool 生命周期事件展示用户自述画像更新状态，不新增画像管理页面。

**计划应覆盖**

- 前端共享工具名和结果状态类型；
- `agent_tool_start` 映射“正在更新学习记忆...”状态；
- `UPDATED`、`NO_CHANGE`、`FAILED` 对应明确文案；
- 工具失败时不覆盖主 Agent 后续正常回答；
- 中文、英文文案和无障碍可读性；
- 与 Review 工具、权限提示和通用未知工具事件的兼容；
- SSE 重连或重复事件下不重复追加状态文案。

**验收出口**

- 四种状态均有组件测试；
- 未知工具仍按现有逻辑处理；
- `FAILED` 只说明暂未更新，不暴露数据库或模型错误；
- 不要求刷新页面即可看到本轮工具结果。

**不包含**

- 不增加画像查看、编辑、删除或历史版本 UI；
- 不增加新的后端 REST API；
- 不主动启动 Vite 开发服务器。

### `LP-13`：画像闭环联调、配置与发布门禁

建议计划文件：`docs/ai-learner-profile-implementation-plans/LP-13-profile-end-to-end-rollout.md`

**目标**

验证存储、队列、同步工具、异步消费者、Prompt 召回和前端状态组成可发布闭环，并形成明确灰度与回滚方案。

**计划应覆盖**

- Flyway 共享版本空间和所有新增迁移的升级测试；
- PostgreSQL 集成测试覆盖画像唯一约束、版本链、队列满批、事务原子性和清理；
- 后端场景测试覆盖同步 `UPDATED / NO_CHANGE / FAILED`、异步五条触发和 Prompt 查询失败降级；
- 前端最小相关测试和全量构建；
- 所有配置项默认值、环境变量映射和 `.env.example` 是否需要补充；
- AI governance purpose/source、模型选择、超时和成本观测；
- 画像内容、Prompt 和日志的隐私检查，不记录完整自述、代码、Authorization 或敏感 header；
- 单消费节点部署方式、consumer 开关切换顺序和停机积压行为；
- 数据库迁移、功能开关、消费开关和 Prompt 注入的分阶段发布顺序；
- 回滚时保留画像/队列数据，优先通过关闭消费者和场景 Policy 停止功能。

**建议发布顺序**

```text
发布 A：画像表 + 队列表 + 队列发布/消费基础设施，consumer disabled
  -> 验证迁移、Publisher 和数据库约束

发布 B：正式 Review 语义 + 标签归因 + 画像消息原子发布，consumer disabled
  -> 验证消息稳定入库、不影响 Review 主流程

发布 C：启用唯一消费节点 + Code Review 画像消费者
  -> 观察批量触发、失败率、Token 和画像版本增长

发布 D：启用用户自述工具和 PRACTICE_CHAT 画像召回
  -> 观察工具状态、Prompt 预算、回答质量和查询降级
```

**最终验收出口**

- 正式 Review 和画像消息具备原子性；
- 五条同用户消息可以驱动一次画像批量更新；
- 用户自述可同步更新且失败不阻断回答；
- 下一次 practice run 能读取最新画像，本次 run 不热重载；
- 正式 Review 评分不受画像注入影响；
- 队列关闭消费时消息可积压，重新开启后按既定语义消费；
- `make backend-test`、相关 PostgreSQL IT、`make frontend-test` 和 `make build` 通过。

## 七、跨任务固定契约

后续每份实施计划都必须遵守以下已确认边界，不应在单个任务内重新发散设计。

### 7.1 数据与画像边界

- 业务表是事实来源，`learner_profile_entry` 只保存当前自然语言结论和历史版本；
- 不新增 `detail_json`、candidate 表、通用 evidence 表和独立 Memory Summary 表；
- `content_text` 面向用户和 Prompt，不作为可解析数据协议；
- 需要确定性消费的时间、计划节奏、语言和状态继续保存在正式业务模型。

### 7.2 Agent 边界

- `agent-core` 不感知学习者画像；
- 画像读取发生在 loop 外围，每个 run 一次快照；
- 正式 Review 模型调用禁用画像；
- 同步画像工具复用通用 tool 生命周期事件。

### 7.3 队列边界

- 第一版 PostgreSQL、单播、单消费节点、每 topic 单线程；
- 第一版至少一次派发，提供租约、有限退避重试和终态失败告警停止；不提供死信、广播和自动选主；
- topic 和 key 是路由契约，payload 使用 Jackson JSON/TEXT，队列不理解业务字段；
- 消息 `SUCCEEDED` 表示已出队，不表示业务回调成功。

### 7.4 安全与可观测性

- 日志和指标使用低基数字段，不记录完整画像正文、完整代码、完整 Review Markdown 或用户隐私；
- 模型 provider、model、prompt version、purpose/source、耗时、Token 和结果状态可追踪；
- 公共 topic、工具名、配置 key、状态值、JSON 字段和 SSE 映射值使用常量或枚举统一管理。

## 八、后续实施计划统一书写要求

每份任务实施计划至少包含以下章节：

1. 任务目标与完成标准；
2. 当前实现基线与已确认冲突；
3. 范围、非目标和跨任务依赖；
4. 关键契约、常量、配置和数据结构；
5. 目标模块、主要文件和职责边界；
6. 分步骤实施清单，每一步包含测试和可观察结果；
7. 单元测试、PostgreSQL 集成测试、前端测试或端到端测试；
8. 日志、指标、AI governance 和隐私要求；
9. 数据迁移、发布顺序、回滚和兼容性；
10. 风险、开放项和明确不在本任务处理的内容；
11. 可复制执行的验证命令；
12. 最终验收 checklist。

实施计划不得只写类名和待办项。对于数据库事务、并发控制、至少一次队列语义、Prompt 预算和失败降级，必须写出可验证的行为和对应测试。

## 九、第一版任务完成定义

当且仅当 `LP-01` 至 `LP-13` 均完成各自实施计划、编码和验收后，AI 学习者画像第一版才视为完成。

以下内容继续留在后续版本，不应阻塞第一版任务计划：

- 错题复述、学习计划进度和聊天求助等第二观察源；
- 用户画像查看、编辑、抑制、删除和历史版本 UI；
- 失败消息自动重试、死信、人工回放和广播队列；
- 多节点自动选主和分布式消费所有权；
- 向量召回、语义检索、图数据库和通用 MemoryFactory；
- 画像重建、精确证据链和正文子项级生命周期。
