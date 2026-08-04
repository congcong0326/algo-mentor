# RDP-07：OpenAI-compatible Responses 共享模块等价抽取

> 波次：C
>
> 状态：DONE
>
> 直接依赖：RDP-06
>
> 建议首轮文件上限：18

## 1. 目标与完成标准

新增纯 Java `llm-openai-compatible` 模块，把 OpenAI 与 DeepSeek 可复用的 Responses SDK 传输、请求/响应映射、stream publisher 和异常归一从 `llm-openai` 等价抽出。

本任务只做模块边界和 OpenAI 行为等价，不实现 reasoning continuation，不注册 DeepSeek，不修改管理前端。完成后线上可见 provider 仍只有 OpenAI，现有 request/result/stream/error fixture 与抽取前一致。

## 2. 必须读取

- `CURRENT.md`、`RDP-06` 完成备注和 `CONTRACTS.md` 第 7、8 节。
- `RDP-00` 记录的共享类清单与 legacy OpenAI 使用结论。
- `backend/pom.xml`、`llm-openai/pom.xml`、`mentor-api/pom.xml`。
- `OpenAiResponsesClient`、`SdkOpenAiResponsesClient`、client factory。
- `OpenAiResponsesMapper`、`OpenAiStreamPublisher`、`OpenAiLlmExceptionMapper`。
- `OpenAiProviderClient`、`OpenAiProviderAdapter`、`OpenAiProviderConfig`、自动配置。
- `OpenAiLlmProviderTest` 和 adapter/auto-configuration tests。

```bash
rg -n 'OpenAiResponses(Client|Mapper)|OpenAiStreamPublisher|OpenAiLlmExceptionMapper|OpenAiProviderClient' \
  backend --glob '*.java'

rg -n 'OpenAI provider|OpenAI SDK|api\.openai\.com|LlmProviderId\.of\("openai"\)' \
  backend/llm-openai --glob '*.java'
```

## 3. 模块与包边界

新增：

```text
backend/llm-openai-compatible/
  pom.xml
  src/main/java/org/congcong/algomentor/llm/openai/compatible/
  src/test/java/org/congcong/algomentor/llm/openai/compatible/
```

- parent reactor 在 `llm-core` 后、`llm-openai` 前注册新模块。
- 共享模块只依赖 `llm-core`、`openai-java`、Jackson 和 SLF4J；测试依赖按现有模式添加。
- 不依赖 Spring、数据库、治理、Agent 或 API 模块。
- 不提供 `AutoConfiguration.imports`，不注册任何 bean。

## 4. 共享类型

按固定契约迁移/重命名：

- `OpenAiCompatibleResponsesClient`。
- `SdkOpenAiCompatibleResponsesClient`。
- `OpenAiCompatibleResponsesMapper`。
- `OpenAiCompatibleStreamPublisher`。
- `OpenAiCompatibleExceptionMapper`。
- `OpenAiCompatibleProviderClient`。
- `OpenAiCompatibleConnectionConfig`。
- `OpenAiCompatibleProviderProfile`。

如测试需要注入 client，保留窄 factory 接口。共享 public API 只暴露 adapter 构造 client 所需类型，不把 SDK 大对象扩散到治理或 API 模块。

## 5. Profile 驱动差异

新增 OpenAI profile，至少提供 provider type、display name、capabilities、accepted efforts、request validation 和 continuation 开关。

共享代码：

- provider id、日志 provider 名称和安全异常文案从 profile 获得。
- 不引用 `OpenAiProviderAdapter.PROVIDER_TYPE`。
- 不包含 OpenAI/DeepSeek Base URL 或 effort 子集。
- 对 SDK 底层 `OpenAIServiceException` 只做统一错误码映射，对上层 provider identity 使用当前 profile。

本任务 OpenAI profile 的 `requiresReasoningContinuationForToolCalls()` 固定为 `false`；continuation 行为由 `RDP-09` 增加。

## 6. OpenAI 薄 adapter

