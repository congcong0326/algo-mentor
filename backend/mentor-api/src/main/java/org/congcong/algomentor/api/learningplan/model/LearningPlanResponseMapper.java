package org.congcong.algomentor.api.learningplan.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanConfirmResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractState;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPage;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;

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
    LearningPlanLoadService loadService = new LearningPlanLoadService();
    return toDetailResponse(plan, List.of(), loadService, new LearningPlanContractService(), null);
  }

  public static LearningPlanDetailResponse toDetailResponse(LearningPlan plan, List<PracticeProgress> progress) {
    LearningPlanLoadService loadService = new LearningPlanLoadService();
    return toDetailResponse(plan, progress, loadService, new LearningPlanContractService(), null);
  }

  public static LearningPlanDetailResponse toDetailResponse(
      LearningPlan plan,
      List<PracticeProgress> progress,
      LearningPlanLoadService loadService
  ) {
    return toDetailResponse(plan, progress, loadService, new LearningPlanContractService(), null);
  }

  public static LearningPlanDetailResponse toDetailResponse(
      LearningPlan plan,
      List<PracticeProgress> progress,
      LearningPlanLoadService loadService,
      LearningPlanContractService contractService,
      LearningPlanContractState contractState
  ) {
    LearningPlanDraftPlan snapshot = plan.plan();
    Map<ProgressKey, PracticeProgressStatus> progressByProblem = progressByProblem(progress);
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
        snapshot.phases().stream()
            .map(phase -> new LearningPlanDetailPhaseResponse(
                phase.phaseIndex(),
                phase.title(),
                phase.durationWeeks(),
                phase.focus(),
                phase.objectives(),
                phase.recommendedTags(),
                phase.acceptanceCriteria(),
                phase.reviewAdvice(),
                phase.problems().stream()
                    .map(problem -> new LearningPlanDetailProblemResponse(
                        problem.slug(),
                        problem.frontendId(),
                        problem.title(),
                        problem.titleCn(),
                        problem.difficulty(),
                        problem.tags(),
                        problem.reason(),
                        problem.sortOrder(),
                        progressByProblem.getOrDefault(
                            new ProgressKey(phase.phaseIndex(), problem.slug()),
                            PracticeProgressStatus.NOT_STARTED)))
                    .toList()))
            .toList(),
        snapshot.metadata(),
        loadService.summarize(snapshot),
        loadService.weeklyBuckets(snapshot),
        loadService.nextTrainingPackage(plan, progress),
        loadService.paceSummary(plan, progress),
        contractService.summarize(plan, progress, contractState == null
            ? LearningPlanContractState.empty(plan.userId(), plan.id())
            : contractState),
        plan.createdAt(),
        plan.updatedAt());
  }

  private static Map<ProgressKey, PracticeProgressStatus> progressByProblem(List<PracticeProgress> progress) {
    Map<ProgressKey, PracticeProgressStatus> progressByProblem = new HashMap<>();
    if (progress == null) {
      return progressByProblem;
    }
    for (PracticeProgress item : progress) {
      progressByProblem.put(new ProgressKey(item.phaseIndex(), item.problemSlug()), item.status());
    }
    return progressByProblem;
  }

  private record ProgressKey(int phaseIndex, String problemSlug) {
  }
}
