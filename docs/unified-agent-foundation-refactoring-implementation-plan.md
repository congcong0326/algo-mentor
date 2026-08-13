# 统一 Agent 底座重构实施计划

更新时间：2026-07-29

状态：`UAF-00` 至 `UAF-13` 已全部完成；最终门禁已通过。

最终验证：`make backend-test`、`make backend-build`、`git diff --check`（2026-07-29）。

依据：

- `docs/unified-agent-foundation-refactoring-design.md`
- `docs/agent-runtime-refactoring-implementation-plan.md`
- `docs/agent-thread-model-refactoring-design.md`
- `docs/agent-loop-lifecycle-design.md`

任务入口：

- `docs/unified-agent-foundation-refactoring-tasks/README.md`
- `docs/unified-agent-foundation-refactoring-tasks/CURRENT.md`

## 1. 计划目的

本计划把统一 Agent 底座重构拆成可以连续执行、逐项验证和随时恢复的研发任务。目标不是先铺完所有抽象再一次迁移九个场景，而是遵循以下路径：

```text
冻结现有行为
  -> 稳定核心契约
  -> 拆出同步 loop 和线程调度
  -> 实现 run-local 工具隔离
  -> 打通审计持久化和三类治理模式
  -> 用 TOPIC_EXPLANATION 完成首个完整竖切
  -> 逐场景迁移
  -> 删除旧入口并建立架构门禁
```

实施过程允许在同一个长任务中自动连续推进，但每个研发任务必须形成独立验证门禁和压缩后的交接状态，不能依赖无限增长的聊天历史继续工作。

## 2. 当前实现基线

截至本文编写时，仓库具备以下基础：

- `AgentLoopRunner` 已实现流式模型调用、工具执行、生命周期、权限、压缩、结构化最终输出和取消。
- `ManagedAgentExecutor` 使用执行组容量总和推导的物理上限、`SynchronousQueue + AbortPolicy`，并通过受信执行组与线程上下文识别 Agent 工作线程。
- `AgentToolRegistry` 只有全量 `specs()` 和按名称 `find()`，尚无 run-local 只读视图。
- `AgentRequest` 已承载 messages、metadata 和 `AgentExecutionOptions`，但最大 step 和工具集合仍由 runner 构造期固定。
- `AgentConversationRepository` 和 PostgreSQL 实现已能创建或复用 task、turn、user message 和 run。
- `agent_run` 已有 `parent_run_id`、`trigger_type`、`attempt_no` 和全局唯一幂等键，但没有稳定 `agent_key` 与 `parent_step_index` 字段。
- `AiRunAdmissionService` 对用户入口执行共享 `ALL` 额度、用户锁、路由和审计；`AiGovernedCompletionService` 已区分 `USER_ENTRY / PARENT_RUN / BACKGROUND`。
- 六个场景直接依赖 loop runner，三个结构化推理场景直接依赖 `AiCompletionGateway`。
- controller 仍直接执行部分 AI 准入，业务校验和额度消耗顺序不统一。

旧的 `docs/agent-runtime-refactoring-implementation-plan.md` 已完成运行态模块与 PostgreSQL 持久化归属调整。本计划不重复迁移已完成的 MyBatis、Flyway、observer 和 repository 模块，只在其上补充统一执行入口需要的能力。

## 3. 实施边界

本计划包含：

- 新增 `agent-runtime` Maven 模块。
- 类型化 `AgentKey<I>`、Definition、Invocation 和 Runtime 契约。
- 同步 Agent loop 内核与 executor 线程上下文判断。
- run-local 工具白名单和 Definition 级 `maxSteps`。
- `USER_ENTRY / CHILD / BACKGROUND` 治理模式。
- 用户归属的独立审计 task/turn/run。
- 九个正式 `AiBusinessScenario` 的渐进迁移。
- 旧 runner、completion gateway 和 controller 准入入口清理。
- 核心、持久化、治理、场景回归和架构门禁测试。

本计划不包含：

- Workflow、DAG、多 Agent 调度或子 Agent 并行。
- Definition 数据库、管理页面、动态插件或远程工具协议。
- Definition 级统一 run timeout。
- 场景级用户额度。
- 前端产品功能改造；只有现有 SSE 契约被意外影响时才修改前端。
- 对现有 task/turn/run 表做解耦重构。

## 4. 固定实施决策

