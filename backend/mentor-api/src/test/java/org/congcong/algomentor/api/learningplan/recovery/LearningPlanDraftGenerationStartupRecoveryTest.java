package org.congcong.algomentor.api.learningplan.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.learningplan.config.LearningPlanGovernanceProperties;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationConstants;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationEventPublisher;
import org.junit.jupiter.api.Test;

class LearningPlanDraftGenerationStartupRecoveryTest {

  @Test
  void marksOnlyStaleGeneratingDraftsAsInterruptedAndThenPublishesMinimalFailure() throws Exception {
    RecordingRepository repository = new RecordingRepository(List.of(102L, 103L));
    List<Long> publishedDraftIds = new ArrayList<>();
    LearningPlanDraftGenerationEventPublisher publisher = (draftId, event) -> {
      assertThat(event).isEqualTo(new LearningPlanDraftGenerationEvent.Failed(
          LearningPlanDraftGenerationConstants.GENERATION_INTERRUPTED_CODE));
      publishedDraftIds.add(draftId);
    };
    LearningPlanGovernanceProperties.GenerationRecovery properties =
        new LearningPlanGovernanceProperties.GenerationRecovery();
    properties.setMinimumAge(java.time.Duration.ofMinutes(1));
    LearningPlanDraftGenerationStartupRecovery recovery = new LearningPlanDraftGenerationStartupRecovery(
        repository,
        publisher,
        Clock.fixed(Instant.parse("2026-08-20T08:00:00Z"), ZoneOffset.UTC),
        properties);

    recovery.run(null);

    assertThat(repository.startedBefore).isEqualTo(Instant.parse("2026-08-20T07:59:00Z"));
    assertThat(repository.code).isEqualTo(LearningPlanDraftGenerationConstants.GENERATION_INTERRUPTED_CODE);
    assertThat(repository.message).isEqualTo(LearningPlanDraftGenerationConstants.GENERATION_INTERRUPTED_MESSAGE);
    assertThat(repository.completedAt).isEqualTo(Instant.parse("2026-08-20T08:00:00Z"));
    assertThat(publishedDraftIds).containsExactly(102L, 103L);
  }

  @Test
  void doesNothingWhenStartupRecoveryIsDisabled() throws Exception {
    RecordingRepository repository = new RecordingRepository(List.of(102L));
    LearningPlanGovernanceProperties.GenerationRecovery properties =
        new LearningPlanGovernanceProperties.GenerationRecovery();
    properties.setEnabled(false);
    LearningPlanDraftGenerationStartupRecovery recovery = new LearningPlanDraftGenerationStartupRecovery(
        repository,
        (draftId, event) -> { throw new AssertionError("event must not be published"); },
        Clock.systemUTC(),
        properties);

    recovery.run(null);

    assertThat(repository.calls).isZero();
  }

  private static final class RecordingRepository implements LearningPlanDraftRepository {

    private final List<Long> recoveredDraftIds;
    private int calls;
    private Instant startedBefore;
    private String code;
    private String message;
    private Instant completedAt;

    private RecordingRepository(List<Long> recoveredDraftIds) {
      this.recoveredDraftIds = recoveredDraftIds;
    }

    @Override
    public List<Long> failInterruptedGenerations(
        Instant startedBefore,
        String code,
        String message,
        Instant completedAt
    ) {
      calls++;
      this.startedBefore = startedBefore;
      this.code = code;
      this.message = message;
      this.completedAt = completedAt;
      return recoveredDraftIds;
    }

    @Override
    public LearningPlanDraft save(LearningPlanDraft draft) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<LearningPlanDraft> findDraftByIdForUser(long draftId, long userId) {
      return Optional.empty();
    }
  }
}
