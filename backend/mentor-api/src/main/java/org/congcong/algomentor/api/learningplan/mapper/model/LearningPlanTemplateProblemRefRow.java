package org.congcong.algomentor.api.learningplan.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;

public record LearningPlanTemplateProblemRefRow(
    Long id,
    Long templateDbId,
    Long phaseDbId,
    Integer phaseIndex,
    Integer sortOrder,
    Integer sourceOrder,
    String problemSlug,
    String sourceTitle,
    String sourceDifficulty,
    String pattern,
    String sourceUrl,
    Boolean matchedProblem,
    JsonNode metadataJson
) {
}
