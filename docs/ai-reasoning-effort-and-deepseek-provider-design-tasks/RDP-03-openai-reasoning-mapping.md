# RDP-03：OpenAI effort 能力声明与 Responses 映射

> 波次：A
>
> 状态：DONE
>
> 直接依赖：RDP-01、RDP-02
>
> 建议首轮文件上限：12

## 1. 目标与完成标准

让 OpenAI 动态 adapter 和现有 OpenAI facade 声明 Reasoning Effort 能力，并把最终 effort 映射到 Responses API `reasoning.effort`。

完成后七个协议值和空值在同步/流式路径行为一致，`max` 不依赖 SDK 静态常量，OpenAI 现有文本、工具、结构化输出和 usage 映射不发生回归。

## 2. 必须读取

- `CURRENT.md`、`RDP-01`/`RDP-02` 完成备注和 `CONTRACTS.md` 第 5 节。
- `OpenAiResponsesMapper`、`OpenAiProviderAdapter`、`OpenAiProviderClient`。
- `OpenAiLlmProvider` 和 `OpenAiLlmProperties` 的生产引用结论。
- `OpenAiLlmProviderTest`、`OpenAiProviderAdapterTest`。
- SDK `Reasoning` 和 `ReasoningEffort.of(...)` 的 `RDP-00` 结论；不要重新保存反编译全文。

```bash
rg -n 'SUPPORTED_CAPABILITIES|toParams\(|ResponseCreateParams|OpenAiResponsesMapper' \
  backend/llm-openai --glob '*.java'

rg -n 'reasoning\(|ReasoningEffort|REASONING_EFFORT' \
  backend/llm-openai backend/llm-core --glob '*.java'
```

## 3. 能力与接受值

- `OpenAiProviderAdapter.supportedCapabilities()` 增加 `REASONING_EFFORT`。
- `acceptedReasoningEfforts()` 返回全部七值，使用稳定不可变集合。
- 如果 legacy `OpenAiLlmProvider` 仍保留，其 capability descriptor 同步增加 `REASONING_EFFORT`。
- 不在 OpenAI config 增加 effort 字段。

## 4. 请求映射

`OpenAiResponsesMapper.toParams(...)` 固定行为：

- effort 为 `null`：不调用 `builder.reasoning(...)`，序列化 JSON 不包含 `reasoning`。
- effort 非空：构造 `Reasoning`，用 `ReasoningEffort.of(effort.wireValue())` 设置值。
- 不发送 reasoning summary 或其他未设计字段。
- 同步与流式 client 继续共用同一个 `toParams(...)`。
- mapper 不重新读取 invocation target 或 route effort；它只消费 gateway 已合并的 request options。

## 5. 测试矩阵

- 空值不出现 `reasoning`。
- `none/minimal/low/medium/high/xhigh/max` 精确序列化。
- `max` 在 SDK 静态常量缺失时仍通过 `of(...)` 工作。
- request 显式 `none` 覆盖 route `high` 后 mapper 收到 `none`。
- route `high` 在 request 空值时 mapper 收到 `high`。
- 同步和流式捕获的 `ResponseCreateParams` 对同一 request 完全一致。
- 原有文本、函数工具、JSON Object、JSON Schema、cached/reasoning token 和错误测试继续通过。
- provider adapter 的目录、capability 和 accepted efforts 与管理服务校验一致。
- provider type API 在接入 OpenAI capability 后返回全部七值和既有安全模板。

## 6. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl llm-openai -am \
  -Dtest='OpenAiReasoningEffortTest,OpenAiLlmProviderTest,OpenAiProviderAdapterTest,OpenAiLlmAutoConfigurationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest='AdminAiProviderControllerTest,AiProviderManagementServiceTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl llm-openai -am -DskipTests package

git diff --check
```

## 7. 非目标与停止条件

- 不抽取共享模块、不实现 continuation、不增加 DeepSeek。
- 不为特定 OpenAI 模型硬编码 effort 子集。
- 若空值仍发送 `reasoning`、同步/流式参数不一致、`max` 依赖静态常量或现有映射回归，不得开始 `RDP-04`。

## 8. 上下文交接

记录 capability/accepted set、mapper 唯一入口、七值/空值测试和 OpenAI 回归摘要。不要复制完整 request fixture。

## 9. 完成备注

完成时间：2026-08-03 09:57 UTC

状态：DONE

主要改动：

- OpenAI dynamic adapter 和 legacy facade 都声明 `REASONING_EFFORT`；adapter 接受全部七个协议值。
- Responses mapper 仅在 effective effort 非空时写入 `reasoning.effort`，并通过 `ReasoningEffort.of(...)` 覆盖 `max`。
- 新增空值、七值、路由优先级和同步/流式参数一致性测试，保留既有文本、工具和结构化输出回归。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl llm-openai -am -Dtest='OpenAiReasoningEffortTest,OpenAiLlmProviderTest,OpenAiProviderAdapterTest,OpenAiLlmAutoConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false test`: PASS（19 tests）
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest='AdminAiProviderControllerTest,AiProviderManagementServiceTest' -Dsurefire.failIfNoSpecifiedTests=false test`: PASS（7 tests）
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl llm-openai -am -DskipTests package`: PASS
- `git diff --check`: PASS

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`RDP-04`
