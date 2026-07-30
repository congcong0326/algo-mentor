# 统一 Agent 底座重构设计

## 1. 文档目的

当前项目已经具备 Agent loop、工具调用、结构化输出、生命周期、权限、上下文压缩、持久化、AI 治理和模型路由等能力，但不同 AI 业务场景仍通过 `AgentLoopRunner`、`AgentRunner` 和 `AiCompletionGateway` 等不同入口执行，场景的提示词、工具、上下文和执行策略也分散在多处。

本次重构希望提供统一的 Agent 底座，使所有正式 AI 业务场景通过同一个 `AgentRuntime` 执行。不同场景主要通过以下定义表达差异：

- 提示词。
- 业务上下文组装。
- 允许使用的工具集合。
- 文本或结构化输出契约。
- 单次 run 的 loop 参数。

本设计只解决当前已经出现的入口分散、工具全量暴露、场景定义分散和业务语义泄漏问题，不提前建设 Workflow、DAG、多 Agent 编排或动态插件平台。

本文与 `docs/agent-runtime-refactoring-implementation-plan.md` 的关系如下：

- 原实施计划主要解决 agent 运行态模型和 PostgreSQL 持久化的模块归属问题，已有结论继续有效。
- 本文补充统一 Agent 执行入口、场景定义和 run 级工具隔离的目标设计。
- 本文不重新设计已经落地的生命周期、结构化输出、权限和上下文压缩能力。

本设计的落地入口如下：

- `docs/unified-agent-foundation-refactoring-implementation-plan.md`：14 个任务的依赖、波次、测试、回滚和上下文管理协议。
- `docs/unified-agent-foundation-refactoring-tasks/README.md`：任务状态板和波次门禁。
- `docs/unified-agent-foundation-refactoring-tasks/CURRENT.md`：连续实施时唯一需要跨任务携带的压缩上下文。

实施时以本文的稳定决策作为架构约束，以当前任务文件作为改动边界。若代码现状要求调整任务细节，应更新对应任务完成备注和 `CURRENT.md`，不能静默偏离本文的治理、线程、审计和工具隔离语义。

## 2. 已定决策

1. 所有登记到 `AiBusinessScenario` 的 AI 执行都必须经过统一 `AgentRuntime`。
2. 普通 CRUD、确定性计算和业务事务不需要包装成 Agent。
3. 当前阶段只有一种 Agent 执行模型，即 Agent loop。
4. 无工具场景是 loop 的退化情况：工具集合为空，模型第一步返回最终结果后结束。
5. 当前阶段不增加 `SINGLE_TURN`、`WORKFLOW` 等执行模式枚举。
6. 当前阶段不实现 Workflow。未来确有固定执行图、暂停恢复或多节点编排需求时再单独设计。
7. 复用现有 `AgentToolRegistry` 保存全局工具实现，但每个 Agent Definition 必须声明自己的工具白名单。
8. AgentRuntime 只负责 AI 执行，学习计划、代码 Review、画像等业务事务仍由 `mentor-application` 负责。
9. `mentor-application` 不再直接依赖具体 `AgentLoopRunner`、`AgentRunner` 或 `AiCompletionGateway`。
10. 采用渐进迁移，不在同一阶段重写现有 loop、生命周期、持久化和 AI 治理实现。
11. 一次结构化推理是无工具、单 step 的退化 Agent loop，仍使用完整的 run、step、生命周期和持久化语义。
12. `AgentRuntime` 负责现有 AI 治理的准入、模型路由、调用关联和成功/失败收尾，业务调用方不再自行编排治理流程。
13. 继续使用当前用户级共享额度，`quotaScope` 保持 `ALL`；不新增“用户 + 场景”额度维度。
14. 调用分为 `USER_ENTRY`、`CHILD` 和 `BACKGROUND`：只有用户入口消耗共享额度并获取用户级运行锁，子调用和后台调用仍独立路由、记账和持久化，但不重复消耗用户交互次数。
15. `AgentDefinition<I>` 和 `AgentKey<I>` 只对业务输入做类型约束；当前 `AgentKey.value` 直接复用对应的 `AiBusinessScenario.code`，不新增第二套场景标识；Runtime 返回通用 `AgentOutput`，领域结果映射继续由 application service 负责。
16. loop 的同步执行内核不自行决定线程。Runtime 在当前线程已经是 Agent 工作线程时直接执行，否则提交到 `ManagedAgentExecutor`；不得依赖线程名称判断。
17. 业务资源存在性、用户归属和状态校验必须在调用 `AgentRuntime` 前完成。准入后的失败由 Runtime 统一标记并释放资源，仍按现有语义视为一次已准入交互。
18. Definition 和工具按现有 Spring 功能开关条件装配，只严格校验当前配置下已生效的 Definition；场景启用但必需工具缺失时启动失败。
19. 第一阶段的 `loopPolicy` 只包含必填 `maxSteps` 和由工具集合推导的 `toolChoice`，不新增 Definition 级 run timeout、`REQUIRED` 或 `SPECIFIC` Tool Choice。
20. 复用现有 `agent_task -> agent_turn -> agent_run` 持久化机制。非会话单次推理、子调用和后台调用创建归属于用户的独立 task/turn，作为上下文隔离和审计单元，不把它们挂入父会话 task。

## 3. 当前现状

### 3.1 已有基础能力

当前底座已经具备以下可复用能力：

- `llm-core` 提供 provider 无关的消息、流式响应、Tool Call 和结构化输出协议。
- `AgentLoopRunner` 已实现模型调用、工具执行、结果回填和多 step 循环。
- `AgentLoopLifecycle` 已提供 observer、interceptor、权限事件和统一生命周期。
- `AgentExecutionOptions` 已支持模型选择、生成参数和结构化输出。
- `AgentToolPermissionGuard` 已支持工具执行前权限门禁。
- run 内消息和工具结果已经支持压缩。
- `agent-persistence-postgres` 已提供 task、turn、message、run 和 trace 持久化。
- `ai-governance` 已提供场景目录、准入、模型路由、调用记账和后台调用上下文。
- `ManagedSystemPromptResolver` 已支持按业务场景解析受管理提示词。

