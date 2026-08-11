package org.congcong.algomentor.mentor.application.learningplan.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.junit.jupiter.api.Test;

class LearningPlanDraftCleanupServiceTest {

  @Test
  void deletesExpiredDraftsAndOldDailyUsageInBoundedBatches() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC);
    RecordingRepository repository = new RecordingRepository();
    LearningPlanDraftCleanupService service = new LearningPlanDraftCleanupService(
        repository, clock, ZoneId.of("UTC"));

    LearningPlanDraftCleanupResult result = service.cleanupOnce(250, 30);

    assertThat(repository.expiredBefore).isEqualTo(Instant.parse("2026-08-11T00:00:00Z"));
    assertThat(repository.usageBefore).isEqualTo(LocalDate.parse("2026-07-12"));
    assertThat(repository.limit).isEqualTo(250);
    assertThat(result.deletedDrafts()).isEqualTo(3);
    assertThat(result.deletedDailyUsageRows()).isEqualTo(4);
  }

  private static final class RecordingRepository implements LearningPlanDraftRepository {

    private Instant expiredBefore;
    private LocalDate usageBefore;
    private int limit;

    @Override
    public LearningPlanDraft save(LearningPlanDraft draft) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<LearningPlanDraft> findDraftByIdForUser(long draftId, long userId) {
      return Optional.empty();
    }

    @Override
    public int deleteExpiredDrafts(Instant expiredBefore, int limit) {
      this.expiredBefore = expiredBefore;
      this.limit = limit;
      return 3;
    }

    @Override
    public int deleteDailyDraftUsageBefore(LocalDate quotaDate, int limit) {
      this.usageBefore = quotaDate;
      this.limit = limit;
      return 4;
    }
  }
}
