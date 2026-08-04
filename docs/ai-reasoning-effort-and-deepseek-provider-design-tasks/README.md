# AI Reasoning Effort 与 DeepSeek Provider 任务状态板

依据：

- `docs/ai-reasoning-effort-and-deepseek-provider-design.md`
- `docs/ai-provider-and-model-routing-design.md`
- 当前仓库中的 `llm-core`、`llm-openai`、`ai-governance`、`agent-core`、`mentor-api` 和管理前端实现

状态值：`PENDING`、`IN_PROGRESS`、`DONE`、`BLOCKED`

## 1. 文档目的

本目录把 Reasoning Effort 全链路和 DeepSeek Responses provider 适配拆成 `RDP-00` 至 `RDP-13` 共 14 个可连续执行、分阶段发布、独立验收的研发任务。

这里的“一把梭哈”表示执行器可以不等待人工逐项确认，按依赖顺序连续完成全部仓库研发工作；不表示把 986 行设计原文、全部任务文件、SDK 反编译输出和整个 Agent Runtime 一次性塞进同一个上下文。每个任务都必须留下可恢复的落盘交接，再用干净上下文进入下一项。

真实 DeepSeek E2E 需要外部 API Key。缺少密钥时，代码研发、fixture、测试夹具、运行手册和本地最终门禁仍可连续完成；但 `DeepSeek 无工具发布门禁` 与 `DeepSeek 工具发布门禁` 必须保持 `NOT_RUN`，不得据此宣称可以灰度对应场景。

## 2. 连续执行协议

每次开始或恢复按以下顺序执行：

1. 读取 `CURRENT.md`，确认唯一当前任务、发布门禁状态和首个未完成任务。
2. 读取本文件的执行协议、状态表和当前波次出口。
3. 完整读取 `CONTRACTS.md`；实现期固定决策以该文件为准。
4. 只读取当前任务文件及其“必须读取”清单；先执行任务给出的 `rg`，再按命中打开代码。
5. 运行 `git status --short` 和 `git diff --stat`，辨认用户已有改动，禁止回退无关变更。
6. 实现当前任务，运行最小相关测试，更新任务完成备注、本状态板和 `CURRENT.md`。
7. 下一任务使用新上下文继续；到达波次出口时额外运行波次门禁。

禁止行为：

- 禁止一次性读取本目录全部任务文件。
- `RDP-00` 后，除非固定契约与代码事实发生无法自行消解的冲突，否则不要重读设计原文。
- 禁止把 API Key、provider config、原始 reasoning item、encrypted reasoning、continuation payload、完整请求/响应 fixture 或长测试日志复制到 `CURRENT.md`。
- 禁止为了接入 DeepSeek 复制一套 OpenAI mapper、stream publisher 或异常映射器。
- 禁止在共享传输层抽取完成前引入 DeepSeek 差异，在 continuation 门禁通过前启用 DeepSeek 工具场景。
- 禁止因为连续执行而跳过任务级测试、状态更新、完成备注或波次出口。
- 当前上下文已经混入两个以上未完成任务时，先完成或交接唯一当前任务，不继续扩大范围。

上下文预算：

- 首轮最多读取当前任务标注数量的生产/测试文件；目录树、`rg` 命中、短配置片段和 Maven 依赖树不计入。
- 需要额外文件时一次只增加一个，并在完成备注中说明超出原因。
- 单任务修改超过 16 个生产/测试文件时，必须按任务内检查点先形成至少一个可编译或可测试状态。
- SDK 类型检查只记录类名、字段和方法结论，不保存 `jar tf`、`javap` 或协议响应全文。
- 完成备注不超过 20 行；`CURRENT.md` 建议保持在 100 行内。
- 交接只记录稳定类型、方法、配置、迁移、测试命令、PASS/FAIL 和首个失败原因，不保存源码片段或敏感 payload。

上下文健康门禁：

- 首轮文件上限耗尽仍无法定位改动时，先用更窄的 `rg`、调用链或失败测试收敛问题，不得直接通读整个模块。
- 同时出现三种以上竞争性 continuation 表达、开始反复重读协议原文，或无法说明 continuation 是否会进入持久化时，停止扩展并先把唯一决策与证据写入交接。
- `CURRENT.md` 是恢复指针，不是第二份设计文档；事实以当前 Git diff、代码、测试报告和受控 E2E 结果为准。
- “干净上下文继续”指新会话或只保留恢复指针的上下文；不能把此前完整工具输出原样带入下一任务。

## 3. 任务状态

