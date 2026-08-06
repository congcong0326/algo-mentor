package org.congcong.algomentor.api.learningplan.model;

import java.util.List;

public record LearningPlanDetailPhaseResponse(
    int phaseIndex,
    String title,
    int durationWeeks,
    String focus,
    List<LearningPlanDetailProblemResponse> problems
) {

  public LearningPlanDetailPhaseResponse {
    problems = problems == null ? List.of() : List.copyOf(problems);
  }
}
