package org.congcong.algomentor.api.learningplan.model;

import org.congcong.algomentor.mentor.application.learningplan.TodayPackHomeActivePlan;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackHomeRecommendedPlan;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackHomeSummary;

public final class TodayPackHomeSummaryResponseMapper {

  private TodayPackHomeSummaryResponseMapper() {
  }

  public static TodayPackHomeSummaryResponse toResponse(TodayPackHomeSummary summary) {
    return new TodayPackHomeSummaryResponse(
        summary.state(),
        summary.localDate(),
        toActivePlanResponse(summary.activePlan()),
        summary.dueProblemCount(),
        toRecommendedPlanResponse(summary.recommendedPlan()),
        summary.nextPackDate());
  }

  private static TodayPackHomeActivePlanResponse toActivePlanResponse(TodayPackHomeActivePlan activePlan) {
    if (activePlan == null) {
      return null;
    }
    return new TodayPackHomeActivePlanResponse(
        activePlan.planId(),
        activePlan.title(),
        activePlan.dailyProblemCount(),
        activePlan.trainingDaysPerWeek(),
        activePlan.remainingProblemCount());
  }

  private static TodayPackHomeRecommendedPlanResponse toRecommendedPlanResponse(
      TodayPackHomeRecommendedPlan recommendedPlan) {
    if (recommendedPlan == null) {
      return null;
    }
    return new TodayPackHomeRecommendedPlanResponse(
        recommendedPlan.title(),
        recommendedPlan.summary());
  }
}
