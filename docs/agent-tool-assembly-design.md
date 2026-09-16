# 教练能力用户黑名单与 Tool 装配钩子设计

> 状态：已确认，待实施  
> 日期：2026-09-11  
> 范围：Practice Chat 教练的「学习记忆」「历史源码」两项用户设置

## 1. 背景与结论

Practice Chat 的 Agent Definition 已经声明了场景允许的 Tool 白名单。用户设置不应重新定义 Tool 的归属类别，也不应把多个底层 Tool 直接暴露为多个技术开关。

本设计采用以下原则：

- 所有已注册且被 Definition 允许的 Tool 保持默认装配。
- 用户设置是面向产品能力的**黑名单**：仅能从本次调用的候选集合移除 Tool，不能添加 Tool、不能绕过全局能力开关、不能越过 Definition 白名单。
- 一个用户能力可以映射多个 Tool，也可以控制非 Tool 的业务动作。
- 未配置用户设置时，能力默认开启，以保持现有用户行为。
- 用户关闭「学习记忆」后，后续新完成的正式 Code Review 不再投递画像更新队列；此前已投递、已执行或既有画像不做取消、回滚或删除。

不采用「Tool 分为 GLOBAL / USER 类别 + 用户逐 Tool 启用记录」方案。该方案会使一个产品开关被拆成多条设置记录，产生部分状态、默认值迁移和聚合状态回读问题，也无法自然覆盖 Code Review 队列投递这一非 Tool 动作。

## 2. 术语与边界

| 名称 | 含义 |
|---|---|
| Tool | LLM function calling 的底层能力，以稳定 `toolName` 标识。 |
| Definition 白名单 | 某 Agent 场景理论上允许使用的最大 Tool 集，由 `allowedToolNames()` 声明。 |
| 教练能力（Capability） | 面向用户的设置项，可映射一组 Tool 和可选业务动作。 |
| 装配钩子 | 在本轮 Agent 的候选 Tool 集形成后、创建 `AgentLoopExecution` 前执行的只减过滤器。 |
| 有效 Tool 集 | Definition 白名单经所有装配钩子过滤后的 Tool 集；LLM schema 与实际执行必须使用同一集合。 |

本期仅覆盖 Practice Chat。学习计划、后台 Agent 和通用 Tool 不因用户设置而被直接隐藏；后台 Code Review 画像更新只受「是否投递」这一业务门禁影响。

## 3. 用户能力与 Tool 映射

映射是应用层的稳定代码契约，使用各 Tool 所属模块的名称常量，不在业务代码中散落字符串字面量。

| Capability | Practice Chat 中移除的 Tool | 额外业务动作 |
|---|---|---|
| `LEARNING_MEMORY` | `update_learner_declared_profile`、`search_learner_memory`、`read_learner_memory_section`、`get_learner_memory_evidence` | 关闭后，新创建的正式 Code Review 不投递学习画像更新队列。 |
| `HISTORICAL_SOURCE_CODE` | `read_practice_submission_detail` | 无。 |

下列 Tool 不属于本期用户开关，保持默认装配：

- `get_current_problem_learning_state`：当前题的受信学习事实；读取笔记正文仍受当前消息意图校验。
- `get_problem_review_trajectory`：当前题历史 Review 事实，不含源码。
- `get_practiced_problem_overview`、`list_practice_problem_submissions`：历史提交的概览和列表，只返回有限事实和 run-local ref，不返回旧源码。
- `read_tool_result`：运行时大结果续读基础设施。关闭源码详情 Tool 后不会创建新的源码详情结果 ref，因此无需单独隐藏它。

`HISTORICAL_SOURCE_CODE` 关闭后，历史概览和列表仍可用；即使开启，`read_practice_submission_detail` 仍必须通过服务端对当前用户消息的“查看旧代码、代码级复盘或版本对比”意图校验。

## 4. 用户设置数据模型与 API

新增独立表 `user_coach_capability_setting`，不复用 `user_ai_preference`，也不复用逐 Tool 设置表。

```text
user_id       BIGINT      NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE
capability    VARCHAR(64) NOT NULL
enabled       BOOLEAN     NOT NULL
version       BIGINT      NOT NULL
created_at    TIMESTAMPTZ NOT NULL
updated_at    TIMESTAMPTZ NOT NULL
PRIMARY KEY (user_id, capability)
```

约束：`version > 0`，`capability` 非空。Capability 枚举仅允许 `LEARNING_MEMORY`、`HISTORICAL_SOURCE_CODE`；数据库无需重复枚举约束，但 API、服务和测试必须严格校验。

缺少记录表示“用户尚未显式覆盖默认值”，其含义是 `enabled=true`、`version=0`。用户第一次保存任一值时创建记录；后续变更递增版本，不删除记录，以保留审计和并发语义。

建议 API：

```text
GET   /api/me/coach-capabilities
PATCH /api/me/coach-capabilities
```

