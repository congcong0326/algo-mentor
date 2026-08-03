# AI 学习计划个性化生成重构任务状态板

依据：

- `docs/learning-plan-personalized-generation-redesign.md`
- 当前仓库中的学习计划、学习者记忆、能力画像、练习进度和复习队列实现

状态值：`PENDING`、`IN_PROGRESS`、`DONE`、`BLOCKED`

## 1. 文档目的

本目录把学习计划个性化生成重构拆成 `LPGR-00` 至 `LPGR-10` 共 11 个可连续执行、分段验收的研发任务。

“一把梭哈”表示执行器可以不等待逐项人工确认，按依赖顺序连续完成全部任务；不表示把设计原文、全部任务文件和整个学习计划模块一次性装入同一个上下文。每个任务都必须留下可恢复的落盘交接，再用干净上下文进入下一项。

## 2. 连续执行协议

每次开始或恢复按以下顺序执行：

1. 读取 `CURRENT.md`，确认唯一当前任务和首个未完成任务。
2. 读取本文件的执行协议、状态表和当前波次出口。
3. 完整读取 `CONTRACTS.md`；实现期间固定决策以该文件为准。
4. 只读取当前任务文件及其“必须读取”清单；先执行任务给出的 `rg`，再按命中打开代码。
5. 运行 `git status --short` 和 `git diff --stat`，辨认用户已有改动，禁止回退无关变更。
6. 实现当前任务，运行最小相关测试，更新任务完成备注、本状态板和 `CURRENT.md`。
7. 下一任务使用新上下文继续；到达波次出口时额外运行波次门禁。

禁止行为：

- 禁止一次性读取本目录全部任务文件。
- `LPGR-00` 后，除非固定契约与代码事实发生无法自行消解的冲突，否则不要重读设计原文。
- 禁止把 Prompt 全文、用户画像正文、Review 正文、模型输出、JSON fixture 或完整测试日志复制到 `CURRENT.md`。
- 禁止因为连续执行而跳过任务级测试、状态更新或完成备注。
- 当前上下文已经混入两个以上未完成任务时，先完成或交接唯一当前任务，不继续扩大范围。

上下文预算：

- 首轮最多读取当前任务标注数量的生产/测试文件；目录树、`rg` 命中和短配置片段不计入。
- 需要额外文件时一次只增加一个，并在完成备注中说明超出原因。
- 单任务修改超过 16 个生产/测试文件时，必须按任务步骤形成至少一个可编译或可测试的子检查点。
- 完成备注不超过 20 行；`CURRENT.md` 建议保持在 80 行内。
- 交接只记录稳定类型、方法、配置、测试命令、PASS/FAIL 和首个失败原因，不保存源码片段或长日志。

## 3. 任务状态

| ID | 任务 | 状态 | 直接依赖 | 波次 | 建议首轮文件上限 |
| --- | --- | --- | --- | --- | --- |
| [`LPGR-00`](LPGR-00-baseline-and-contract-freeze.md) | 基线、契约与旧符号冻结 | `DONE` | 无 | A | 10 |
| [`LPGR-01`](LPGR-01-brief-and-validation.md) | Brief、目标默认值与难度分布 | `DONE` | `LPGR-00` | A | 10 |
| [`LPGR-02`](LPGR-02-plan-snapshot-and-persistence.md) | 计划快照、模板映射与 JSON 持久化 | `DONE` | `LPGR-01` | A | 16 |
| [`LPGR-03`](LPGR-03-initial-generation-and-backend-api.md) | 初次生成模型契约与后端 API 切换 | `DONE` | `LPGR-02` | B | 18 |
| [`LPGR-04`](LPGR-04-frontend-create-and-preview.md) | 创建表单、请求与草案预览前端切换 | `DONE` | `LPGR-03` | B | 14 |
| [`LPGR-05`](LPGR-05-personalization-context-core.md) | 个性化上下文模型、裁剪与降级内核 | `DONE` | `LPGR-04` | C | 12 |
| [`LPGR-06`](LPGR-06-personalization-adapters-and-draft-integration.md) | 聚合数据适配与初次生成注入 | `DONE` | `LPGR-05` | C | 16 |
| [`LPGR-07`](LPGR-07-draft-revision-context-and-output.md) | 草案修订 Brief 解析与上下文注入 | `DONE` | `LPGR-06` | D | 16 |
| [`LPGR-08`](LPGR-08-extension-template-and-downstream-consumers.md) | 扩展、模板与下游 objective 收口 | `DONE` | `LPGR-07` | D | 16 |
| [`LPGR-09`](LPGR-09-observability-security-and-e2e.md) | 指标、安全与端到端验证 | `DONE` | `LPGR-08` | E | 18 |
| [`LPGR-10`](LPGR-10-legacy-cleanup-and-final-gates.md) | 旧契约清理与仓库级最终门禁 | `DONE` | `LPGR-09` | E | 18 |

## 4. 依赖主链

```text
LPGR-00 -> LPGR-01 -> LPGR-02 -> LPGR-03 -> LPGR-04 -> LPGR-05
  -> LPGR-06 -> LPGR-07 -> LPGR-08 -> LPGR-09 -> LPGR-10
```

