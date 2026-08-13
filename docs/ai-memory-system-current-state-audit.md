# AI 记忆系统现状审计

## 文档信息

- 审计日期：2026-07-30
- 文档性质：现状基线
- 审计范围：学习者画像长期记忆、Agent 会话短期记忆、写入链路、召回链路、用户界面、配置、可观测性和隐私边界
- 非目标：本文不确定后续优化方案，不调整现有业务语义，不修改业务代码

## 一、结论摘要

当前项目中的“记忆系统”实质上由两部分组成：

1. 已经形成闭环的第一版学习者画像长期记忆。
2. 具备滑动窗口但尚未真正接通滚动摘要的 Agent 会话短期记忆。

长期记忆当前不是通用的原子事实、事件记忆或向量记忆平台，而是：

> 受信业务事实作为输入，按固定 dimension 或 tag 保存自然语言 Profile，并通过版本链维护当前状态。

这套实现已经具备较好的数据约束、并发控制、事务边界和失败降级能力，但在记忆粒度、证据追溯、用户控制、输入来源、异步可靠性、召回质量、会话摘要和隐私治理方面仍有明显扩展空间。

## 二、范围与边界

### 2.1 本文所称长期记忆

长期记忆指 `learner_profile_entry` 承载的学习者画像，包括：

- 用户明确表达的长期背景、目标、约束、偏好和自我评价。
- 系统基于多次正式 Code Review 形成的跨题观察。
- 系统针对受信算法标签形成的掌握情况评价。

### 2.2 本文所称短期记忆

短期记忆指 Agent 会话运行时注入 Prompt 的历史上下文，包括：

- 最近若干轮用户与助手消息。
- 设计上预留、但当前未实际生成和恢复的 active summary。

### 2.3 不属于当前统一记忆系统的业务数据

下列数据虽然可以成为未来记忆输入，但当前仍由各业务模块独立管理，没有进入统一画像写入和召回链路：

- 学习计划及计划执行进度。
- 题目练习状态和完成结果。
- 题目笔记、复习评级和 FSRS 状态。
- 普通导师聊天中的长期信息。
- 用户文件、外部资料和知识库内容。

## 三、总体数据流

```text
Practice Chat 用户明确陈述
        |
        v
声明画像更新工具/Agent -----------+
                                   |
正式 Code Review                   v
        |                    learner_profile_entry
        v                           |
Review + 标签 + queue 同事务         v
        |                    Practice Chat 画像召回
        v                           |
每用户严格满 5 条后消费              v
        |                    最终 Prompt / LLM 请求
        v
Review 画像更新 Agent --------------+

Agent 历史消息 ----------------> 最近 8 轮滑动窗口 ----> 最终 Prompt
active summary 数据位 ----------> 当前未实际生成/恢复 --x
```

## 四、长期记忆数据模型

### 4.1 条目类型和固定维度

核心表由 `backend/mentor-api/src/main/resources/db/migration/V34__learner_profile_entry.sql` 创建。

| 条目类型 | 固定维度 | 当前主要来源 |
|---|---|---|
| `DECLARED_FACT` | `LEARNER_BACKGROUND` | Practice Chat 用户自述 |
| `DECLARED_FACT` | `GOALS_AND_INTENTS` | Practice Chat 用户自述 |
| `DECLARED_FACT` | `TIME_AND_RESOURCE_CONSTRAINTS` | Practice Chat 用户自述 |
| `DECLARED_FACT` | `LEARNING_AND_INTERACTION_PREFERENCES` | Practice Chat 用户自述 |
| `DECLARED_FACT` | `SELF_ABILITY_ASSESSMENT` | Practice Chat 用户自述 |
| `GENERAL_OBSERVATION` | `PROBLEM_SOLVING_APPROACH` | 正式 Code Review 聚合，目前已接入 |
| `GENERAL_OBSERVATION` | `IMPLEMENTATION_AND_ERROR_PATTERN` | 正式 Code Review 聚合，目前已接入 |
| `GENERAL_OBSERVATION` | `LEARNING_INTERACTION_AND_INDEPENDENCE` | 已建模，当前没有自动生成来源 |
| `GENERAL_OBSERVATION` | `REVIEW_AND_GROWTH_PERFORMANCE` | 已建模，当前没有自动生成来源 |
| `TAG_ASSESSMENT` | `TAG_MASTERY` | 正式 Code Review 聚合，绑定受信 `tag_id` |

维度由服务端枚举和数据库约束共同控制，模型不能自由扩展维度或生成未注册标签。

### 4.2 业务身份和版本语义

每条画像由以下业务身份唯一确定：

- `DECLARED_FACT` / `GENERAL_OBSERVATION`：`user_id + entry_kind + dimension`。
- `TAG_ASSESSMENT`：`user_id + tag_id`。

