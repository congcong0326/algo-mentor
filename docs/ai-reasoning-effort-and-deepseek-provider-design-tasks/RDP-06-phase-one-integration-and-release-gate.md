# RDP-06：第一阶段集成、回归与独立发布门禁

> 波次：B
>
> 状态：DONE
>
> 直接依赖：RDP-03、RDP-04、RDP-05
>
> 建议首轮文件上限：16

## 1. 目标与完成标准

补齐从路由 JSON 到 OpenAI request、调用台账、指标和管理前端的第一阶段集成证据，并建立可以独立发布的灰度/回滚说明。

本任务不引入共享模块或 DeepSeek。完成后第一阶段必须能够在所有存量路由保持 `null` 的前提下发布，并能对单个低风险路由显式启用 effort。

## 2. 必须读取

- `CURRENT.md`、`RDP-03` 至 `RDP-05` 完成备注和波次 A 测试摘要。
- `CONTRACTS.md` 第 1 至 6、13 节。
- `DefaultAiModelRouteResolver`、`AiRunGovernanceService`/准入阶段绑定 target 的实际调用链。
- `DynamicLlmGateway`、OpenAI provider client、accounting 和 metrics wrapper 的最终装配顺序。
- `MentorAiConfiguration` 中 gateway decorator、adapter registry 和 controller 装配片段。
- 管理端 route/provider 测试和一个 PostgreSQL migration IT。

```bash
rg -n 'new DynamicLlmGateway|AiAccountingLlmGateway|AiProviderCallMetricsLlmGateway|invocationTargetStore|ResolvedAiModelSnapshot' \
  backend/mentor-api backend/ai-governance backend/agent-runtime --glob '*.java'

rg -n 'reasoningEffort|reasoning_effort|REASONING_EFFORT' \
  backend frontend/src --glob '!**/target/**' --glob '!**/dist/**'
```

## 3. 后端集成测试

新增一个不调用真实 OpenAI 的集成测试，例如 `AiReasoningEffortEndToEndIT`，至少覆盖：

1. 旧 route JSON 缺 effort -> snapshot null -> OpenAI params 无 reasoning -> 台账 NULL -> metric provider_default。
2. route `high` -> target 固定 `high` -> OpenAI params high -> 台账 high -> metric high。
3. request `none` 覆盖 route `high` -> OpenAI、台账和 metric 都为 none。
4. provider 不支持 capability -> 远程 client 零调用，稳定 `UNSUPPORTED_CAPABILITY`。
5. route 保存 DeepSeek 风格非法子集的 fake adapter 值时，治理校验拒绝。
6. 同一 run 中修改 policy 后，已有 invocation target 的后续 step 仍使用旧 effort。
7. 流式取消保留 start effort，SDK resource 关闭语义不回归。

使用 fake adapter/client 或本地 fixture，禁止使用真实 API Key。

## 4. 前后端契约回归

- provider type API、effective route API 和前端 TypeScript shape 一致。
- policy create/update/toggle/reorder 的 request body 保留 effort。
- 旧 policy content 在前端打开、保存为 default 后行为可预测。
- migration clean install 和已有数据升级都通过。
- frontend build 不存在 TS 宽化为自由 string 的逃生口。

## 5. 第一阶段运行手册

新增中文 `docs/ai-reasoning-effort-and-deepseek-provider-rollout-runbook.md`，本任务先完成 Reasoning Effort 部分：

- 发布顺序：migration/backend/frontend 同批。
- 发布后确认所有存量 route 显示 Provider default。
- 内部测试用户和低风险场景配置 `low` 的步骤。
- 观察 latency、output tokens、reasoning tokens、错误率和人工质量。
- 停止条件和把 route 改回 Provider default 的回滚步骤。
- 不包含 API Key、真实用户数据或 provider config。

`RDP-13` 在同一文档继续补 DeepSeek 灰度和回滚。

## 6. 波次门禁

通过后把 `CURRENT.md` 的“第一阶段 Reasoning Effort 本地门禁”更新为 `PASS`：

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl llm-core,llm-openai,ai-governance,mentor-api -am \
  -Dtest='*ReasoningEffort*,DynamicLlmGatewayTest,OpenAiLlmProviderTest,OpenAiProviderAdapterTest,DefaultAiModelRouteResolverTest,AiProviderManagementServiceTest,AiAccountingLlmGatewayTest,AiProviderCallMetricsLlmGatewayTest,AdminAiModelRoutingControllerTest,AdminAiProviderControllerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dtest=NoUnitTestsSpecified \
  -Dit.test='*AiReasoningEffortEndToEndIT,*AiReasoningEffortMigrationIT' verify

npm --cache ./.npm --prefix frontend test -- \
  src/admin/ai/AiModelRoutingPanel.test.tsx \
  src/admin/ai/AiProviderModelPanel.test.tsx \
  src/services/api.test.ts

npm --cache ./.npm --prefix frontend run build

git diff --check
```

## 7. 非目标与停止条件

- 不进行真实 OpenAI 灰度，不抽取共享模块，不增加 DeepSeek。
- 不为通过测试临时绕过 migration、capability 或 route validation。
- 若存量 route 会自动发送 effort、三处最终值不一致、run snapshot 不稳定、前端仍会丢字段或回滚手册不完整，不得开始 `RDP-07`。

## 8. 上下文交接

记录 IT 类名、七个集成场景、波次命令结果、运行手册路径和第一阶段门禁状态。不要复制 request params、台账行或 metric dump。

## 9. 完成备注

完成时间：2026-08-03 10:22 UTC

状态：DONE

主要改动：

- 新增 `AiReasoningEffortEndToEndIT`，以 fake provider client、真实动态网关装饰链、内存台账和 Micrometer registry 覆盖七个第一阶段场景。
- 新增中文发布与回滚手册 `docs/ai-reasoning-effort-and-deepseek-provider-rollout-runbook.md` 的 Reasoning Effort 章节。
- 确认 OpenAI request 映射、调用台账、指标和管理前端的 effort 契约均在波次 B 门禁中回归。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl llm-core,llm-openai,ai-governance,mentor-api -am -Dtest='*ReasoningEffort*,DynamicLlmGatewayTest,OpenAiLlmProviderTest,OpenAiProviderAdapterTest,DefaultAiModelRouteResolverTest,AiProviderManagementServiceTest,AiAccountingLlmGatewayTest,AiProviderCallMetricsLlmGatewayTest,AdminAiModelRoutingControllerTest,AdminAiProviderControllerTest' -Dsurefire.failIfNoSpecifiedTests=false test`: PASS（62 tests）
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=NoUnitTestsSpecified -Dit.test='*AiReasoningEffortEndToEndIT,*AiReasoningEffortMigrationIT' verify`: PASS（8 ITs）
- `npm --cache ./.npm --prefix frontend test -- src/admin/ai/AiModelRoutingPanel.test.tsx src/admin/ai/AiProviderModelPanel.test.tsx src/services/api.test.ts`: PASS（41 tests）
- `npm --cache ./.npm --prefix frontend run build`: PASS
- `git diff --check`: PASS

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`RDP-07`