连续单执行器按编号推进。依赖图只说明代码前置关系，不授权同时维护多个未完成任务。

## 5. 实施波次与出口

| 波次 | 任务 | 波次出口 |
| --- | --- | --- |
| A | `LPGR-00` 至 `LPGR-02` | Brief 和计划快照新领域契约可独立测试；模板仍可确定性生成；JSONB 不做旧结构兼容 |
| B | `LPGR-03` 至 `LPGR-04` | AI 创建请求、初次模型输出、草案响应和前端表单形成完整新链路 |
| C | `LPGR-05` 至 `LPGR-06` | 个性化开启、关闭、空数据和部分失败均可验证；初次生成按 run 固定上下文 |
| D | `LPGR-07` 至 `LPGR-08` | 聊天修订、计划扩展、模板创建和 Practice Chat 下游语义全部一致 |
| E | `LPGR-09` 至 `LPGR-10` | 指标、安全、集成测试、旧符号清理和仓库级最终门禁全部通过 |

波次 A、C 完成后运行 `mentor-application` 相关完整测试；波次 B、D 完成后运行学习计划后端定向测试、前端学习计划测试和前端构建；波次 E 运行仓库级最终门禁。

## 6. 实施期切换策略

项目未上线，最终状态不兼容旧 API、旧草案 JSON 或旧正式计划 JSON。为了保证连续实施期间每个任务可编译、可测试，允许以下短期桥接：

1. `LPGR-01` 先新增新领域类型，不切换生产入口。
2. `LPGR-02` 先切换计划快照；旧命令到新快照的内部映射仅用于保持后端绿色，必须在 `LPGR-03` 删除。
3. `LPGR-03` 一次切换 AI 创建请求、草案持久化命令和初次生成模型契约；不保留旧 API 字段或旧 JSON fallback。
4. `LPGR-07` 前可临时保留“完整计划修订 Schema”，但初次生成不得继续使用它；`LPGR-07` 完成后必须删除该桥接。
5. `LPGR-10` 清除所有桥接符号、旧测试 fixture 和兼容映射。

实施期禁止双写 `goal/objective`、`difficultyPreference/difficultyDistribution`，禁止从旧 JSON 猜测新字段，禁止新增数据库表或迁移脚本。

## 7. 状态更新规则

- 任一时刻最多一个任务为 `IN_PROGRESS`。
- 开始任务时同时更新本表和 `CURRENT.md`。
- 最小相关测试未通过时不得标记 `DONE`。
- 非本任务引入的基线失败只记录证据，不修改无关代码。
- `BLOCKED` 只用于确实需要外部状态或产品决策且无法继续的情况。
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
下一任务：LPGR-xx
```

## 8. 验证层级

- 任务级：当前领域、Prompt/Schema、repository、controller 或前端组件的最小相关测试。
- 波次级：涉及模块及其依赖的完整测试；跨前后端波次额外运行前端构建。
- 最终级：`make backend-test`、`make backend-build`、`make frontend-test`、`make frontend-build`、旧符号扫描和 `git diff --check`。
- 不主动启动 Vite；页面由用户通过 `make up` 查看。

## 9. 上下文恢复

发生压缩、中断或新会话恢复时：

1. 读取 `CURRENT.md`。
2. 检查本表第一个非 `DONE` 任务。
3. 完整读取 `CONTRACTS.md` 和当前任务文件。
4. 用 `git status --short`、`git diff --stat` 和最近测试结果核对实际状态。
5. 只补读当前任务明确涉及的代码，不从 `LPGR-00` 重放全部分析。

## 10. 总体完成定义

只有 `LPGR-00` 至 `LPGR-10` 全部为 `DONE`，且满足以下条件，重构才算完成：

- AI 创建 API、草案命令和正式计划不再使用语义过载的 `goal`。
- 初次生成模型只输出 `title`、`summary`、`phases` 和受限 `metadata`。
- 个性化上下文有界、可关闭、按 run 固定、部分失败可降级，且不包含原始代码、完整 Review 或聊天记录。
- 草案修订能更新允许的 Brief 字段并保持其他字段；扩展只能追加阶段。
- 模板生成不读取个性化数据，仍保持确定性。
- 前端创建、预览、聊天修订、确认保存路径保持可用。
- 最终门禁全部通过，活动源码中只在模板边界保留设计允许的 `goal` 和 `difficultyPreference`。

## 11. 评审关注点

任务包为消除实施期歧义，补充冻结了四项细节；评审时重点确认：

1. 六种 intent 的中英文默认 objective 使用 `CONTRACTS.md` 第 3 节固定文案。
2. 正式计划仅在内部 metadata 保存 `personalizationEnabled`，用于扩展 run 继续尊重用户选择；不保存上下文正文，也不通过公共 metadata 暴露。
3. 个性化摘要上限为 declared 8 条、general 6 条、弱项 3 条、强项 3 条；复习“今日”边界首期使用 UTC。
4. 为保持连续实施可编译，允许两个有明确删除任务的短期桥接：旧命令到新快照映射，以及修订专用完整计划 v2 Schema；最终均不得保留。