本次重构不替换这些能力，而是在它们之上建立统一入口和场景定义边界。

### 3.2 当前业务场景与执行方式

正式 AI 业务场景定义在 `AiBusinessScenario`，当前共九个。

| 业务场景 | 当前执行方式 | 当前入口 |
|---|---|---|
| `MENTOR_CONVERSATION` | 直接进入 loop | `AgentConversationRunCoordinator` |
| `TOPIC_EXPLANATION` | 流式接口进入 loop；同步兼容接口使用 `AgentRunner` | `ExplainTopicUseCase` |
| `PRACTICE_CHAT` | 通过会话协调器进入 loop | `PracticeTurnOrchestrator` |
| `LEARNING_PLAN_DRAFT` | 直接进入 loop | `LearningPlanDraftStreamService` |
| `LEARNING_PLAN_REVISION` | 直接进入 loop | `LearningPlanDraftRevisionStreamService` |
| `LEARNING_PLAN_EXTENSION` | 直接进入 loop | `LearningPlanExtensionProposalStreamService` |
| `PRACTICE_CODE_REVIEW` | 由 Practice loop 调用工具，工具内部直接 completion | `PracticeCodeReviewAgentTool`、`PracticeCodeReviewService` |
| `LEARNER_DECLARED_PROFILE_UPDATE` | 由 Practice loop 调用工具，工具内部直接 completion | `UpdateLearnerDeclaredProfileAgentTool`、`DeclaredProfileUpdateService` |
| `CODE_REVIEW_PROFILE_UPDATE` | 后台队列直接 completion | `CodeReviewProfileBatchConsumer`、`CodeReviewProfileUpdateService` |

按执行链路分类：

- 六个场景直接使用 `AgentLoopRunner`。
- 两个场景由外层 loop 触发，但自身绕过 Agent loop，直接使用 `AiCompletionGateway`。
- 一个后台场景完全绕过 Agent loop，直接使用 `AiCompletionGateway`。

### 3.3 当前调用入口分散

业务层当前直接依赖三个执行入口：

```text
AgentLoopRunner
  -> 多 step、工具调用、流式事件

AgentRunner
  -> 单次同步 LLM 调用

AiCompletionGateway
  -> 治理后的单次 completion 或后台 completion
```

这些入口分别具备不同的生命周期、测试方式和调用上下文。新增业务场景时，需要先判断应依赖哪个具体类，再单独处理模型路由、流式输出、结构化结果和父子调用关系。

### 3.4 当前工具是全局暴露的

`MentorAiConfiguration` 当前收集所有 `AgentTool` Bean 创建一个全局 `AgentToolRegistry`，再创建一个默认 `AgentLoopRunner`。

`AgentLoopRunner` 每个 step 都把 `toolRegistry.specs()` 全量发送给模型。结果是：

- 主题讲解和学习计划场景也会看到代码 Review、画像更新等无关工具 schema。
- 工具内部的场景校验只能阻止实际执行，不能阻止无关 schema 进入模型上下文。
- 模型可能发起无效工具调用并额外消耗 step 和 token。
- 新增工具会自动影响所有使用同一个 runner 的场景。

### 3.5 当前 loop 参数是全局的

默认 `AgentLoopRunner` 共享以下配置：

- `toolChoice`。
- `maxSteps`。
- observer 和 interceptor 集合。
- permission guard。
- executor。

`AgentExecutionOptions` 可以按 run 配置模型、生成参数和输出格式，但不能配置本次 run 允许使用的工具和最大 step。业务场景因此无法完整声明自己的执行边界。

### 3.6 当前场景定义分散

一个业务场景的完整定义目前散落在多个位置：

- `AiBusinessScenario` 和 `AiRunSource` 中的场景标识。
- controller 或 service 中的治理准入参数。
- `ManagedSystemPromptDefinitions` 中的提示词定义。
- application service 中的上下文组装。
- Spring Configuration 中的工具注册。
- `AgentExecutionOptions` 中的输出格式。
- observer 中的 source 和 metadata 判断。

项目中没有一个位置能够回答“这个 Agent 使用什么提示词、能调用哪些工具、输出什么结果、最多执行多少步”。

### 3.7 metadata 和 observer 理解了业务语义

当前部分通用代码已经开始理解具体业务字段：

- `AgentRuntimeMetadataKeys` 包含 `adapter`、`title`、`topicTitle` 等场景语义。
- `AgentRequest.displayTitle()` 直接读取字符串 `title`。
- `AgentStreamEvent.AgentRunStart` 将通用 run 标题命名为 `topic`。
- `PersistentAgentRunObserver` 判断 `PRACTICE_CHAT`，并读取 `practiceSessionId`、`planId`、`phaseIndex` 和 `problemSlug`。
- `AgentOpsObserver` 通过字符串 switch 映射业务 source，当前未覆盖学习计划修订和扩展场景。

这些问题说明业务场景缺少强类型描述，只能通过 metadata 和分支补充语义。

## 4. 本次需要解决的痛点

本次重构只处理以下五个问题：

1. 所有 AI 业务场景没有统一执行入口。
2. 所有 Agent run 默认暴露全部已注册工具。
3. 场景的提示词、上下文、工具和输出契约没有集中定义。
4. 业务层直接依赖具体 runner 或 completion gateway。
5. 通用 observer 和 metadata 开始承担业务判断。

以下能力不属于本次痛点，不在本次设计范围内：

- Workflow 或 DAG 引擎。
- 多 Agent 协作和任务分派。
- 动态创建、删除或组合 Agent。
- Agent Definition 管理页面或数据库配置。
- 工具插件市场、远程工具协议或热加载。
- 通用业务事务编排。
- 自动 Prompt 优化、自动重试图或结构化输出修复 loop。