同一个业务身份最多存在一个 `ACTIVE` 条目。更新采用：

- `NO_CHANGE`：保留当前版本，不写新记录。
- `REPLACE`：当前版本转为 `SUPERSEDED`，创建 revision 加一的新 `ACTIVE` 版本。

表结构还支持 `SUPPRESSED` 状态。应用层 `LearnerProfileUpdateService` 中已有包内可见的 `suppress` 和 `deleteIdentity` 方法，但尚未形成正式用例服务、API 和 UI。

### 4.3 当前持久化的来源信息

画像条目会保存：

- `origin_type`。
- `model_provider`。
- `model_name`。
- `prompt_version`。
- 版本号、生效时间和被替代关系。

当前不会保存：

- 触发更新的 Review ID 或消息 ID。
- Agent run ID、queue message ID 或工具调用 ID。
- 支撑正文结论的证据片段。
- 置信度、重要性和有效期策略。
- 模型决策时给出的 reason。

因此现有版本链可以回答“内容如何变化”，但不能精确回答“为什么变化、依据是什么”。

## 五、长期记忆写入链路

### 5.1 用户自述写入

入口位于：

- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/tool/UpdateLearnerDeclaredProfileAgentTool.java`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/ai/DeclaredProfileUpdateService.java`

当前行为：

- 只在 `PRACTICE_CHAT` 场景暴露 `update_learner_declared_profile`。
- 只允许五个 `DECLARED_FACT` 维度。
- 更新意图只允许 `DECLARE` 和 `CORRECT`。
- 模型根据当前正文和新陈述决定 `NO_CHANGE` 或生成完整替换正文。
- 快照陈旧时最多重新计算一次，默认 `max-stale-retries=1`。
- 更新失败会降级为工具失败状态，不阻断聊天主流程。
- 同一个 run 内更新后的画像不会重新注入当前 Prompt，需要下一个 run 才能召回。

当前限制：

- 用户不能通过正式 API 精确编辑某一条事实。
- 用户不能查看本次改写前后的差异。
- 用户不能通过 UI 抑制或删除画像。
- 整段替换可能在修改一个事实时覆盖同维度的其他事实。

### 5.2 Code Review 系统观察写入

入口位于：

- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewCommitService.java`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/review/CodeReviewProfileUpdateService.java`

提交阶段会在同一个数据库事务中写入：

1. 正式 Code Review。
2. Review 受影响的受信标签。
3. `learner-profile.code-review.v1` queue message。

队列契约：

- topic：`learner-profile.code-review.v1`。
- key：用户 ID。
- payload v1：只包含 Review ID。
- batch size：固定为 5，不通过配置调整。

消费阶段：

- 每个用户满 5 条消息后才形成一次批次。
- 画像分析窗口最多包含 10 道不同题目的最新正式 Review。
- 当前只更新两个通用观察维度和本批次受影响标签的 `TAG_MASTERY`。
- 模型输出经过严格 JSON schema、固定 dimension 和受信 tag 白名单校验。
- 批量画像更新通过用户行锁和快照令牌保证全有或全无。
- 模型调用发生在画像更新事务之外。

当前队列语义是成功确认的至少一次回调：

- 消息先在事务内由 `PENDING` 改为 `PROCESSING` 并持有租约。
- 事务提交后调用业务 callback，成功或 `NO_CHANGE` 后再确认 `SUCCEEDED`。
- callback 或模型调用失败时回到 `PENDING` 并按退避重试。
- 达到最大次数后转为 `FAILED`，触发告警并停止 topic；`FAILED` 不自动重放。
- 同一用户只有 1 至 4 条消息时可以长期等待，不存在最大等待时间。
- 当前没有 DLQ、消费租约、重试次数、失败原因持久化或人工补偿入口。

## 六、长期记忆召回链路

入口位于：

- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/recall/LearnerProfileRecallService.java`
- `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/recall/LearnerProfilePromptSectionProvider.java`

### 6.1 当前召回范围

当前只有 `PRACTICE_CHAT` 使用学习者画像。每个 run 会读取一次：

- 策略允许的五个当前 `DECLARED_FACT`。
- 策略允许的两个当前 `GENERAL_OBSERVATION`。
- 当前题目所关联受信标签的当前 `TAG_ASSESSMENT`。

其他 Agent 场景和普通业务接口不会自动召回画像。

### 6.2 当前排序和预算

Prompt 中的固定顺序为：

1. 用户明确自述。
2. 当前题目标签能力评价。
3. 跨题通用观察。

画像使用独立的默认 800 token 预算，并通过约 `字符数 / 4` 估算 token。超出预算后按上述固定顺序截断，可能从单条自然语言正文中间截断。

当前没有：

- 基于当前问题语义的相关性检索。
- 重要性、置信度、时效性或使用频率评分。
- 多条候选记忆之间的综合排序。
- 记忆冲突检测和按来源可信度决策。
- 针对学习计划、复习、推荐等不同场景的召回策略。

召回查询异常时会记录低敏日志并返回空画像，不阻断聊天。

## 七、会话短期记忆

### 7.1 设计目标

`ContextAssemblyPolicy.defaultPolicy()` 当前声明：

- 最近 8 轮消息。
- 总上下文预算 8,000 token。
- 策略名 `sliding-window-with-active-summary`。

数据库 `agent_task` 也已经预留 `active_summary_artifact_id`，Prompt 组装器具备 active summary 插槽。

### 7.2 实际运行状态

`PostgresAgentConversationRepository` 在以下路径构造 `PreparedAgentRun` 时均传入 `null` active summary：

- 新建 run。
- 幂等重放已有 run。
- retry run。

当前未发现负责以下工作的完整实现：

- 根据旧会话生成滚动摘要。
- 保存摘要 artifact。
- 更新 `active_summary_artifact_id`。
- 创建或恢复 run 时读取 active summary。
- 摘要过期、重建和版本管理。

因此当前会话短期记忆的实际语义主要是“最近 8 轮滑动窗口”，不是设计文档描述的“滑动窗口 + 滚动摘要”。

## 八、API 与用户界面

### 8.1 后端 API

当前提供：

```http
GET /api/me/learner-profile
```

身份只取当前登录用户，不接受前端传入的 user ID。接口只返回当前有效画像，并按以下三组组织：

- 用户自述。
- AI 观察。
- 专题评价。

### 8.2 前端

`frontend/src/MyPage.tsx` 在 `/me` 页面只读展示当前画像。

当前不展示或不支持：

- 历史版本。
- 来源详情和证据。
- 模型、Prompt 版本和生成时间等治理信息。
- 置信度和有效期。
- 用户编辑、确认、抑制和删除。
- 单条画像是否允许被后续 Agent 使用的授权控制。

Practice Chat 只展示画像工具的运行中、已更新、无需更新和失败状态，不展示具体更新内容差异。

## 九、配置与实际启用状态

基础配置 `backend/mentor-api/src/main/resources/application.yml` 中以下能力默认关闭：

- `learner-profile.declared-update.enabled=false`。
- `learner-profile.recall.practice-chat.enabled=false`。
- `learner-profile.code-review-consumer.enabled=false`。
- `queue.consumer.enabled=false`。

`application-local.yml` 中上述能力默认开启。

因此仓库代码已经形成画像闭环，但不能仅根据代码判断某个非 local 部署环境是否正在使用记忆功能，实际状态取决于部署时注入的环境变量和 profile。

## 十、可观测性与隐私

### 10.1 可观测性

当前已具备：

- 持久化队列和 Review 画像消费的低基数 Micrometer 指标。
- 画像召回数量、token 估算和是否裁剪的运行 metadata。
- 画像召回成功、失败的低敏日志。

当前未找到 rollout runbook 中提及的 recall/prompt 独立 Micrometer 指标实现。LP-01 至 LP-13 实施计划仍保留大量未勾选清单，与实际代码完成状态存在文档漂移。

### 10.2 隐私与诊断快照

画像正文会作为 Prompt 内容进入最终 LLM 请求。`PersistentAgentTraceObserver` 会保存最终请求 messages、tools 和 generation options 的诊断快照。

当前诊断数据保留策略为 30 天。`AgentTraceRedactor` 主要根据 JSON 字段名识别 API key、Authorization、token、password、secret 等敏感字段，不能自动识别自然语言正文中的用户隐私。

因此用户写入画像的背景、求职目标、时间限制等内容可能同时存在于：

- `learner_profile_entry` 长期画像表。
- Agent 最终请求诊断快照。

后续优化需要明确画像数据的授权、保留、删除传播和诊断快照处理边界。

## 十一、当前实现的优势

1. 模块职责清楚，画像领域、声明更新、Review 聚合、召回、API 和队列边界相对独立。
2. 固定维度、受信标签、数据库 check constraint 和模型 JSON 白名单形成多层防护。
3. ACTIVE 唯一约束、用户行锁、快照令牌和批量原子更新可以防止陈旧模型结果覆盖新版本。
4. 模型调用与数据库事务隔离，不会在长时间 AI 调用期间持有画像更新事务。
5. Review、标签和 queue message 同事务提交，写入源头不会出现三者部分成功。
6. 画像召回失败会降级为空，不影响 Practice Chat 主链路。
7. 功能开关默认关闭，本地环境明确开启，具备发布和 AI 成本控制边界。
8. 单元测试、PostgreSQL 集成测试和前端交互测试覆盖了主要闭环及并发、失败降级场景。

## 十二、主要问题清单

| 类别 | 当前问题 | 直接影响 |
|---|---|---|
| 记忆模型 | 一个维度保存一整段自然语言正文 | 局部修改可能覆盖无关事实，难以独立失效和排序 |
| 证据追溯 | 没有源事件、证据片段、置信度和更新理由 | 无法解释结论依据，也难以人工纠错 |
| 用户控制 | 没有正式编辑、确认、抑制、删除 API 和 UI | 用户无法有效管理 AI 对自己的长期认知 |
| 输入覆盖 | 只有 Practice Chat 自述和正式 Code Review | 计划执行、复习和长期成长信号没有进入画像 |
| 维度覆盖 | 四个通用观察维度只生成两个 | 互动独立性和复习成长维度长期为空 |
| 异步可靠性 | 严格满 5 条、callback 至少一次确认、有限重试 | 小批次无限等待；连续失败进入 FAILED 并告警停止 topic，需显式运维恢复 |
| 召回质量 | 固定顺序和字符预算裁剪 | 无法优先选择与当前任务最相关、最可靠的记忆 |
| 会话连续性 | active summary 未生成和恢复 | 超出最近 8 轮的信息会直接退出上下文 |
| 隐私治理 | 画像正文同时进入长期表和诊断快照 | 删除传播、授权和自然语言隐私处理边界不清晰 |
| 运维治理 | 文档状态和部分指标与代码不一致 | 发布判断、故障定位和后续改造基线容易失真 |

## 十三、测试基线

本次审计未修改业务代码。后端执行：

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application,persistent-queue,mentor-api -am \
  -Dtest='*LearnerProfile*,*DeclaredProfile*,*CodeReviewProfile*,Queue*,PersistentQueue*,AgentConversationServiceTest,PracticeChatAgentDefinitionTest' test
```

