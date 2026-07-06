package org.congcong.algomentor.mentor.application.learningplan;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LearningPlanDraftValidatorTest {

  private final LearningPlanDraftValidator validator = new LearningPlanDraftValidator();

  @Test
  void generatedPlanRejectsMoreThanFiveProblemsPerPhase() {
    LearningPlanDraftPlan plan = plan(1, 6, Map.of(
        LearningPlanDraftMetadataKeys.PROBLEM_RECOMMENDATION_INCOMPLETE,
        false));

    assertThatThrownBy(() -> validator.validateGeneratedPlan(plan))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("每个阶段最多推荐 5 道题。");
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
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Array"),
        "profile",
        List.of(phase(1, phaseCount, problemsPerPhase)),
        metadata);
  }

  private LearningPlanPhaseDraft phase(int phaseIndex, int durationWeeks, int problemCount) {
    return new LearningPlanPhaseDraft(
        phaseIndex,
        "阶段 " + phaseIndex,
        durationWeeks,
        "Array",
        List.of("完成训练"),
        List.of("Array"),
        List.of("能复盘"),
        "记录错题。",
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
