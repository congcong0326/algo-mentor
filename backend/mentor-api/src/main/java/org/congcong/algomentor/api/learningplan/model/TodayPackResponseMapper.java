package org.congcong.algomentor.api.learningplan.model;

import org.congcong.algomentor.mentor.application.learningplan.TodayPack;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackActivePlan;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackProblem;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackRecommendedPlan;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackSection;

public final class TodayPackResponseMapper {

  private TodayPackResponseMapper() {
  }

  public static TodayPackResponse toResponse(TodayPack pack) {
    return new TodayPackResponse(
        pack.state(),
        pack.localDate(),
        pack.timezone(),
        pack.packOffset(),
        toActivePlanResponse(pack.activePlan()),
        pack.sections().stream().map(TodayPackResponseMapper::toSectionResponse).toList(),
        pack.notice(),
        toRecommendedPlanResponse(pack.recommendedPlan()),
        pack.nextPackDate());
  }

  private static TodayPackActivePlanResponse toActivePlanResponse(TodayPackActivePlan activePlan) {
    if (activePlan == null) {
      return null;
    }
    return new TodayPackActivePlanResponse(
        activePlan.planId(),
        activePlan.title(),
        activePlan.activatedAt(),
        activePlan.dailyProblemCount(),
        activePlan.trainingDaysPerWeek(),
        activePlan.remainingProblemCount());
  }

  private static TodayPackRecommendedPlanResponse toRecommendedPlanResponse(TodayPackRecommendedPlan recommendedPlan) {
    if (recommendedPlan == null) {
      return null;
    }
    return new TodayPackRecommendedPlanResponse(
        recommendedPlan.templateId(),
        recommendedPlan.title(),
        recommendedPlan.summary());
  }

  private static TodayPackSectionResponse toSectionResponse(TodayPackSection section) {
    return new TodayPackSectionResponse(
        section.type(),
        section.title(),
        section.date(),
        section.problems().stream().map(TodayPackResponseMapper::toProblemResponse).toList());
  }

  private static TodayPackProblemResponse toProblemResponse(TodayPackProblem problem) {
    return new TodayPackProblemResponse(
        problem.planId(),
        problem.phaseIndex(),
        problem.slug(),
        problem.frontendId(),
        problem.title(),
        problem.titleCn(),
        problem.difficulty(),
        problem.tags(),
        problem.progressStatus(),
        problem.scheduledDate(),
        problem.carryoverDays());
  }
}
