# RDP-12：DeepSeek fixture、异常、取消、安全与观测门禁

> 波次：D
>
> 状态：DONE
>
> 直接依赖：RDP-10、RDP-11
>
> 建议首轮文件上限：18

## 1. 目标与完成标准

使用 fake SDK client、固定 fixture 或本地 mock transport 覆盖 DeepSeek 文本、流式、结构化、工具 continuation、usage、错误和取消，并完成 reasoning state 的全边界泄漏审计。

本任务不调用真实外部服务。完成后本地证据必须证明 DeepSeek provider 行为完整映射到现有 `llm-core` 契约，且 continuation 只存在于当前 Agent run 内存。

## 2. 必须读取

- `CURRENT.md`、`RDP-10`/`RDP-11` 完成备注和 `CONTRACTS.md` 第 9、10、12、14 节。
- DeepSeek adapter/profile/config 和共享 mapper/publisher/client tests。
- `DynamicLlmGateway`、accounting、provider metrics 的最终 wrapper。
- `AgentLoopEngine` continuation tests。
- `PersistentAgentTraceObserver`、`PersistentAgentRunTraceObserver`、`LlmStreamSseMapper` tests。
- 管理 API 集成测试和 PostgreSQL IT 基类。

```bash
rg -n 'reasoning|encryptedContent|providerContinuation|continuation' \
  backend/llm-core backend/llm-openai-compatible backend/llm-deepseek backend/agent-core \
  backend/agent-persistence-postgres backend/mentor-api --glob '*.java'

rg -n 'OpenAI provider|provider.?=.?openai|LlmProviderId\.of\("openai"\)' \
  backend/llm-deepseek backend/llm-openai-compatible --glob '*.java'
```

## 3. Request 与 result fixture

至少覆盖：

- effort `null/none/low/high/max` 的 request JSON。
- `minimal/medium/xhigh` 和 thinking + temperature/topP 本地拒绝。
- 同步 text。
- 流式 text 的 start/delta/usage/end 顺序。
- JSON Object 和 JSON Schema。
- function tool call 参数与 call id。
- cached tokens、reasoning tokens 和 total tokens。
- 不支持字段不进入 request JSON。

fixture 只保存在测试资源，完成备注不复制内容。测试日志不得打印响应 body。

## 4. Continuation 与 Agent 场景

使用带唯一 sentinel 的 opaque payload，覆盖：

1. 单工具调用：reasoning item -> function call -> result -> final answer。
2. 连续两次工具调用：每个 step continuation 完整，call id 不串线。
3. effort `none` 工具调用：无 continuation 也能完成。
4. reasoning text/summary delta 被丢弃，用户只看到最终 content。
5. provider mismatch、损坏 payload、缺失 reasoning item 在远程零调用前失败。
6. max steps、tool permission 拒绝/超时、tool 失败和 Agent 取消不持久化 sentinel。

## 5. 异常与取消

- 401 -> `AUTHENTICATION_FAILED`。
- 403 -> `PERMISSION_DENIED`。
- 408/timeout -> `TIMEOUT` 或当前统一契约。
- 429 -> `RATE_LIMITED` 且 retryable。
- 5xx/IO/SSE failure -> `PROVIDER_UNAVAILABLE` 且按现有规则 retryable。
- invalid request -> `INVALID_REQUEST`。
- stream error event、迭代器异常、订阅前异常都只产生一次终态。
- cancel 关闭 SDK stream/resource，accounting 写 `CANCELLED`，active gauge 回落。

所有异常 provider/model identity 使用 `deepseek` 和目标 model，不出现 OpenAI 安全文案。

## 6. 安全审计

必须以自动化断言覆盖 sentinel 不存在于：

- `ContentDelta`、SSE `message_end` 和其他 SSE DTO。
- `LlmCompletionResult.message/metadata`。
- `LlmMessage` JSON。
- request diagnostic snapshot 的 messages、metadata、generation options。
- run step/tool trace、tool result blob、agent message 持久化 fake。
- accounting row、metrics tags、exception message/metadata。
- 捕获的应用日志。

只允许测试代码直接访问 continuation accessor。生产日志和 `toString()` 扫描不得出现 payload。

## 7. 本地集成 IT

新增 `DeepSeekProviderEndToEndIT` 或等价本地 IT，使用本地 mock/fake transport 串联：

