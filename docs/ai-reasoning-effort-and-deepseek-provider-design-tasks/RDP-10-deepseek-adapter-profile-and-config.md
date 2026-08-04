# RDP-10：DeepSeek adapter、profile、配置与请求约束

> 波次：D
>
> 状态：DONE
>
> 直接依赖：RDP-09
>
> 建议首轮文件上限：16

## 1. 目标与完成标准

新增独立 `llm-deepseek` 模块，使用共享 OpenAI-compatible transport 实现 `deepseek` provider adapter、严格配置、能力 profile、request validation 和 Spring 自动配置。

完成后 DeepSeek 可以作为代码注册 provider type 创建 client，所有不支持或会被静默忽略的关键参数都在远程调用前拒绝，provider identity 全程保持 `deepseek`。

## 2. 必须读取

- `CURRENT.md`、`RDP-09` 完成备注和 `CONTRACTS.md` 第 8、9 节。
- `backend/pom.xml`、`llm-openai-compatible/pom.xml`、`llm-openai/pom.xml`。
- OpenAI adapter/config/profile/auto-configuration 的最终薄实现。
- `OpenAiCompatibleProviderClient` 和 connection config 构造入口。
- `LlmProviderAdapterRegistryTest` 和 OpenAI auto-configuration test 模式。
- `LlmGenerationOptions` 的 temperature/topP 范围校验。

```bash
rg -n 'OpenAiProvider(Adapter|Config|Profile)|OpenAiLlmAutoConfiguration|defaultConfig|acceptedReasoningEfforts' \
  backend/llm-openai backend/llm-openai-compatible --glob '*.java'

rg -n 'LlmCapability\.(VISION_INPUT|FILE_INPUT|EMBEDDING)|temperature\(|topP\(' \
  backend/llm-core backend/llm-openai-compatible --glob '*.java'
```

## 3. 模块与稳定类型

新增：

```text
backend/llm-deepseek/
  pom.xml
  src/main/java/org/congcong/algomentor/llm/deepseek/
  src/main/java/org/congcong/algomentor/llm/deepseek/autoconfigure/
  src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

稳定类型：

- `DeepSeekProviderAdapter`。
- `DeepSeekProviderConfig`。
- `DeepSeekProviderProfile`。
- `DeepSeekLlmAutoConfiguration`。

parent reactor 在 `llm-openai` 后注册 `llm-deepseek`；module 依赖 `llm-core`、`llm-openai-compatible` 和 Spring auto-config，不依赖 `llm-openai`。

## 4. Provider 身份与能力

- `DeepSeekProviderAdapter.PROVIDER_TYPE = LlmProviderType.of("deepseek")`。
- display name 为 `DeepSeek`。
- capabilities 和不支持项严格使用 `CONTRACTS.md` 第 9 节。
- accepted efforts 只返回 `none/low/high/max` 的强类型集合。
- default config 使用官方 Base URL 模板，但 create/update provider 时 Base URL 仍是显式必填配置。
- 不硬编码模型白名单或默认生产模型。

## 5. 严格配置

`DeepSeekProviderConfig.fromJson(...)`：

- 只接受 `apiKey/baseUrl/timeoutSeconds/maxRetries`。
- 未知字段、空 key、非 HTTP(S) 绝对 URL、非正 timeout、负 retry 拒绝。
- `toString()` 同时脱敏 API Key 和 Base URL。
- 转换为共享 connection config 后创建 SDK client。
- 不读取 `DEEPSEEK_*` 环境变量或 YAML fallback。

## 6. Request validation

`DeepSeekProviderProfile.validateRequest(...)` 在 SDK 调用前执行：

- effort 为空或为 `none/low/high/max`。
- effort 为 `null/low/high/max` 时 temperature 和 topP 都为空。
- effort 为 `none` 时允许既有合法 temperature/topP。
- image/file 由 gateway capability 拒绝；profile 可做防御性二次检查。
- tools 只能是现有函数工具抽象；不接受 hosted/custom tool。
- continuation required 开关为 `true`。
- 不修改 request，不静默删除管理员配置的无效参数；有业务语义冲突时明确失败。

## 7. Adapter 与自动配置

- adapter 校验 instance provider type 必须为 `deepseek`。
- adapter 用 DeepSeek profile 创建共享 provider client。
- 自动配置只注册一个 `DeepSeekProviderAdapter`，使用 `@ConditionalOnMissingBean` 与现有模式一致。
- 不创建静态全局 client，不读取数据库；动态实例 client 仍由 `ProviderClientRegistry` 管理版本。

## 8. 重点测试

- provider type、display name、capabilities、accepted effort 和 default template。
- config 合法/未知字段/空 key/URL/timeout/retry 和 REDACTED `toString()`。
- `null/none/low/high/max` 通过，`minimal/medium/xhigh` 拒绝。
- thinking 默认或开启时 temperature/topP 拒绝；`none` 时合法参数通过。
- adapter provider type 不匹配拒绝，client factory 零调用。
- auto-configuration 注册、用户 bean 覆盖和 Spring imports 正确。
- result/error provider id 使用 `deepseek`，不出现 `openai`。

## 9. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl llm-deepseek -am \
  -Dtest='DeepSeekProviderConfigTest,DeepSeekProviderProfileTest,DeepSeekProviderAdapterTest,DeepSeekLlmAutoConfigurationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl llm-deepseek -am -DskipTests package

rg -n 'LlmProviderType\.of\("openai"\)|OpenAI provider' \
  backend/llm-deepseek/src/main

git diff --check
```

上述 `rg` 预期零命中；SDK 底层类型名不属于 provider identity。

## 10. 非目标与停止条件

- 不修改管理前端、不创建 provider/model 数据、不运行真实 API。
- 不让 DeepSeek 依赖 `llm-openai`，不复制 shared mapper。
- 若非法参数会被静默删除、environment fallback 存在、provider id 漂移或 module 依赖反向，不得开始 `RDP-11`。

## 11. 上下文交接

记录 module、四个稳定类型、capability/effort 子集、config 字段、request validation 表和测试结果。不要记录任何测试 key 或 Base URL 实例。

## 12. 完成备注

完成时间：2026-08-03 11:01 UTC

状态：DONE

主要改动：

- 新增不依赖 `llm-openai` 的 `llm-deepseek` reactor module、独立 adapter/config/profile 和 Spring auto-configuration。
- strict config 仅接受四个字段；profile 固定 `deepseek` identity、能力和 `none/low/high/max` effort 子集，并在 thinking 默认/开启时拒绝 temperature/topP。
- adapter 经共享 OpenAI-compatible client 创建运行时 client，启用 reasoning continuation，未读取环境变量或引入模型白名单。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl llm-deepseek -am -Dtest='DeepSeekProviderConfigTest,DeepSeekProviderProfileTest,DeepSeekProviderAdapterTest,DeepSeekLlmAutoConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false test`: PASS（7 tests）
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl llm-deepseek -am -DskipTests package`: PASS。
- DeepSeek provider identity scan 与 `git diff --check`: PASS。

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`RDP-11`
