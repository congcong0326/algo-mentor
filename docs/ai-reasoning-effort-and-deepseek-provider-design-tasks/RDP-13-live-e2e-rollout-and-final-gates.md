# RDP-13：真实 E2E 入口、灰度手册与仓库级最终门禁

> 波次：E
>
> 状态：DONE
>
> 直接依赖：RDP-12
>
> 建议首轮文件上限：18

## 1. 目标与完成标准

提供显式 opt-in 的 DeepSeek 真实 Responses E2E 测试入口，补完整灰度/回滚手册、代码索引和仓库级最终门禁，并把代码研发状态与外部发布资格清晰分离。

缺少真实 API Key 时，本任务仍应完成测试 harness、skip 语义、运行手册、本地最终门禁和文档收口；两个 DeepSeek 发布门禁保持 `NOT_RUN`。只有真实场景全部通过，才更新对应门禁为 `PASS`。

## 2. 必须读取

- `CURRENT.md`、`RDP-12` 完成备注和所有遗留事项。
- `CONTRACTS.md` 第 1、12 至 15 节。
- `docs/ai-reasoning-effort-and-deepseek-provider-rollout-runbook.md` 的第一阶段内容。
- DeepSeek adapter、共享 client、continuation 和本地 IT 的稳定入口。
- mentor API PostgreSQL IT 基类、一个受环境变量控制的现有外部测试模式；没有则使用 JUnit assumption。
- `docs/code-index.md` 中 LLM、AI governance、Agent 和管理前端条目。
- 实施时重新运行 provider identity、continuation 和 migration 扫描，只打开实际命中的生产文件。

## 3. Opt-in 真实 E2E

新增 `DeepSeekResponsesE2EIT` 或等价受控测试：

- 仅在 `DEEPSEEK_API_KEY` 非空时执行；缺失时明确 skipped，不失败、不尝试网络。
- `DEEPSEEK_BASE_URL` 仅测试入口可默认官方地址。
- `DEEPSEEK_MODEL` 仅测试入口可使用运行手册记录的当前官方模型默认值。
- secret 只进入内存 config，不写日志、异常、报告属性或测试名称。
- 每个场景使用独立 run/call，设置有界 timeout，失败后仍关闭 stream/resource。
- 测试输出只记录场景、PASS/FAIL、耗时、token 摘要和稳定错误码。

## 4. 真实场景与门禁

无工具发布门禁至少包括：

- 同步短文本 `none`。
- 同步推理 `high`。
- 流式文本 `low`。
- JSON Schema `high`。
- 无效 Key -> `AUTHENTICATION_FAILED`。
- usage 与控制台的可解释对账记录。

工具发布门禁至少包括：

- 单工具调用 `high`。
- 连续两次工具调用 `high`。
- 工具调用 `none`。
- 取消 -> resource closed + accounting `CANCELLED`。
- reasoning/continuation 不出现在 SSE、日志和持久化诊断。
- 受控 429 -> `RATE_LIMITED` 且 retryable。

如果受控环境无法稳定制造真实 429，工具发布门禁保持未完整通过；不得用 `RDP-12` mock 429 替代。

## 5. 发布门禁更新规则

- 没有 key：IT skipped，两个 DeepSeek 门禁保持 `NOT_RUN`，`RDP-13` 可因仓库研发完成而标记 `DONE`。
- 无工具场景全部通过：`DeepSeek 无工具真实 E2E = PASS`。
- 工具场景全部通过：`DeepSeek 工具 continuation 真实 E2E = PASS`。
- 任一真实场景失败：对应门禁为 `FAIL`，记录首个稳定原因；代码任务是否需要重新打开取决于失败是否为实现缺陷。
- 用户要求“完成上线准备”时，相关门禁不是 `PASS` 就不得宣称完成。

## 6. 灰度与回滚手册

补全 `docs/ai-reasoning-effort-and-deepseek-provider-rollout-runbook.md`：

1. 先发布共享模块/OpenAI 等价版本，确认仍只有 OpenAI 路由。
2. 发布 DeepSeek adapter，创建 disabled provider、显式模型和价格。
3. 运行无工具真实 E2E，通过后启用内部用户低风险无工具路由。
4. 验证结构化输出、usage、成本和错误率。
5. 工具真实门禁通过后，再按场景灰度 Agent 工具调用。
6. 观察 provider calls、effort、reasoning tokens、latency、错误、取消和成本。
7. 停止条件：reasoning 泄漏、tool call id 不匹配、重复调用、结构化解析失败、provider 错归类、usage 明显偏差。
8. 回滚只停用/删除 DeepSeek route、停用 provider 并恢复显式 OpenAI route；不回滚 migration，不自动 fallback，不删除 provider/model 数据。

手册使用中文，不包含密钥、真实用户、完整请求/响应或 continuation。

## 7. 代码索引与最终扫描

更新 `docs/code-index.md`：

- `llm-core` effort/continuation 契约。
- `llm-openai-compatible` 共享传输边界。
- `llm-openai` 与 `llm-deepseek` 薄 adapter。
- `ai-governance` route effort、accounting 和 metrics。
- `agent-core` continuation 传递。
- mentor API/provider catalog 和前端管理入口。
- 本任务目录和 rollout runbook。

