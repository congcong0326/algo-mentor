package org.congcong.algomentor.mentor.application.learningplan;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LearningPlanDraftValidatorTest {

  private final LearningPlanDraftValidator validator = new LearningPlanDraftValidator();

  @Test
  void generatedPlanRejectsMoreThanFiveProblemsPerPhase() {
    LearningPlanDraftPlan plan = plan(1, 6, Map.of());

    assertThatThrownBy(() -> validator.validateGeneratedPlan(plan))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("每个阶段最多推荐 5 道题。");
  }

  @Test
  void generatedPlanAllowsSixStagesForTheThirtyProblemTarget() {
    validator.validateGeneratedPlan(planWithPhases(
        List.of(5, 5, 5, 5, 5, 5),
        6,
        Map.of(LearningPlanDraftMetadataKeys.TARGET_PROBLEM_COUNT, 30)));
  }

  @Test
  void generatedPlanRejectsProblemCountAboveTheTarget() {
    LearningPlanDraftPlan plan = planWithPhases(
        List.of(5, 5, 1),
        3,
        Map.of(LearningPlanDraftMetadataKeys.TARGET_PROBLEM_COUNT, 10));

    assertThatThrownBy(() -> validator.validateGeneratedPlan(plan))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("学习计划推荐题数不能超过目标规模。");
  }

  @Test
  void templatePlanAllowsMoreThanFiveProblemsPerPhaseWhenAllMatchedProblemsArePresent() {
    validator.validateTemplatePlan(plan(1, 6, templateMetadata(6)));
  }

  @Test
  void templatePlanRejectsMissingMatchedProblems() {
    LearningPlanDraftPlan plan = plan(1, 5, templateMetadata(6));

    assertThatThrownBy(() -> validator.validateTemplatePlan(plan))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("模板学习计划草案必须包含所有本地匹配题。");
  }

  @Test
  void briefValidationReturnsStableInvalidFieldNames() {
    LearningPlanBrief brief = new LearningPlanBrief(
        LearningPlanIntent.TOPIC_BREAKTHROUGH,
        "x".repeat(301),
        53,
        null,
        81,
        null,
        null,
        List.of(),
        "x".repeat(1001),
        true,
        LearningPlanContentLocale.ZH_CN);

    assertThat(validator.missingRequiredFields(brief)).containsExactly(
        "level",
        "difficultyDistribution",
        "topicPreferences");
  }

  private LearningPlanDraftPlan plan(int phaseCount, int problemsPerPhase, Map<String, Object> metadata) {
    return new LearningPlanDraftPlan(
        "模板计划",
        "summary",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备算法面试",
        phaseCount,
        LearningPlanLevel.INTERMEDIATE,
        8,
        "Java",
        new LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Array"),
        null,
        List.of(phase(1, phaseCount, problemsPerPhase)),
        metadata);
  }

  private LearningPlanDraftPlan planWithPhases(
      List<Integer> problemCounts,
      int durationWeeks,
      Map<String, Object> metadata
  ) {
    List<LearningPlanPhaseDraft> phases = java.util.stream.IntStream.range(0, problemCounts.size())
        .mapToObj(index -> phase(index + 1, 1, problemCounts.get(index)))
        .toList();
    return new LearningPlanDraftPlan(
        "AI 计划",
        "summary",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备算法面试",
        durationWeeks,
        LearningPlanLevel.INTERMEDIATE,
        5,
        "Java",
        new LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Array"),
        null,
        phases,
        metadata);
  }

  private LearningPlanPhaseDraft phase(int phaseIndex, int durationWeeks, int problemCount) {
    return new LearningPlanPhaseDraft(
        phaseIndex,
        "阶段 " + phaseIndex,
        durationWeeks,
        "Array",
        problems(problemCount));
  }

  private List<LearningPlanProblemDraft> problems(int problemCount) {
    return java.util.stream.IntStream.rangeClosed(1, problemCount)
        .mapToObj(index -> new LearningPlanProblemDraft(
            "problem-" + index,
            index,
            "Problem " + index,
            "题目 " + index,
            "MEDIUM",
            List.of("Array"),
            "模板训练题。",
            index))
        .toList();
  }

  private Map<String, Object> templateMetadata(int matchedProblemCount) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(LearningPlanDraftMetadataKeys.DRAFT_SOURCE, LearningPlanDraftMetadataKeys.DRAFT_SOURCE_TEMPLATE);
    metadata.put(LearningPlanDraftMetadataKeys.TEMPLATE, Map.of(
        LearningPlanDraftMetadataKeys.TEMPLATE_ID,
        "template",
        LearningPlanDraftMetadataKeys.MATCHED_PROBLEM_COUNT,
        matchedProblemCount));
    return metadata;
  }
}
