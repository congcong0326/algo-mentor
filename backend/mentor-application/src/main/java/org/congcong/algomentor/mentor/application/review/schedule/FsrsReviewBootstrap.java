package org.congcong.algomentor.mentor.application.review.schedule;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Scheduler;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 用指定的首个日级间隔初始化 FSRS REVIEW memory，避免到期日与 stability 不一致。
 */
public class FsrsReviewBootstrap {

  private final ReviewDayBoundary dayBoundary;

  public FsrsReviewBootstrap(ReviewDayBoundary dayBoundary) {
    this.dayBoundary = Objects.requireNonNull(dayBoundary, "dayBoundary must not be null");
  }

  public Bootstrap bootstrap(
      Scheduler scheduler,
      Card sourceCard,
      ReviewRating rating,
      int intervalDays,
      int repetitions,
      int lapses,
      Instant now,
      ZoneId userZone
  ) {
    Objects.requireNonNull(scheduler, "scheduler must not be null");
    Objects.requireNonNull(sourceCard, "sourceCard must not be null");
    Objects.requireNonNull(rating, "rating must not be null");
    Objects.requireNonNull(now, "now must not be null");
    Objects.requireNonNull(userZone, "userZone must not be null");
    if (intervalDays <= 0) {
      throw new IllegalArgumentException("intervalDays must be positive");
    }

    Card fsrsCard = scheduler.reviewCard(sourceCard, rating.fsrsRating(), now).card();
    Double difficulty = fsrsCard.getDifficulty();
    if (difficulty == null) {
      throw new IllegalStateException("FSRS bootstrap card did not contain difficulty");
    }
    SchedulingState state = new SchedulingState(
        repetitions,
        intervalDays,
        lapses,
        FsrsState.REVIEW,
        null,
        decimal(calibratedStability(scheduler, intervalDays)),
        decimal(difficulty));
    return new Bootstrap(state, dayBoundary.dueAt(now, intervalDays, userZone), fsrsCard.getLastReview());
  }

  public double calibratedStability(Scheduler scheduler, int intervalDays) {
    Objects.requireNonNull(scheduler, "scheduler must not be null");
    if (intervalDays <= 0) {
      throw new IllegalArgumentException("intervalDays must be positive");
    }
    double retentionFactor = Math.pow(
        scheduler.getDesiredRetention(),
        1.0d / scheduler.getDECAY()) - 1.0d;
    return Math.max(Scheduler.STABILITY_MIN, scheduler.getFACTOR() * intervalDays / retentionFactor);
  }

  private BigDecimal decimal(double value) {
    return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
  }

  public record Bootstrap(SchedulingState state, Instant dueAt, Instant reviewedAt) {
  }
}
