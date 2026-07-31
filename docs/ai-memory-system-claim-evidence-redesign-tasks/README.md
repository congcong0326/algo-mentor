# AI 记忆系统 Claim/Evidence 重构任务状态板

依据：

- `docs/ai-memory-system-claim-evidence-redesign.md`
- `docs/ai-memory-system-current-state-audit.md`
- `docs/ai-learner-profile-implementation-plans/`

状态值：`PENDING`、`IN_PROGRESS`、`DONE`、`BLOCKED`

## 1. 文档目的

本目录把 AI 记忆系统破坏性重构拆成 `AMR-00` 至 `AMR-14` 共 15 个可连续执行、独立验收的研发任务。

这里的“一把梭哈”指执行器可以不等待人工逐项确认，按依赖连续完成全部任务；不代表把全部设计、任务和代码一次性塞进同一个模型上下文。每个任务都必须形成持久化交接，再用干净上下文继续下一项。

## 2. 连续执行协议

每次开始或恢复只做以下事情：

1. 读取 `CURRENT.md`，确认唯一当前任务。
2. 读取本文件的执行协议、状态表和当前波次出口。
3. 完整读取 `CONTRACTS.md`，它是实现期固定契约的唯一摘要。
4. 只读取当前任务文件及其“必须读取”清单；先执行任务给出的 `rg`，再按命中逐个打开代码。
5. 运行 `git status --short`、`git diff --stat`，辨认已有用户改动，禁止回退无关变更。
6. 实现、运行最小相关测试、更新任务完成备注、本状态板和 `CURRENT.md`。
7. 下一任务使用新上下文继续；波次出口必须额外运行波次门禁。

禁止行为：

- 禁止一次性读取本目录全部任务文件。
- `AMR-00` 之后，除非固定契约出现无法由代码判断的冲突，否则不要重读 1186 行设计原文。
- 禁止把测试完整日志、Prompt 全文、claim 正文、Review 正文或代码样本复制到 `CURRENT.md`。
- 禁止因为连续执行而跳过任务级测试、状态更新或完成备注。
- 禁止在上下文已经混入两个以上未完成任务时继续扩展范围；先落盘交接，再换新上下文。

上下文预算：

- 每个任务首轮最多读取任务文件列出的生产/测试文件数量；目录树、`rg` 命中和短配置片段不计入。
- 需要额外文件时一次只增加一个，并在完成备注记录为什么超出建议上限。
- 单个任务修改超过 16 个生产/测试文件时，先按任务内步骤形成可验证子检查点；不得提前读取下一任务。
- 每个任务完成备注不超过 20 行，`CURRENT.md` 建议保持在 80 行内。

上下文健康门禁：

- “完成备注”只读任务文件末尾对应小节；引用前序任务时不要重新读取其目标、步骤和测试全文。
- 首轮文件上限耗尽仍不能定位改动时，先用更窄的 `rg`、调用链或失败测试收敛问题；禁止直接扩大为通读模块。
- 当前上下文同时出现两个以上未完成任务、三条以上相互竞争的实现方案，或开始反复重读同一设计段落时，必须停止扩展，先把唯一决策和证据写入交接。
- 交接只记录类型/方法/配置/迁移/测试的稳定标识、PASS/FAIL 和首个失败原因；不保存源码片段、SQL 全文、JSON fixture、模型输出或长日志。
- `CURRENT.md` 是恢复指针，不是第二份设计文档；事实以当前 Git diff、代码和测试报告为准，发现偏差立即修正指针。
- “干净上下文继续”指新会话或只保留 `CURRENT.md` 指针的上下文恢复；不能把此前完整工具输出原样带入下一任务。

## 3. 任务状态

| ID | 任务 | 状态 | 直接依赖 | 波次 | 建议首轮文件上限 |
| --- | --- | --- | --- | --- | --- |
| [`AMR-00`](AMR-00-baseline-and-contract-freeze.md) | 基线、版本与契约冻结 | `DONE` | 无 | A | 8 |
| [`AMR-01`](AMR-01-claim-schema-and-domain.md) | Claim、证据与更新 Run 表结构及领域模型 | `DONE` | `AMR-00` | A | 12 |
| [`AMR-02`](AMR-02-repository-query-and-snapshots.md) | Repository、查询、容量与快照原语 | `DONE` | `AMR-01` | A | 14 |
| [`AMR-03`](AMR-03-operation-validation-and-atomic-apply.md) | Operation 校验、证据分级与原子应用 | `DONE` | `AMR-02` | B | 14 |
| [`AMR-04`](AMR-04-declared-claim-update.md) | 用户自述原子 Claim 更新链路 | `DONE` | `AMR-03` | B | 12 |
| [`AMR-05`](AMR-05-review-history-and-update-tools.md) | Review 历史事实、纵向轨迹与更新工具 | `DONE` | `AMR-02` | B | 14 |
| [`AMR-06`](AMR-06-code-review-update-agent.md) | Code Review Claim 更新 Agent | `DONE` | `AMR-03`、`AMR-05` | C | 14 |
| [`AMR-07`](AMR-07-code-review-v2-batch-pipeline.md) | v2 队列、满批消费与写入闭环 | `DONE` | `AMR-06` | C | 14 |
| [`AMR-08`](AMR-08-recall-snapshot-and-bootstrap.md) | Run-local 召回快照与启动索引 | `DONE` | `AMR-02`、`AMR-05` | C | 14 |
| [`AMR-09`](AMR-09-practice-chat-memory-tools.md) | Practice Chat 记忆探索工具与预算 | `DONE` | `AMR-08` | C | 16 |
| [`AMR-10`](AMR-10-document-projection-and-api.md) | 画像文档投影、引用与后端 API | `DONE` | `AMR-02`、`AMR-03` | D | 14 |
| [`AMR-11`](AMR-11-profile-document-frontend.md) | 单篇画像、句子引用与依据抽屉 | `DONE` | `AMR-10` | D | 12 |
| [`AMR-12`](AMR-12-review-deep-link-and-return-anchor.md) | 精确 Review 深链与原句返回 | `DONE` | `AMR-11` | D | 10 |
| [`AMR-13`](AMR-13-observability-security-and-e2e.md) | 指标、安全、隐私与端到端门禁 | `DONE` | `AMR-04`、`AMR-07`、`AMR-09`、`AMR-10` 至 `AMR-12` | E | 16 |
| [`AMR-14`](AMR-14-legacy-cleanup-and-rollout.md) | 旧模型清理、最终迁移与发布收口 | `DONE` | `AMR-13` | E | 18 |

