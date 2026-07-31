# AMR-13：指标、安全、隐私与端到端门禁

> 波次：E
>
> 状态：DONE
>
> 直接依赖：AMR-04、AMR-07、AMR-09、AMR-10、AMR-11、AMR-12
>
> 建议首轮文件上限：16

## 1. 目标与完成标准

统一补齐 AI 记忆系统的低基数指标、运行 metadata、日志脱敏、诊断快照/工具 blob 隐私审计，并建立覆盖两条写入、召回、投影和深链的后端、Agent、前端及端到端门禁。

本任务不再改变产品语义。发现契约缺口时回到所属模块做最小修复，并在完成备注记录；不能用 E2E 测试掩盖局部缺失。

## 2. 必须读取

- `CURRENT.md` 和 `AMR-04`、`AMR-07`、`AMR-09` 至 `AMR-12` 的完成备注。
- `CONTRACTS.md` 第 1、6、9 至 12 节。
- 新 learner memory metrics 接口/实现及旧 `CodeReviewProfileMetrics`、`MicrometerCodeReviewProfileMetrics`。
- `MentorAiConfiguration.java` 中现有 Micrometer tag 白名单模式。
- `AgentRuntimeMetadataKeys` 和 learner memory 新 metadata constants。
- `PersistentAgentTraceObserver.java`、`PersistentAgentRunTraceObserver.java` 的最终请求快照和 tool result 持久化片段。
- `PostgresToolResultStore.java`、`AgentRunTraceMapper.xml` 的 blob ownership/range read 片段。
- `V31__agent_diagnostic_retention.sql` 及其测试。
- 新 PostgreSQL IT、Agent contract/eval 和前端画像/深链测试入口。

## 3. 指标契约

建立单一 `LearnerMemoryMetrics` 端口和 Micrometer/no-op 实现。指标名及允许 label 固定为：

| 指标 | 类型 | label |
| --- | --- | --- |
| `learner_memory_update_run_total` | Counter | `trigger,status` |
| `learner_memory_operation_total` | Counter | `action,kind,dimension` |
| `learner_memory_tool_call_total` | Counter | `purpose,tool,status` |
| `learner_memory_evidence_count` | DistributionSummary | `pattern,grade` |
| `learner_memory_invalid_output_total` | Counter | `reason` |
| `learner_memory_claim_active_count` | DistributionSummary | `kind,dimension` |
| `learner_memory_active_limit_total` | Counter | `level` |
| `learner_memory_recall_count` | Counter | `scenario,kind` |
| `learner_memory_bootstrap_token_estimate` | DistributionSummary | `scenario` |
| `learner_memory_bootstrap_direct_claim_count` | DistributionSummary | `scenario` |
| `learner_memory_bootstrap_trimmed_total` | Counter | `scenario` |
| `learner_memory_recall_tool_result_chars` | DistributionSummary | `tool` |
| `learner_memory_recall_range_read_total` | Counter | `status` |
| `learner_memory_profile_projection_total` | Counter | `status,projector_version` |
| `learner_memory_profile_citation_count` | DistributionSummary | 无 |

约束：

- 所有 label 值来自封闭 enum/常量目录，未知失败统一映射到低基数 `OTHER`。
- 不使用 user ID、claim key、revision/review/message ID、tag ID/value、problem slug、provider error text 或自由文本 label。
- 不为每个用户注册长期 Gauge；ACTIVE 数使用事件时分布或固定维度聚合。
- 不重复记录同一语义；明确 publish、consume、apply、recall、projection 的唯一埋点位置。

## 4. Run Metadata 与日志

运行 metadata 只记录计数和低敏标识：

- 横向窗口题目数、初始 ACTIVE claim 数、operation/evidence/tool 数和历史版本数。
- Practice Chat 的 document revision hash、section 数、直接命中数、bootstrap token 估算、工具/分页/range read 数和结果字符数。
- token、耗时、provider/model ID、Prompt/schema/projector version。

审计所有 learner memory 日志、异常和 tool trace：

- 允许记录 action、kind、dimension、tag ID、工具名、字符数、耗时和低敏失败码。
- 禁止记录 claim、Review、diff、代码、消息、evidence excerpt、Authorization、ref/cursor/resultRef 原值和模型完整输出。
- 异常链进入统一日志前必须去除 provider body、SQL 参数正文和 invalid JSON 原文。
- metadata key 使用常量；同一字段不能在不同链路用不同名称。

## 5. 诊断快照与 Tool Blob 隐私

- 验证最终 LLM request snapshot 中 Practice Chat 只含 bootstrap 和按需工具结果，不含全量 ACTIVE claim、完整 evidence 或未请求的历史。
- Code Review update snapshot 只含横向窗口、相关 ACTIVE scope 和已调用工具结果；不额外注入完整代码。
- 大结果只在 `ToolResultCompactor + ToolResultStore` 中保存脱敏后的工具结果；原始 Review/code/message 不先写 blob 再裁剪。
- `read_tool_result` 必须同时校验 run ownership、provenance、recall scope 和范围预算；审计接口不能绕过这些 guard。
- 现有最终请求诊断数据继续使用 30 天 retention；验证 redaction/删除传播会处理 context snapshot、tool result blob 和关联 trace。
- 不在本任务扩大诊断读取权限；如现有 admin API 能读取 snapshot，补充 learner memory 敏感字段测试和权限测试。

## 6. 后端与 Agent 验证矩阵

补齐或整合以下测试，不依赖真实付费模型：

