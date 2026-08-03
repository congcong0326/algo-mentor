# LPGR-04：创建表单、请求与草案预览前端切换

> 波次：B
>
> 状态：DONE
>
> 直接依赖：LPGR-03
>
> 建议首轮文件上限：14

## 1. 目标与完成标准

在不重做页面结构的前提下，把 AI 创建表单、API 类型、草案预览和计划消费方切换到新契约，删除前端表单摘要生成链路。

完成后用户仍按原步骤选择参数、填写可选文本、生成草案并通过聊天修订；提交体不再包含 `goal` 或枚举难度偏好。

## 2. 必须读取

- `CURRENT.md`、`LPGR-03` 完成备注和后端最终请求/响应 record。
- `CONTRACTS.md` 第 4、5、11 节。
- `frontend/src/types/api.ts` 学习计划片段、`frontend/src/services/api.ts` 创建 SSE 方法及测试。
- `LearningPlanCreateForm.tsx`、`options.ts`、`DifficultyDistributionControl.tsx`。
- `LearningPlanCreatePage.tsx`、`LearningPlanDraftPanel.tsx`。
- `LearningPlanListCard.tsx`、`LearningPlanExtensionPanel.tsx` 中旧 goal 消费。
- `frontend/src/i18n/locales.ts` 学习计划字段和中英文文案片段。
- 相关测试；样式只读取与 create form、goal summary、checkbox 相邻的片段。

先运行：

```bash
rg -n 'buildLearningPlanGoal|profileSummary|difficultyPreference|\bgoal\b|additionalThoughts' \
  frontend/src --glob '!**/dist/**'
```

## 3. 表单与类型

1. `LearningPlanCreateDraftRequest` 改为新字段，新增 `LearningPlanDifficultyDistribution` 类型。
2. 难度滑块继续使用当前插值算法，提交 `easyPercent/mediumPercent/hardPercent`；模板类型仍保留 `LearningPlanDifficultyPreference`。
3. 在场景区域附近增加可选 objective 输入，最大长度与后端一致。
4. “补充想法”改为“其他限制”，提交原文为 `additionalConstraints`，不拼标签前缀。
5. 增加原生 checkbox“参考我的学习数据”，默认选中，提交 `personalizationEnabled`。
6. dirty state、取消确认、loading/disabled、专题突破校验纳入三个新输入状态。
7. 删除 `BuildGoalInput`、`buildLearningPlanGoal` 和只服务表单摘要的 i18n 函数。

## 4. 响应与展示

- `LearningPlanDraftPlan`、summary/detail 类型使用 objective、distribution、constraints，删除 profileSummary。
- 草案顶部只展示 objective；没有额外说明卡或个性化宣传文案。
- 列表搜索/语言 fallback 使用 title + objective；详情和扩展面板不再读取 plan goal。
- 聊天修订输入、SSE reader、确认按钮和错误状态保持现状。
- 不展示个性化上下文、能力分数或“AI 使用了哪些数据”的新界面。

## 5. 重点测试

- 默认提交包含 `personalizationEnabled=true` 和精确 `25/55/20`。
- 关闭 checkbox 提交 false；空 objective 可提交。
- objective 与 constraints 不互相拼接，输入最大长度和 dirty state 正确。
- 专题突破仍要求专题，滑块极值提交合法 100 总和。
- 草案只显示 objective，旧 profile summary 和表单摘要消失。
- 列表、详情、扩展面板使用新字段；修订聊天行为不变。
- API 请求 body 与后端 contract 完全一致。

## 6. 验证命令

```bash
npm --cache ./.npm --prefix frontend test -- \
  src/learning-plans/options.test.ts \
  src/learning-plans/DifficultyDistributionControl.test.tsx \
  src/learning-plans/LearningPlanCreateModal.test.tsx \
  src/learning-plans/LearningPlanCreatePage.test.tsx \
  src/learning-plans/LearningPlanDraftPanel.test.tsx \
  src/learning-plans/LearningPlanListCard.test.tsx \
  src/learning-plans/LearningPlanDetail.test.tsx \
  src/services/api.test.ts

npm --cache ./.npm --prefix frontend run build

rg -n 'buildLearningPlanGoal|profileSummary' frontend/src

git diff --check
```

`rg` 预期零命中。

## 7. 非目标与停止条件

- 不改成多步骤向导、纯聊天创建或营销式说明页。
- 不展示画像明细，不增加历史 Tool 或隐私设置中心。
- 不启动 Vite。
- 若请求仍包含旧字段、constraints 仍被拼入 objective、或聊天修订路径回归，不得开始波次 C。

## 8. 上下文交接

记录表单状态字段、请求类型、难度提交 helper、旧 i18n 删除项、受影响消费组件和测试结果。不要复制 DOM 快照或完整 request fixture。

## 9. 完成备注

完成时间：2026-08-03 05:29 UTC

状态：DONE

主要改动：

- AI 创建表单和前端类型改用 objective、difficultyDistribution、additionalConstraints、personalizationEnabled。
- 删除表单摘要构造与相关本地化文案；草案预览、列表语言 fallback 和扩展摘要不再依赖旧计划 goal。
- 补充新请求体、空 objective、个性化开关、输入长度和取消确认覆盖；同步更新计划夹具。

验证：

- 学习计划定向前端测试：PASS（8 文件，76 tests）。
- 预览、练习聊天和 App 补充测试：PASS（3 文件，114 tests）。
- `npm --cache ./.npm --prefix frontend run build`：PASS。
- `rg -n 'buildLearningPlanGoal|profileSummary' frontend/src`：零命中；`git diff --check`：PASS。

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`LPGR-05`
