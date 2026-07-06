package org.congcong.algomentor.api.learningplan.service;

import java.util.Map;

public record LearningPlanTemplateProblemRefSeedRecord(
    String templateId,
    int phaseIndex,
    int sortOrder,
    int sourceOrder,
    String problemSlug,
    String sourceTitle,
    String sourceDifficulty,
    String pattern,
    String sourceUrl,
    Map<String, Object> metadata
) {

  public LearningPlanTemplateProblemRefSeedRecord {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