最终扫描：

```bash
rg -n 'api\.openai\.com|api\.deepseek\.com|LlmProviderType\.of\("(openai|deepseek)"\)|OpenAI provider' \
  backend/llm-openai-compatible/src/main

rg -n 'providerContinuation|LlmProviderContinuation|encryptedContent|reasoningText|reasoningSummary' \
  backend --glob '*.java' --glob '!**/target/**'

rg -n 'reasoningEffort|reasoning_effort|REASONING_EFFORT' \
  backend frontend/src --glob '!**/target/**' --glob '!**/dist/**'

find backend -path '*/src/main/resources/db/migration/*.sql' -type f -print | sort -V
```

第一条共享模块 provider identity 扫描预期零命中。第二、三条不是零命中门禁，必须逐项审核只存在于设计允许的 carrier、mapper、测试和观测入口；禁止落点不得命中。

## 8. 本地最终门禁

```bash
make backend-test

make backend-build

make frontend-test

make frontend-build

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dtest=NoUnitTestsSpecified \
  -Dit.test='*AiReasoningEffortEndToEndIT,*DeepSeekProviderEndToEndIT' verify

git diff --check
```

真实 E2E 显式运行示例；`DEEPSEEK_API_KEY` 必须由受控环境预先注入：

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am -Dtest=NoUnitTestsSpecified \
  -Dit.test='*DeepSeekResponsesE2EIT' verify
```

不要把 secret 写入 shell history、文档或完成备注。

## 9. 最终验收

- `RDP-00` 至 `RDP-13` 状态、完成备注、README 和 CURRENT 一致。
- 第一阶段本地门禁和 reasoning 泄漏审计为 `PASS`。
- 无密钥本地测试不访问公网，真实 IT 明确 skipped。
- 共享模块无具体 provider 身份；DeepSeek module 无 OpenAI identity。
- 唯一 Flyway effort migration 可升级和 clean install，历史数据保持 NULL。
- 管理端不硬编码 effort 子集/模板，构建通过。
- runbook、代码索引、回滚和真实门禁状态完成记录。

## 10. 非目标与停止条件

- 不为了让真实 E2E 通过而提交 key、固定远端响应、降低安全断言或开启自动 fallback。
- 不把 skipped 真实测试写成 PASS。
- 若仓库级命令失败、共享模块仍硬编码 provider、reasoning 泄漏审计非 PASS、runbook/索引缺失或任务状态不一致，不得标记研发任务整体完成。

## 11. 上下文交接

这是最后一个任务。记录本地四个仓库级命令、两个本地 IT、最终扫描、runbook、代码索引和四个发布/安全门禁状态；不要复制构建日志或真实 E2E payload。

随后把 `RDP-13` 和状态板标记为 `DONE`，将 `CURRENT.md` 更新为“研发任务全部完成”。真实发布门禁按实际结果保留 `NOT_RUN/PASS/FAIL`，不伪造完成。

## 12. 完成备注

完成时间：2026-08-03 11:32 UTC

状态：DONE

主要改动：

- 新增 `DeepSeekResponsesE2EIT`：仅在显式提供 `DEEPSEEK_API_KEY` 时创建真实 client；缺少密钥时文本、流式、结构化、无效 Key、工具、取消场景均跳过。测试 Base URL 与模型默认值仅属于测试入口。
- 补全 DeepSeek 第二阶段的灰度、观测、停止与回滚手册；明确无自动 fallback、provider/model 数据保留和不回滚 Flyway。
- 更新代码索引，记录 core effort/continuation 契约、共享 transport、两个 thin provider adapter、治理、Agent、管理 API 和前端目录边界。

验证：

- `make backend-test`：PASS。
- `make backend-build`：PASS。
- `make frontend-test`：PASS。
- `make frontend-build`：PASS。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=NoUnitTestsSpecified -Dit.test='*AiReasoningEffortEndToEndIT,*DeepSeekProviderEndToEndIT' verify`：PASS（10 tests）。
- `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=NoUnitTestsSpecified -Dit.test='*DeepSeekResponsesE2EIT' verify`：PASS（无 `DEEPSEEK_API_KEY`，10 tests skipped）。
- provider identity、continuation/reasoning 泄漏与 Flyway 排序扫描：PASS；共享 transport provider identity 扫描零命中，所有 continuation 命中均为允许的 carrier、mapper 或安全测试。
- `git diff --check`：PASS。

偏离计划：

- 本地 IT 首次在 `mentor-application` 编译阶段遇到已生成 `ai-governance` JAR 的瞬时读取异常；重建该依赖后以原命令通过，未修改业务代码。

遗留事项：

- DeepSeek 无工具真实 E2E：`NOT_RUN`；需要受控环境提供 API Key 并完成文本、流式、结构化、无效 Key 与 usage 对账。
- DeepSeek 工具 continuation 真实 E2E：`NOT_RUN`；除工具与取消场景外，仍需可稳定制造的真实 429 验证，不能以 mock 结果替代。

下一任务：无，进入最终交付。