## 5. 目标架构

### 5.1 顶层调用关系

```text
Controller / Queue Consumer
  -> mentor-application Use Case
       -> 业务前置校验并构造类型化 Invocation
       -> AgentRuntime
            -> AgentDefinitionRegistry
            -> AI Governance Admission / Routing / Lifecycle
            -> Managed Prompt / Context Assembler
            -> run-local Tool Set
            -> Agent Executor
            -> synchronous Agent Loop Engine
                 -> LlmGateway
                 -> AgentToolRegistry
            -> AgentRunResult / AgentStreamEvent
```

业务入口只依赖 `AgentRuntime`。`AgentRuntime` 根据 Agent key 查找 Definition，完成提示词、上下文、工具和执行参数组装，再进入同一个 Agent loop。

### 5.2 AgentRuntime

`AgentRuntime` 是业务层唯一允许依赖的 AI Agent 执行入口，提供两种消费方式：

- `execute`：等待同一个 loop 完成并返回最终结果，适用于后台任务和结构化结果场景。
- `stream`：订阅同一个 loop 的实时事件，适用于 SSE 和交互式场景。

两种方法共享同一套 loop 状态机、生命周期、治理、工具权限、持久化和最终输出语义，不代表两种执行模式。

`AgentRuntime` 的职责：

- 根据 Agent key 解析唯一 Agent Definition。
- 校验 Invocation 输入类型、调用模式和 Definition 静态契约。
- 按 `USER_ENTRY`、`CHILD` 或 `BACKGROUND` 执行现有治理准入、动态开关、模型路由、记账关联和终态收尾。
- 解析受管理提示词。
- 调用业务 Context Assembler 生成受信上下文。
- 从全局 `AgentToolRegistry` 选择本次 run 的允许工具。
- 生成 run 级 loop 参数和 LLM 执行配置，并校验 Definition 的 `maxSteps` 不超过全局硬上限。
- 根据当前线程是否属于 Agent executor 决定内联执行或提交任务。
- 创建或复用本次调用所需的 task、turn 和 run 审计记录。
- 启动同一个同步 loop 内核，并返回事件流或最终结果。
- 保留父 run、父 step、用户、场景和治理关联信息。
- 对准入后的任意失败统一标记终态、释放用户级运行锁和清理运行时路由绑定。

`AgentRuntime` 不负责：

- 验证学习计划、Practice Session 或 Review 归属关系。
- 创建或提交业务事务。
- 将 Agent 输出直接写入业务表。
- 决定业务接口的 HTTP 状态和 SSE 文案。

用户入口的固定顺序如下：

```text
Controller DTO 校验
  -> Use Case 校验资源存在、用户归属和业务状态
  -> 构造受信 AgentInvocation
  -> AgentRuntime 治理准入并消耗一次共享额度
  -> Definition 解析 Prompt、组装 Context 和运行记录
  -> Agent loop
  -> Runtime 统一完成或失败收尾
```

因此，无效 Session、越权资源或已归档业务对象不能进入 Runtime，也不能先消耗 AI 额度。准入之后发生的上下文加载、持久化或模型错误继续按现有治理语义记录为一次已接受的交互。

### 5.3 执行线程模型

Agent loop 的控制流应收敛为可在当前线程同步执行的内核。线程调度属于 `AgentRuntime`，不属于 loop 本身：

```text
AgentRuntime.execute / stream subscribe
  -> AgentExecutor.inExecutorThread() == true
       -> 当前线程直接执行同步 loop
  -> AgentExecutor.inExecutorThread() == false
       -> 提交 ManagedAgentExecutor 后执行同步 loop
```

`AgentExecutor` 需要增加显式的当前线程归属判断能力，由执行器包装任务时设置并清理线程上下文标记。不得通过线程名称前缀判断，因为名称是运维配置，不是执行契约。

这个规则主要解决父 Agent 工具同步调用子 Agent 的线程放大问题：父 run 已占用 Agent 工作线程时，`AgentRuntime.execute` 直接在该线程执行子 run；否则父线程等待子线程会导致一个逻辑调用同时占用两条 Agent 线程，并在 `SynchronousQueue` 饱和时产生额外拒绝风险。

`stream` 和 `execute` 共享同一个同步 loop 内核、取消令牌和终态收集器。`stream` 只增加事件发布能力，`execute` 只增加最终结果等待能力，不维护第二套状态机。

### 5.4 Agent Definition

每个正式 AI 业务场景注册一个 Agent Definition。Definition 是不可变的场景执行定义，不保存单次请求状态。

| 字段 | 作用 |
|---|---|
| `agentKey` | 类型化稳定标识 `AgentKey<I>`，当前九个正式场景与 `AiBusinessScenario` 一一对应 |
| `inputType` | 业务输入运行时类型，用于 Registry 边界的防御性校验 |
| `promptProvider` | 解析该场景的受管理系统提示词 |
| `contextAssembler` | 将业务输入转换为受信模型上下文 |
| `allowedToolNames` | 本场景允许向模型暴露和执行的工具白名单 |
| `outputContract` | 文本或现有 JSON Schema 结构化输出契约 |
| `maxSteps` | 本场景显式声明的最大 step 数，不得超过全局硬上限 |

第一阶段 Definition 通过代码和 Spring Bean 注册，不增加数据库表和管理接口。

`agent-core` 使用通用 `AgentKey`，不直接依赖 `AiBusinessScenario`。当前九个 `AgentKey.value` 直接使用对应的 `AiBusinessScenario.code`，避免再维护一套可漂移的字符串标识；`ai-governance` 根据该 code 解析现有场景，并集中映射到当前 `AiRunSource` 和 `AiPurpose`。调用方不能在 Invocation 中覆盖这些治理标识。

这项一对一约束只适用于当前阶段。未来只有在同一个治理场景确实需要同时运行不同工具集合、输出契约或 loop policy 时，才重新设计“治理场景与 Definition 一对多”；受管理 Prompt 的用户灰度本身不构成拆分 Definition 的理由。

