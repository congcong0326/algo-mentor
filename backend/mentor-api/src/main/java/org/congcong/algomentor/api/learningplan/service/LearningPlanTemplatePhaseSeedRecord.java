package org.congcong.algomentor.api.learningplan.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** v3 模板阶段 Seed；读取 v2 文件时忽略已废弃阶段文案。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LearningPlanTemplatePhaseSeedRecord(
    int phaseIndex,
    String title,
    String titleEn,
    int durationWeeks,
    String focus,
    String focusEn
) {
}
