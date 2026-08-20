package org.congcong.algomentor.api.learningplan.model;

import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLivingContractSummary;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPaceSummary;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRhythmSettings;

/**
 * 今日题包页面所需的计划执行摘要，不包含阶段与题目详情。
 */
public record TodayPackPlanContextResponse(
    long id,
    int durationWeeks,
    LearningPlanRhythmSettings rhythmSettings,
    LearningPlanPaceSummary paceSummary,
    LearningPlanLivingContractSummary livingContractSummary
) {
}