1. 当前 `AgentKey.value` 直接复用 `AiBusinessScenario.code`。
2. Definition 只约束输入类型，Runtime 返回现有 `AgentRunResult` 和 `AgentOutput`。
3. 业务资源存在、用户归属和状态校验先于 Runtime 调用。
4. `USER_ENTRY` 消耗一次共享 `ALL` 额度并获取用户锁。
5. `CHILD` 和 `BACKGROUND` 不消耗交互额度、不获取用户锁，但独立路由和记账。
6. Agent 工作线程中的同步子调用内联执行，非 Agent 线程提交 executor。
7. 空工具 Definition 固定 `NONE + maxSteps=1`，非空工具 Definition 固定 `AUTO`。
8. 全局 `maxSteps` 只作为硬上限。
9. Definition 和工具按当前 Spring 配置条件装配。
10. 单次、子调用和后台调用创建归属于受信用户的独立 task/turn。
11. 子 task 不复用父会话 task，run 使用父 run 与父 step 关联。
12. Runtime 内不得调用 `AiCompletionGateway` 形成第二种执行模式。
13. 同一逻辑调用重试复用 turn，创建新的 run attempt。
14. 当前实现优先复用 `agent_run.trigger_type` 表示调用模式；数据库新增 `agent_key` 和 `parent_step_index`，除非实施时发现已有等价稳定列。

第 14 条在执行 `UAF-04` 时需要通过最新 schema 再确认。若仓库在此之前已有新迁移，必须重新选择全局唯一 Flyway 版本号，不得修改历史迁移。

## 5. 任务总览

详细状态和入口由任务目录 README 管理。

| ID | 任务 | 主要交付 | 直接依赖 |
| --- | --- | --- | --- |
| [`UAF-00`](unified-agent-foundation-refactoring-tasks/UAF-00-baseline-and-contract-freeze.md) | 基线与行为冻结 | 当前调用清单、特征测试、基线结果 | 无 |
| [`UAF-01`](unified-agent-foundation-refactoring-tasks/UAF-01-core-contracts-and-module-skeleton.md) | 核心契约与模块骨架 | `agent-runtime`、类型化契约、Definition Registry | `UAF-00` |
| [`UAF-02`](unified-agent-foundation-refactoring-tasks/UAF-02-synchronous-loop-and-executor-context.md) | 同步 loop 与线程模型 | 同步执行内核、executor 工作线程标记 | `UAF-01` |
| [`UAF-03`](unified-agent-foundation-refactoring-tasks/UAF-03-run-local-tools-and-loop-policy.md) | run-local 工具与 loop policy | 工具只读视图、白名单、场景 maxSteps | `UAF-02` |
| [`UAF-04`](unified-agent-foundation-refactoring-tasks/UAF-04-audit-run-persistence.md) | 审计运行准备与持久化 | 通用 task/turn/run 准备、父子字段、重试语义 | `UAF-01` |
| [`UAF-05`](unified-agent-foundation-refactoring-tasks/UAF-05-runtime-governance-modes.md) | Runtime 治理模式 | 三类调用模式、路由、额度、锁和终态适配 | `UAF-01`、`UAF-04` |
| [`UAF-06`](unified-agent-foundation-refactoring-tasks/UAF-06-default-runtime-and-topic-pilot.md) | Default Runtime 与 Topic 竖切 | execute/stream、Spring 装配、首个生产场景 | `UAF-02` 至 `UAF-05` |
| [`UAF-07`](unified-agent-foundation-refactoring-tasks/UAF-07-mentor-conversation-migration.md) | Mentor Conversation 迁移 | 会话 task、幂等、task 锁与 Runtime 接线 | `UAF-06` |
| [`UAF-08`](unified-agent-foundation-refactoring-tasks/UAF-08-learning-plan-scenarios-migration.md) | Learning Plan 场景迁移 | draft/revision/extension 三个 Definition | `UAF-06` |
| [`UAF-09`](unified-agent-foundation-refactoring-tasks/UAF-09-practice-chat-root-migration.md) | Practice Chat 根场景迁移 | 业务预检、会话上下文、工具与权限 | `UAF-07`、`UAF-08` |
| [`UAF-10`](unified-agent-foundation-refactoring-tasks/UAF-10-practice-code-review-child-agent.md) | Practice Code Review 子 Agent | `CHILD` 内联执行、父子 run、结构化 Review | `UAF-09` |
| [`UAF-11`](unified-agent-foundation-refactoring-tasks/UAF-11-declared-profile-child-agent.md) | Declared Profile 子 Agent | `CHILD` 画像决策与 stale retry | `UAF-10` |
| [`UAF-12`](unified-agent-foundation-refactoring-tasks/UAF-12-code-review-profile-background-agent.md) | Code Review Profile 后台 Agent | `BACKGROUND` 队列消费与独立审计 | `UAF-11` |
| [`UAF-13`](unified-agent-foundation-refactoring-tasks/UAF-13-old-entry-cleanup-and-final-gates.md) | 旧入口清理与最终门禁 | 删除旧依赖、observer 清理、全量验收 | `UAF-12` |

