# RDP-01：Reasoning Effort 核心契约与生效值合并

> 波次：A
>
> 状态：DONE
>
> 直接依赖：RDP-00
>
> 建议首轮文件上限：14

## 1. 目标与完成标准

在 `llm-core` 建立七值 effort、不可变生成参数、路由级调用目标字段和唯一生效值合并器，并让 `DynamicLlmGateway` 在 capability 校验前使用最终请求。

完成后 request 显式值能够覆盖 route 值，route 值能作为默认值，二者都为空时保持不发送；同步和流式 gateway 使用完全相同的合并和能力判断。

## 2. 必须读取

- `CURRENT.md`、`RDP-00` 完成备注和 `CONTRACTS.md` 第 2、3 节。
- `LlmGenerationOptions.java`、`LlmCompletionRequest.java`、`LlmInvocationTarget.java`。
- `LlmProviderAdapter.java`、`LlmCapability.java`、`DynamicLlmGateway.java`。
- `DynamicLlmGatewayTest.java`、`LlmCoreModelTest.java`、`LlmProviderAdapterRegistryTest.java`。
- `rg` 命中的 `new LlmGenerationOptions(...)` 与 `new LlmInvocationTarget(...)` 首轮调用点；只打开为保持编译所需文件。

```bash
rg -n 'new LlmGenerationOptions\(|new LlmInvocationTarget\(' \
  backend --glob '*.java' --glob '!**/target/**'

rg -n 'requiredCapabilities\(|supportedCapabilities\(|REASONING_EFFORT' \
  backend/llm-core backend/ai-governance backend/llm-openai --glob '*.java'
```

## 3. 核心类型

新增 `LlmReasoningEffort`：

- 七个枚举值和固定小写 `wireValue`。
- 使用 Jackson `@JsonValue` 和 `@JsonCreator`；大小写不做宽松兼容。
- 空字符串、未知字符串和非字符串 JSON 明确拒绝。
- `toString()` 不作为 wire 契约，业务代码使用 `wireValue()`。

调整 `LlmGenerationOptions`：

- canonical record 增加可空 `reasoningEffort`。
- 保留现有六参数构造器并委托到新构造器。
- `defaults()` 为 `null`。
- 新增 `withReasoningEffort(...)`，保留其他字段和规范化后的 stop 列表。

调整 `LlmCompletionRequest`：

- 新增 `withOptions(...)`，完整保留 model selector、messages、tools、tool choice、response format、metadata 和 invocation target。
- 不新增 reasoning metadata key。

## 4. 调用目标与 adapter 契约

- `LlmInvocationTarget` 增加可空 `routeReasoningEffort`。
- `LlmProviderAdapter` 增加默认 `acceptedReasoningEfforts()`，返回不可变空集合。
- 如构造器调用点过多，可增加向后兼容重载并默认 route effort 为 `null`；canonical record 字段仍必须存在。
- 不在本任务修改治理路由内容；调用点先传 `null`，由 `RDP-02` 正式接线。

## 5. 唯一生效值合并器

在 `llm-core` 新增单一职责类型，例如 `LlmReasoningEffortResolver`：

- `resolve(request)` 或 `resolve(options, target)` 返回最终可空 effort。
- `apply(request)` 返回带最终 options 的不可变请求；已相等时可以返回原对象。
- 不读取 metadata、环境变量、provider config 或模型名。
- null request/target 的错误语义与现有 gateway 保持一致。

`DynamicLlmGateway.complete/stream` 固定顺序：

1. 校验并取得 invocation target。
2. 使用合并器得到 effective request。
3. 基于 effective request 计算 required capabilities。
4. 校验 target capabilities。
5. 把 effective request 发送给 target client。

生效 effort 非空时 required capabilities 增加 `REASONING_EFFORT`；为空时不增加。

## 6. 重点测试

- 七个 wire value 的 JSON round-trip 和未知值拒绝。
- defaults、旧六参数构造器和 `withReasoningEffort` 兼容。
- `null/null -> null`、request null + route、request 覆盖 route、`none` 覆盖 `high`。
- complete 与 stream 的 client 都收到 effective request，而不是原始 request。
- effort 非空且 target 不支持时，在 client 调用前返回 `UNSUPPORTED_CAPABILITY`。
- effort 为空时不要求 capability；既有工具、结构化、vision/file 能力判断不回归。
- `withOptions` 不丢 metadata、tools、response format 或 invocation target。

## 7. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl llm-core -am \
  -Dtest='LlmReasoningEffortTest,LlmCoreModelTest,DynamicLlmGatewayTest,LlmProviderAdapterRegistryTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl ai-governance,llm-openai,agent-core -am -DskipTests compile

git diff --check
```

## 8. 非目标与停止条件

- 不修改路由 JSON、OpenAI mapper、数据库、指标或前端。
- 不把 effort 合并逻辑复制到 gateway、accounting 和 metrics 三处。
- 若 client 收到的仍是原始 request、`null` 与 `none` 混淆或 capability 校验发生在合并前，不得开始 `RDP-02`。

## 9. 上下文交接

记录枚举路径、合并器类型/方法、兼容构造器、gateway 调用顺序和定向测试结果。不要携带测试 request JSON 全文。

## 10. 完成备注

完成时间：2026-08-03 09:44 UTC

状态：DONE

主要改动：

- 新增 `LlmReasoningEffort` 七值 JSON/wire 契约与 `LlmReasoningEffortResolver` 唯一生效值合并入口。
- 扩展生成参数、调用目标和 adapter 目录，并保留六参数/七参数兼容构造器。
- Dynamic gateway 在 capability 校验前应用 effective request，同步和流式均传递最终 effort。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl llm-core -am -Dtest='LlmReasoningEffortTest,LlmCoreModelTest,DynamicLlmGatewayTest,LlmProviderAdapterRegistryTest' -Dsurefire.failIfNoSpecifiedTests=false test`: PASS（29 tests）
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance,llm-openai,agent-core -am -DskipTests compile`: PASS
- `git diff --check`: PASS

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`RDP-02`