结果：

- `persistent-queue`：10 项通过。
- `mentor-application`：40 项通过。
- `mentor-api`：24 项通过。
- 后端合计：74 项通过，0 失败，0 错误。

前端执行：

```bash
npm --cache ./.npm --prefix frontend test -- \
  src/MyPage.test.tsx \
  src/services/api.test.ts \
  src/learning-plans/PracticeChatWorkbench.test.tsx \
  src/App.test.tsx \
  src/app/AppShell.test.tsx
```

结果：5 个测试文件、161 项测试全部通过。

## 十四、后续需要产品方向输入的问题

本文不预设优化方案。进入优化设计前，需要优先明确以下方向：

1. 长期记忆继续采用维度摘要，还是演进为原子事实与摘要并存。
2. 哪些业务事件可以成为可信写入源，证据和置信度如何定义。
3. 系统观察的业务幂等键、终态 FAILED 的运维恢复权限和告警接入范围。
4. 哪些 Agent 场景允许召回哪些类型的记忆，如何排序和控制预算。
5. 用户对 AI 记忆应具备哪些查看、确认、编辑、抑制和删除权利。
6. 自然语言画像进入诊断快照时采用何种授权、脱敏和保留策略。
7. 会话滚动摘要是否作为独立工作流落地，以及摘要与长期画像如何分工。

## 十五、关键代码索引

- 数据模型：`backend/mentor-api/src/main/resources/db/migration/V34__learner_profile_entry.sql`
- 画像契约：`backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/LearnerProfileContract.java`
- 版本更新：`backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/LearnerProfileUpdateService.java`
- 用户声明工具：`backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/tool/UpdateLearnerDeclaredProfileAgentTool.java`
- Review 提交：`backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewCommitService.java`
- Review 画像更新：`backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/review/CodeReviewProfileUpdateService.java`
- 画像召回：`backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/recall/LearnerProfileRecallService.java`
- Prompt 渲染：`backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/profile/recall/LearnerProfilePromptSectionProvider.java`
- 画像 API：`backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/profile/LearnerProfileController.java`
- 前端展示：`frontend/src/MyPage.tsx`
- 会话策略：`backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/context/ContextAssemblyPolicy.java`
- 会话持久化：`backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/repository/PostgresAgentConversationRepository.java`
- 请求快照：`backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/observer/PersistentAgentTraceObserver.java`
- Trace 脱敏：`backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/observer/AgentTraceRedactor.java`
- 诊断保留：`backend/agent-persistence-postgres/src/main/resources/db/migration/agent/V31__agent_diagnostic_retention.sql`
- 功能配置：`backend/mentor-api/src/main/resources/application.yml`、`backend/mentor-api/src/main/resources/application-local.yml`
