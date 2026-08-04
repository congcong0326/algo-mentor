# AI Provider 发布与回滚手册

本文记录 AI provider 能力的分阶段发布操作。`RDP-06` 完成 Reasoning Effort 第一阶段；DeepSeek 的真实灰度与回滚步骤由 `RDP-13` 补充。

## Reasoning Effort 第一阶段

### 发布前检查

1. 确认本次发布包含调用台账的 `V54` 迁移、后端 API 和管理前端，三者同批上线。
2. 确认存量模型路由没有 `reasoningEffort` 字段或字段为 `null`；这表示使用 provider 默认值，不会向上游发送 `reasoning.effort`。
3. 在预发布环境通过波次 B 本地门禁，并确认管理端 provider type 目录能返回安全默认模板和支持的 effort 列表。

### 发布步骤

1. 先部署包含迁移的后端版本，确认 Flyway 成功执行且历史 `ai_llm_call_usage` 行的 `reasoning_effort` 保持为空。
2. 发布管理前端和相同版本的后端 API，避免旧前端编辑路由时覆盖新的 policy content 字段。
3. 打开模型路由管理页，抽查存量规则的 Effort 列均显示 `Provider default`；`none` 只能显示为显式的 `none`。
4. 选择内部测试用户和低风险场景，创建或编辑一条范围最小的路由规则，将 effort 设为 `low`，并保存。
5. 使用该测试用户完成少量正常请求，确认有效路由模拟、调用台账和 provider 调用指标均显示 `low`。
6. 逐步扩大该规则的适用范围。不要同时修改 provider、模型、路由优先级和 effort，以便可以定位变化来源。

### 观察项

- 对比基线和灰度期间的端到端延迟、输出 token、reasoning token、错误率和人工质量抽样结果。
- 监控 `ai_provider_calls_total` 的 `provider_type`、`reasoning_effort` 和 `status` 固定标签；空值应为 `provider_default`。
- 抽查调用台账中的 `reasoning_effort`：存量路由为 `NULL`，显式灰度规则为配置的 wire value。
- 仅记录汇总指标和固定错误码；日志、导出和截图不得包含 API Key、Authorization 或原始 reasoning 内容。

### 停止条件

满足任一条件时停止扩大范围：

- 错误率、超时或取消率持续高于同场景基线。
- 延迟或 token 消耗超过团队预设的可接受范围。
- 人工抽样发现答案质量、可用性或学习体验明显回退。
- 路由模拟、台账和指标中的 effort 值不一致，或存量路由意外出现非默认值。

### 回滚

1. 在管理端将受影响路由的 effort 改为 `Provider default` 并保存；不要用 `none` 替代默认值。
2. 若需要立即止损，先停用该低风险路由或将其优先级下调到已有的显式 OpenAI 默认路由之后。
3. 再次使用有效路由模拟确认命中规则和 effort 已恢复为预期，并观察错误率回落。
4. 保留 `reasoning_effort` 列和 JSON 字段，不回滚 Flyway 或回填历史调用台账。代码回滚后它们仍保持兼容。

## DeepSeek 第二阶段

### 发布前门禁

1. 确认共享 `llm-openai-compatible` 模块与 OpenAI 薄 adapter 已发布并完成 OpenAI 回归；此时线上路由仍只允许显式配置的 OpenAI provider。
2. 在受控环境显式运行 `DeepSeekResponsesE2EIT`。缺少 `DEEPSEEK_API_KEY` 时测试应跳过，不能把跳过结果记为通过。
3. 只有文本、推理、流式、JSON Schema、无效 Key 和 usage 对账均通过后，才将“DeepSeek 无工具真实 E2E”更新为 `PASS`。单工具、连续两次工具、`none` 工具、取消和受控 429 全部通过前，“DeepSeek 工具 continuation 真实 E2E”保持 `NOT_RUN`。
4. 确认管理端 provider type 目录显示 `DeepSeek`、安全默认配置模板和 `none`、`low`、`high`、`max`；不要在前端或路由中把它标记为 OpenAI compatible。

### 发布与灰度步骤

1. 发布包含 DeepSeek adapter 的应用版本，但不创建自动路由或自动 fallback。
2. 在管理端创建 disabled 的 DeepSeek provider，单独配置密钥、超时和重试；随后创建显式 upstream model 与价格。密钥只保存在受控配置中，不进入日志、截图或工单。
3. 通过管理端的有效路由模拟确认 provider、模型和 effort；先不启用 provider，也不修改既有 OpenAI 路由。
4. 无工具真实 E2E 门禁通过后，启用 provider，并只为内部测试用户和低风险、无工具场景创建最小范围的显式 DeepSeek 路由。
5. 先验证同步文本、流式、结构化输出、调用台账和成本。每次扩大范围时只变更路由范围，不同时改变模型、provider、优先级或 effort。
6. 工具 continuation 真实 E2E（包括受控 429）通过后，才可按场景灰度 Practice Chat、学习计划 Agent 和画像更新 Agent 的工具调用；每个场景保留显式 OpenAI 回退路由。

### 观测与停止条件

- 观察 `ai_provider_calls_total` 的 `provider_type`、`reasoning_effort`、`status`，以及延迟、错误、取消、输入/输出/reasoning token 和当前价格成本；所有标签必须是低基数固定值。
- 对照 DeepSeek 控制台聚合 usage 与本地调用台账，记录场景、时间窗、token 摘要和稳定错误码，不保存完整请求、响应、reasoning 或 continuation。
- 抽查 SSE、Agent trace、调用台账和管理 API，确认 reasoning、encrypted content 与 continuation 没有泄漏，且 DeepSeek 一直统计为 `deepseek`。
- 出现 reasoning 泄漏、tool call id 不匹配、重复工具调用、结构化解析失败、provider 错归类、usage 明显偏差、错误率/取消率持续高于基线时，立即停止扩大范围。

### 回滚

1. 停用或删除受影响的 DeepSeek 路由，恢复已验证的显式 OpenAI 路由；不使用自动 fallback。
2. 停用 DeepSeek provider 实例，保留 provider、模型、价格和调用台账记录以供排障。
3. 复核有效路由模拟、调用台账和固定标签指标，确认流量已不再命中 DeepSeek，并记录稳定错误码与影响范围。
4. 不回滚 Flyway migration，不删除历史数据，也不把 `none` 当作 Provider 默认值。
