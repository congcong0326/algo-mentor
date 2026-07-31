# 当前执行上下文

更新时间：2026-07-30

当前状态：全部完成

当前任务：无

下一任务文件：无

## 最终交付

- `AMR-00` 至 `AMR-14` 均已完成；长期记忆唯一事实源为 claim/evidence 五表。
- `V49__remove_legacy_learner_profile_storage.sql` 删除 v1 queue topic 数据和 `learner_profile_entry`；不回填、不创建兼容视图。
- 旧画像模型、v1 topic/Prompt/DTO、旧 recall 与前端分类视图均已移除；recall 开关默认关闭。
- 发布与关闭式止损流程见 `docs/ai-memory-system-rollout-runbook.md`。

## 最终验证

- LearnerMemory migration/E2E Failsafe 选择器：PASS（6 类、15 项）。
- `make backend-test`：PASS（286 项）；`make backend-build`：PASS。
- `make frontend-test`：PASS（48 files、368 tests）；`make frontend-build`：PASS。
- 旧符号扫描与 `git diff --check`：PASS。

## 审核遗留

- 旧表/topic 符号仅存在于 V34/V47/V49 历史迁移，以及验证最终删除行为的 PostgreSQL 回归测试。
- `revisionNo` 和 `contentText` 命中属于学习计划 revision 或通用 tool blob，不属于旧画像兼容契约。

## 历史交接

- `AMR-13` 于 2026-07-30 完成：统一 `LearnerMemoryMetrics` 以 allowlist 限制所有 label，覆盖 apply、declared、recall、projection、范围读取及三项 Review 工具；`V48` 在不修改已应用 V31 的情况下清理过期 context/tool blob/trace 诊断数据。新增安全和脚本化 Agent eval，并以 12 项 PostgreSQL IT、115 项前端测试、前端 build 与 `git diff --check` 验证通过。
- `AMR-12` 于 2026-07-30 完成：submissions deep link 通过 `learningPlanPracticeSubmissionsPath` 的 typed options 仅接受正安全 review、`learner-profile` 来源和固定 ASCII anchor；App 对 initial/navigation/popstate 统一白名单归一化，并保留 `pack=today`。历史页只从 session history 自动选择目标、受保护 detail 加载、滚动和短暂来源高亮；不存在目标不探测其他 ID。返回路径只由 `learnerProfilePath` 构造，`/me` 文档渲染后通过 `getElementById` 定位、聚焦、高亮并 replace 清理 anchor。指定 110 项 Vitest、前端 build 和 `git diff --check` 通过。
- `AMR-11` 于 2026-07-30 完成：`/me` 改为受限 AST 单篇画像，supported sentence 以稳定 `learner-profile-statement-{claimRevisionId}` anchor 和始终可见 citation 交互。hover/focus 显示安全 preview，点击/Enter/Space/触摸打开带焦点返回的 evidence drawer；请求按 statement 懒加载，切换 abort、cursor 和 item 均去重。前端类型替换为 document/evidence discriminated union，旧 tab/card/Markdown 路径已清除；Review 先用受控 submissions base path。指定 43 项 Vitest、前端 build、`git diff --check` 和旧符号扫描全部通过。
- `AMR-10` 于 2026-07-30 完成：`LearnerProfileDocument` 以固定主题、受限 AST 和稳定 citation 投影当前用户 ACTIVE claim；主 API 使用 document revision 强 ETag。statement ref/cursor 均由独立 HMAC 绑定用户、revision 和页大小，cursor 保留秒/纳秒排序精度；证据仅分页返回安全 Review/message 摘要。旧分类数组 DTO、display mapper 和 view service 已删除，旧存储写路径保留至 AMR-14。应用定向 6 项测试、API controller/mapper XML 与 `LearnerProfileDocumentIT`、`git diff --check` 全部通过。
- `AMR-09` 于 2026-07-30 完成：Practice Chat 已通过 run-local opaque scope 接入 `search_learner_memory`、`read_learner_memory_section`、`get_learner_memory_evidence`；业务工具最多 3 次、记忆 result range read 最多 2 次，单次 8000 字符、合计 24000 字符。通用 `ToolResultProvenance` / `ToolResultReadGuard` 仅对记忆工具计数，PostgreSQL blob 读取强制同 run。任务定向测试、`git diff --check` 与波次 C 的 `make backend-test`、46 项 `make backend-it` 全部通过。
- `AMR-08` 于 2026-07-30 完成：Practice Chat 仅在非 replay run 打开 claim recall snapshot；随机 lease 管理全量 claim 与 opaque ref，Runtime 终态与异常路径释放资源。bootstrap 以完整项裁剪，默认 1000 token、最大 1500，metadata 只记录低敏引用和统计。移除旧 profile recall 链路；应用单测 13 项、自动配置/属性测试 18 项和 `LearnerMemoryRecallIT` 均通过。
- `AMR-07` 于 2026-07-30 完成：正式 Review 提交使用 `learner-memory.code-review.v2`，队列消费者校验严格五条、同用户、唯一 review、facts 和 ownership 后调用 AMR-06。`V47` 清理旧 v1 topic 的 PENDING/SUCCEEDED 消息；消费者仅在完整 runtime/Definition/端口/worker 条件下装配，配置前缀为 `algo-mentor.learner-memory.code-review-consumer`。队列单测、API verify、自动配置/Flyway 定向测试和 `git diff --check` 均通过。
- `AMR-06` 于 2026-07-30 完成：`CODE_REVIEW_PROFILE_UPDATE` 使用 4 step 和三项受 scope lease 约束的 AMR-05 只读工具，v2 Schema 只接受严格 `operations`。更新服务固定五条触发 Review，构造最多十题窗口、相关 claim snapshot/容量与服务端 review evidence context，通过统一原子 apply 写入；首次 stale 完整重读后最多重试一次。update run 记录业务幂等、五条触发 Review、最终 Agent run、operation/tool 数和失败码。应用定向测试 7 项、`CodeReviewMemoryUpdateIT` PostgreSQL 验证和 `git diff --check` 均通过；后者的 runtime 替身已创建真实 `agent_run` 以满足审计外键。
- `AMR-05` 于 2026-07-30 完成：新增 `CodeReviewHistoryRepository` 及用户范围 Mapper，支持最近 5 版、受限详情、内部规范化代码和批量复核。`ReviewTrajectoryService` 与基于 `java-diff-utils` 的 `SubmissionVersionDiffService` 均为确定性实现。三项只读工具通过 `learnerMemoryScopeRef` 使用 `LearnerMemoryRunScopeRegistry`，总预算 3 次，scope 释放/过期后不回退查询。工具条件注册但未加入现有 Definition。应用定向测试 9 项和 `CodeReviewMemoryToolIT` 通过；后者发现并修复 detail row 的 MyBatis 构造参数顺序。
- `AMR-04` 于 2026-07-30 完成：自述子 Agent 使用 v2 `operations` Schema，输入为请求维度的 ACTIVE declared claim；当前消息通过 `AgentTurnMessageLookupRepository` 查询并经数据库归属校验。`ADD` 写声明证据，`REVISE / RETIRE` 写纠正证据并保留旧消息证据。子 Agent 幂等键绑定父 run、step、工具名和规范化请求；第一次 stale 完整重算，第二次 stale 失败且零原批次写入。工具继续返回稳定的 `type/status/message/items`。
