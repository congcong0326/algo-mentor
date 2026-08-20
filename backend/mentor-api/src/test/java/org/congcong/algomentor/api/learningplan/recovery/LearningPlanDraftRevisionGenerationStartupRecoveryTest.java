package org.congcong.algomentor.api.learningplan.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.learningplan.config.LearningPlanGovernanceProperties;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationConstants;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationEvent;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationMetrics;
import org.junit.jupiter.api.Test;

class LearningPlanDraftRevisionGenerationStartupRecoveryTest {

  private static final Instant NOW = Instant.parse("2026-08-20T08:00:00Z");

  @Test
  void conditionallyFailsStaleRevisionsThenPublishesTheMinimalFailureEvent() throws Exception {
    LearningPlanProposalRepository repository = mock(LearningPlanProposalRepository.class);
    LearningPlanDraftRevision revision = revision();
    when(repository.findInterruptedDraftRevisionGenerations(NOW.minusSeconds(60)))
        .thenReturn(List.of(revision));
    when(repository.failDraftRevisionIfGenerating(
        revision.id(), revision.userId(), revision.draftId(),
        LearningPlanDraftRevisionGenerationConstants.GENERATION_INTERRUPTED_CODE,
        LearningPlanDraftRevisionGenerationConstants.GENERATION_INTERRUPTED_MESSAGE,
        NOW)).thenReturn(Optional.of(revision.withFailure(
            LearningPlanDraftRevisionGenerationConstants.GENERATION_INTERRUPTED_CODE,
            LearningPlanDraftRevisionGenerationConstants.GENERATION_INTERRUPTED_MESSAGE,
            NOW)));
    List<LearningPlanDraftRevisionGenerationEvent> events = new ArrayList<>();
    CapturingMetrics metrics = new CapturingMetrics();
    LearningPlanGovernanceProperties.GenerationRecovery properties =
        new LearningPlanGovernanceProperties.GenerationRecovery();
    properties.setMinimumAge(java.time.Duration.ofMinutes(1));
    LearningPlanDraftRevisionGenerationStartupRecovery recovery =
        new LearningPlanDraftRevisionGenerationStartupRecovery(
            repository,
            (draftId, revisionId, event) -> events.add(event),
            Clock.fixed(NOW, ZoneOffset.UTC),
            properties,
            metrics);

    recovery.run(null);

    verify(repository).failDraftRevisionIfGenerating(
        revision.id(), revision.userId(), revision.draftId(),
        LearningPlanDraftRevisionGenerationConstants.GENERATION_INTERRUPTED_CODE,
        LearningPlanDraftRevisionGenerationConstants.GENERATION_INTERRUPTED_MESSAGE,
        NOW);
    assertThat(events).containsExactly(new LearningPlanDraftRevisionGenerationEvent.Failed(
        LearningPlanDraftRevisionGenerationConstants.GENERATION_INTERRUPTED_CODE));
    assertThat(metrics.failedStartedAt).containsExactly(revision.generationStartedAt());
  }

  @Test
  void doesNotPublishOrRecordWhenTheConditionalUpdateLosesTheTerminalRace() throws Exception {
    LearningPlanProposalRepository repository = mock(LearningPlanProposalRepository.class);
    LearningPlanDraftRevision revision = revision();
    when(repository.findInterruptedDraftRevisionGenerations(NOW.minusSeconds(60)))
        .thenReturn(List.of(revision));
    when(repository.failDraftRevisionIfGenerating(
        revision.id(), revision.userId(), revision.draftId(),
        LearningPlanDraftRevisionGenerationConstants.GENERATION_INTERRUPTED_CODE,
        LearningPlanDraftRevisionGenerationConstants.GENERATION_INTERRUPTED_MESSAGE,
        NOW)).thenReturn(Optional.empty());
    List<LearningPlanDraftRevisionGenerationEvent> events = new ArrayList<>();
    CapturingMetrics metrics = new CapturingMetrics();
    LearningPlanGovernanceProperties.GenerationRecovery properties =
        new LearningPlanGovernanceProperties.GenerationRecovery();
    properties.setMinimumAge(java.time.Duration.ofMinutes(1));

    new LearningPlanDraftRevisionGenerationStartupRecovery(
        repository,
        (draftId, revisionId, event) -> events.add(event),
        Clock.fixed(NOW, ZoneOffset.UTC),
        properties,
        metrics).run(null);

    assertThat(events).isEmpty();
    assertThat(metrics.failedStartedAt).isEmpty();
  }

  private static LearningPlanDraftRevision revision() {
    LearningPlanDraftPlan plan = new LearningPlanDraftPlan(
        "revision plan", "summary", LearningPlanIntent.INTERVIEW_SPRINT, "prepare for interviews", 4,
        LearningPlanLevel.INTERMEDIATE, 6, "Java", new LearningPlanDifficultyDistribution(30, 50, 20),
        List.of("array"), null, List.of(new LearningPlanPhaseDraft(1, "phase", 1, "focus", List.of())),
        java.util.Map.of());
    return new LearningPlanDraftRevision(
        203L, 72L, 102L, 7L, 3, LearningPlanProposalRevisionStatus.GENERATING,
        "add graph review", plan, null, null, null, NOW, NOW)
        .withGenerationStarted("request-key", "a".repeat(64), "run-id", NOW.minusSeconds(300));
  }

  private static final class CapturingMetrics implements LearningPlanDraftRevisionGenerationMetrics {
    private final List<Instant> failedStartedAt = new ArrayList<>();

    @Override
    public void recordFailed(Instant startedAt, Instant completedAt) {
      failedStartedAt.add(startedAt);
    }
  }
}