输入类型采用以下边界：

```java
public record AgentKey<I>(String value, Class<I> inputType) {}

public interface AgentDefinition<I> {
  AgentKey<I> key();

  AgentPreparedRequest prepare(I input, AgentInvocationContext context);
}

public record AgentInvocation<I>(
    AgentKey<I> agentKey,
    I input,
    AgentInvocationContext context
) {}
```

Registry 内部可以保存 `AgentDefinition<?>`，但在调用 `prepare` 前必须通过 `AgentKey.inputType()` 校验并转换输入。这样正常调用错误可以在编译期暴露，错误装配或类型擦除问题则在 run 启动前以稳定错误失败，不把强制类型转换分散到各个 Context Assembler。

输出不增加泛型，`execute` 直接复用现有通用运行结果：

```java
AgentRunResult execute(AgentInvocation<?> invocation);
```

`AgentRunResult.output()` 提供通用 `AgentOutput`。例如 `PRACTICE_CODE_REVIEW` 的 Runtime 结果仍由 `PracticeCodeReviewService` 映射为领域对象并执行评分校验和落库，Runtime 不直接返回 `PracticeReviewDecision`。

`toolChoice` 不作为第一阶段的任意配置字段：

- `allowedToolNames` 为空时固定使用 `NONE`，`maxSteps` 固定为 `1`。
- `allowedToolNames` 非空时固定使用 `AUTO`。
- 全局 `maxSteps` 配置改为硬上限，不再作为所有场景共享的实际值。
- 当前阶段不实现 Definition 级 run timeout、`REQUIRED` 或 `SPECIFIC` Tool Choice。

### 5.5 Agent Invocation 与治理模式

单次调用通过 Agent Invocation 表达，至少包含：

- 类型化 Agent key 和业务输入。
- `USER_ENTRY`、`CHILD` 或 `BACKGROUND` 调用模式。
- 受信用户身份；当前九个正式场景都必须能关联到具体用户。
- idempotency key 等调用关联信息。
- 可选的父 run id 和父 step index。
- 请求规模、流式标记和治理所需的低敏调用上下文。

业务输入应保持类型化。metadata 继续用于 trace、路由快照、持久化关联和低敏诊断，不再作为场景核心控制协议。

三种调用模式保持现有治理语义：

| 调用模式 | 触发方式 | 用户共享额度 | 用户级运行锁 | 模型路由与 Token 记账 | Agent 运行记录 |
|---|---|---:|---:|---:|---:|
| `USER_ENTRY` | HTTP/SSE 用户直接请求 | 消耗一次 | 获取 | 独立 | 独立 |
| `CHILD` | 父 Agent 的 Tool 调用 | 不重复消耗 | 不重复获取 | 按子场景独立 | 独立并关联父 run/step |
| `BACKGROUND` | Queue Consumer 或后台任务 | 不消耗 | 不获取 | 按后台场景独立 | 独立 |

额度继续使用现有 `SHARED_QUOTA_SCOPE = "ALL"`，只按用户统计总交互次数，不按 `AiBusinessScenario` 进一步分桶。`AiBusinessScenario` 和 `AiRunSource` 继续承担模型路由、审计和调用来源标识，不改变当前额度策略。

### 5.6 Agent loop 语义

当前阶段所有 Agent 都使用同一个 loop：

```text
准备 messages、tools 和执行参数
  -> 请求模型
       -> 没有 tool call：产生最终输出并结束
       -> 有 tool call：校验白名单和权限
            -> 执行工具
            -> 回填工具结果
            -> 进入下一 step
```

无工具场景不需要单独执行器：

```text
allowedToolNames = []
toolChoice = NONE
maxSteps = 1
```

模型第一步返回文本或结构化结果后，loop 自然结束。Review、画像更新和后台画像批处理都使用这条路径。

有工具场景声明自己的工具白名单和 `maxSteps`。模型只有实际返回 Tool Call 时才进入下一 step。

### 5.7 工具目录和 run 级工具集合

第一阶段复用现有 `AgentToolRegistry` 作为全局工具目录，不再增加另一套工具容器。

需要增加 run 级选择能力：

```text
AgentDefinition.allowedToolNames
  -> AgentToolRegistry 校验工具存在
  -> 生成本次 run 的 tool specs 和可执行工具视图
  -> 仅把该集合发送给模型
  -> 仅允许执行该集合中的 Tool Call
```

启动期或 Definition 注册期需要校验当前配置下已生效的 Definition：

- 工具名称不能为空。
- 工具名称不能重复。
- Definition 引用的工具必须已经注册。
- 一个 Tool Call 即使存在于全局 registry，只要不在当前 Definition 白名单中，也必须拒绝执行。

Definition 和工具继续使用现有 Spring 条件装配。某功能关闭时，对应工具不进入父场景白名单，也不注册 no-op 工具；某场景已经启用但它声明的必需工具缺失时，应用启动失败。生产完整配置通过架构测试确认九个场景全部存在，精简本地配置只校验实际生效的 Definition。

工具权限门禁继续复用现有 `AgentToolPermissionGuard`，它解决“允许的工具在本次调用中是否需要用户确认”；工具白名单解决“这个场景是否具备该工具能力”。两者职责不同。

### 5.8 Prompt、Context 和输出边界

提示词继续复用现有 `ManagedSystemPromptResolver`，不新增 Prompt DSL。

Context Assembler 负责读取和转换业务上下文，例如：

- Practice Session、题面和当前练习状态。
- 学习计划草案和修订指令。
- 用户画像召回快照。
- 代码提交和受信题目标签。

Context Assembler 可以依赖业务 repository 端口，但属于 `mentor-application`，不能进入 `agent-core` 或 `agent-runtime`。

Context Assembler 接收的输入必须已经通过 Use Case 的资源存在性、用户归属和业务状态校验。它可以继续读取题面、历史消息、画像快照等受信数据，但不能把“当前用户是否有权访问这个 session”之类的授权判断推迟到 Runtime 准入之后。

