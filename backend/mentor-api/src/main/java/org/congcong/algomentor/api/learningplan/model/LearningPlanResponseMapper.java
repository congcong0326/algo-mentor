package org.congcong.algomentor.api.learningplan.model;

import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanConfirmResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPage;

public final class LearningPlanResponseMapper {

  private LearningPlanResponseMapper() {
  }

  public static LearningPlanDraftResponse toDraftResponse(LearningPlanDraftResult result) {
    return new LearningPlanDraftResponse(
        result.draftId(),
        result.status(),
        result.assistantMessage(),
        result.missingFields(),
        result.draftPlan());
  }

  public static LearningPlanConfirmResponse toConfirmResponse(LearningPlanConfirmResult result) {
    return new LearningPlanConfirmResponse(result.planId(), result.title(), result.status());
  }

  public static LearningPlanSummaryResponse toSummaryResponse(LearningPlan plan) {
    LearningPlanDraftPlan snapshot = plan.plan();
    return new LearningPlanSummaryResponse(
        plan.id(),
        snapshot.title(),
        snapshot.intent(),
        snapshot.goal(),
        snapshot.durationWeeks(),
        snapshot.level(),
        snapshot.programmingLanguage(),
        snapshot.weeklyHours(),
        plan.status(),
        plan.createdAt());
  }

  public static LearningPlanPageResponse toPageResponse(LearningPlanPage page) {
    return new LearningPlanPageResponse(
        page.items().stream().map(LearningPlanResponseMapper::toSummaryResponse).toList(),
        page.total(),
        page.page(),
        page.pageSize(),
        page.activeCount(),
        page.archivedCount(),
        page.latestCreatedAt());
  }

  public static LearningPlanDetailResponse toDetailResponse(LearningPlan plan) {
    LearningPlanDraftPlan snapshot = plan.plan();
    return new LearningPlanDetailResponse(
        plan.id(),
        snapshot.title(),
        snapshot.summary(),
        snapshot.intent(),
        snapshot.goal(),
        snapshot.durationWeeks(),
        snapshot.level(),
        snapshot.weeklyHours(),
        snapshot.programmingLanguage(),
        snapshot.difficultyPreference(),
        snapshot.interviewOriented(),
        snapshot.topicPreferences(),
        snapshot.profileSummary(),
        plan.status(),
        snapshot.phases(),
        snapshot.metadata(),
        plan.createdAt(),
        plan.updatedAt());
  }
}