- 创建 DeepSeek provider/model/route。
- route snapshot -> dynamic gateway -> shared client。
- text/structured/tool request。
- accounting 与 metric provider type/effort。
- provider disable 和非法 route 的失败路径。

不得访问公网；测试必须在无 `DEEPSEEK_API_KEY` 环境稳定运行。

## 8. 波次 D 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl llm-openai-compatible,llm-deepseek,agent-core,ai-governance -am \
  -Dtest='OpenAiCompatible*Test,DeepSeek*Test,AgentLoopEngineTest,RunMessageCompactorTest,AiAccountingLlmGatewayTest,AiProviderCallMetricsLlmGatewayTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-persistence-postgres,mentor-api -am \
  -Dtest='PersistentAgentTraceObserverTest,PersistentAgentRunTraceObserverTest,LlmStreamSseMapperTest,AdminAiProviderControllerTest,AdminAiModelRoutingControllerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test='*DeepSeekProviderEndToEndIT' verify

npm --cache ./.npm --prefix frontend test -- \
  src/admin/ai/AiProviderModelPanel.test.tsx \
  src/admin/ai/AiModelRoutingPanel.test.tsx \
  src/services/api.test.ts

npm --cache ./.npm --prefix frontend run build

git diff --check
```

全部通过后把 `CURRENT.md` 的“reasoning 泄漏审计”更新为 `PASS`。真实 E2E 门禁仍保持 `NOT_RUN`。

## 9. 非目标与停止条件

- 不使用真实 API Key，不把 mock 结果标记为真实发布证据。
- 不降低 trace/SSE 测试要求来迁就 continuation carrier。
- 若 sentinel 在任一禁止边界出现、两次工具调用丢上下文、取消资源未关闭、provider identity 漂移或本地 IT 访问公网，不得开始 `RDP-13`。

## 10. 上下文交接

记录 fixture 场景名、本地 IT、异常矩阵、资源关闭结果、sentinel 禁止落点和泄漏门禁状态。不要携带 fixture、日志或 payload。

## 11. 完成备注

完成时间：2026-08-03 11:19 UTC

状态：DONE

主要改动：

- DeepSeek fake client 覆盖 `null/none/low/high/max` 请求、参数 allowlist、文本、JSON Object/Schema、function tool continuation、usage、流式顺序、取消资源关闭与 provider identity。
- 增加 SDK HTTP/IO 异常矩阵；401/403/408/429/5xx、无效请求与 IO 错误均映射为正确的统一错误码，错误身份与文案保持 `deepseek`。
- 新增无网络 `DeepSeekProviderEndToEndIT`，经路由快照、`DynamicLlmGateway`、台账和指标调用 DeepSeek fake transport，覆盖结构化、工具 continuation、disabled provider 与非法 route。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl llm-openai-compatible,llm-deepseek,agent-core,ai-governance -am -Dtest='OpenAiCompatible*Test,DeepSeek*Test,AgentLoopEngineTest,RunMessageCompactorTest,AiAccountingLlmGatewayTest,AiProviderCallMetricsLlmGatewayTest' -Dsurefire.failIfNoSpecifiedTests=false test`：PASS（共享 10、DeepSeek 15、Agent 11、治理 13 tests）。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-persistence-postgres,mentor-api -am -Dtest='PersistentAgentTraceObserverTest,PersistentAgentRunTraceObserverTest,LlmStreamSseMapperTest,AdminAiProviderControllerTest,AdminAiModelRoutingControllerTest' -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='*DeepSeekProviderEndToEndIT' verify`：PASS（持久化安全 4、SSE 11、管理 API 6、local IT 3 tests）。
- `npm --cache ./.npm --prefix frontend test -- src/admin/ai/AiProviderModelPanel.test.tsx src/admin/ai/AiModelRoutingPanel.test.tsx src/services/api.test.ts`：PASS（41 tests）。
- `npm --cache ./.npm --prefix frontend run build` 与 `git diff --check`：PASS。

偏离计划：

- 无；本地 IT 使用内存 fake transport，不访问公网或读取 `DEEPSEEK_*`。

遗留事项：

- 真实 DeepSeek 发布门禁仍由 RDP-13 的显式 opt-in IT 执行。

下一任务：`RDP-13`
