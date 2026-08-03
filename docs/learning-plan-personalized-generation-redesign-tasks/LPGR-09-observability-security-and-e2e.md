# LPGR-09：指标、安全与端到端验证

> 波次：E
>
> 状态：DONE
>
> 直接依赖：LPGR-08
>
> 建议首轮文件上限：18

## 1. 目标与完成标准

补齐学习计划个性化上下文的低敏指标、安全回归和前后端端到端验证，证明开启、关闭、空数据和部分失败都不会破坏创建、修订、扩展与模板路径。

本任务不扩展产品功能，重点是让最终清理前已有可复现的行为证据。

## 2. 必须读取

- `CURRENT.md`、`LPGR-08` 完成备注及波次 B/C/D 验证摘要。
- `CONTRACTS.md` 第 6 至 14 节。
- `LearningPlanPersonalizationContextService`、renderer、adapter 和 metadata 常量。
- 学习计划三种 Agent Definition/StreamService 的最终接线。
- 项目现有 Micrometer 接口和一个 allowlist label 实现，例如 `LearnerMemoryMetrics`。
- `LearningPlanControllerTest`、repository tests、SSE subscriber tests 的现有 fake Runtime 模式。
- 前端创建、草案修订、扩展和 API 测试。
- PostgreSQL IT 基类和一个学习计划 JSON round-trip IT；只读取建立最小新 IT 所需文件。

## 3. 可观测性

新增 `LearningPlanPersonalizationMetrics` 和 Micrometer 实现或等价边界：

- 记录 context build，固定 label：scenario、enabled、outcome、trimmed。
- 记录 source load，固定 label：source、outcome。
- 记录条目数和估算 token 的 distribution summary；不以数值作为 label。
- scenario 只允许 draft、revision、extension；source 只允许四个固定枚举。
- 无 Micrometer 时使用 NOOP，不影响应用启动。

日志只允许 run/scenario、固定结果、计数、耗时和异常类型，不记录异常 message、Prompt、objective、claim、tag label、Review 或用户 ID。

## 4. 安全回归

- 恶意 claim/constraints 中的 system 指令、XML/Markdown/JSON 片段不能改变消息角色、Tool 白名单或 Schema。
- Prompt 中不存在原始代码、Review Markdown、聊天正文、claim/review/card/plan/user ID。
- metadata 和持久化 snapshot 不包含个性化正文。
- provider 强制使用服务端当前 userId，模型和前端不能提供查询用户或扩大范围。
- 个性化来源异常不泄露数据库、SQL、类路径或正文到 SSE error。

## 5. 集成场景

使用 fake Agent Runtime/固定 structured output，不调用真实外部模型，至少覆盖：

1. 创建开启个性化：四来源摘要进入 Prompt，Brief 覆盖冲突信息，保存新 snapshot。
2. 创建关闭个性化：零来源调用，仍可生成、确认和读取正式计划。
3. 空用户：无画像、无 Review、无激活计划仍正常生成。
4. 部分失败：一个来源抛错，其余来源保留，SSE ready 正常。
5. 修订：更新周期/objective/难度并原子更新 Brief 与计划。
6. 扩展：读取正式计划开关，只追加阶段。
7. 模板：完全不读取个性化并可确认、开始 Practice Chat。
8. JSON round-trip：草案、正式计划、修订和扩展不存在旧字段。

## 6. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am \
  -Dtest='LearningPlanPersonalization*Test,LearningPlanDraft*Test,LearningPlanExtension*Test,PracticeChatPromptSectionProviderTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest='LearningPlanControllerTest,*LearningPlanPersonalization*Test,*LearningPlan*RepositoryTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test='*LearningPlanPersonalizedGenerationIT' verify

npm --cache ./.npm --prefix frontend test -- \
  src/learning-plans/LearningPlanCreatePage.test.tsx \
  src/learning-plans/LearningPlanDraftPanel.test.tsx \
  src/learning-plans/LearningPlanExtensionPanel.test.tsx \
  src/learning-plans/PracticeChatWorkbench.test.tsx \
  src/services/api.test.ts

npm --cache ./.npm --prefix frontend run build

git diff --check
```

## 7. 非目标与停止条件

- 不接真实 AI provider，不做主观计划质量评测平台。
- 不新增管理后台、用户画像解释页或详细历史查询。
- 不使用高基数 metric label，不在测试日志保存个性化正文。
- 若任一主场景缺少集成证据、安全扫描发现正文/ID 泄漏、或 disabled 仍读取数据，不得开始 `LPGR-10`。

## 8. 上下文交接

记录 metrics 接口、固定 label、IT 类名、八个场景 PASS/FAIL、首个失败原因和前端测试摘要。不要携带 Prompt、数据库行或测试用户内容。

## 9. 完成备注

完成时间：2026-08-03 06:44 UTC

状态：DONE

主要改动：

- 新增 `LearningPlanPersonalizationMetrics` 及 Micrometer 实现；固定 `scenario`、`source`、`outcome`、`enabled`、`trimmed` 标签，并在无 registry 时退化为 NOOP。
- 增加恶意个性化内容安全回归，以及 `LearningPlanPersonalizedGenerationIT`；后者使用 PostgreSQL/MyBatis 验证草案、正式计划、修订和扩展 JSONB 快照往返后均无旧字段。
- 创建开启/关闭、空数据、单来源失败、修订、扩展、模板和 JSONB round-trip 八个场景均有 fake runtime 或 PostgreSQL 集成测试证据，全部通过。

验证：

- 应用层个性化、草案、扩展与 Practice Chat 门禁：PASS（80 tests）。
- API controller、个性化、repository 与 `*LearningPlanPersonalizedGenerationIT` 门禁：PASS（含 1 个 PostgreSQL IT）。
- 前端指定学习计划/API 测试：PASS（5 files，90 tests）；生产构建：PASS；`git diff --check`：PASS。

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`LPGR-10`
