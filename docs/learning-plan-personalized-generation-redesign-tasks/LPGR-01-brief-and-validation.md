# LPGR-01：Brief、目标默认值与难度分布

> 波次：A
>
> 状态：DONE
>
> 直接依赖：LPGR-00
>
> 建议首轮文件上限：10

## 1. 目标与完成标准

新增并测试 `LearningPlanBrief`、精确难度分布和默认 objective 解析，使新输入契约在不切换生产入口的情况下先成为可独立验证的领域能力。

本任务结束时旧 `LearningPlanDraftCommand` 仍可存在，但后续任务不需要再决定字段语义、默认值或校验边界。

## 2. 必须读取

- `CURRENT.md` 和 `LPGR-00` 完成备注。
- `CONTRACTS.md` 第 2 至 4 节。
- `LearningPlanDraftCommand.java`、`LearningPlanDraftValidator.java` 及对应测试。
- `LearningPlanIntent.java`、`LearningPlanContentLocale.java`、`LearningPlanDifficultyPreference.java`。
- `LearningPlanCreateDraftRequest.java`，只确认 API 到领域映射惯例，本任务不切换它。
- `frontend/src/learning-plans/options.ts` 的三组难度百分比，只核对固定模板映射值。

## 3. 实现要求

新增：

- `LearningPlanBrief`。
- `LearningPlanDifficultyDistribution`。
- `LearningPlanObjectiveDefaults` 或等价固定默认值入口。
- 集中的 `LearningPlanDifficultyDistributions`，包含模板枚举到百分比的唯一映射。

规则：

1. Brief 构造完成后字符串和列表已规范化，`personalizationEnabled`、`interviewOriented`、`contentLocale` 均为确定值。
2. objective 默认解析必须发生在进入 Agent 和持久化前；默认文案严格使用 `CONTRACTS.md` 表格。
3. 难度分布构造即校验每项范围和总和，非法对象不能进入 Prompt。
4. Brief 校验复用或扩展现有 `LearningPlanDraftValidator`，不要并存两套冲突的边界。
5. `TOPIC_BREAKTHROUGH` 空专题、过长 objective/constraints、非法周期和时间返回稳定字段名或现有异常类型。
6. 不把周期、时间、水平、语言、难度或专题重新拼入 objective。

## 4. 重点测试

- 六种 intent 的中英文默认 objective。
- 显式 objective trim 后保留原文，空白才走默认。
- `additionalConstraints` 与 objective 完全独立。
- 难度 `0/100` 边界、合法总和、负数、超过 100 和总和不等于 100。
- topic 稳定去重和专题突破必选。
- API 未提供的布尔值经后续 factory 可解析为 `personalizationEnabled=true`、`interviewOriented=false`。
- 四种模板难度枚举的唯一映射。

## 5. 验证命令

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl mentor-application -am \
  -Dtest='LearningPlanBrief*Test,LearningPlanDifficulty*Test,LearningPlanDraftValidatorTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

git diff --check
```

## 6. 非目标与停止条件

- 不切换 controller、repository、Prompt、Schema、计划快照或前端。
- 不修改模板 seed 和模板 API。
- 不为旧 JSON 增加 Jackson alias、默认反序列化或兼容构造器。
- 若默认 objective 仍依赖前端 locale 文案或难度映射散落多处，不得开始 `LPGR-02`。

## 7. 上下文交接

记录新类型全名、默认值入口、校验入口、模板难度 mapper 和测试结果。不要复制默认文案测试 fixture 全文。

## 8. 完成备注

完成时间：2026-08-03 04:37 UTC

状态：DONE

主要改动：

- 新增 `LearningPlanBrief`、精确难度分布、固定默认 objective 和模板枚举映射。
- 扩展 `LearningPlanDraftValidator`，统一返回 Brief 的稳定无效字段名。
- 增加 Brief 规范化、默认值、难度边界和模板映射测试。

验证：

- `mvn -f backend/pom.xml ... -Dtest='LearningPlanBrief*Test,LearningPlanDifficulty*Test,LearningPlanDraftValidatorTest' ... test`：PASS（10 tests）。
- `git diff --check`：PASS。

偏离计划：

- 无。

遗留事项：

- 无。

下一任务：`LPGR-02`
