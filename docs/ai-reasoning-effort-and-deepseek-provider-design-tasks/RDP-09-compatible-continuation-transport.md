# RDP-09：共享 mapper 的 reasoning continuation 收发与 allowlist

> 波次：C
>
> 状态：DONE
>
> 直接依赖：RDP-08
>
> 建议首轮文件上限：18

## 1. 目标与完成标准

在 `llm-openai-compatible` 中实现 profile 驱动的 reasoning item 收集、opaque continuation 生成和下一轮 input 恢复，并固定共享请求字段 allowlist。

完成后需要 continuation 的测试 profile 可以在同步、流式、单工具和多 step 工具链中保持 reasoning 上下文；reasoning text/summary 不会成为可见内容；OpenAI profile 保持既有行为。

## 2. 必须读取

- `CURRENT.md`、`RDP-08` 完成备注和 `CONTRACTS.md` 第 7 至 10、12 节。
- `OpenAiCompatibleProviderProfile`、provider client、responses mapper、stream publisher。
- `LlmProviderContinuation`、`LlmMessage` continuation accessor。
- `RDP-00` 记录的 SDK reasoning input/output/stream 类型结论。
- 共享模块现有 request/result/stream fixture tests。
- Agent loop continuation 单元测试，只读取 request message 断言部分。

```bash
rg -n 'ResponseReasoningItem|isReasoning|reasoningText|reasoningSummary|OutputItemDone|toInput\(' \
  backend/llm-openai-compatible --glob '*.java'

rg -n 'store\(|previousResponseId|conversation\(|metadata\(|include\(|serviceTier' \
  backend/llm-openai-compatible backend/llm-openai --glob '*.java'
```

## 3. Profile 校验顺序

`OpenAiCompatibleProviderClient.complete/stream` 固定顺序：

1. profile 校验 model/request。
2. mapper 校验 continuation provider type 和 payload shape。
3. 构造 allowlist Responses params。
4. 调用 SDK。

任何本地校验失败都发生在 SDK client 调用前，并映射为稳定 `INVALID_REQUEST` 或现有更精确错误。

## 4. 同步 continuation

- 从 `Response.output` 按顺序收集完整 reasoning output item。
- 只有 profile 要求 continuation 且同一 response 存在函数 tool call 时，创建 `LlmProviderContinuation`。
- payload 只包含恢复下一轮 request 所需的原始 provider item，不包含额外可见 summary、日志字段或本地 metadata。
- `LlmCompletionResult.continuation` 携带 opaque state；message content、structured output 和 metadata 不变。
- 无工具最终 response、失败或取消不创建 continuation。

## 5. 流式 continuation

- `response.output_item.done` 遇到完整 reasoning item时在 publisher 内部按顺序收集。
- reasoning text/summary delta/done 全部忽略，不发送 `ContentDelta`、Heartbeat 或自定义事件。
- completed/incomplete/failed 前根据是否已经产生 tool call 决定 `MessageEnd.continuation`。
- incomplete/failed/error 默认不向下一 step 提供 continuation。
- publisher cancel 清空收集状态并关闭 SDK resource。

## 6. 下一轮 input 恢复

处理带 continuation 的 assistant tool-call message：

- provider type 必须与当前 profile 完全相同。
- payload 必须解析为允许的 reasoning item 集合；未知/缺失结构拒绝，不发送自由 JSON additional item。
- input 顺序固定为 reasoning item -> 本 assistant message 的 function call items -> 后续对应 function result items。
- 不使用 `previous_response_id`、`store` 或 conversation state。
- 同一 message 不允许多个 provider continuation 或把 continuation 附着到非 assistant/tool-call message。

## 7. 共享请求 allowlist

共享 mapper 只发送项目明确抽象的字段：

- model、stateless input items。
- temperature、topP、maxOutputTokens、reasoning effort。
- function tools 和 tool choice。
- text/JSON Object/JSON Schema response format。
- stream 由 SDK 调用方式决定。

不发送 stop/seed，除非当前 mapper 已有经过测试的协议支持；如现状没有发送，本任务不顺手扩展。禁止发送 `store`、`previous_response_id`、`conversation`、远端 metadata、include、service tier 或 hosted tools。

## 8. 重点测试

- 同步 tool response 生成 continuation，下一 request 恢复 reasoning + call + result 顺序。
- 流式 output item done 收集 reasoning，reasoning delta 不进入 content。
- 连续两次工具调用，每 step 使用自己的 continuation，call id 全部匹配。
- provider type 不匹配、payload 非法、continuation 附错 message 在 SDK 零调用前失败。
- 最终 answer、error、incomplete、cancel 不保留 continuation。
- OpenAI profile `requires...=false` 时 request/result fixture 与 `RDP-07` 完全一致。
- allowlist 之外字段不出现在序列化 request JSON。
- continuation sentinel 仍不出现在 SSE、trace 或日志回归测试。

## 9. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl llm-openai-compatible -am \
  -Dtest='OpenAiCompatible*Continuation*Test,OpenAiCompatibleResponsesMapperTest,OpenAiCompatibleStreamPublisherTest,OpenAiCompatibleProviderClientTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl llm-openai,agent-core,agent-persistence-postgres,mentor-api -am \
  -Dtest='OpenAi*Test,AgentLoopEngineTest,RunMessageCompactorTest,PersistentAgentTraceObserverTest,LlmStreamSseMapperTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

git diff --check
```

## 10. 波次 C 出口与停止条件

- 共享层 continuation sync/stream/multi-step 测试通过。
- OpenAI 抽取后全部 fixture 继续等价。
- reasoning delta、payload sentinel 和 provider state 在外部/持久化边界零泄漏。
- request JSON 只包含 allowlist 字段。

任一条件不满足，不得注册 DeepSeek adapter。

## 11. 上下文交接

记录 profile 校验顺序、payload codec 类型、sync/stream 收集点、input 排序、allowlist 和波次测试结果。不要携带 reasoning item JSON。

## 12. 完成备注

完成时间：2026-08-03 10:56 UTC

状态：DONE

主要改动：

- 新增受限 SDK reasoning item codec，同步响应仅在 completed tool response 中生成 matching-provider continuation，并在下一轮按 reasoning、function call、function result 顺序恢复输入。
- stream publisher 从 `output_item.done` 收集完整 reasoning item；reasoning text/summary 不映射为内容，incomplete、failed、error、cancel 均清理本地状态且不保留 continuation。
- 共享 Responses 请求保持 allowlist；stop、seed、store、previous response、conversation、metadata、include 和 service tier 均不发送。
- 覆盖同步、连续工具调用、非法 provider/payload 的 SDK 零调用、stream、incomplete、取消和 allowlist 回归。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl llm-openai-compatible -am -Dtest='OpenAiCompatible*Continuation*Test,OpenAiCompatibleResponsesMapperTest,OpenAiCompatibleStreamPublisherTest,OpenAiCompatibleProviderClientTest' -Dsurefire.failIfNoSpecifiedTests=false test`: PASS（6 continuation tests）
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl llm-openai,agent-core,agent-persistence-postgres,mentor-api -am -Dtest='OpenAi*Test,AgentLoopEngineTest,RunMessageCompactorTest,PersistentAgentTraceObserverTest,LlmStreamSseMapperTest' -Dsurefire.failIfNoSpecifiedTests=false test`: PASS（OpenAI 18、Agent core 11、persistence 2、API 11 tests）
- `git diff --check`: PASS。

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`RDP-10`
