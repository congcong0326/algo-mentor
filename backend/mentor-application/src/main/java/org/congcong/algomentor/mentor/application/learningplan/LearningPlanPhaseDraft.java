package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;

public record LearningPlanPhaseDraft(
    int phaseIndex,
    String title,
    int durationWeeks,
    String focus,
    List<String> objectives,
    List<String> recommendedTags,
    List<String> acceptanceCriteria,
    String reviewAdvice,
    List<LearningPlanProblemDraft> problems
) {

  public LearningPlanPhaseDraft {
    objectives = objectives == null ? List.of() : List.copyOf(objectives);
    recommendedTags = recommendedTags == null ? List.of() : List.copyOf(recommendedTags);
    acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
    problems = problems == null ? List.of() : List.copyOf(problems);
  }
}
