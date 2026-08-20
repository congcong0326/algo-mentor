package org.congcong.algomentor.mentor.application.learningplan;

import java.time.LocalDate;

/**
 * 首页训练入口使用的轻量今日题包概览，不包含题目明细。
 */
public record TodayPackHomeSummary(
    TodayPackState state,
    LocalDate localDate,
    TodayPackHomeActivePlan activePlan,
    int dueProblemCount,
    TodayPackHomeRecommendedPlan recommendedPlan,
    LocalDate nextPackDate
) {
}