输出继续复用现有能力：

- 普通聊天和讲解使用文本输出。
- 学习计划、代码 Review 和画像更新使用现有 JSON Schema 输出。
- 业务输出校验和业务落库仍由 application service 完成。

### 5.9 子 Agent 调用

代码 Review 和自述画像更新当前发生在 Practice loop 的工具内部。迁移后，工具继续负责读取受信上下文，但不再直接调用 `AiCompletionGateway`，而是通过 `AgentRuntime` 发起另一个正式业务场景：

```text
PRACTICE_CHAT run
  -> Agent Tool
       -> AgentRuntime.execute(PRACTICE_CODE_REVIEW)
            -> 无工具 loop
```

子调用必须携带：

- 自己的业务场景和模型路由。
- 父 run id。
- 父 step index。
- 受信 user id。
- `CHILD` 调用模式。

子调用不再次消耗用户共享额度，也不获取父 run 已持有的用户级运行锁；它仍检查动态 AI 开关，按自己的 `AiBusinessScenario` 路由模型并单独记录 Token。父工具运行在 Agent 工作线程时，子 Runtime 在当前线程内联执行，不再次提交同一个 `ManagedAgentExecutor`。

每个子调用创建归属于同一用户的独立 task、turn 和 run，并通过 `parentRunId + parentStepIndex` 关联父 run。它不复用父会话 task，因此结构化 Review 输出不会进入父 Practice Chat 的历史消息。

这样可以保留当前独立模型路由、调用记账和审计语义，同时统一执行入口。第一阶段不增加通用多 Agent 调度、递归规划或子 Agent 并行执行能力。

## 6. 模块职责

### 6.1 agent-core

保留稳定、业务无关的公共契约：

- `AgentRuntime` 接口。
- `AgentKey`、Agent Invocation、Agent Definition 契约。
- `AgentInvocationMode`、类型校验错误和通用治理关联模型。
- Agent Result、Output 和 Stream Event。
- Agent Tool、权限、生命周期和通用运行态模型。
- Agent loop 所需的业务无关端口，包括可判断当前执行线程归属的 `AgentExecutor`。

不应包含：

- `AiBusinessScenario`。
- Practice、学习计划、题目或画像字段。
- Spring、数据库和 provider SDK。

### 6.2 agent-runtime

新增 `agent-runtime` Maven 模块，提供默认实现：

- `DefaultAgentRuntime`。
- `AgentDefinitionRegistry`。
- run 级工具选择和 Definition 校验。
- 现有 AI 治理准入、路由和终态服务的统一调用。
- Agent task、turn 和 run 的统一准备。
- Agent executor 调度和工作线程内联判断。
- 可在当前线程运行的同步 Agent loop 执行内核。
- 同步结果收集和流式事件发布。
- 通用运行参数解析。

迁移初期不要求把 `AgentLoopRunner` 一次性物理移动到新模块，但必须先把“同步 loop 控制流”和“提交 executor”拆开。`DefaultAgentRuntime` 可以复用现有 runner 内部实现或提取出的 package，但不能通过再次订阅现有 `stream()` 来实现工作线程内的子调用，否则仍会二次提交 executor。

当前阶段 `agent-runtime` 不包含 Workflow Engine、Strategy Registry 或多 Agent Scheduler。

### 6.3 ai-governance

继续负责：

- `AiBusinessScenario` 目录。
- 准入、额度和调用上下文。
- 模型路由和执行快照。
- LLM 调用记账。

`DefaultAgentRuntime` 直接编排现有 `AiRunAdmissionService`、`AiRunLifecycleService`、模型路由和 LLM 记账能力：

- `USER_ENTRY` 继续走完整准入，使用用户共享 `ALL` 额度并获取用户级运行锁。
- `CHILD` 和 `BACKGROUND` 不走额度消费和用户锁，但继续检查动态开关、解析自己的模型路由并写调用级 Token 台账。
- 三种模式都使用自己的 `AiBusinessScenario`，父子关联不能替代子场景的独立路由。

业务代码不再直接使用 `AiCompletionGateway`。该接口只作为迁移期旧调用兼容边界，不能成为新 Runtime 内的第二执行模式；对应场景迁移完成后删除生产注入点。

### 6.4 agent-persistence-postgres

继续负责通用 Agent 运行态持久化：

- task、turn、message、run、step 和 trace。
- 通用 run metadata 和最终输出。
- 通用持久化 observer。

本次不把 `agent_run` 从 task/turn 结构中拆出，也不把现有外键改为可空。统一后的语义是：

- `agent_task` 是 Agent 上下文与审计隔离单元，不只代表前端可见的多轮聊天。
- `agent_turn` 是该隔离单元内的一次逻辑调用。
- `agent_run` 是该逻辑调用的一次实际执行尝试；同一逻辑调用重试时复用 turn 并增加 run attempt。
- `agent_step` 是 run 内的一次模型调用。

会话型场景继续复用业务对象已经关联的 task，并为每次用户消息创建 turn。单次结构化推理、`CHILD` 和 `BACKGROUND` 调用则创建归属于受信 `userId` 的独立 task 和 turn，再在其下创建 run：

```text
PRACTICE_CHAT conversation task
  -> conversation turn
       -> parent PRACTICE_CHAT run

independent child audit task (same userId)
  -> child logical turn
       -> PRACTICE_CODE_REVIEW run
            parentRunId = parent PRACTICE_CHAT run
            parentStepIndex = triggering tool step

independent background audit task (same userId)
  -> background logical turn
       -> CODE_REVIEW_PROFILE_UPDATE run
```

子调用不得复用父会话 task，避免内部结构化输出进入 Practice Chat 的上下文召回。内部 task/turn 允许保存模型协议中的 user/assistant 消息和结构化最终输出，用于审计和排障，但前端会话查询仍只通过业务对象显式关联的 task id 读取，不扫描某个用户的全部 Agent task。