| ID | 任务 | 状态 | 直接依赖 | 波次 | 建议首轮文件上限 |
| --- | --- | --- | --- | --- | --- |
| [`RDP-00`](RDP-00-baseline-and-contract-freeze.md) | 基线、协议与契约冻结 | `DONE` | 无 | A | 12 |
| [`RDP-01`](RDP-01-reasoning-core-and-effective-options.md) | Reasoning Effort 核心契约与生效值合并 | `DONE` | `RDP-00` | A | 14 |
| [`RDP-02`](RDP-02-route-snapshot-validation-and-admin-api.md) | 路由内容、运行快照、校验与管理 API | `DONE` | `RDP-01` | A | 16 |
| [`RDP-03`](RDP-03-openai-reasoning-mapping.md) | OpenAI effort 能力声明与 Responses 映射 | `DONE` | `RDP-01`、`RDP-02` | A | 12 |
| [`RDP-04`](RDP-04-reasoning-accounting-migration-and-metrics.md) | 调用台账迁移、终态快照与低基数指标 | `DONE` | `RDP-01`、`RDP-02` | A | 16 |
| [`RDP-05`](RDP-05-admin-reasoning-effort-frontend.md) | 管理端 effort 编辑、展示与配置模板 | `DONE` | `RDP-02` 至 `RDP-04` | B | 14 |
| [`RDP-06`](RDP-06-phase-one-integration-and-release-gate.md) | 第一阶段集成、回归与独立发布门禁 | `DONE` | `RDP-03` 至 `RDP-05` | B | 16 |
| [`RDP-07`](RDP-07-openai-compatible-module-extraction.md) | OpenAI-compatible Responses 共享模块等价抽取 | `DONE` | `RDP-06` | C | 18 |
| [`RDP-08`](RDP-08-provider-continuation-core-and-agent-loop.md) | Opaque continuation 核心契约与 Agent loop 传递 | `DONE` | `RDP-07` | C | 18 |
| [`RDP-09`](RDP-09-compatible-continuation-transport.md) | 共享 mapper 的 reasoning continuation 收发与 allowlist | `DONE` | `RDP-08` | C | 18 |
| [`RDP-10`](RDP-10-deepseek-adapter-profile-and-config.md) | DeepSeek adapter、profile、配置与请求约束 | `DONE` | `RDP-09` | D | 16 |
| [`RDP-11`](RDP-11-deepseek-admin-and-frontend-integration.md) | DeepSeek 管理后端与前端集成 | `DONE` | `RDP-10` | D | 14 |
| [`RDP-12`](RDP-12-deepseek-fixtures-security-and-observability.md) | DeepSeek fixture、异常、取消、安全与观测门禁 | `DONE` | `RDP-10`、`RDP-11` | D | 18 |
| [`RDP-13`](RDP-13-live-e2e-rollout-and-final-gates.md) | 真实 E2E 入口、灰度手册与仓库级最终门禁 | `DONE` | `RDP-12` | E | 18 |

## 4. 依赖主链

```text
RDP-00 -> RDP-01 -> RDP-02
RDP-02 -> RDP-03
RDP-02 -> RDP-04
RDP-03 + RDP-04 -> RDP-05 -> RDP-06 -> RDP-07 -> RDP-08 -> RDP-09 -> RDP-10
RDP-10 -> RDP-11
RDP-10 + RDP-11 -> RDP-12 -> RDP-13
```

连续单执行器按编号推进。依赖图只描述代码前置关系，不授权同时维护多套未完成契约。

## 5. 实施波次与出口

| 波次 | 任务 | 波次出口 |
| --- | --- | --- |
| A | `RDP-00` 至 `RDP-04` | 后端 effort 从路由到 OpenAI、台账和指标全链路可测；存量路由继续不发送 effort |
| B | `RDP-05` 至 `RDP-06` | 管理端可安全编辑和模拟 effort，第一阶段可独立发布并小范围灰度 |
| C | `RDP-07` 至 `RDP-09` | OpenAI 行为等价，共享传输层和 run-local continuation 完成，敏感状态不进入外部或持久化边界 |
| D | `RDP-10` 至 `RDP-12` | DeepSeek provider 在本地 fixture 下覆盖文本、结构化、工具、usage、错误、取消、安全和观测 |
| E | `RDP-13` | 真实 E2E 入口、灰度/回滚手册、代码索引和仓库级最终门禁完成 |

波次 A 完成后运行 `llm-core`、`llm-openai`、`ai-governance` 相关完整测试；波次 B 额外运行管理前端测试与构建；波次 C 运行共享模块、OpenAI、Agent core 和持久化安全回归；波次 D 运行 DeepSeek、本地集成、管理端和前端完整相关测试；波次 E 运行仓库级最终门禁。

## 6. 分阶段发布策略

