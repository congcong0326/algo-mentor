package org.congcong.algomentor.api.learningplan.service;

import java.util.List;

public record LearningPlanTemplatePhaseSeedRecord(
    int phaseIndex,
    String title,
    String titleEn,
    int durationWeeks,
    String focus,
    String focusEn,
    List<String> objectives,
    List<String> objectivesEn,
    List<String> recommendedTags,
    List<String> acceptanceCriteria,
    List<String> acceptanceCriteriaEn,
    String reviewAdvice,
    String reviewAdviceEn
) {

  public LearningPlanTemplatePhaseSeedRecord {
    objectives = objectives == null ? List.of() : List.copyOf(objectives);
    objectivesEn = objectivesEn == null ? List.of() : List.copyOf(objectivesEn);
    recommendedTags = recommendedTags == null ? List.of() : List.copyOf(recommendedTags);
    acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
    acceptanceCriteriaEn = acceptanceCriteriaEn == null ? List.of() : List.copyOf(acceptanceCriteriaEn);
  }
}
