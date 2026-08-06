package org.congcong.algomentor.api.learningplan.personalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.api.ability.model.AbilityProfileResponse;
import org.congcong.algomentor.api.ability.model.AbilityTagScoreResponse;
import org.congcong.algomentor.api.ability.service.AbilityProfileService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanActivationService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPaceSummary;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRhythmSettings;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanAbilityTagSummary;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanActiveProgressSummary;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationContextService;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSource;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSourceOutcome;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanReviewLoadSummary;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.repository.LearnerMemoryClaimRepository;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimQueryService;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimSnapshotFactory;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.review.card.ReviewQueueService;
import org.congcong.algomentor.mentor.application.review.card.ReviewSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ApiLearningPlanPersonalizationDataProviderTest {

  private static final long USER_ID = 7L;
  private static final Instant NOW = Instant.parse("2026-08-03T05:30:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void mapsAllFourExistingAggregatesWithoutExposingTheirSourceModels() {
    LearnerMemoryClaimRepository claimRepository = mock(LearnerMemoryClaimRepository.class);
    LearnerMemoryClaimRevision claim = claim();
    when(claimRepository.findActiveByUser(USER_ID)).thenReturn(List.of(claim));
    LearnerMemoryClaimQueryService claimQueryService = new LearnerMemoryClaimQueryService(
        claimRepository, new LearnerMemoryClaimSnapshotFactory());

    AbilityProfileService abilityProfileService = mock(AbilityProfileService.class);
    when(abilityProfileService.getProfile(USER_ID)).thenReturn(new AbilityProfileResponse(List.of(
        new AbilityTagScoreResponse(
            "array", "数组", 12, 4, new BigDecimal("45.00"), new BigDecimal("30.00"))), null));

    LearningPlanActivationService activationService = mock(LearningPlanActivationService.class);
    when(activationService.findActivePlanId(USER_ID)).thenReturn(Optional.of(91L));
    LearningPlanRepository planRepository = mock(LearningPlanRepository.class);
    LearningPlan plan = activePlan();
    when(planRepository.findPlanByIdForUser(91L, USER_ID)).thenReturn(Optional.of(plan));
    PracticeSessionRepository practiceSessionRepository = mock(PracticeSessionRepository.class);
    List<PracticeProgress> progress = List.of(progress());
    when(practiceSessionRepository.findProgressByPlan(USER_ID, 91L)).thenReturn(progress);

    ReviewQueueService reviewQueueService = mock(ReviewQueueService.class);
    Instant nextDueAt = NOW.plusSeconds(3_600);
    when(reviewQueueService.summary(USER_ID, "UTC")).thenReturn(new ReviewSummary(4, 5, nextDueAt));

    ApiLearningPlanPersonalizationDataProvider provider = provider(
        claimQueryService,
        abilityProfileService,
        activationService,
        planRepository,
        practiceSessionRepository,
        reviewQueueService);

    assertThat(provider.findActiveClaims(USER_ID)).containsExactly(claim);
    assertThat(provider.findAbilityTagSummaries(USER_ID)).containsExactly(
        new LearningPlanAbilityTagSummary(
            "array", "数组", 4, new BigDecimal("45.00"), new BigDecimal("30.00")));

    LearningPlanLoadService loadService = new LearningPlanLoadService(CLOCK);
    LearningPlanPaceSummary pace = loadService.paceSummary(plan, progress);
    LearningPlanRhythmSettings rhythm = loadService.rhythmSettings(plan.plan(), progress);
    var contract = new LearningPlanContractService(CLOCK, loadService).summarize(plan, progress);
    assertThat(provider.findActivePlanProgress(USER_ID)).contains(new LearningPlanActiveProgressSummary(
        plan.plan().objective(),
        pace.currentWeek(),
        pace.totalWeeks(),
        contract.progressPercent(),
        pace.status(),
        rhythm.dailyProblemCount(),
        rhythm.trainingDaysPerWeek(),
        rhythm.remainingProblemCount()));
    assertThat(provider.findReviewLoad(USER_ID)).contains(
        new LearningPlanReviewLoadSummary(4, 5, nextDueAt));

    verify(claimRepository).findActiveByUser(USER_ID);
    verify(activationService).findActivePlanId(USER_ID);
    verify(planRepository).findPlanByIdForUser(91L, USER_ID);
    verify(practiceSessionRepository).findProgressByPlan(USER_ID, 91L);
    verify(reviewQueueService).summary(USER_ID, "UTC");
  }

  @Test
  void missingOptionalSourceIsRecordedAsErrorWhileOtherSourcesRemainAvailable() {
    AbilityProfileService abilityProfileService = mock(AbilityProfileService.class);
    when(abilityProfileService.getProfile(USER_ID)).thenReturn(new AbilityProfileResponse(List.of(), null));
    LearningPlanActivationService activationService = mock(LearningPlanActivationService.class);
    when(activationService.findActivePlanId(USER_ID)).thenReturn(Optional.empty());
    ReviewQueueService reviewQueueService = mock(ReviewQueueService.class);
    when(reviewQueueService.summary(USER_ID, "UTC")).thenReturn(new ReviewSummary(2, 1, null));
    ApiLearningPlanPersonalizationDataProvider provider = provider(
        unavailable(),
        abilityProfileService,
        activationService,
        mock(LearningPlanRepository.class),
        mock(PracticeSessionRepository.class),
        reviewQueueService);

    assertThatIllegalStateException().isThrownBy(() -> provider.findActiveClaims(USER_ID));
    assertThatCode(() -> provider.findAbilityTagSummaries(USER_ID)).doesNotThrowAnyException();
    assertThatCode(() -> provider.findActivePlanProgress(USER_ID)).doesNotThrowAnyException();
    assertThatCode(() -> provider.findReviewLoad(USER_ID)).doesNotThrowAnyException();

    var snapshot = new LearningPlanPersonalizationContextService(provider, null, CLOCK).snapshot(USER_ID, true);

    assertThat(snapshot.sourceOutcomes())
        .containsEntry(LearningPlanPersonalizationSource.ACTIVE_CLAIMS,
            LearningPlanPersonalizationSourceOutcome.ERROR)
        .containsEntry(LearningPlanPersonalizationSource.ABILITY_TAGS,
            LearningPlanPersonalizationSourceOutcome.EMPTY)
        .containsEntry(LearningPlanPersonalizationSource.ACTIVE_PLAN,
            LearningPlanPersonalizationSourceOutcome.EMPTY)
        .containsEntry(LearningPlanPersonalizationSource.REVIEW_LOAD,
            LearningPlanPersonalizationSourceOutcome.SUCCESS);
    assertThat(snapshot.context().reviewLoad()).isEqualTo(new LearningPlanReviewLoadSummary(2, 1, null));
  }

  @Test
  void normalizesEmptyAggregateResultsWithoutCreatingReferenceEntries() {
    LearnerMemoryClaimRepository claimRepository = mock(LearnerMemoryClaimRepository.class);
    when(claimRepository.findActiveByUser(USER_ID)).thenReturn(null);
    LearnerMemoryClaimQueryService claimQueryService = new LearnerMemoryClaimQueryService(
        claimRepository, new LearnerMemoryClaimSnapshotFactory());
    AbilityProfileService abilityProfileService = mock(AbilityProfileService.class);
    when(abilityProfileService.getProfile(USER_ID)).thenReturn(new AbilityProfileResponse(null, null));
    LearningPlanActivationService activationService = mock(LearningPlanActivationService.class);
    when(activationService.findActivePlanId(USER_ID)).thenReturn(Optional.empty());
    ReviewQueueService reviewQueueService = mock(ReviewQueueService.class);
    when(reviewQueueService.summary(USER_ID, "UTC")).thenReturn(new ReviewSummary(0, 0, null));
    ApiLearningPlanPersonalizationDataProvider provider = provider(
        claimQueryService,
        abilityProfileService,
        activationService,
        mock(LearningPlanRepository.class),
        mock(PracticeSessionRepository.class),
        reviewQueueService);

    assertThat(provider.findActiveClaims(USER_ID)).isEmpty();
    assertThat(provider.findAbilityTagSummaries(USER_ID)).isEmpty();
    assertThat(provider.findActivePlanProgress(USER_ID)).isEmpty();
    assertThat(provider.findReviewLoad(USER_ID)).isEmpty();
  }

  private ApiLearningPlanPersonalizationDataProvider provider(
      LearnerMemoryClaimQueryService claimQueryService,
      AbilityProfileService abilityProfileService,
      LearningPlanActivationService activationService,
      LearningPlanRepository planRepository,
      PracticeSessionRepository practiceSessionRepository,
      ReviewQueueService reviewQueueService
  ) {
    return provider(
        provider(claimQueryService),
        abilityProfileService,
        activationService,
        planRepository,
        practiceSessionRepository,
        reviewQueueService);
  }

  private ApiLearningPlanPersonalizationDataProvider provider(
      ObjectProvider<LearnerMemoryClaimQueryService> claimQueryService,
      AbilityProfileService abilityProfileService,
      LearningPlanActivationService activationService,
      LearningPlanRepository planRepository,
      PracticeSessionRepository practiceSessionRepository,
      ReviewQueueService reviewQueueService
  ) {
    LearningPlanLoadService loadService = new LearningPlanLoadService(CLOCK);
    return new ApiLearningPlanPersonalizationDataProvider(
        claimQueryService,
        provider(abilityProfileService),
        provider(activationService),
        provider(planRepository),
        provider(practiceSessionRepository),
        provider(reviewQueueService),
        loadService,
        new LearningPlanContractService(CLOCK, loadService));
  }

  private LearningPlan activePlan() {
    LearningPlanDraftPlan snapshot = new LearningPlanDraftPlan(
        "面试冲刺",
        "summary",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "完成当前算法面试冲刺",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        new LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Array"),
        null,
        List.of(new LearningPlanPhaseDraft(
            1,
            "数组",
            4,
            "Array",
            List.of(
                problem("two-sum", 1),
                problem("contains-duplicate", 2)))),
        Map.of("dailyProblemCount", 2, "trainingDaysPerWeek", 3));
    return new LearningPlan(91L, USER_ID, LearningPlanStatus.ACTIVE, snapshot, NOW, NOW);
  }

  private LearningPlanProblemDraft problem(String slug, int sortOrder) {
    return new LearningPlanProblemDraft(
        slug, sortOrder, slug, slug, "EASY", List.of("Array"), "训练题。", sortOrder);
  }

  private PracticeProgress progress() {
    return new PracticeProgress(
        1L,
        USER_ID,
        91L,
        1,
        "two-sum",
        PracticeProgressStatus.COMPLETED,
        NOW,
        NOW,
        null,
        NOW,
        NOW);
  }

  private LearnerMemoryClaimRevision claim() {
    return new LearnerMemoryClaimRevision(
        1L,
        new UUID(0L, 1L),
        USER_ID,
        new LearnerMemoryClaimScope(
            LearnerMemoryClaimContract.Kind.DECLARED_FACT,
            LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS,
            null),
        1,
        LearnerMemoryClaimContract.RevisionStatus.ACTIVE,
        "准备算法面试",
        "a".repeat(64),
        LearnerMemoryClaimContract.Origin.SYSTEM_DERIVED,
        LearnerMemoryEvidenceContract.Pattern.CROSS_PROBLEM_RECURRENCE,
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED,
        null,
        1,
        null,
        NOW,
        null,
        NOW,
        NOW);
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> provider(T bean) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  private static <T> ObjectProvider<T> unavailable() {
    return provider(null);
  }
}
