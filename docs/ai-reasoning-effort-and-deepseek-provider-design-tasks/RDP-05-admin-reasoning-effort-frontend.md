# RDP-05：管理端 effort 编辑、展示与配置模板

> 波次：B
>
> 状态：DONE
>
> 直接依赖：RDP-02、RDP-03、RDP-04
>
> 建议首轮文件上限：14

## 1. 目标与完成标准

让管理端依据 provider type 目录编辑模型路由 effort、展示路由和模拟结果，并让新建 provider 使用后端安全默认配置模板。

完成后管理员不能输入自由 effort；`Provider default` 与 `none` 清晰区分；切换模型、编辑、启停和重排都不会丢失或伪造 policy content。

## 2. 必须读取

- `CURRENT.md`、`RDP-02` 至 `RDP-04` 完成备注和 `CONTRACTS.md` 第 4、11 节。
- `frontend/src/types/api.ts` 的 AI provider/routing 类型。
- `frontend/src/services/api.ts` 和 `api.test.ts` 的 provider type、policy、effective route 请求。
- `AiModelRoutingPanel.tsx` 及测试。
- `AiProviderModelPanel.tsx` 及测试。
- `frontend/src/styles.css` 中 AI governance 表格/editor 片段。

```bash
rg -n 'AdminAiProviderType|AiModelRoutePolicyContent|AdminAiEffectiveRoute|getAdminAiProviderTypes' \
  frontend/src --glob '*.ts' --glob '*.tsx'

rg -n 'content: policy\.content|content = \{ modelId|providerType|configText' \
  frontend/src/admin/ai --glob '*.tsx'
```

## 3. 前端类型与目录

- 新增 `LlmReasoningEffort` 七值 union。
- `AiModelRoutePolicyContent.reasoningEffort` 为可选可空字段。
- `AdminAiProviderType` 增加 `reasoningEfforts` 和 `defaultConfig`。
- `AdminAiEffectiveRoute` 增加可空 `reasoningEffort`。
- API 未返回新增数组/模板时只为兼容部署顺序使用安全空集合/空对象，不允许前端自行补 OpenAI 七值。

## 4. 路由编辑器

- 初始加载同时取得 scenarios、providers、provider types 和 models。
- 根据选中 model 的 provider type 查找 effort 目录。
- effort 使用 select；首项显示 `Provider default`，内部值映射为 `null`。
- 编辑规则时加载已有 effort；`none` 必须选中显式 `none`。
- 切换目标模型后，如果当前 effort 不属于新 provider 目录，重置为 default 并显示稳定提示。
- 保存 content 时始终包含 `modelId`，effort 为空可省略或写 `null`；全项目保持一种稳定序列化方式。
- API 返回未知 effort 时显示明确错误并阻止保存，不把未知值塞入 option。

## 5. 保留与展示

- route table 增加固定宽度 Effort 列，空值显示 `Provider default`。
- effective simulation 显示命中路由 effort，空值同样显示 Provider default。
- toggle、reorder、priority update 和 scope edit 继续传完整 `policy.content`。
- route row、editor 和窄屏布局不得因新增列发生文本重叠或按钮挤压。

## 6. Provider 配置模板

- 新建 provider 时使用当前 provider type 的 `defaultConfig` 初始化 JSON textarea。
- 未保存的新 provider 切换 type 时同步切换模板；如用户已手工修改 config，可使用明确确认或只在仍等于旧模板时自动替换，测试固定所选行为。
- 编辑已有 provider 时 type 只读，切换/刷新不得重置已保存 config。
- 模板 API Key 为空；前端不缓存或回显其他 provider 的 API Key。

## 7. 重点测试

- OpenAI 七值和 Provider default 选项来自 API。
- default 保存为空；`none` 保存为 `none`。
- 模型切换到更小 effort 子集时非法值重置。
- 编辑、启停、重排和更新优先级不丢 effort。
- 路由表与模拟结果正确展示 default/none/high。
- 未知 effort 进入错误态并阻止保存。
- 新建 provider 切换 type 更新模板，编辑已有 provider 不重置 config。
- provider type 目录缺增量字段时页面保持有界，不硬编码选项。

## 8. 验证命令

```bash
npm --cache ./.npm --prefix frontend test -- \
  src/admin/ai/AiModelRoutingPanel.test.tsx \
  src/admin/ai/AiProviderModelPanel.test.tsx \
  src/admin/ai/AiGovernancePage.test.tsx \
  src/services/api.test.ts

npm --cache ./.npm --prefix frontend run build

git diff --check
```

## 9. 非目标与停止条件

- 不新增用户侧 effort 设置，不修改 AI usage 页面，不启动 Vite。
- 不在前端硬编码 provider effort 子集或 DeepSeek Base URL。
- 若 toggle/reorder 会丢 effort、unknown 值被静默保存、default 与 none 混淆或编辑 provider 会重置 config，不得开始 `RDP-06`。

## 10. 上下文交接

记录前端 union、provider type 查找、模型切换规则、policy content 序列化方式、配置模板切换策略和测试摘要。不要复制 config 文本或截图内容。

## 11. 完成备注

完成时间：2026-08-03 10:14 UTC

状态：DONE

主要改动：

- 前端类型增加七值 `LlmReasoningEffort`、provider type 目录字段，以及路由内容和有效路由的可空 effort。
- 路由编辑器按目标模型的 provider type 目录提供 select 选项，稳定保存 `reasoningEffort: null`，展示 default/none，并拒绝未知或不支持的值。
- 新建 provider 使用目录的安全默认模板；仅当未手工修改配置时才会随 type 变更替换模板，编辑已有 provider 不重置配置。

验证：

- `npm --cache ./.npm --prefix frontend test -- src/admin/ai/AiModelRoutingPanel.test.tsx src/admin/ai/AiProviderModelPanel.test.tsx src/admin/ai/AiGovernancePage.test.tsx src/services/api.test.ts`: PASS（45 tests）
- `npm --cache ./.npm --prefix frontend run build`: PASS
- `git diff --check`: PASS

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`RDP-06`
