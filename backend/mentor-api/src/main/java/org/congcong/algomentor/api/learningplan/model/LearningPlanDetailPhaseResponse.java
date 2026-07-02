package org.congcong.algomentor.api.learningplan.model;

import java.util.List;

public record LearningPlanDetailPhaseResponse(
    int phaseIndex,
    String title,
    int durationWeeks,
    String focus,
    List<String> objectives,
    List<String> recommendedTags,
    List<String> acceptanceCriteria,
    String reviewAdvice,
    List<LearningPlanDetailProblemResponse> problems
) {

  public LearningPlanDetailPhaseResponse {
    objectives = objectives == null ? List.of() : List.copyOf(objectives);
    recommendedTags = recommendedTags == null ? List.of() : List.copyOf(recommendedTags);
    acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
    problems = problems == null ? List.of() : List.copyOf(problems);
  }
}
