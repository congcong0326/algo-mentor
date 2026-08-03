# LPGR-10：旧契约清理与仓库级最终门禁

> 波次：E
>
> 状态：DONE
>
> 直接依赖：LPGR-09
>
> 建议首轮文件上限：18

## 1. 目标与完成标准

删除实施期桥接、旧字段、旧前端摘要文案和过时测试 fixture，更新代码索引并运行仓库级最终门禁。

完成后活动源码只在模板边界保留设计允许的 `goal/difficultyPreference`；AI 创建、草案、正式计划、提案、扩展和 Practice Chat 全部使用新契约。

## 2. 必须读取

- `CURRENT.md`、`LPGR-09` 完成备注和所有遗留事项摘要。
- `CONTRACTS.md` 第 1、12、13、15 节。
- 实施时重新运行旧符号扫描，不信任早期文件列表。
- `LPGR-02`、`LPGR-03`、`LPGR-07` 完成备注中的桥接位置。
- 学习计划最终领域类型、API DTO、repository、三种 Agent Definition 和前端公共类型。
- `docs/code-index.md` 的学习计划相关条目。

首轮只打开扫描命中的活动生产文件：

```bash
rg -n 'LearningPlanDraftCommand|buildLearningPlanGoal|profileSummary' \
  backend frontend/src --glob '!**/target/**' --glob '!**/dist/**'

rg -n '\bgoal\b|difficultyPreference' \
  backend/mentor-application/src backend/mentor-api/src frontend/src \
  --glob '!**/target/**' --glob '!**/dist/**'

rg -n 'personalizationEnabled|additionalConstraints|difficultyDistribution|objective' \
  backend/mentor-application/src backend/mentor-api/src frontend/src \
  --glob '!**/target/**' --glob '!**/dist/**'
```

## 3. 清理范围

- 删除旧命令类、内部旧命令 mapper、临时完整计划修订 Schema 和兼容测试。
- 删除 AI API/DTO/JSON 中的 `goal/difficultyPreference/profileSummary`。
- 删除前端 goal builder、摘要 i18n 函数、旧类型、旧 fixture 和只服务旧字段的样式。
- 审核所有 `new LearningPlanDraftPlan`、copy/with、append/update/apply 路径，确保不丢新字段和内部 metadata。
- 模板 allowlist 之外的 `goal/difficultyPreference` 生产命中全部清零。
- 删除任何旧 JSON alias、fallback、双写或临时默认；不创建数据迁移。
- 更新 `docs/code-index.md`，加入本任务包、Brief、个性化上下文 adapter/service 和三种 Agent 新契约入口。

## 4. 最终扫描规则

以下扫描必须零命中：

```bash
rg -n 'LearningPlanDraftCommand|buildLearningPlanGoal|profileSummary' \
  backend frontend/src --glob '!**/target/**' --glob '!**/dist/**'
```

以下扫描只能命中模板领域、模板 seed/API、模板测试和历史文档：

```bash
rg -n '\bgoal\b|difficultyPreference' \
  backend/mentor-application/src backend/mentor-api/src frontend/src \
  --glob '!**/target/**' --glob '!**/dist/**'
```

逐项审核 `goal` 命中，Practice Chat、AI request/response、draft/plan/proposal snapshot、前端 AI 页面不允许残留。不要为了文本零命中重命名模板契约或历史 migration。

## 5. 最终验收

- AI 创建、预览、修订、确认、列表、详情、扩展和 Practice Chat 全链路通过。
- 模板创建、确认和练习通过，且未读取个性化数据。
- disabled、空数据和部分失败集成场景通过。
- 初次模型 Schema 只包含生成内容；修订 Schema 只包含 resolvedBrief + generatedContent。
- 个性化 Prompt 预算、隐私和 metadata 安全测试通过。
- 清洁 JSON round-trip 通过；旧 JSON 失败不视为回归。
- 无数据库迁移、个性化表、学习计划历史 Tool 或 Workflow 新增。

## 6. 验证命令

```bash
make backend-test
make backend-build
make frontend-test
make frontend-build

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest=NoMatchingUnitTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test='*LearningPlanPersonalizedGenerationIT' verify

rg -n 'LearningPlanDraftCommand|buildLearningPlanGoal|profileSummary' \
  backend frontend/src --glob '!**/target/**' --glob '!**/dist/**'

rg -n '\bgoal\b|difficultyPreference' \
  backend/mentor-application/src backend/mentor-api/src frontend/src \
  --glob '!**/target/**' --glob '!**/dist/**'

git diff --check
```

不要把完整构建日志或扫描全文写入完成备注；只记录测试数量、PASS/FAIL 和允许命中路径。

## 7. 非目标与停止条件

- 不修改模板契约，不迁移旧 JSON，不新增兼容视图或 Flyway。
- 不启动 Vite，不接真实模型，不扩展产品范围。
- 若仓库级命令失败、桥接仍有生产引用、模板外旧字段仍命中、或代码索引未更新，不得标记整体完成。

## 8. 上下文交接

这是最后一个任务。记录删除清单、模板 allowlist、最终 IT、四个仓库级命令、扫描和 `git diff --check` 结果。

随后把 `LPGR-10` 和状态板标记为 `DONE`，将 `CURRENT.md` 更新为“全部完成”，不再指定下一任务。

## 9. 完成备注

完成时间：2026-08-03 06:58 UTC

状态：DONE

主要改动：

- 删除 AI 创建请求中仅为旧字段保留的 allowlist，改为严格拒绝任何未定义字段；删除旧字段专用 fixture、断言和未使用的前端摘要文案。
- 清理后的快照集成测试改为断言 Brief、计划和扩展 JSONB 的精确字段集；保留模板领域、seed、API 和唯一难度映射中的允许旧命名。
- 更新 `docs/code-index.md`，记录 Brief、个性化上下文、三种 Agent 流程、API adapter 与前端学习计划入口。

验证：

- `make backend-test`：PASS（1244 tests）；`make backend-build`：PASS。
- `make frontend-test`：PASS；`make frontend-build`：PASS。
- `*LearningPlanPersonalizedGenerationIT`：PASS（1 PostgreSQL IT）；旧桥接/画像符号扫描：零命中；模板 allowlist 审核：PASS；`git diff --check`：PASS。

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：无，进入最终交付。
