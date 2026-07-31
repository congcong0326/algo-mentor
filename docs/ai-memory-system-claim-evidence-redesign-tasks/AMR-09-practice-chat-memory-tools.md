# AMR-09：Practice Chat 记忆探索工具与预算

> 波次：C
>
> 状态：DONE
>
> 直接依赖：AMR-08
>
> 建议首轮文件上限：16

## 1. 目标与完成标准

实现并接入 `search_learner_memory`、`read_learner_memory_section`、`get_learner_memory_evidence`，复用现有 tool result blob/range read，并严格执行 run-local 调用、字符和分页预算。

完成后 Practice Chat 可以按需探索当前快照，不能借参数或 resultRef 越过用户、run 或 snapshot；bootstrap Prompt 会明确何时调用、何时不调用。

## 2. 必须读取

- `CURRENT.md`、`AMR-08` 完成备注和 recall scope 实现。
- `CONTRACTS.md` 第 9、12 节。
- `PracticeChatAgentDefinition.java` 和自动配置中的 tool name 组装。
- `AgentToolRegistry.java`、`AgentLoopEngine.java` 的工具执行/compaction 片段。
- `ToolResultCompactor.java`、`ToolResultStore.java`、`StoredToolResult.java`。
- `ReadToolResultTool.java`、`PostgresToolResultStore.java` 及测试。
- `AgentRunTraceMapper` 中 result blob 与 tool call 的关联查询。
- 新 recall snapshot、section catalog、scope registry 和 claim/evidence query 端口。

## 3. 三项工具契约

- `search_learner_memory(query, sectionRef?, tagValues?, limit, cursor)`：规范化文本、scope/tag 过滤和确定性排序，最多 20 条。
- `read_learner_memory_section(sectionRef, afterStatementRef?, limit)`：按投影顺序读完整 statement，最多 20 条。
- `get_learner_memory_evidence(statementRef, limit, cursor)`：返回 Review/message 摘要，最多 20 条。

参数不接受 user ID、claim/revision ID 或 document revision。伪造 ref/cursor 统一返回不存在或无权限，不泄漏 ref 是否属于其他用户。

搜索无结果必须返回“当前 run 快照未找到匹配项”的受控语义，禁止推断用户从未有相关经历。

## 4. 预算与分页

- 每个 Practice Chat run 最多 3 次业务记忆工具调用。
- 单次序列化后模型可见结果最多 8000 字符；使用完整 item 删除和 nextCursor 降载，不截断 claim。
- 三项工具返回字符合计最多 24000；达到预算返回稳定 `BUDGET_EXHAUSTED` 结果。
- search/section/evidence 都使用语义 cursor，稳定、无重复、无遗漏。
- evidence 摘要不返回 raw code、normalized code、完整 Review Markdown 或完整消息。

## 5. `read_tool_result` 接入

为避免在 mentor 业务代码中复制通用读取工具，增加最小通用扩展：

- agent-core 定义可选 `ToolResultReadGuard` 或等价 no-op 端口。
- `ReadToolResultTool` 在读取前后调用 guard，不感知 learner memory。
- ToolResultStore/持久化层提供受控 provenance：当前 run、原始 tool name、step/toolCall，不返回 blob 内容给 guard。
- learner memory guard 只在 recall scope 存在且 provenance tool name 属于三项记忆工具时计数。
- 每个 recall run 最多 2 次记忆结果范围读取，返回内容同样计入 24000 字符。
- 非记忆工具的 `read_tool_result` 保持原行为；同 run 校验和通用 8000 字符上限不变。

## 6. Practice Chat 接线

- Definition allowed tools 增加三项记忆工具；仅在 `ReadToolResultTool` 实际注册时增加其名称。
- 不影响 submit Review 和 declared update 工具的现有白名单与权限。
- bootstrap Prompt 增加工具名、Prompt 未出现不等于记忆不存在、相关时自主查询、无关时不要全量检查等约束。
- 工具查询失败返回有界结果或降级，不因记忆系统故障终止普通聊天。

## 7. 重点测试

- 三项工具只读取当前 scope；跨用户、跨 run、伪造 section/statement/cursor 全部失败且不泄漏。
- 20 项、8000 单次、24000 总量、3 次业务调用和 2 次范围读取边界。
- 大结果生成 preview + resultRef；同 run 可读，其他 run 不可读。
- provenance 只对记忆工具计数，其他工具 range read 不被误扣。
- 并发画像更新不改变当前工具结果。
- eval 覆盖：直接命中足够不调用、未展开历史必须调用、无关问题不调用、长结果范围读取、无结果不臆断。

## 8. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-core,agent-persistence-postgres,mentor-application -am \
  -Dtest='ReadToolResultToolTest,*ToolResult*Provenance*Test,*LearnerMemory*RecallTool*Test,PracticeChatAgentDefinitionTest' test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dit.test='*LearnerMemory*RecallToolIT,*LearnerMemory*ToolResultIT' verify

git diff --check
```

## 9. 非目标与停止条件

- 不实现向量搜索、跨 run cursor、写入工具或画像 API。
- 若通用 `read_tool_result` 被 learner memory 业务耦合、预算可通过并行 tool call 绕过或 resultRef 可跨 run 读取，不得开始最终联调。

## 10. 上下文交接

记录三项工具名、cursor/ref codec、预算 tracker、通用 guard/provenance 扩展和 eval 结果。不要携带工具返回正文。

## 11. 完成备注

完成时间：2026-07-30

状态：DONE

主要改动：

- Practice Chat 接入 `search_learner_memory`、`read_learner_memory_section`、`get_learner_memory_evidence`，使用 run-local opaque ref 和语义 cursor。
- 业务调用最多 3 次、范围读取最多 2 次；单次 8000 字符、合计 24000 字符，完整项分页并返回稳定预算结果。
- `read_tool_result` 增加通用 provenance/read guard；PostgreSQL 强制同 run blob 读取，记忆 guard 仅对三项工具计数。
- 自动配置、Definition 白名单和 bootstrap Prompt 已接线；查询故障有界降级，不阻断普通聊天。

验证：

- 任务定向 unit、Failsafe 命令均通过；`git diff --check` 通过。
- 波次 C 门禁 `make backend-test`、`make backend-it` 通过；后者完成 46 项 IT，零失败。

偏离计划：

- 为避免业务耦合，扩展了 agent-core 的通用 result provenance/read guard；同时修正 PostgreSQL `BYTEA` 原始数组映射。
- 修正随机 scope token 的测试断言；完整 unit gate 首次出现的 cache 指标断言重试后通过，未改变生产契约。

遗留事项：

- 无。

下一任务：`AMR-10`
