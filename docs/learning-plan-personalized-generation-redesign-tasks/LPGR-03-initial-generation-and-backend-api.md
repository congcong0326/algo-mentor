# LPGR-03：初次生成模型契约与后端 API 切换

> 波次：B
>
> 状态：DONE
>
> 直接依赖：LPGR-02
>
> 建议首轮文件上限：18

## 1. 目标与完成标准

把 AI 创建入口、草案保存的命令、Agent input、Prompt、初次 JSON Schema 和输出 mapper 一次切换到 `LearningPlanBrief + LearningPlanGeneratedContent`。

完成后后端不再接收或持久化 `LearningPlanDraftCommand`，初次模型不再重复返回服务端规划字段，创建 SSE 的事件名和用户流程保持不变。

## 2. 必须读取

- `CURRENT.md`、`LPGR-02` 完成备注及其临时桥接位置。
- `CONTRACTS.md` 第 2 至 6、11、13 节。
- `LearningPlanDraft.java`、`LearningPlanDraftService.java`、`LearningPlanAgentService.java`。
- `LearningPlanDraftAgentInput.java`、Definition、Prompt Builder、JSON Schema、StructuredOutputMapper、StreamService 及测试。
- `LearningPlanDraftRevisionAgentInput.java` 和 Definition，只确认临时完整计划修订 Schema 的接线。
- `LearningPlanProposalPromptBuilder.java` 的草案修订片段。
- `LearningPlanCreateDraftRequest.java`、`LearningPlanController.java` 创建 SSE 方法。
- `MyBatisLearningPlanRepository.java` 的 command JSON 读写与测试。
- `LearningPlanTemplateDraftService.java` 的 draft command 创建位置。

首轮先执行：

```bash
rg -l 'LearningPlanDraftCommand' backend/mentor-application/src backend/mentor-api/src | sort
rg -n 'LearningPlanDraftJsonSchema|SCHEMA_VERSION|profileSummary|difficultyPreference|goal' \
  backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/stream \
  backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream
```

## 3. 实现检查点

### 检查点一：Brief 与 API

1. `LearningPlanDraft.command` 改为 `brief`，repository `command_json` 只读写 `LearningPlanBrief`。
2. 创建请求改为 `objective`、`difficultyDistribution`、`additionalConstraints`、`personalizationEnabled`，由服务端注入 locale 并解析默认 objective。
3. 模板创建直接构造 Brief，personalization=false。
4. 删除 `LearningPlanDraftCommand` 和 `LPGR-02` 的旧命令桥接。
5. 非流式 fallback、缺失字段提示和 request size 统计同步使用 Brief。

完成后先运行领域与 repository 定向测试，确认可编译再继续。

### 检查点二：初次模型契约

1. 新增 `LearningPlanGeneratedContent` 和初次生成专用严格 Schema。
2. Prompt 最后一条 user message序列化完整 Brief，不再使用手写重复字段和表单摘要。
3. 初次输出 mapper 只解析 generated content，规范化题库事实后与 Brief 合并。
4. 初次 Schema 断言不存在 `intent/objective/durationWeeks/level/weeklyHours/programmingLanguage/difficultyDistribution/interviewOriented/topicPreferences/additionalConstraints/profileSummary`。
5. 更新初次 Schema version 常量；不要复用旧 version 假装契约未变化。
6. 为 `LPGR-07` 前的修订链路保留单独的完整计划 v2 Schema，不得让初次 Definition 使用它。

## 4. 重点测试

- API 空 objective 使用 intent+locale 固定默认值；显式 objective 原样保留。
- personalization 未提供为 true，false 不被覆盖。
- command JSON 是完整 Brief 且无旧字段。
- Prompt 顺序为固定 system + Brief user；本任务尚无个性化 system message。
- 初次 Schema 根字段严格缩小，unknown field 被拒绝。
- Mapper 的所有规划字段来自 Brief，模型只能决定生成内容。
- SSE collecting、ready、error 事件和幂等键行为保持现状。
- 模板创建、草案确认和临时修订链路仍可运行。

## 5. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am \
  -Dtest='LearningPlanBrief*Test,LearningPlanDraft*Test,LearningPlanGenerated*Test,LearningPlanTemplateDraftServiceTest,LearningPlanDraftRevision*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-api -am \
  -Dtest='LearningPlanControllerTest,MyBatisLearningPlanRepositoryTest,*LearningPlanDraftStream*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false test

rg -n 'LearningPlanDraftCommand' backend/mentor-application/src backend/mentor-api/src

git diff --check
```

`rg` 预期零命中。

## 6. 非目标与停止条件

- 不注入学习画像、能力、当前计划或复习负载；由 `LPGR-05/06` 完成。
- 不完成 resolvedBrief 修订协议；只保留独立临时完整计划 Schema。
- 不修改前端；跨层契约在 `LPGR-04` 波次出口恢复。
- 若初次模型仍能输出服务端字段、command JSON 仍读旧类型、或创建 API 接受旧字段，不得开始 `LPGR-04`。

## 7. 上下文交接

记录 Brief factory/API 映射、初次 Schema 名/version、generated mapper、临时修订 Schema 名和测试结果。不要携带 Prompt 或模型 JSON 全文。

## 8. 完成备注

完成时间：2026-08-03

状态：DONE

主要改动：

- 创建 API、草案持久化、Agent input 与非流式 fallback 统一使用 `LearningPlanBrief`，并删除旧命令与桥接。
- 初次生成改为 `LearningPlanGeneratedContent` 严格 Schema（`learning_plan_generated_content` / `v2`）；Prompt 最后一条 user message 为完整 Brief JSON。
- 初次 mapper 仅合并模型生成内容与服务端 Brief；修订链路暂用独立完整计划 Schema（`learning_plan_draft_revision` / `v2`）。
- 创建请求支持 objective、难度分布、补充限制和个性化开关，拒绝旧 `goal`、`difficultyPreference`、`profileSummary` 字段。

验证：

- `mvn -f backend/pom.xml ... -pl mentor-application -am -Dtest='LearningPlanBrief*Test,LearningPlanDraft*Test,LearningPlanGenerated*Test,LearningPlanTemplateDraftServiceTest,LearningPlanDraftRevision*Test' ... test`：PASS（53 tests）。
- `mvn -f backend/pom.xml ... -pl mentor-api -am -Dtest='LearningPlanControllerTest,MyBatisLearningPlanRepositoryTest,*LearningPlanDraftStream*Test' ... test`：PASS（27 tests）。
- `rg -n 'LearningPlanDraftCommand' backend/mentor-application/src backend/mentor-api/src`：零命中；`git diff --check`：PASS。

偏离计划：

- 全局 JSON mapper 保持宽松；创建请求通过 DTO 级 legacy-field 拒绝保证新 API 契约。

遗留事项：

- `LPGR-07` 删除临时完整计划修订 Schema，并切换到 resolved Brief 修订协议。

下一任务：`LPGR-04`
