package org.congcong.algomentor.mentor.application.learningplan.cleanup;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;

/** 分批删除到期草案和超过保留窗口的每日草案计数。 */
public class LearningPlanDraftCleanupService {

  private final LearningPlanDraftRepository repository;
  private final Clock clock;
  private final ZoneId quotaZone;

  public LearningPlanDraftCleanupService(
      LearningPlanDraftRepository repository,
      Clock clock,
      ZoneId quotaZone
  ) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.clock = clock == null ? Clock.systemUTC() : clock;
    this.quotaZone = Objects.requireNonNull(quotaZone, "quotaZone must not be null");
  }

  public LearningPlanDraftCleanupResult cleanupOnce(int batchSize, int dailyUsageRetentionDays) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    if (dailyUsageRetentionDays < 1) {
      throw new IllegalArgumentException("dailyUsageRetentionDays must be positive");
    }
    Instant startedAt = clock.instant();
    int deletedDrafts = repository.deleteExpiredDrafts(startedAt, batchSize);
    LocalDate usageBefore = LocalDate.ofInstant(startedAt, quotaZone).minusDays(dailyUsageRetentionDays);
    int deletedUsageRows = repository.deleteDailyDraftUsageBefore(usageBefore, batchSize);
    return new LearningPlanDraftCleanupResult(
        deletedDrafts,
        deletedUsageRows,
        Duration.between(startedAt, clock.instant()));
  }
}