`agentKey`、调用模式、父 run 和父 step 等跨模块字段必须使用 `agent-core` 中的常量或枚举写入稳定列或 metadata，不能继续散落字符串字面量。

Practice 专属的 assistant message metadata 投影应迁出通用 observer，由 `mentor-application` 定义业务投影逻辑，装配层注册对应 observer 或 contributor。

### 6.5 ops-observability

继续负责通用 Agent 指标、日志和 Trace。

场景标识直接来自统一 Agent 描述或治理场景，不再维护独立字符串 switch。新增业务场景不应要求修改通用 observer 才能获得正确 source。

### 6.6 mentor-application

负责：

- 业务 Use Case 和事务。
- 各业务场景的 Agent Definition。
- Prompt Provider 和 Context Assembler。
- 业务 Agent Tool 和权限 hook。
- Agent 输出到业务结果的校验、转换和持久化。

建议按业务域组织，而不是建立一个平铺的大型 agent 目录：

```text
mentor/application/practice/agent/
mentor/application/learningplan/agent/
mentor/application/profile/agent/
mentor/application/explanation/agent/
```

当前阶段不新增 `mentor-agent` Maven 模块，避免产生不必要的模块和依赖拆分。

### 6.7 mentor-api

作为 Spring composition root，负责：

- 装配 `AgentRuntime`、Definitions、工具和 adapters。
- HTTP 认证、请求校验和 DTO 映射。
- 将 `AgentStreamEvent` 映射为 SSE。
- 将应用异常映射为 HTTP 错误。

controller 不负责选择 loop、拼接 Prompt、决定工具集合或直接调用 `AiRunAdmissionService`。业务前置校验由 Use Case 完成，正式 AI 准入由 Runtime 完成。

### 6.8 persistent-queue

继续负责后台消息派发和批处理生命周期。后台消费者调用 application service，由 application service 使用 `BACKGROUND` Invocation 通过 `AgentRuntime.execute` 执行对应 Agent，不直接调用模型 gateway，也不消耗用户交互额度。

## 7. 目标模块依赖

```text
llm-core
llm-openai -> llm-core

agent-core -> llm-core
ai-governance -> agent-core + llm-core
agent-runtime -> agent-core + llm-core + ai-governance

agent-persistence-postgres -> agent-core
ops-observability -> agent-core

mentor-application
  -> domain
  -> agent-core
  -> ai-governance

mentor-api
  -> mentor-application
  -> agent-runtime
  -> agent-persistence-postgres
  -> ops-observability
  -> ai-governance
  -> llm-openai
```

禁止形成以下依赖：

```text
agent-core -> mentor-application
agent-runtime -> mentor-application
agent-persistence-postgres -> mentor-application
ops-observability -> mentor-application
mentor-application -> agent-runtime implementation classes
```

`mentor-application` 只依赖 `agent-core` 中的 `AgentRuntime` 接口，默认实现由 `mentor-api` 装配。

## 8. 现有场景的目标形态

| 业务场景 | Runtime 调用 | 调用模式 | task/turn | 工具集合 |
|---|---|---|---|---|
| `MENTOR_CONVERSATION` | `stream` | `USER_ENTRY` | 复用会话 task，新建 turn | Definition 明确声明通用会话工具 |
| `TOPIC_EXPLANATION` | `stream` 或 `execute` | `USER_ENTRY` | 独立审计 task/turn | 默认空集合；确有计算或题库需求时显式加入 |
| `PRACTICE_CHAT` | `stream` | `USER_ENTRY` | 复用 Practice task，新建 turn | 题面、代码 Review、画像更新等 Practice 工具 |
| `LEARNING_PLAN_DRAFT` | `stream` | `USER_ENTRY` | 独立审计 task/turn | 学习计划需要的题库工具 |
| `LEARNING_PLAN_REVISION` | `stream` | `USER_ENTRY` | 独立审计 task/turn | 学习计划需要的题库工具 |
| `LEARNING_PLAN_EXTENSION` | `stream` | `USER_ENTRY` | 独立审计 task/turn | 学习计划需要的题库工具 |
| `PRACTICE_CODE_REVIEW` | `execute` | `CHILD` | 独立审计 task/turn，关联父 run/step | 空集合 |
| `LEARNER_DECLARED_PROFILE_UPDATE` | `execute` | `CHILD` | 独立审计 task/turn，关联父 run/step | 空集合 |
| `CODE_REVIEW_PROFILE_UPDATE` | `execute` | `BACKGROUND` | 独立审计 task/turn | 空集合 |

工具白名单的最终内容以当前业务实际需要为准。迁移时不因为工具已经全局注册就默认加入某个 Definition。

## 9. 渐进迁移方案

### 阶段一：稳定核心契约和 Definition Registry

- 在 `agent-core` 定义 `AgentRuntime`、`AgentKey<I>`、`AgentDefinition<I>`、`AgentInvocation<I>`、`AgentInvocationMode`，并复用通用 `AgentRunResult`。
- 输入类型由 `AgentKey.inputType()` 约束，输出继续返回通用 `AgentOutput`。
- 新增 `agent-runtime` 模块和 `AgentDefinitionRegistry`。
- 按当前 Spring 配置收集已生效 Definition，校验 key、输入类型、工具名称和 `maxSteps`。
- 为生产完整配置增加架构测试，确认九个 `AiBusinessScenario` 都有且只有一个 Definition。
- 暂不迁移业务调用者，不引入运行时 Definition 数据库或管理接口。

完成标准：Definition 注册错误在启动期失败，业务代码可以编译期安全地构造 Invocation。

### 阶段二：拆分同步 loop 内核并实现 run 级工具隔离

