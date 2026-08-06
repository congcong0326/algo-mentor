package org.congcong.algomentor.api.learningplan.mapper.model;

public record LearningPlanTemplatePhaseRow(
    Long id,
    Long templateDbId,
    Integer phaseIndex,
    String title,
    String titleEn,
    Integer durationWeeks,
    String focus,
    String focusEn
) {
}