## 6. 依赖主链与波次

```text
UAF-00
  -> UAF-01
       -> UAF-02 -> UAF-03
       -> UAF-04 -> UAF-05
                    \       /
                     UAF-06
                       -> UAF-07
                       -> UAF-08
                            -> UAF-09
                                 -> UAF-10
                                      -> UAF-11
                                           -> UAF-12
                                                -> UAF-13
```

| 波次 | 任务 | 波次出口 |
| --- | --- | --- |
| A | `UAF-00` 至 `UAF-03` | loop、线程和工具边界稳定，旧场景仍可运行 |
| B | `UAF-04` 至 `UAF-06` | Runtime 完整竖切落地，Topic 不再依赖旧入口 |
| C | `UAF-07` 至 `UAF-09` | 所有用户入口场景进入 Runtime |
| D | `UAF-10` 至 `UAF-12` | 子调用和后台结构化推理进入同一 loop |
| E | `UAF-13` | 旧入口删除，架构门禁与全量测试通过 |

除代码冲突或同一个基础类被并行修改外，本计划默认按 ID 顺序串行执行。对单人或单模型“一把梭哈”实施而言，串行比并行更容易维持可恢复状态。

## 7. 上下文管理协议

### 7.1 每个任务只加载必要上下文

开始任务时只读取：

1. 仓库根 `AGENTS.md`。
2. `docs/code-index.md` 中与本任务相关的条目。
3. `docs/unified-agent-foundation-refactoring-tasks/CURRENT.md`。
4. 当前任务文件。
5. 当前任务文件“必须读取”列出的代码和测试。

除非当前任务明确要求，不要重新通读：

- 全部 14 个任务文件。
- 完整历史实施计划。
- 已完成任务涉及的全部代码。
- `target`、`dist`、`build`、`node_modules`、`.m2`、`.npm` 等生成目录。

搜索入口优先使用 `rg` 和 `rg --files`，先定位再读取。单个任务开始时建议把实际打开的生产文件控制在 12 个以内；超过时先按职责拆分搜索结果，不要一次输出所有文件内容。

### 7.2 任务完成后压缩上下文

每个任务完成后必须：

1. 更新任务文件状态和不超过 20 行的完成备注。
2. 更新任务 README 中的一行状态和测试摘要。
3. 覆盖写 `CURRENT.md`，不得追加历史日志。
4. `CURRENT.md` 保持在 120 行以内，只保留：
   - 已完成任务 ID；
   - 当前稳定契约；
   - 最近一次任务的关键改动；
   - 测试结果；
   - 未解决但不阻塞的事项；
   - 下一个任务和必读文件。
5. 开始下一任务时以 `CURRENT.md` 和代码现状为准，不依赖聊天中旧的工具输出。

详细实现历史保存在对应任务文件的完成备注和 Git diff 中，不复制到 `CURRENT.md`。

### 7.3 长任务自动续跑规则

当用户要求连续完成全部计划时，执行模型应循环执行：

```text
读取 CURRENT.md
  -> 找到第一个 PENDING 任务
  -> 标记 IN_PROGRESS
  -> 只读取该任务上下文
  -> 实施并运行最小相关测试
  -> 更新任务文件、README、CURRENT.md
  -> 释放旧任务细节
  -> 继续下一个任务
```

以下情况才暂停并请求用户决策：

- 需要破坏性删除用户数据或修改已应用 Flyway 历史脚本。
- 设计与当前代码出现无法同时满足的业务语义冲突。
- 同一阻塞经过三种安全方案仍无法推进。
- 需要用户提供外部密钥、真实服务或不可推断的产品选择。

普通编译错误、测试失败、依赖调整、包移动和局部接口选择不构成暂停理由，应在当前任务内解决。

### 7.4 输出和日志控制

- 不把完整 Maven 日志、堆栈或大段 diff 写入任务文档。
- 完成备注只记录命令、结果、失败原因摘要和关键文件。
- 大测试输出先用测试报告和 `rg` 定位，避免重复读取完整日志。
- 每个任务只保留一个进行中步骤；测试运行较久时等待完成，不提前进入下一任务。

### 7.5 任务内上下文水位保护

任务边界之外依赖 `CURRENT.md`，任务执行过程中也必须保持可恢复：

