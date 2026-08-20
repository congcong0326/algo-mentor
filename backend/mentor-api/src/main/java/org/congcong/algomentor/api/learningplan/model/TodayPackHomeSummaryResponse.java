package org.congcong.algomentor.api.learningplan.model;

import java.time.LocalDate;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackState;

/**
 * 首页训练入口的轻量响应，不返回题目列表。
 */
public record TodayPackHomeSummaryResponse(
    TodayPackState state,
    LocalDate localDate,
    TodayPackHomeActivePlanResponse activePlan,
    int dueProblemCount,
    TodayPackHomeRecommendedPlanResponse recommendedPlan,
    LocalDate nextPackDate
) {
}
