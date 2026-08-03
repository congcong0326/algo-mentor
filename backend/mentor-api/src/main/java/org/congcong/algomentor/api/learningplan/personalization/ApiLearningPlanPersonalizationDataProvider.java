package org.congcong.algomentor.api.learningplan.personalization;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.congcong.algomentor.api.ability.service.AbilityProfileService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanActivationService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanAbilityTagSummary;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanActiveProgressSummary;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationDataProvider;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanReviewLoadSummary;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimQueryService;
import org.congcong.algomentor.mentor.application.review.card.ReviewQueueService;
import org.springframework.beans.factory.ObjectProvider;

/** 将 API 层已有聚合服务投影到学习计划应用层个性化端口。 */
public final class ApiLearningPlanPersonalizationDataProvider implements LearningPlanPersonalizationDataProvider {

  private final ObjectProvider<LearnerMemoryClaimQueryService> claimQueryService;
  private final ObjectProvider<AbilityProfileService> abilityProfileService;
  private final ObjectProvider<LearningPlanActivationService> activationService;
  private final ObjectProvider<LearningPlanRepository> planRepository;
  private final ObjectProvider<PracticeSessionRepository> practiceSessionRepository;
  private final ObjectProvider<ReviewQueueService> reviewQueueService;
  private final LearningPlanLoadService loadService;
  private final LearningPlanContractService contractService;

  public ApiLearningPlanPersonalizationDataProvider(
      ObjectProvider<LearnerMemoryClaimQueryService> claimQueryService,
      ObjectProvider<AbilityProfileService> abilityProfileService,
      ObjectProvider<LearningPlanActivationService> activationService,
      ObjectProvider<LearningPlanRepository> planRepository,
      ObjectProvider<PracticeSessionRepository> practiceSessionRepository,
      ObjectProvider<ReviewQueueService> reviewQueueService,
      LearningPlanLoadService loadService,
      LearningPlanContractService contractService
  ) {
    this.claimQueryService = claimQueryService;
    this.abilityProfileService = abilityProfileService;
    this.activationService = activationService;
    this.planRepository = planRepository;
    this.practiceSessionRepository = practiceSessionRepository;
    this.reviewQueueService = reviewQueueService;
    this.loadService = loadService;
    this.contractService = contractService;
  }

  @Override
  public List<LearnerMemoryClaimRevision> findActiveClaims(long userId) {
    var snapshot = required(claimQueryService.getIfAvailable(), "learner memory").snapshot(userId);
    if (snapshot == null || snapshot.activeClaims() == null) {
      return List.of();
    }
    return snapshot.activeClaims().stream().filter(Objects::nonNull).toList();
  }

  @Override
  public List<LearningPlanAbilityTagSummary> findAbilityTagSummaries(long userId) {
    var profile = required(abilityProfileService.getIfAvailable(), "ability profile").getProfile(userId);
    if (profile == null || profile.tags() == null) {
      return List.of();
    }
    return profile.tags().stream()
        .filter(Objects::nonNull)
        .filter(tag -> tag.tag() != null && !tag.tag().isBlank()
            && tag.label() != null && !tag.label().isBlank()
            && tag.rawAverageScore() != null && tag.abilityScore() != null)
        .map(tag -> new LearningPlanAbilityTagSummary(
            tag.tag(), tag.label(), tag.reviewedProblemCount(), tag.rawAverageScore(), tag.abilityScore()))
        .toList();
  }

  @Override
  public Optional<LearningPlanActiveProgressSummary> findActivePlanProgress(long userId) {
    var activation = required(activationService.getIfAvailable(), "learning plan activation");
    var repository = required(planRepository.getIfAvailable(), "learning plan repository");
    var sessions = required(practiceSessionRepository.getIfAvailable(), "practice session repository");
    return optional(activation.findActivePlanId(userId))
        .flatMap(planId -> optional(repository.findPlanByIdForUser(planId, userId)))
        .map(plan -> {
          var progress = sessions.findProgressByPlan(userId, plan.id());
          var pace = loadService.paceSummary(plan, progress);
          var rhythm = loadService.rhythmSettings(plan.plan(), progress);
          var contract = contractService.summarize(plan, progress);
          return new LearningPlanActiveProgressSummary(
              plan.plan().objective(), pace.currentWeek(), pace.totalWeeks(), contract.progressPercent(),
              pace.status(), rhythm.dailyProblemCount(), rhythm.trainingDaysPerWeek(), rhythm.remainingProblemCount());
        });
  }

  @Override
  public Optional<LearningPlanReviewLoadSummary> findReviewLoad(long userId) {
    var summary = required(reviewQueueService.getIfAvailable(), "review queue").summary(userId, "UTC");
    if (summary == null || (summary.dueCount() == 0
        && summary.remainingTodayCount() == 0 && summary.nextDueAt() == null)) {
      return Optional.empty();
    }
    return Optional.of(new LearningPlanReviewLoadSummary(
        summary.dueCount(), summary.remainingTodayCount(), summary.nextDueAt()));
  }

  private static <T> Optional<T> optional(Optional<T> value) {
    return value == null ? Optional.empty() : value;
  }

  private <T> T required(T value, String source) {
    if (value == null) {
      throw new IllegalStateException(source + " is unavailable");
    }
    return value;
  }
}