- `llm-openai` 保留 `OpenAiProviderAdapter`、`OpenAiProviderConfig`、OpenAI profile 和 Spring 自动配置。
- adapter 把严格 config 转为共享 connection config，再创建共享 provider client。
- `OpenAiProviderConfig.fromJson(...)`、default template、capabilities 和 accepted efforts 外部行为保持不变。
- legacy `OpenAiLlmProvider/OpenAiLlmProperties` 按 `RDP-00` 结论处理：仍有生产引用时改为共享层薄 facade；只有测试引用时也不在本任务顺手删除，最终任务再审核。
- `mentor-api` 继续只直接依赖 `llm-openai`，不直接使用共享 SDK 类型。

## 7. 分段检查点

本任务预期移动文件较多，必须形成两个检查点：

1. 模块骨架、profile、connection config、client/mapper/publisher/exception mapper 完成，`llm-openai-compatible` 独立测试和 compile 通过。
2. `llm-openai` 改为薄 adapter，旧共享类引用清零，OpenAI 全量相关测试通过。

检查点 1 未通过时，不同时修改 OpenAI 自动配置和 legacy facade。

## 8. 行为等价测试

- 抽取前后同一 request 的 Responses params fixture 完全一致，包括 effort。
- 同步 text/tool/JSON Object/JSON Schema result 一致。
- stream event 顺序、tool arguments 累积、usage、MessageEnd 和 error 一致。
- 取消关闭 SDK stream resource；同一 stream 仍只允许一个 subscriber。
- 401、403、408、429、5xx、IO 和 SSE error 的 code/retryable/provider/model 一致。
- 日志和异常不再在共享层硬编码 OpenAI；OpenAI adapter 对外仍显示 OpenAI。
- 共享模块生产源码不存在 `api.openai.com`、`api.deepseek.com`、`LlmProviderType.of("openai")` 或 `LlmProviderType.of("deepseek")`。

## 9. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl llm-openai-compatible -am \
  -Dtest='OpenAiCompatible*Test' -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl llm-openai -am \
  -Dtest='OpenAi*Test' -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -DskipTests compile

rg -n 'api\.openai\.com|api\.deepseek\.com|LlmProviderType\.of\("(openai|deepseek)"\)' \
  backend/llm-openai-compatible/src/main

git diff --check
```

上述 `rg` 预期零命中；类型名中的 `OpenAiCompatible` 不属于 provider 身份硬编码。

## 10. 非目标与停止条件

- 不实现 continuation，不增加 DeepSeek module，不改变管理 API 或数据库。
- 不通过复制旧类再长期保留两套 mapper；迁移结束后共享职责只有一个实现。
- 若 OpenAI fixture 漂移、共享模块注册 Spring bean、provider identity 仍硬编码或取消资源语义回归，不得开始 `RDP-08`。

## 11. 上下文交接

记录新 module/package、profile 类型、OpenAI 薄 adapter 入口、legacy facade 处理、两个检查点和等价测试结果。不要复制 fixture 或异常 body。

## 12. 完成备注

完成时间：2026-08-03 10:35 UTC

状态：DONE

主要改动：

- 新增 `llm-openai-compatible` 纯 Java 模块，并迁移 Responses SDK client、请求/响应 mapper、stream publisher、异常映射和 provider client。
- 新增脱敏 connection config、profile 和窄 client factory；共享层通过 profile 取得身份、能力、effort、请求校验、异常文案和流日志身份。
- OpenAI adapter 保留严格 config 边界并转换为共享连接配置；legacy facade 复用共享 mapper/client，同时保留独立 stream timeout 行为。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl llm-openai-compatible -am -Dtest='OpenAiCompatible*Test' -Dsurefire.failIfNoSpecifiedTests=false test`：PASS（4 tests）。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl llm-openai -am -Dtest='OpenAi*Test' -Dsurefire.failIfNoSpecifiedTests=false test`：PASS（18 tests）。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -DskipTests compile`：PASS。
- 共享层 provider identity 扫描与 `git diff --check`：PASS。

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`RDP-08`