1. 第一阶段只增加 effort 契约、OpenAI 映射、台账和管理端；所有存量路由读取为 `null`，不批量迁移或自动启用。
2. 第一阶段通过 `RDP-06` 后即可独立发布，不需要等待 DeepSeek。
3. 第二阶段先完成共享模块等价抽取，保持线上仍只有 OpenAI。
4. continuation 在共享层和 Agent loop 完成后，才允许注册 DeepSeek adapter。
5. DeepSeek 首先只允许 disabled provider、显式模型和本地 fixture；无工具真实 E2E 通过后才允许无工具灰度。
6. 工具调用真实 E2E 通过后，才允许 Practice Chat、学习计划 Agent、画像更新 Agent 等工具场景命中 DeepSeek。
7. 全程不增加 OpenAI 与 DeepSeek 自动 fallback；切换与回滚只使用既有显式路由、provider 启停和优先级规则。

## 7. 外部 E2E 状态语义

任务状态与发布资格分开记录：

- `RDP-13 = DONE`：表示 opt-in 真实 E2E 测试入口、运行手册和所有无密钥本地门禁已经完成。
- `DeepSeek 无工具发布门禁 = PASS`：表示受控环境的文本、流式和结构化真实调用已经通过。
- `DeepSeek 工具发布门禁 = PASS`：表示单工具、连续两次工具调用、关闭推理工具调用和取消已经通过，且无 reasoning 泄漏。
- 没有 API Key 时两个发布门禁保持 `NOT_RUN`，不将任务标记为 `BLOCKED`；如果用户明确要求“可上线”或“可灰度”，`NOT_RUN` 即为未完成条件。
- 真实 E2E 失败时记录稳定场景名、错误码和首个失败原因，不记录请求、响应、API Key 或 continuation。

## 8. 状态更新规则

- 任一时刻最多一个任务为 `IN_PROGRESS`。
- 开始任务时同时更新本表和 `CURRENT.md`。
- 最小相关测试未通过时不得标记 `DONE`。
- 非本任务引入的基线失败只记录证据，不修改无关代码。
- `BLOCKED` 只用于缺失设计输入、无法恢复的基线冲突或必须由用户决定且无法继续的情况；缺少可选真实 E2E 密钥不属于代码研发阻塞。
- 代码、测试和 Git diff 与文档冲突时，以代码事实为准，并立即修正 `CURRENT.md`。

任务文件末尾完成备注统一使用：

```text
完成时间：
状态：DONE
主要改动：
- ...
验证：
- command: PASS/FAIL
偏离计划：
- 无 / ...
遗留事项：
- 无 / ...
下一任务：RDP-xx
```

## 9. 验证层级

- 任务级：当前核心模型、mapper、provider、路由、台账、controller 或前端组件的最小相关测试。
- 波次级：涉及模块及依赖的完整测试；跨前后端波次额外运行前端构建。
- 最终级：`make backend-test`、`make backend-build`、`make frontend-test`、`make frontend-build`、迁移/旧符号/泄漏扫描和 `git diff --check`。
- 真实 E2E：只在显式提供受控环境变量时运行，不进入默认 CI，不输出秘密或 reasoning payload。
- 不主动启动 Vite；页面由用户通过 `make up` 查看。

## 10. 上下文恢复

发生压缩、中断或新会话恢复时：

1. 读取 `CURRENT.md`。
2. 检查本表第一个非 `DONE` 任务。
3. 完整读取 `CONTRACTS.md` 和当前任务文件。
4. 用 `git status --short`、`git diff --stat` 和最近测试结果核对实际状态。
5. 只补读当前任务明确涉及的代码，不从 `RDP-00` 重放全部分析。
6. 若恢复时发现真实 E2E 状态变化，只更新发布门禁，不重新打开已完成的代码任务。

## 11. 总体完成定义

研发任务完成要求 `RDP-00` 至 `RDP-13` 全部为 `DONE`，且满足：

- Reasoning Effort 七值、`null` 与 `none`、请求覆盖路由和 provider 子集校验具有唯一实现。
- OpenAI 同步与流式请求对 effort 的映射一致，存量路由保持不发送参数。
- 最终 effort 进入调用台账和低基数指标，成功、失败、取消均保留开始时快照。
- `llm-openai-compatible` 不拥有具体 provider 身份或 Spring bean，OpenAI 抽取后行为等价。
- DeepSeek 是独立 `deepseek` provider，使用严格配置、独立 profile 和显式请求约束。
- reasoning continuation 只在当前 Agent run 内存中存在，不进入 SSE、日志、trace、消息、台账或 metadata。
- DeepSeek 本地 fixture 覆盖文本、结构化输出、工具、usage、异常和取消，provider 身份不被归类为 OpenAI。
- 管理前端从 provider type 目录获取 effort 和默认配置模板，不硬编码 provider effort 子集。
- 仓库级最终门禁通过，代码索引与灰度/回滚手册更新。

发布资格另行要求：无工具或工具场景只有对应真实 E2E 门禁为 `PASS` 时，才可以进入相应灰度。
