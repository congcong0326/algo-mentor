package org.congcong.algomentor.api.learningplan.service;

import java.util.List;

public record LearningPlanTemplatePhaseSeedRecord(
    int phaseIndex,
    String title,
    int durationWeeks,
    String focus,
    List<String> objectives,
    List<String> recommendedTags,
    List<String> acceptanceCriteria,
    String reviewAdvice
) {

  public LearningPlanTemplatePhaseSeedRecord {
    objectives = objectives == null ? List.of() : List.copyOf(objectives);
    recommendedTags = recommendedTags == null ? List.of() : List.copyOf(recommendedTags);
    acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
  }
}