## 4. 依赖主链

```text
AMR-00
  -> AMR-01 -> AMR-02 -> AMR-03 -> AMR-04
                    |         |
                    |         +-> AMR-06 -> AMR-07
                    +-> AMR-05 -+
                    |
                    +-> AMR-08 -> AMR-09
                    |
                    +-> AMR-10 -> AMR-11 -> AMR-12

AMR-04 + AMR-07 + AMR-09 + AMR-10 + AMR-11 + AMR-12
  -> AMR-13 -> AMR-14
```

允许的并行只表示代码依赖独立。连续单执行器仍按编号推进，避免同时维护多套未完成契约。

## 5. 实施波次与出口

| 波次 | 任务 | 波次出口 |
| --- | --- | --- |
| A | `AMR-00` 至 `AMR-02` | 新表、领域契约、Repository 和只读快照可独立测试；旧线上行为未切换 |
| B | `AMR-03` 至 `AMR-05` | 原子应用内核、用户消息证据和 Review 历史工具均有独立测试 |
| C | `AMR-06` 至 `AMR-09` | 两条写入链路与 Practice Chat 召回工具完成，全部功能仍可由开关关闭 |
| D | `AMR-10` 至 `AMR-12` | 后端文档 API、前端引用交互和 Review 深链形成用户闭环 |
| E | `AMR-13` 至 `AMR-14` | 安全、指标、E2E、旧代码删除、破坏性清理和发布门禁全部通过 |

波次 A、B、C 完成后运行相关模块完整测试；波次 D 完成后运行前端完整测试与构建；波次 E 运行仓库级最终门禁。

## 6. 迁移实施策略

为保证连续执行期间每个检查点可编译、可测试，数据库迁移采用两段式切换：

1. `AMR-01` 使用实施时扫描得到的下一个全局唯一 Flyway 版本创建新表，不删除 `learner_profile_entry`，不切换任何生产读写。
2. `AMR-04`、`AMR-07`、`AMR-09`、`AMR-10` 逐条切换新写入、新召回和新 API；期间不做新旧双写，也不从旧正文回填 claim。
3. `AMR-14` 使用新的全局唯一 Flyway 版本删除旧表和遗留 v1 queue 数据，并删除旧代码。

这只是实施期的绿色构建策略，不改变最终破坏性结果：旧画像数据不保留、不转换、不恢复；最终系统中不存在旧表、旧 topic、旧 DTO 或兼容视图。

## 7. 状态更新规则

- 任一时刻最多一个任务为 `IN_PROGRESS`。
- 开始任务时同时更新本表和 `CURRENT.md`。
- 最小相关测试未通过时不得标记 `DONE`。
- 非本任务引入的基线失败只记录证据，不修改无关代码。
- `BLOCKED` 只用于确实需要外部状态或用户决策且无法继续的情况。
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
下一任务：AMR-xx
```

## 8. 验证层级

- 任务级：只运行当前变更涉及的单元测试、Mapper XML 测试、PostgreSQL IT 或前端测试。
- 波次级：运行涉及模块及其依赖的完整测试。
- 最终级：`make backend-test`、`make backend-build`、`make frontend-test`、`make frontend-build`、`git diff --check`。
- 不主动启动 Vite；需要查看页面时由用户执行 `make up`。

## 9. 上下文恢复

发生压缩、中断或新会话恢复时：

1. 读取 `CURRENT.md`。
2. 检查本表第一个非 `DONE` 任务。
3. 读取该任务文件和 `CONTRACTS.md`。
4. 用 `git status --short`、`git diff --stat` 和最近测试报告核对实际状态。
5. 只补读当前任务明确涉及的代码，不从 `AMR-00` 重放全部分析。

## 10. 总体完成定义

只有 `AMR-00` 至 `AMR-14` 全部为 `DONE`，且最终门禁通过，重构才算完成。最终必须满足：

- 长期记忆事实源是 ACTIVE claim revision，而不是整段画像正文或投影 Markdown。
- 用户自述和 Code Review 两条写入链路都写完整证据链。
- Practice Chat 使用启动索引和受控工具探索，不注入全量记忆。
- `/me` 展示单篇结构化文档并支持句子级来源、懒加载证据和精确 Review 深链。
- 旧表、旧 topic、旧 DTO、旧 Prompt 和旧实现引用全部清零。