- 从现有 `AgentLoopRunner` 中拆出可在当前线程执行的同步 loop 控制流，保持事件顺序、生命周期和错误语义不变。
- 让 executor 提交和单订阅者 Publisher 成为同步 loop 外层适配器。
- 为 `AgentExecutor` 增加显式工作线程归属判断，由 `ManagedAgentExecutor` 使用线程上下文标记实现。
- 为现有 `AgentToolRegistry` 增加按名称选择的 run-local 只读视图。
- 每个 LLM step 只发送当前 Definition 白名单中的 tool specs，也只允许执行该集合中的 Tool Call。
- 空工具 Definition 固定 `NONE + maxSteps=1`，非空工具 Definition 固定 `AUTO`。
- 将全局 `maxSteps` 改为硬上限，每个 Definition 显式声明自己的值。

完成标准：新增一个 Agent Tool 不会自动出现在其他 Agent 的模型请求中；同步 loop 可在测试线程、Agent 工作线程或 executor 任务中得到相同结果。

### 阶段三：建立 Runtime 治理、调度与审计闭环

- 实现 `DefaultAgentRuntime.execute` 和 `stream`，两者调用同一个同步 loop 内核。
- 当前线程是 Agent 工作线程时内联执行，否则提交 `ManagedAgentExecutor`。
- Runtime 集成现有 AI 治理：
  - `USER_ENTRY` 走完整准入、共享额度和用户锁。
  - `CHILD`、`BACKGROUND` 只做动态开关、独立模型路由和调用记账。
- 将 `AiBusinessScenario`、`AiRunSource` 和当前用户共享 `ALL` 额度语义原样接入，不增加场景级配额。
- 统一创建 task、turn 和 run：会话场景复用既有 task，非会话调用创建归属于用户的独立审计 task/turn。
- 保存父 run、父 step、Agent key 和调用模式等稳定关联字段。
- Runtime 对准入后的上下文组装、持久化、executor 拒绝、取消和模型失败统一执行治理终态与资源释放。

完成标准：一个测试 Definition 可以分别以三种调用模式执行，并得到正确的额度、运行锁、路由、记账、task/turn/run 和终态行为。

### 阶段四：迁移现有用户入口场景

依次迁移：

1. `TOPIC_EXPLANATION`。
2. `MENTOR_CONVERSATION`。
3. `PRACTICE_CHAT`。
4. `LEARNING_PLAN_DRAFT`。
5. `LEARNING_PLAN_REVISION`。
6. `LEARNING_PLAN_EXTENSION`。

每个场景迁移时同步完成：

- 将资源存在、用户归属和业务状态校验放在 Runtime 调用之前。
- 将 controller 中的直接 `AiRunAdmissionService` 调用收进 Runtime。
- 建立类型化业务输入、Definition、Prompt Provider、Context Assembler、工具白名单和输出契约。
- 保持原 SSE 事件、幂等、会话锁和业务落库行为。
- 同一个请求只走新旧入口中的一个，不做双执行或影子模型调用。

迁移后 application service 只调用 `AgentRuntime`，不直接注入 `AgentLoopRunner` 或 `AgentRunner`。

### 阶段五：迁移子调用和后台无工具场景

依次迁移：

1. `PRACTICE_CODE_REVIEW`。
2. `LEARNER_DECLARED_PROFILE_UPDATE`。
3. `CODE_REVIEW_PROFILE_UPDATE`。

这些 Definition 使用空工具集合和单 step loop：

- Review 和自述画像更新使用 `CHILD`，在父 Agent 工作线程内联执行。
- 后台 Review 画像更新使用 `BACKGROUND`。
- 三者都创建归属用户的独立审计 task/turn 和独立 run。
- 子 run 关联父 run/step，但不继承父场景的模型快照，也不重复消耗用户共享额度。
- 结构化输出仍由原 application service 做领域映射、并发陈旧检测和事务落库。

业务代码停止直接注入 `AiCompletionGateway`。迁移期间旧场景可以继续使用该 gateway，但已迁移场景不得从 Runtime 内再次绕回 direct completion。

### 阶段六：清理兼容入口和业务泄漏

- 将 Practice 专属 metadata 投影移出 `PersistentAgentRunObserver`。
- 让 `AgentOpsObserver` 使用统一场景描述，删除业务 source 字符串 switch。
- 将 `title`、`topic` 等通用展示字段改为中性命名。
- 删除无生产调用的旧 `AgentRunner` 入口或兼容层。
- 确认 `mentor-application` 不再直接引用具体 runner 和 completion gateway。
- 删除 controller 对正式 AI 场景的直接治理准入编排。
- 在调用者完成迁移后，再决定是否删除 `AgentLoopRunner` 外观或把剩余实现物理移动到 `agent-runtime`；该物理整理不是业务迁移的前置条件。

## 10. 测试与验收

### 10.1 核心测试

- 当前生效的 Definition key 重复时启动失败。
- Agent key 与 Invocation 输入类型不匹配时，在 loop 启动前产生稳定类型错误。
- 当前生效的 Definition 引用未注册工具时启动失败；未启用场景不要求注册其可选工具。
- 完整生产配置下九个 `AiBusinessScenario` 都存在唯一 Definition。
- Definition 的 `maxSteps` 小于 `1` 或超过全局硬上限时启动失败。
- 空工具 Definition 的 LLM 请求不携带工具，并在第一步返回最终结果。
- 空工具 Definition 强制使用 `NONE + maxSteps=1`，非空工具 Definition 使用 `AUTO`。
- 有工具 Definition 只暴露白名单中的工具。
- 模型调用全局存在但不在当前白名单中的工具时，run 被拒绝并产生稳定错误。
- 工具执行和结果回填继续遵循现有 loop 事件顺序。
- 文本和 JSON Schema 输出继续产生正确 `AgentOutput`。
- `execute` 和 `stream` 使用同一 loop，并得到一致最终结果。
- 非 Agent 工作线程调用 Runtime 时任务提交 executor；Agent 工作线程调用 `execute` 时当前线程内联执行。
- 工作线程识别使用执行上下文标记，修改线程名前缀不影响判断结果。
- executor 提交失败、取消和上下文组装失败都产生唯一终态，并执行治理资源清理。

