# 统一 Agent 底座重构任务状态板

依据：[统一 Agent 底座重构实施计划](../unified-agent-foundation-refactoring-implementation-plan.md)

状态值：`PENDING`、`IN_PROGRESS`、`DONE`、`BLOCKED`

## 1. 使用方式

连续执行时只做三件事：

1. 读取 `CURRENT.md` 确认当前任务。
2. 只读取当前任务文件及其“必须读取”清单。
3. 完成后更新本表、当前任务完成备注，并覆盖写 `CURRENT.md`。

禁止一次性读取本目录全部任务文件。任务文件是渐进披露边界，不是需要同时装入上下文的总文档。

## 2. 任务状态

| ID | 任务 | 状态 | 依赖 | 最近验证 |
| --- | --- | --- | --- | --- |
| [`UAF-00`](UAF-00-baseline-and-contract-freeze.md) | 基线与行为冻结 | `DONE` | 无 | 最小测试与 `make backend-test` 通过 |
| [`UAF-01`](UAF-01-core-contracts-and-module-skeleton.md) | 核心契约与模块骨架 | `DONE` | `UAF-00` | 目标测试与依赖编译通过 |
| [`UAF-02`](UAF-02-synchronous-loop-and-executor-context.md) | 同步 loop 与线程模型 | `DONE` | `UAF-01` | loop/executor 目标测试通过 |
| [`UAF-03`](UAF-03-run-local-tools-and-loop-policy.md) | run-local 工具与 loop policy | `IN_PROGRESS` | `UAF-02` | - |
| [`UAF-04`](UAF-04-audit-run-persistence.md) | 审计运行准备与持久化 | `PENDING` | `UAF-01` | - |
| [`UAF-05`](UAF-05-runtime-governance-modes.md) | Runtime 治理模式 | `PENDING` | `UAF-01`、`UAF-04` | - |
| [`UAF-06`](UAF-06-default-runtime-and-topic-pilot.md) | Default Runtime 与 Topic 竖切 | `PENDING` | `UAF-02` 至 `UAF-05` | - |
| [`UAF-07`](UAF-07-mentor-conversation-migration.md) | Mentor Conversation 迁移 | `PENDING` | `UAF-06` | - |
| [`UAF-08`](UAF-08-learning-plan-scenarios-migration.md) | Learning Plan 场景迁移 | `PENDING` | `UAF-06` | - |
| [`UAF-09`](UAF-09-practice-chat-root-migration.md) | Practice Chat 根场景迁移 | `PENDING` | `UAF-07`、`UAF-08` | - |
| [`UAF-10`](UAF-10-practice-code-review-child-agent.md) | Practice Code Review 子 Agent | `PENDING` | `UAF-09` | - |
| [`UAF-11`](UAF-11-declared-profile-child-agent.md) | Declared Profile 子 Agent | `PENDING` | `UAF-10` | - |
| [`UAF-12`](UAF-12-code-review-profile-background-agent.md) | Code Review Profile 后台 Agent | `PENDING` | `UAF-11` | - |
| [`UAF-13`](UAF-13-old-entry-cleanup-and-final-gates.md) | 旧入口清理与最终门禁 | `PENDING` | `UAF-12` | - |

## 3. 波次检查点

| 波次 | 完成任务 | 必须验证 |
| --- | --- | --- |
| A | `UAF-00` 至 `UAF-03` | 目标测试 + `make backend-test` |
| B | `UAF-04` 至 `UAF-06` | Topic 完整竖切 + `make backend-test` |
| C | `UAF-07` 至 `UAF-09` | 所有用户入口回归 + `make backend-test` |
| D | `UAF-10` 至 `UAF-12` | child/background 治理回归 + `make backend-test` |
| E | `UAF-13` | `make backend-test`、`make backend-build`、`git diff --check` |

## 4. 状态更新规则

- 开始任务前将且仅将一个任务标记为 `IN_PROGRESS`。
- 最小相关测试未通过时不得标记 `DONE`。
- 非本任务引入的基线失败需记录证据，但不得借机修改无关代码。
- `BLOCKED` 只用于需要用户或外部状态才能继续的真实阻塞。
- “最近验证”只写命令简称与结果，不复制日志。

## 5. 完成备注格式

每个任务文件末尾保留以下格式，整体不超过 20 行：

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
下一任务：UAF-xx
```

## 6. 上下文恢复规则

发生上下文压缩、会话恢复或执行中断时：

1. 不从头重读全部设计。
2. 读取 `CURRENT.md`。
3. 检查本表第一个非 `DONE` 任务。
4. 读取该任务文件和其中指定代码。
5. 用 `git status`、`git diff --stat` 和测试报告核对实际状态后继续。

若 `CURRENT.md` 与代码不一致，以代码、测试和 Git diff 为准，并立即修正 `CURRENT.md`。