GET 返回每项的：`capability`、`enabled`、`version`、`available`、`effectiveEnabled`。

- `enabled` 是用户偏好或缺省偏好。
- `available` 表示当前部署是否已注册该能力的必要 Tool、并通过全局功能开关。
- `effectiveEnabled = enabled && available`；全局开关仍是管理员安全边界，用户设置不能将未注册或全局关闭的能力重新打开。

PATCH 接收一个或多个 `{ capability, enabled, expectedVersion }` 更新项。版本不匹配返回统一冲突错误；前端重新读取后再提示用户。所有枚举、JSON 字段、路径和错误码必须在对应模块的常量类中集中声明。

## 5. 通用 Tool 装配钩子

在 `agent-runtime` 引入与具体业务无关的 `AgentToolAssemblyHook` 和 hook chain。该扩展点只由
`DefaultAgentRuntime` 在创建本轮 `AgentLoopExecution` 前消费，属于 Runtime 生命周期，而不是
`agent-core` 的 Tool、Registry 和 Loop 基础模型。概念接口如下：

```java
interface AgentToolAssemblyHook {
  AgentToolAssemblyHookResult apply(
      AgentToolAssemblyContext context,
      Set<String> candidateToolNames);
}
```

`AgentToolAssemblyContext` 至少包含可信 `userId` 与 `agentKey`；不能从 HTTP `SecurityContext` 重新取用户，因为 Runtime 也可能由后台任务调用。结果只允许提供 `excludedToolNames` 和安全的审计摘要，禁止返回“新增 Tool”。

Runtime 中的顺序固定为：

```text
Definition.allowedToolNames()
  -> 按声明顺序执行 Assembly Hook Chain
  -> 移除各 Hook 的 excludedToolNames
  -> AgentLoopExecution.forRuntime(registry, effectiveToolNames, ...)
  -> LLM schema 与 Tool 执行共享同一 effectiveToolNames
```

约束如下：

- Hook 只能移除候选集内的名称；传入不在候选集的名称必须忽略并记录低敏诊断，不得报错中断正常聊天。
- 空结果允许进入 Runtime；Runtime 已有无 Tool 的收敛行为，不能为“至少保留一个 Tool”而重新暴露被用户关闭的能力。
- 若多个 Hook 都排除同一 Tool，结果取并集，顺序不得影响最终集合。
- 每轮运行将有效 Tool 名称和 Hook 的安全摘要写入 run metadata，供审计与问题定位；不保存设置正文或敏感用户资料。
- 没有业务 Hook 时使用空 chain，以维持既有 Runtime 构造和单元测试兼容。

### 5.1 模块职责

```text
backend/agent-runtime/
  toolassembly/
    AgentToolAssemblyContext
    AgentToolAssemblyHook
    AgentToolAssemblyHookChain
    AgentToolAssemblyHookResult

backend/mentor-application/
  coach/capability/
    CoachCapability
    CoachCapabilityToolCatalog
    CoachCapabilityToolAssemblyHook
    UserCoachCapabilitySettingService
    LearningMemoryReviewPublishGate

backend/mentor-api/
  coach/capability/
    Controller、DTO、MyBatis Mapper、Repository
  autoconfigure/
    将应用层 Hook 注入 Runtime hook chain
```

框架层不得依赖 Capability、Practice Chat、用户设置表或 HTTP 身份上下文；应用层不得修改候选
Tool 集之外的 Runtime 行为。`mentor-api` 只负责传输、持久化适配和 Spring 装配，不承载能力映射规则。

## 6. 教练能力黑名单 Hook

在 `mentor-application` 实现 `CoachCapabilityToolAssemblyHook`，依赖：

- `UserCoachCapabilitySettingService`：读取当前用户两个 Capability 的有效偏好；
- `CoachCapabilityToolCatalog`：Capability 到 `(agentKey, toolName)` 的不可变映射；
- Tool 名称常量和 Practice Chat 的稳定 Agent Key。

其逻辑为：

```text
若 agentKey 不是 PRACTICE_CHAT：不移除任何 Tool
若 LEARNING_MEMORY 为 disabled：移除其四个记忆 Tool
若 HISTORICAL_SOURCE_CODE 为 disabled：移除源码详情 Tool
返回被移除 Tool 名称与 Capability/version 的审计摘要
```

`mentor-api` 的自动配置将该 Hook 注入通用 chain。Hook 在 Definition 允许集之后执行，因此即便将来某 Tool 未被当前场景允许，映射存在也不会把它暴露给模型。

启动期校验应覆盖：

1. 每个 Capability 映射的 Tool 名称都能在当前注册表中找到，或该 Tool 已因对应全局能力关闭而被明确标记为 unavailable；
2. 映射的 `agentKey + toolName` 属于该场景的 Definition 白名单；
3. 同一 `(agentKey, toolName)` 不得同时属于两个 Capability，除非未来显式引入组合策略；
4. 关闭某 Capability 不得移除本期定义为基础设施或默认事实读取的 Tool。