- 开始任务后立即把状态板中的该任务标记为 `IN_PROGRESS`，并在 `CURRENT.md` 记录当前目标、已打开的关键文件和待验证命令。
- 完成契约修改、生产接线、目标测试三个里程碑中的任意一个后，覆盖更新一次 `CURRENT.md`；进行中摘要控制在 40 行以内。
- 读取 diff 时先用 `git diff --stat`、`git diff --name-only` 和限定路径的 `git diff -- <path>`，不得默认输出全仓库完整 diff。
- 测试失败先读 Surefire/Failsafe 摘要和单个失败报告，不重复加载完整 Maven 日志。
- 当前任务实际需要展开超过任务文件建议上限的生产/测试文件时，先按“契约、接线、验证”分三段处理；每段结束后把结论压缩进 `CURRENT.md`，再释放前一段细节。
- 发生自动上下文压缩前，优先完成上述 checkpoint。恢复后只读取 `CURRENT.md`、当前任务文件、`git status` 和限定 diff，不从聊天历史重建状态。
- 任务标记 `DONE` 后，后续任务不得依赖先前工具输出；只允许读取直接依赖任务的完成备注，且每个备注不超过 20 行。

## 8. 每任务统一实施门禁

每个任务必须满足：

- 只修改任务声明的主要模块；必要的跨模块契约改动需在完成备注说明。
- 不删除或回退用户已有改动。
- 新增跨模块 key、状态、工具名和 metadata 字段时使用常量或枚举。
- 行为变更先补或同步修改测试。
- 目标模块最小测试通过。
- `git diff --check` 无问题。
- 不启动 Vite 或后端开发服务器。
- 未完成当前任务时不开始下一个任务。

波次结束时额外执行：

- 波次 A、B、C、D：`make backend-test`。
- 波次 E：`make backend-test` 和 `make backend-build`。
- 修改前端时才运行 `make frontend-test` 和 `make frontend-build`。

## 9. 数据库与兼容策略

- 实施 `UAF-04` 前使用 `rg` 重新确认所有模块最新 Flyway 版本；当前最新为 `V44`，但计划不预占后续版本号。
- 不修改 `V2`、`V3`、`V31` 等历史 Agent migration。
- 优先为 `agent_run` 增加 `agent_key` 和 `parent_step_index`，复用 `trigger_type` 表示调用模式。
- 历史 run 的 `agent_key` 可以为空或按可证明规则回填；不得凭不可靠 metadata 猜测。
- task、turn 和 message 外键保持非空。
- 新旧入口在迁移期可以同时存在于代码库，但同一业务场景的单次请求只能选择其中一个入口。
- 不做影子 LLM 调用或双写模型结果。
- 每个场景迁移完成后立即删除该场景对旧入口的生产依赖，不等到最后统一切换。

## 10. 测试策略

测试按风险分层：

```text
agent-core 单元测试
  -> agent-runtime 单元测试
  -> ai-governance 模式测试
  -> agent-persistence-postgres mapper/repository 测试
  -> mentor-application 场景测试
  -> mentor-api 装配与 SSE 测试
  -> 波次级 backend 全量测试
```

重点必须覆盖：

- loop 事件顺序和最终输出只产生一次。
- 工作线程内联执行和外部线程提交。
- 工具白名单既限制 schema 暴露，也限制实际执行。
- Definition 条件装配和生产完整配置校验。
- 根调用只消耗一次额度。
- child/background 不消耗额度、不获取用户锁。
- 子 run 独立模型路由、Token 记账和父 run/step。
- 非会话 task 归属用户且不进入父会话历史。
- 幂等 replay 与 stale retry 不产生重复业务副作用。
- executor 拒绝、取消、结构化输出错误和准入后失败都释放资源。

## 11. 回滚和故障处理

- 每个任务形成逻辑上可独立提交的 diff，但是否创建 Git commit 由用户决定。
- 基础任务只增加兼容入口，不立即删除旧 API。
- 场景迁移采用单场景切换，回滚时只恢复该场景调用入口。
- 数据库新增列采用向后兼容方式，旧代码可以忽略新列。
- 已写入的新审计 task/turn/run 不在应用回滚时自动删除。
- 若新 Runtime 失败率异常，优先回滚当前场景接线，不回退已经验证稳定的同步 loop、工具视图和持久化字段。

## 12. 最终完成标准

- 14 个任务全部标记 `DONE`。
- 九个正式场景只通过 `AgentRuntime` 发起模型执行。
- `mentor-application` 不直接注入 `AgentLoopRunner`、`AgentRunner` 或 `AiCompletionGateway`。
- 正式 AI controller 不直接调用 `AiRunAdmissionService`。
- execute/stream 使用同一个同步 loop 内核。
- 每个 Definition 有类型化输入、显式工具白名单和场景 maxSteps。
- 三种调用模式的额度、锁、路由、记账和持久化测试通过。
- 通用 observer 不再包含 Practice 等业务字符串分支。
- 完整生产装配包含九个唯一 Definition。
- `make backend-test`、`make backend-build` 和 `git diff --check` 通过。
- `CURRENT.md` 显示无下一个任务和无阻塞事项。
