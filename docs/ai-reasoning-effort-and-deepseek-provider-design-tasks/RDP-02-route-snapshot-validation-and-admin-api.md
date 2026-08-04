# RDP-02：路由内容、运行快照、校验与管理 API

> 波次：A
>
> 状态：DONE
>
> 直接依赖：RDP-01
>
> 建议首轮文件上限：16

## 1. 目标与完成标准

把路由级 effort 接入 `generic_policy` 内容、保存校验、运行快照、调用目标、provider type 目录和有效路由模拟 API。

完成后旧路由 JSON 无需迁移即可读取为 `null`；新路由只能保存目标 adapter 接受的值；同一 run 固定持有模型、provider 版本和 route effort；provider 目录的通用字段与安全模板就绪。OpenAI 实际七值目录由 `RDP-03` 在声明能力时接通。

## 2. 必须读取

- `CURRENT.md`、`RDP-01` 完成备注和 `CONTRACTS.md` 第 3、4 节。
- `AiModelRoutePolicyContent`、`AiModelRoutePolicyTypeContributor`、`AiProviderManagementService`。
- `ResolvedAiModelSnapshot`、`DefaultAiModelRouteResolver`、`AiRunInvocationTargetStore`。
- `AiGovernanceMetadataKeys` 和 `AiModelRoutingContract`。
- `AdminAiProviderController`、`AdminAiModelRoutingController` 及其测试。
- `OpenAiProviderAdapter` 的 descriptor/config 边界。
- Generic policy JSON 反序列化和校验测试模式，只读取一个代表性测试。

```bash
rg -n 'AiModelRoutePolicyContent|validateModelReference|supportedProviderTypes|ProviderTypeResponse|EffectiveRouteResponse' \
  backend --glob '*.java'

rg -n 'new ResolvedAiModelSnapshot\(|invocationTarget\(\)|trustedMetadata\(' \
  backend --glob '*.java'
```

## 3. 路由内容与兼容

- `AiModelRoutePolicyContent` 增加可空 `LlmReasoningEffort reasoningEffort`。
- 保留单参数构造器 `new AiModelRoutePolicyContent(modelId)` 并默认 `null`，减少无关调用点改动。
- 旧 `{"modelId":...}` JSON round-trip 后 effort 为 `null`。
- `none` 必须原样保存，不被序列化为缺失字段。
- 不为 `generic_policy.content` 增加列、alias、双写或 migration。

## 4. 路由校验

把 `validateModelReference(...)` 收口为 `validateModelRoute(modelId, reasoningEffort)` 或等价入口：

1. 模型存在。
2. provider 实例存在。
3. provider type 已注册。
4. effort 为空时通过。
5. effort 非空但 adapter 未声明 `REASONING_EFFORT` 时拒绝。
6. effort 不在 `acceptedReasoningEfforts()` 时拒绝。

Generic policy type contributor 只调用这个统一入口。错误使用既有低敏治理错误模型，不暴露 config 或 provider secret。

## 5. 快照与 metadata

- `ResolvedAiModelSnapshot` 增加可空 `routeReasoningEffort`，构造 `LlmInvocationTarget` 时原样传入。
- `DefaultAiModelRouteResolver` 从命中 policy content 读取 effort，一次解析后不再查询策略。
- `AiRunInvocationTargetStore` 无需单独保存字段，它保存完整 invocation target。
- 如记录 trusted metadata，新增稳定公共 key，并只写 wire value；为空时省略，不写字符串 `null`。
- 现有模型、provider instance、client handle 和 supported capabilities 快照行为保持不变。

## 6. Provider type 目录

扩展 adapter/descriptor，使 provider type 目录返回：

- `code`。
- `displayName`。
- `reasoningEfforts`，按统一枚举稳定顺序返回 wire value。
- `defaultConfig`，只包含安全模板。

模板来源于 adapter，不在 `AdminAiProviderController` 按 `openai/deepseek` 分支。第一阶段 OpenAI 模板使用 `CONTRACTS.md` 固定值，API Key 为空。目录机制使用 fake adapter 覆盖非空 effort 子集；OpenAI adapter 在本任务结束时仍可暂时返回空 effort 集合。

如果为此给 `LlmProviderAdapter` 增加默认模板方法，默认返回空 JSON object；返回值必须 defensive copy 或不可变，避免 controller 修改 adapter 内部对象。

## 7. 有效路由 API

- `EffectiveRouteResponse` 增加可空 `reasoningEffort`，使用 wire value或强类型 enum 的既有 Jackson 输出。
- 未命中路由时为 `null`。
- 命中但目标模型缺失/停用时仍返回规则 effort，便于管理员诊断；reason 保持现有语义。
- 该字段表示路由配置值，不计算请求覆盖。

## 8. 重点测试

- 旧 JSON 缺字段、`null`、`none`、`max` 和未知值。
- provider 无 capability、空 accepted set，以及 fake adapter 的非空子集接受/拒绝。
- disabled 模型/provider 仍可保存引用，但运行解析按现有规则不可用。
- route snapshot 固定 effort，策略在 run 中途变化不影响已有 target。
- trusted metadata 不写 secret 或高基数自由文本。
- provider type API 返回安全模板；使用 fake adapter 验证非空 effort 的稳定顺序。
- effective API 的命中、未命中、缺失模型都返回正确 effort。

## 9. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl ai-governance -am \
  -Dtest='AiModelRoutePolicyContentTest,AiProviderManagementServiceTest,DefaultAiModelRouteResolverTest,AiRunInvocationTargetStoreTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest='AdminAiModelRoutingControllerTest,AdminAiProviderControllerTest,MentorAiConfigurationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

git diff --check
```

## 10. 非目标与停止条件

- 不映射 OpenAI request、不写调用台账、不修改前端。
- 不硬编码模型级 effort 白名单，不向远端探测模型能力。
- 若旧 JSON 无法读取、controller 硬编码 provider 分支、run snapshot 会重新解析路由或非法 effort 能保存，不得开始 `RDP-03`。

## 11. 上下文交接

记录 route content 字段、统一校验入口、snapshot 字段、metadata key、provider type 响应字段和测试结果。不要复制 provider config JSON 实例。

## 12. 完成备注

完成时间：2026-08-03 09:50 UTC

状态：DONE

主要改动：

- 路由内容、运行快照和调用目标携带可空 route effort，trusted metadata 只保存固定 wire value。
- 路由保存统一按 provider capability 与 adapter accepted subset 校验；旧路由 JSON 继续读取为 `null`。
- provider type 目录从 adapter 返回 effort 子集与安全默认配置模板；有效路由模拟返回配置值。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance -am -Dtest='AiModelRoutePolicyContentTest,AiProviderManagementServiceTest,DefaultAiModelRouteResolverTest,AiRunInvocationTargetStoreTest' -Dsurefire.failIfNoSpecifiedTests=false test`: PASS（12 tests）
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -Dmaven.compiler.useIncrementalCompilation=false -pl mentor-api -am -Dtest='AdminAiModelRoutingControllerTest,AdminAiProviderControllerTest,MentorAiConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false test`: PASS（20 tests）
- `git diff --check`: PASS

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`RDP-03`
