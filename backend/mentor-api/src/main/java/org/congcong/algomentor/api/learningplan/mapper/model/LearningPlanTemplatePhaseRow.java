package org.congcong.algomentor.api.learningplan.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;

public record LearningPlanTemplatePhaseRow(
    Long id,
    Long templateDbId,
    Integer phaseIndex,
    String title,
    Integer durationWeeks,
    String focus,
    JsonNode objectivesJson,
    JsonNode recommendedTagsJson,
    JsonNode acceptanceCriteriaJson,
    String reviewAdvice
) {
}
