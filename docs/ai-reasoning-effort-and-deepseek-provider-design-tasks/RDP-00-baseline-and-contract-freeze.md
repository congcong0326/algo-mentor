# RDP-00：基线、协议与契约冻结

> 波次：A
>
> 状态：DONE
>
> 直接依赖：无
>
> 建议首轮文件上限：12

## 1. 目标与完成标准

在修改业务代码前，重新验证仓库基线、上游 Responses 协议、`openai-java 4.39.1` 实际类型、Flyway 版本和现有 provider/Agent 调用链，冻结后续任务使用的代码事实。

完成后必须能够回答：当前相关测试是否通过、下一个可用迁移版本是什么、OpenAI 共享职责实际分布在哪些类、continuation 会经过哪些内存/持久化边界、SDK 是否能表达七个 effort 和 reasoning input/output item。

本任务不修改业务行为、不创建 Maven 模块、不创建迁移，也不顺手清理 legacy OpenAI facade。

## 2. 必须读取

- `CURRENT.md`、`README.md`、`CONTRACTS.md`。
- `docs/ai-reasoning-effort-and-deepseek-provider-design.md`；这是唯一默认允许完整读取设计原文的任务。
- `docs/ai-provider-and-model-routing-design.md` 的 provider、模型、路由和回滚章节。
- `docs/code-index.md` 中 LLM、AI governance、Agent Runtime、持久化和管理前端条目。
- `backend/pom.xml`、`backend/llm-openai/pom.xml`、`backend/mentor-api/pom.xml`。
- `LlmGenerationOptions`、`LlmCompletionRequest`、`LlmInvocationTarget`、`DynamicLlmGateway`、`LlmProviderAdapter`。
- `AiModelRoutePolicyContent`、`ResolvedAiModelSnapshot`、`DefaultAiModelRouteResolver`、`AiProviderManagementService`。
- `OpenAiResponsesMapper`、`OpenAiStreamPublisher`、`OpenAiLlmExceptionMapper`、`OpenAiProviderAdapter`。
- `AgentLoopEngine`、`LlmMessage`、`AgentStepResult`、`PersistentAgentTraceObserver` 和 `LlmStreamSseMapper` 的相关片段。

首轮先执行扫描，按命中打开代码：

```bash
rg -n 'REASONING_EFFORT|LlmGenerationOptions|AiModelRoutePolicyContent|ResolvedAiModelSnapshot|OpenAiResponses|ai_llm_call_usage' \
  backend frontend/src --glob '!**/target/**' --glob '!**/dist/**'

rg -n 'assistantToolCalls|MessageEnd|AgentStepResult|valueToTree\(request\.messages|LlmStreamSseMapper' \
  backend --glob '*.java'

find backend -path '*/src/main/resources/db/migration/*.sql' -type f -print | sort -V
```

## 3. SDK 与协议核对

只记录结论，不保存完整反编译输出：

1. 核对 `Reasoning`、`ReasoningEffort.of(String)` 和 `ResponseCreateParams.Builder.reasoning(...)`。
2. 核对 `ResponseOutputItem.reasoning()`、`ResponseReasoningItem`、`ResponseInputItem.ofReasoning(...)` 或等价输入构造。
3. 核对 stream event 能否从 `output_item.done` 取得完整 reasoning item，以及 reasoning text/summary delta 的实际类型名。
4. 核对 SDK `MAX` 静态常量是否缺失；无论结果如何，后续都使用 `of(wireValue)`。
5. 用官方资料复核 DeepSeek Responses 当前 Base URL、effort 子集、默认 thinking、temperature/topP 约束、工具调用 reasoning 续传和不支持字段。

若上游协议与设计原文发生变化，只修正 `CONTRACTS.md` 的事实字段；不得在本任务重新设计产品范围。

## 4. 基线检查

1. 记录 `git status --short` 和 `git diff --stat`，区分学习计划既有改动与本任务包后续改动。
2. 扫描全仓 Flyway 版本，记录当前最大版本和 `RDP-04` 候选版本；真正创建迁移前仍需重扫。
3. 记录 `llm-openai` 中 provider 身份类与可共享传输类的实际清单。
4. 记录 legacy `OpenAiLlmProvider/OpenAiLlmProperties` 是否仍被生产 wiring 使用；不凭测试引用直接删除。
5. 记录请求快照、run trace、SSE、普通消息和 accounting 对 continuation 的潜在泄漏入口。
6. 运行当前最小相关后端和前端测试，基线失败只记录首个原因，不修改无关代码。

## 5. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl llm-core,llm-openai,ai-governance,agent-core,mentor-api -am \
  -Dtest='DynamicLlmGatewayTest,LlmCoreModelTest,OpenAiLlmProviderTest,OpenAiProviderAdapterTest,DefaultAiModelRouteResolverTest,AiProviderManagementServiceTest,AiAccountingLlmGatewayTest,AgentLoopEngineTest,LlmStreamSseMapperTest,AdminAiModelRoutingControllerTest,AdminAiProviderControllerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

npm --cache ./.npm --prefix frontend test -- \
  src/admin/ai/AiModelRoutingPanel.test.tsx \
  src/admin/ai/AiProviderModelPanel.test.tsx \
  src/services/api.test.ts

git diff --check
```

## 6. 出口与停止条件

- 基线测试、最大迁移版本、候选版本、SDK 类型结论和 legacy OpenAI 使用状态已写入 `CURRENT.md`。
- `CONTRACTS.md` 与当前协议、SDK 和代码事实没有阻断性冲突。
- continuation 的五类禁止落点已经定位到稳定类型/方法。
- 若 SDK 无法表达 reasoning item 输入、设计依赖的 DeepSeek 协议已撤销、基线无法编译或迁移版本冲突，停止并标记 `BLOCKED`，不得开始 `RDP-01`。

## 7. 上下文交接

只记录测试摘要、迁移版本、SDK 类型/方法名、共享类清单、legacy facade 状态和泄漏边界。不要携带设计原文、官方响应示例、`javap` 全文或 reasoning fixture。

## 8. 完成备注

完成时间：2026-08-03 09:40 UTC

状态：DONE

主要改动：

- 完成基线、OpenAI/DeepSeek 上游协议、`openai-java 4.39.1` 类型和 continuation 边界核对；未修改业务行为。
- 当前最大 Flyway 版本为 `V53`，`RDP-04` 创建迁移前重新扫描并从 `V54` 重新确认。
- 共享候选为 `OpenAiResponsesClient`、`SdkOpenAiResponsesClient`、`OpenAiResponsesMapper`、`OpenAiStreamPublisher`、`OpenAiLlmExceptionMapper` 与 `OpenAiProviderClient`；legacy facade 未参与生产 wiring。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl llm-core,llm-openai,ai-governance,agent-core,mentor-api -am -Dtest='DynamicLlmGatewayTest,LlmCoreModelTest,OpenAiLlmProviderTest,OpenAiProviderAdapterTest,DefaultAiModelRouteResolverTest,AiProviderManagementServiceTest,AiAccountingLlmGatewayTest,AgentLoopEngineTest,LlmStreamSseMapperTest,AdminAiModelRoutingControllerTest,AdminAiProviderControllerTest' -Dsurefire.failIfNoSpecifiedTests=false test`: PASS
- `npm --cache ./.npm --prefix frontend test -- src/admin/ai/AiModelRoutingPanel.test.tsx src/admin/ai/AiProviderModelPanel.test.tsx src/services/api.test.ts`: PASS（36 tests）
- `git diff --check`: PASS

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`RDP-01`
