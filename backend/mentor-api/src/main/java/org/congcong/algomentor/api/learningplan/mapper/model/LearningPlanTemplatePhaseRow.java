package org.congcong.algomentor.api.learningplan.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;

public record LearningPlanTemplatePhaseRow(
    Long id,
    Long templateDbId,
    Integer phaseIndex,
    String title,
    String titleEn,
    Integer durationWeeks,
    String focus,
    String focusEn,
    JsonNode objectivesJson,
    JsonNode objectivesEnJson,
    JsonNode recommendedTagsJson,
    JsonNode acceptanceCriteriaJson,
    JsonNode acceptanceCriteriaEnJson,
    String reviewAdvice,
    String reviewAdviceEn
) {
}