## 7. Code Review 画像投递门禁

装配 Hook 只影响 LLM 在本轮能否调用 Tool，不能影响 `PracticeCodeReviewCommitService` 的队列发布。因此新增应用服务 `LearningMemoryReviewPublishGate`，同样读取 `LEARNING_MEMORY` Capability。

在一条新的正式 Review 已成功创建后：

```text
学习记忆 effectiveEnabled = true
  -> 保存 Review，并在当前事务内发布 learner-profile.code-review.v1 队列消息

学习记忆 effectiveEnabled = false
  -> 只保存 Review；queueMessageId 为空；不创建画像更新消息
```

此处的用户关闭语义刻意保持轻量：只拦截设置保存之后的新发布。无需扫描、取消或清理已有 PENDING/PROCESSING 消息，也无需撤销既有 Claim、画像版本和证据。若用户在 Review 已创建、队列已发布后才关闭设置，该消息仍可被后台消费者处理。

全局 `LEARNER_MEMORY_CODE_REVIEW_CONSUMER_ENABLED` 仍然只控制消费者是否运行，不应反向改变正式 Review 保存与用户偏好含义。

## 8. 前端呈现

设置页在“AI 教练”区域提供两个开关：

- **学习记忆**：允许教练记录用户主动表达的长期信息、在后续对话中参考学习记忆，并在新正式 Review 后投递画像更新。
- **历史源码**：允许教练在用户明确要求代码级复盘或版本对比时读取历史正式提交源码。

关闭后的说明固定为“影响之后的新对话与新 Review；不会删除已有学习记忆或取消已开始的后台任务”。全局不可用时显示不可用原因，不能把用户偏好显示成实际已生效。

前端只使用 Capability API，不识别或展示底层 Tool 名称，也不自行组装 Tool 映射。

## 9. 实施步骤

1. 新建 Capability 枚举、设置领域模型、Repository、Flyway 迁移和单元/集成测试。
2. 实现 `GET/PATCH /api/me/coach-capabilities`、权限校验、版本冲突与前端类型。
3. 在 `agent-runtime` 增加只减式 Tool assembly hook chain，并在 `DefaultAgentRuntime` 创建 `AgentLoopExecution` 前接入。
4. 实现 `CoachCapabilityToolCatalog` 与 `CoachCapabilityToolAssemblyHook`，注册到 Application 的 hook chain。
5. 将 `LearningMemoryReviewPublishGate` 接入正式 Review 提交事务，使关闭时跳过队列发布但不影响 Review 保存。
6. 在设置页增加两项开关、可用性状态和本地化文案。
7. 补充运行 metadata、Micrometer 计数与管理审计字段，完成发布验证。

## 10. 测试与验收

核心单元测试：

- 无用户记录时候选 Tool 集不变。
- 关闭 `LEARNING_MEMORY` 时四个记忆 Tool 均不出现在有效集合和 LLM schema。
- 关闭 `HISTORICAL_SOURCE_CODE` 时仅源码详情 Tool 被移除；历史概览、列表和 `read_tool_result` 保留。
- 非 Practice Chat 场景不受两个 Hook 映射影响。
- Hook 无法新增未在 Definition 白名单中的 Tool。
- 多 Hook 排除结果取并集；空有效 Tool 集不会重新加入任何 Tool。

后端集成测试：

- 未配置用户默认两个能力开启；PATCH 后版本递增，错误 `expectedVersion` 返回冲突。
- 全局未注册某能力 Tool 时，API 正确返回 `available=false` 与 `effectiveEnabled=false`。
- 学习记忆关闭后，新的正式 Review 仍保存成功，但不插入画像 topic 队列消息。
- 学习记忆开启后，新的正式 Review 保持现有“Review 与队列消息同事务”的提交语义。
- 已存在的队列消息不因用户随后关闭设置而被修改；这符合本设计的弱关闭语义。

交付前至少执行相关 Maven 单测、对应 PostgreSQL 集成测试、前端测试与 `git diff --check`。

## 11. 发布与回滚

- Flyway 先创建空设置表；缺少记录默认开启，因此发布不改变现有用户的 Tool 集和 Review 队列行为。
- 先观察 Tool 装配过滤数、Capability API 错误率、因关闭学习记忆而跳过的队列发布数；所有指标均不得以 userId 作为 tag。
- 若出现问题，可关闭新的前端入口并移除 Application Hook Bean；空表和既有用户设置记录不影响旧版本运行。
- 本设计不删除任何已有 Tool、Review、画像 Claim、证据或队列消息；回滚不需要 down migration。

## 12. 明确非目标

- 不做用户级模型、Provider、密钥、外部 MCP 或通用 Tool 市场配置。
- 不做逐 Tool 用户设置页面。
- 不做关闭后的既有画像删除、队列取消或在途 Agent 强制终止。
- 不将历史 Review 结论与历史源码合并成一个设置；本期只控制旧源码详情读取。