### 10.2 治理与持久化测试

- `USER_ENTRY` 消耗一次用户共享 `ALL` 额度并获取、释放用户级运行锁。
- `CHILD` 和 `BACKGROUND` 不消耗用户共享额度，也不获取用户级运行锁。
- 子 Agent 使用自己的 `AiBusinessScenario` 路由和记账，不继承父 run 的模型快照。
- 一次 Practice Chat 同时触发代码 Review 和画像更新时，用户交互次数只增加一次，三个模型调用分别记录 Token。
- 业务资源校验失败时不调用 Runtime、不消耗额度；Runtime 准入后的失败按现有语义记录失败并释放锁。
- 会话场景复用既有 task 并创建新 turn。
- 单次、子调用和后台场景创建归属于受信用户的独立 task/turn/run。
- 子 run 保存父 run 和父 step，且其消息不会出现在父会话 task 的历史查询中。
- 同一逻辑调用重试时复用 task/turn 并增加 run attempt，不创建新的伪交互。

### 10.3 场景回归

- Practice Chat 的题面、Review 权限和画像更新流程不回归。
- 学习计划草案、修订和扩展的流式事件及最终结构化结果不回归。
- Topic Explanation 的 SSE 协议不回归。
- Code Review 和画像更新的 JSON Schema、落库和失败降级不回归。
- 后台画像队列的批量、最多一次消费和记账语义不回归。

### 10.4 架构验收

- 完整生产装配下，九个 `AiBusinessScenario` 都存在唯一 Agent Definition；精简配置只校验已生效场景。
- 九个正式 AI 场景都通过 `AgentRuntime` 执行。
- `mentor-application` 不直接依赖 `AgentLoopRunner`、`AgentRunner`、`AiCompletionGateway` 或正式场景的治理准入服务。
- 每个 Definition 都显式声明工具白名单，即使集合为空。
- `agent-core` 不依赖 `ai-governance` 或 mentor 业务包；`agent-runtime` 可以依赖 `ai-governance`，但不能依赖 mentor 业务包。
- 通用 persistence 和 ops observer 不通过业务字符串 switch 判断场景。
- Runtime 和 loop 不通过线程名称判断当前执行线程归属。

## 11. 风险与控制

### 11.1 一次迁移范围过大

统一入口、工具隔离、completion 迁移和 observer 清理应分阶段提交。每个阶段保持可编译、可测试，不进行一次性包结构重写。

### 11.2 Runtime 吸收业务职责

AgentRuntime 只执行已经通过业务前置校验的 Agent 调用。业务归属校验、事务和结果落库继续保留在 application service，避免把 Runtime 变成通用业务工作流引擎。Context Assembler 可以读取受信业务数据，但不能接管授权判断。

### 11.3 工具白名单遗漏

迁移不能按照全局 registry 推断场景工具。每个 Definition 的工具集合需要结合当前 Prompt 和实际调用链逐项确认，并通过场景测试验证。

### 11.4 子调用治理语义丢失

Review 和画像更新必须继续使用自己的 `AiBusinessScenario` 解析模型并单独记账，不能继承父 `PRACTICE_CHAT` 的模型快照。父子 run 关联只用于 trace，不替代独立治理；同时 `CHILD` 不能再次消费共享额度或获取用户锁。

### 11.5 子调用重复提交 executor

如果父工具调用 `AgentRuntime.execute` 时仍提交同一个 `SynchronousQueue` executor，一个逻辑请求会同时占用父、子两条线程，饱和时还可能直接拒绝。Runtime 必须使用 executor 提供的显式线程上下文判断，在 Agent 工作线程内联执行子 run，并通过测试覆盖线程名前缀变化和嵌套调用。

### 11.6 准入和业务校验顺序错误

如果 controller 在业务资源校验前执行 AI 准入，无效 Session 或越权请求也可能消耗额度并遗留用户锁。所有业务前置校验必须在构造 Invocation 前完成；准入后的所有异常则必须由 Runtime 统一进入失败终态和资源释放路径。

### 11.7 审计 task/turn 数量增长

单次、子调用和后台 Agent 都会创建独立 task/turn，记录数量将高于真实前端会话数。这是本次明确接受的审计语义，不应再用全部 `agent_task` 或 `agent_turn` 行数直接代表用户聊天次数。前端会话读取继续以业务对象显式关联的 task id 为边界，运行诊断清理需要覆盖独立审计 task 下的关联记录。

### 11.8 条件装配掩盖生产缺失

按当前配置校验可以支持精简本地环境，但也可能让生产环境遗漏 Definition 或工具。需要用固定的完整生产装配测试验证九个场景及其必需工具，而不能只依赖开发环境启动成功。

## 12. 完成定义

满足以下条件时，本次统一 Agent 底座重构完成：

- 业务层只通过 `AgentRuntime` 发起正式 AI 场景。
- 所有场景通过 Agent Definition 集中声明 Prompt、Context、工具、输出和 loop 参数。
- 无工具调用和工具调用共享同一个 Agent loop。
- `execute` 和 `stream` 共享同步 loop 内核，Agent 工作线程中的子调用不会再次提交 executor。
- 每个 run 只向模型暴露当前 Definition 允许的工具。
- 用户入口、子调用和后台调用保持现有额度语义，并分别获得正确的模型路由、记账和审计记录。
- 非会话调用复用归属于用户的独立 task/turn，不进入父会话上下文。
- 现有治理、模型路由、记账、权限、生命周期、持久化和 SSE 行为保持兼容。
- 通用 runtime、persistence 和 ops 代码不理解 Practice、学习计划或画像业务字段。
- 完整生产装配下九个正式场景都有唯一 Definition，业务层不再直接依赖旧 runner、completion gateway 或治理准入入口。
- 当前代码中不包含未被实际需求驱动的 Workflow、DAG、多 Agent 或动态插件实现。
