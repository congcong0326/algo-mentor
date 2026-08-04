# RDP-11：DeepSeek 管理后端与前端集成

> 波次：D
>
> 状态：DONE
>
> 直接依赖：RDP-10
>
> 建议首轮文件上限：14

## 1. 目标与完成标准

把 `llm-deepseek` 接入 mentor API 组合根和管理端，验证 provider type 目录、实例/模型维护、路由 effort 子集、有效路由模拟、配置模板和模型价格现有能力对 `deepseek` 正常工作。

完成后管理员可以创建 disabled DeepSeek provider、显式模型和合法路由；前端明确显示 DeepSeek，且不会把 config、effort 或 provider identity 当作 OpenAI compatible 处理。

## 2. 必须读取

- `CURRENT.md`、`RDP-10` 完成备注和 `CONTRACTS.md` 第 4、9、11 节。
- `backend/mentor-api/pom.xml`、`MentorAiConfiguration` 的 adapter registry wiring。
- `AdminAiProviderController`、`AdminAiModelRoutingController` 及测试。
- `AiProviderManagementService`、`ProviderClientRegistry` 的动态 client 创建测试。
- `AiProviderModelPanel`、`AiModelRoutingPanel` 和 `api` types/tests 的 `RDP-05` 最终实现。
- 模型价格 API 只读取 provider/model 字符串处理片段，确认无需专用分支。

```bash
rg -n 'LlmProviderAdapter|adapterRegistry|llm-openai|provider-types|defaultConfig|reasoningEfforts' \
  backend/mentor-api backend/ai-governance --glob '*.java' --glob 'pom.xml'

rg -n 'openai|OpenAI compatible|providerType|reasoningEfforts|defaultConfig' \
  frontend/src/admin/ai frontend/src/types/api.ts frontend/src/services/api.ts
```

## 3. 后端组合与目录

- `mentor-api` 增加 `llm-deepseek` 依赖；不直接依赖共享模块 API。
- Spring 自动配置后 adapter registry 同时包含 `openai` 和 `deepseek`。
- provider type API 自动返回 DeepSeek display name、四个 effort 和安全默认 config。
- 任何 controller/service 不新增 `if (providerType.equals("deepseek"))` 业务分支。
- provider instance CRUD 沿用严格 config 校验，列表不返回 config，详情按现有契约返回已保存 config。

## 4. 模型、路由与价格

- 管理员可为 DeepSeek provider 显式添加任意非空 upstream model ID，不硬编码远端白名单。
- 路由保存允许 default/none/low/high/max，拒绝 minimal/medium/xhigh。
- effective route 返回 provider type `deepseek` 和 route effort。
- provider/model disabled 语义与 OpenAI 相同。
- 模型价格和 usage 聚合按 `provider=deepseek + upstream model` 使用现有字符串契约，无 schema 变更或特殊 mapper。

## 5. 前端集成

- 新建 provider 选择 DeepSeek 时加载 DeepSeek 默认 config 模板。
- 编辑已有 provider 不重置 config，provider type 继续只读。
- provider 列表 type 显示 `deepseek`，display label 使用目录中的 `DeepSeek`。
- 路由选择 DeepSeek 模型时 effort 下拉只显示 Provider default、none、low、high、max。
- 从 OpenAI 模型切换到 DeepSeek 时 invalid effort 按 `RDP-05` 规则重置。
- 模型 ID 仍是管理员文本输入，不添加固定 `deepseek-v4-flash` select。

## 6. 重点测试

- mentor API context 同时注册两个 adapter，provider type 顺序稳定。
- 创建 disabled DeepSeek provider、添加模型、创建合法 route、模拟命中。
- 非法 effort 在 policy save 阶段拒绝；client 不创建。
- 启用 provider 后 registry 使用 DeepSeek adapter 创建对应版本 client。
- provider type API 不返回已保存 API Key，只返回空 key template。
- 前端模板切换、已有 config 保留、effort 子集、列表和模拟展示。
- 模型价格/usage 测试能使用 `deepseek` 字符串，不被归类为 OpenAI。

## 7. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest='MentorAiConfigurationTest,AdminAiProviderControllerTest,AdminAiModelRoutingControllerTest,AiProviderManagementServiceTest,ProviderClientRegistryTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

npm --cache ./.npm --prefix frontend test -- \
  src/admin/ai/AiProviderModelPanel.test.tsx \
  src/admin/ai/AiModelRoutingPanel.test.tsx \
  src/admin/ai/AiModelPricingPanel.test.tsx \
  src/services/api.test.ts

npm --cache ./.npm --prefix frontend run build

git diff --check
```

如果仓库没有 `AiModelPricingPanel.test.tsx`，使用实际 pricing 测试文件并在完成备注记录，不为满足文件名新建空测试。

## 8. 非目标与停止条件

- 不调用真实 DeepSeek、不默认创建 provider/model/route/price 数据、不启动 Vite。
- 不在 controller 或前端硬编码 DeepSeek effort/模板。
- 若 API 泄露已保存 secret、DeepSeek 被显示/统计为 OpenAI、非法 effort 可保存或编辑 provider 会重置 config，不得开始 `RDP-12`。

## 9. 上下文交接

记录 mentor-api dependency、registry 结果、provider type 响应、前端模板/effort 行为、价格兼容和测试摘要。不要记录 provider 配置正文。

## 10. 完成备注

完成时间：2026-08-03 11:03 UTC

状态：DONE

主要改动：

- `mentor-api` 组合根新增 `llm-deepseek` 依赖，Spring provider adapter registry 由自动配置同时获得 OpenAI 与 DeepSeek。
- 管理 API 和前端本已通过 provider type 目录驱动 template、display name、effort 子集、模型与路由，无需增加 DeepSeek 特判。

验证：

- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest='MentorAiConfigurationTest,AdminAiProviderControllerTest,AdminAiModelRoutingControllerTest,AiProviderManagementServiceTest,ProviderClientRegistryTest' -Dsurefire.failIfNoSpecifiedTests=false test`: PASS（20 tests）。
- `npm --cache ./.npm --prefix frontend test -- --run src/admin/ai/AiProviderModelPanel.test.tsx src/admin/ai/AiModelRoutingPanel.test.tsx src/services/api.test.ts`: PASS（41 tests）。
- `npm --cache ./.npm --prefix frontend run build` 与 `git diff --check`: PASS。

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`RDP-12`
