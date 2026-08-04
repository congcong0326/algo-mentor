# RDP-08：Opaque continuation 核心契约与 Agent loop 传递

> 波次：C
>
> 状态：DONE
>
> 直接依赖：RDP-07
>
> 建议首轮文件上限：18

## 1. 目标与完成标准

在 `llm-core` 和 `agent-core` 建立 provider opaque continuation 的敏感内存模型，并让 Agent loop 能从 step 终态取得 continuation、附着到 assistant tool-call message、在下一步请求中继续携带。

本任务先打通 provider 无关的承载与生命周期，不解析 SDK reasoning item。完成后 continuation 默认不可序列化、不可外发、不可持久化，现有无 continuation 行为和构造器保持兼容。

## 2. 必须读取

- `CURRENT.md`、`RDP-07` 完成备注和 `CONTRACTS.md` 第 10、12 节。
- `LlmMessage`、`LlmCompletionResult`、`LlmStreamEvent`。
- `AgentStepResult`、`AgentLoopEngine` 的 run loop、StepCollector 和 message compaction 调用。
- `RunMessageCompactor` 与测试。
- `AgentStreamEvent`、`LlmStreamSseMapper` 及测试。
- `PersistentAgentTraceObserver`、`PersistentAgentRunTraceObserver` 及测试。
- 一个 ObjectMapper message serialization 测试入口。

```bash
rg -n 'new LlmCompletionResult\(|new LlmStreamEvent\.MessageEnd\(|new AgentStepResult\(|assistantToolCalls\(' \
  backend --glob '*.java' --glob '!**/target/**'

rg -n 'valueToTree\(request\.messages|MessageEndData|onLlmEvent|onStepEnd' \
  backend/agent-persistence-postgres backend/mentor-api backend/agent-core --glob '*.java'
```

## 3. 敏感核心模型

新增 `LlmProviderContinuation`：

- 字段为 `LlmProviderType providerType` 和 `JsonNode payload`。
- provider type、非空 object/array payload 在构造时校验；具体 payload shape 由 provider mapper 负责。
- 构造时 defensive copy；对外 accessor 不允许调用方修改内部 payload。
- `toString()` 固定返回不含长度、hash 或内容的 REDACTED 摘要。
- 不实现通用 JSON DTO 语义，不进入 metadata constants。

## 4. Carrier 兼容扩展

以下类型增加可空 continuation，并保留现有构造器/工厂默认 `null`：

- `LlmCompletionResult`。
- `LlmStreamEvent.MessageEnd`。
- `AgentStepResult`。
- `LlmMessage` 的 assistant tool-call message。

通用 Jackson 序列化必须通过 `@JsonIgnore` 或等价显式机制忽略所有 carrier 的 continuation。新增：

- `LlmMessage.assistantToolCalls(toolCalls, continuation)`。
- `message.providerContinuation()` Java 内存 accessor。

不得把 continuation 放入既有 `metadata` Map 或 toolCalls metadata key。

## 5. Agent loop 传递

- StepCollector 从 `MessageEnd` 捕获 continuation。
- `AgentStepResult` 返回 tool calls、finish reason、content 和 continuation。
- 只有 `requiresTools()` 为 true 时，外层 loop 把 continuation 附着到改写后的 assistant tool-call message。
- 无工具最终输出忽略/清空 continuation，不进入 `AgentRunResult`。
- error、取消、permission 拒绝导致 run 结束时清理 collector 和局部引用。
- 下一步 `AgentLlmRequestFactory` 使用已有 messages 原样构造 request，不额外复制到 metadata。

## 6. Compaction 规则

- `RunMessageCompactor` 识别 continuation-bearing assistant tool-call message 时，必须把它和匹配 tool results 作为不可拆分 group。
- 当前尚未结束的最近 tool interaction group 不得被 snip；如果预算规则无法保留，明确失败而不是静默丢 continuation 后继续请求。
- 压缩旧 tool result 时保留 assistant message 对象及 continuation。
- 生成 compact marker 时不复制 continuation，也不写任何存在标志、长度或 provider payload。
- group 被安全移除后不在其他缓存/metadata 保留引用。

## 7. 外部与持久化边界

- `LlmStreamSseMapper` 的 `MessageEndData` 只包含 finish reason 和安全 metadata，显式不映射 continuation。
- `AgentStreamEvent` 的名称和现有外部事件不新增 continuation event。
- `PersistentAgentTraceObserver` 序列化 request messages 后不含 continuation。
- `PersistentAgentRunTraceObserver` 只保存 provider/model/usage/finish reason，不访问 continuation。
- 普通 agent message、tool result store、accounting 和 runtime metadata 都不得新增 continuation 字段。

## 8. 重点测试

- carrier 的旧构造器和 factory 继续工作，continuation 为 null。
- continuation provider/payload 校验、defensive copy 和 REDACTED `toString()`。
- ObjectMapper 序列化 message/result/MessageEnd 不包含 payload 或 continuation 字段。
- 单工具和连续两 step 的 Agent loop 把相同 opaque state 送入下一 request message。
- 最终无工具 step 不保留 continuation。
- compactor 保留当前 tool group，旧 group snip 不复制 continuation。
- SSE、request snapshot、run trace 的捕获结果不含 payload 中的唯一 sentinel。
- 取消、异常和 max steps 路径不会把 sentinel 写入日志或持久化 fake。

## 9. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl llm-core,agent-core -am \
  -Dtest='LlmProviderContinuationTest,LlmCoreModelTest,AgentLoopEngineTest,AgentLoopRunnerTest,RunMessageCompactorTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-persistence-postgres,mentor-api -am \
  -Dtest='PersistentAgentTraceObserverTest,PersistentAgentRunTraceObserverTest,LlmStreamSseMapperTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

git diff --check
```

## 10. 非目标与停止条件

- 不解析 reasoning SDK item、不注册 DeepSeek、不把 continuation 跨 run 持久化。
- 不通过通用 metadata/redactor 兜底来“隐藏” payload；结构上必须不进入序列化对象。
- 若 sentinel 出现在 SSE、request snapshot、trace、日志或 JSON，或 compactor 能静默丢失当前 continuation，不得开始 `RDP-09`。

## 11. 上下文交接

记录 continuation 类型、四个 carrier、Agent loop 附着点、compactor 保留规则、禁止落点测试和 sentinel 扫描结果。不要记录 payload fixture。

## 12. 完成备注

完成时间：2026-08-03 10:45 UTC

状态：DONE

主要改动：

- 新增 `LlmProviderContinuation`，以 defensive copy 和 `REDACTED` 文本承载仅当前 run 可见的 provider 私有 payload。
- completion、stream end、agent step 和 assistant tool-call message 增加忽略序列化的可空 continuation；旧构造器与工厂保持兼容。
- Agent loop 仅在工具调用 step 将 continuation 放入下一轮 assistant message；compactor 保留相关 tool interaction group，无法安全保留时明确失败。
- SSE、trace、持久化与普通 JSON 的 sentinel 回归测试确认 continuation 不越过外部边界。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl llm-core,agent-core -am -Dtest='LlmProviderContinuationTest,LlmCoreModelTest,AgentLoopEngineTest,AgentLoopRunnerTest,RunMessageCompactorTest' -Dsurefire.failIfNoSpecifiedTests=false test`: PASS（44 tests）
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-persistence-postgres,mentor-api -am -Dtest='PersistentAgentTraceObserverTest,PersistentAgentRunTraceObserverTest,LlmStreamSseMapperTest' -Dsurefire.failIfNoSpecifiedTests=false test`: PASS（15 tests）
- `git diff --check`: PASS。

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`RDP-09`
