package org.congcong.algomentor.mentor.application.review.attempt;

import java.math.BigDecimal;
import java.time.Instant;
import org.congcong.algomentor.mentor.application.review.card.ProblemReviewCard;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsReviewSchedulerService;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsState;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.congcong.algomentor.mentor.application.review.schedule.SchedulingState;

public record ReviewSchedulingSnapshot(
    int repetitions,
    int intervalDays,
    int lapses,
    FsrsState fsrsState,
    Integer fsrsStep,
    BigDecimal fsrsStability,
    BigDecimal fsrsDifficulty,
    Instant dueAt,
    Instant lastReviewedAt,
    ReviewRating lastRating
) {
  public static ReviewSchedulingSnapshot before(ProblemReviewCard card) {
    return from(card.scheduling(), card.dueAt(), card.lastReviewedAt(), card.lastRating());
  }

  public static ReviewSchedulingSnapshot after(
      FsrsReviewSchedulerService.Scheduled scheduled,
      Instant reviewedAt
  ) {
    return from(scheduled.state(), scheduled.dueAt(), reviewedAt, scheduled.rating());
  }

  private static ReviewSchedulingSnapshot from(
      SchedulingState state,
      Instant dueAt,
      Instant lastReviewedAt,
      ReviewRating lastRating
  ) {
    return new ReviewSchedulingSnapshot(
        state.repetitions(),
        state.intervalDays(),
        state.lapses(),
        state.fsrsState(),
        state.fsrsStep(),
        state.fsrsStability(),
        state.fsrsDifficulty(),
        dueAt,
        lastReviewedAt,
        lastRating);
  }
}