- PostgreSQL：五表约束、repository 隔离、原子 apply、并发 stale、evidence FK、升级路径和 statement cursor。
- declared：声明、纠正、退役、no-op、工具失败不阻断主聊天。
- Code Review：严格五条满批、v2 幂等、横向窗口、三项历史工具、一次 stale 重算和全批零写入。
- Recall：run-local snapshot、bootstrap 完整项裁剪、三项记忆工具、3/2/8000/24000 预算和同 run blob 读取。
- Projector/API：稳定 AST/ETag、preview 2 条、cursor 20 条、XSS 纯文本和跨用户拒绝。
- Agent eval：直接命中不查、需要历史才查、无关不查、长结果 range read、无结果不臆断，以及六个固定业务语义样例。

Agent eval 使用 scripted fake runtime/fixture；不读取外部网络，不把 fixture 正文写入完成备注。

## 7. 前端与完整 E2E

- 前端交互测试覆盖单篇文档、引用 popover、lazy drawer、分页、键盘、移动端、深链和返回 anchor。
- 建立一个完整 E2E/集成场景：5 条正式 Review -> v2 满批 -> claim/evidence -> Practice Chat bootstrap/工具 -> projector -> `/me` -> 指定 Review -> 返回原句。
- 另建 declared 场景：当前用户消息 -> 原子 claim/message evidence -> 下一个 Practice Chat run 可见，当前 run 不刷新。
- E2E 使用固定 provider stub 和 PostgreSQL；断言数据与外部行为，不断言整段 Prompt/模型自然语言。
- 测试结束清理 scope/lease、queue 和 stub；不得残留后台 worker 或执行中的命令。

## 8. 安全专项测试

- 所有 API/tool/Mapper SQL 的用户范围；裸 review/revision/message ID 不能扩大范围。
- statement/section/cursor/resultRef 跨用户、跨 run、过期、伪造和并发更新边界。
- claim/message/Review 中 HTML、Markdown、URL、控制字符和超长内容的输出安全。
- 日志捕获器、Micrometer registry 和 metadata snapshot 中不存在正文或高基数 ID label。
- tool blob 保存内容已脱敏，range read 不能越界或跨 run。
- Review detail 仍由已有受保护 API 加载，画像 API 不返回完整 Review 或代码。

## 9. 实施步骤

1. 盘点各任务已埋指标，建立唯一 metrics 端口和 label allowlist，消除重复/高基数埋点。
2. 统一 metadata constants 和低敏失败码，增加日志捕获测试。
3. 审计诊断快照、tool blob、range read 和 30 天清理传播，做最小安全修复。
4. 补齐后端 PostgreSQL IT 和 Agent eval，按子系统先跑最小集。
5. 补齐前端交互和两条完整 E2E。
6. 运行波次 E 前置门禁并记录仅包含命令、PASS/FAIL 和首个失败类的结果。

## 10. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-core,agent-persistence-postgres,mentor-application -am \
  -Dtest='*LearnerMemory*Metrics*Test,*LearnerMemory*Security*Test,*LearnerMemory*AgentEval*Test,*ToolResult*Test' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dit.test='*LearnerMemory*IT,*LearnerProfile*DocumentIT,*LearnerMemory*EndToEndIT' verify

npm --cache ./.npm --prefix frontend test -- \
  src/MyPage.test.tsx \
  src/learner-profile \
  src/learning-plans/PracticeSubmissionHistoryPage.test.tsx \
  src/app/navigation.test.ts \
  src/App.test.tsx

npm --cache ./.npm --prefix frontend run build

git diff --check
```

## 11. 非目标与停止条件

- 不增加自动重试、DLQ、向量检索、用户反馈或新的产品入口。
- 不通过关闭诊断、删除安全断言或弱化用户隔离来让测试通过。
- 若存在高基数 label、日志/metadata/blob 正文泄漏、跨用户读取、真实付费模型依赖或完整 E2E 未通过，不得开始破坏性清理。

## 12. 上下文交接

记录 metrics 端口、允许 label、metadata constants、诊断/retention 结论、E2E 测试类和验证命令结果。只记录失败码和首个失败位置，不复制日志、Prompt、tool blob 或 fixture 正文。

## 13. 完成备注

完成时间：

状态：DONE

主要改动：

- 建立统一 `LearnerMemoryMetrics` 与 Micrometer label allowlist；写入、召回、投影、范围读取和三项 Review 历史工具均只在唯一语义位置记录低基数指标。
- 新增 `V48__agent_diagnostic_retention_cleanup.sql`，在保留既有 V31 checksum 的前提下清理过期 agent context、tool blob 和诊断 metadata。
- 新增 `LearnerMemorySecurityTest` 与脚本化 `LearnerMemoryAgentEvalTest`；复用 PostgreSQL IT 覆盖五表、满批、原子 apply、recall/tool blob、投影及用户隔离，前端回归覆盖画像/抽屉/深链/返回锚点。

验证：

- learner memory metrics/security/Agent eval/ToolResult 定向 Maven 测试：PASS（10 项相关测试）。
- agent diagnostic retention/trace/mapper 定向 Maven 测试：PASS（10 项）。
- learner memory 与 document PostgreSQL Failsafe：PASS（12 项，报告于 2026-07-30 15:05 UTC 刷新）。
- 指定 Vitest：PASS（7 files，115 tests）；`npm --cache ./.npm --prefix frontend run build`：PASS（仅既有 bundle size warning）。
- `git diff --check`：PASS。

偏离计划：

- 标准生命周期 `verify` 未刷新 Failsafe 报告，随后在指定本地 Maven 缓存已安装 reactor 构件后直接执行相同 IT 选择器的 Failsafe 目标并获得当前报告；未改变产品代码或测试语义。

遗留事项：

- 无。

下一任务：`AMR-14`
