package org.congcong.algomentor.mentor.application.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FsrsReviewSchedulerServiceTest {

  private final Instant now = Instant.parse("2026-07-02T00:00:00Z");
  private final FsrsReviewSchedulerService scheduler = new FsrsReviewSchedulerService(ReviewSchedulerProperties.defaults());

  @Test
  void schedulesAllAnkiRatingsWithFsrsState() {
    MistakeNote note = note(new SchedulingState(0, 0, 0, "LEARNING", 0, null, null), now, null);

    assertSchedule(note, ReviewRating.AGAIN);
    assertSchedule(note, ReviewRating.HARD);
    assertSchedule(note, ReviewRating.GOOD);
    assertSchedule(note, ReviewRating.EASY);
  }

  @Test
  void againOnReviewedCardResetsRepetitionsAndCountsLapse() {
    MistakeNote note = note(
        new SchedulingState(2, 3, 1, "REVIEW", 0, null, null),
        Instant.parse("2026-07-05T00:00:00Z"),
        now.minusSeconds(86_400));

    FsrsReviewSchedulerService.Scheduled scheduled = scheduler.apply(
        note,
        ReviewRating.AGAIN,
        ReviewPreference.defaults(7L, ReviewSchedulerProperties.defaults()),
        now);

    assertThat(scheduled.rating()).isEqualTo(ReviewRating.AGAIN);
    assertThat(scheduled.state().repetitions()).isZero();
    assertThat(scheduled.state().lapses()).isEqualTo(2);
    assertThat(scheduled.state().fsrsState()).isIn("LEARNING", "RELEARNING", "REVIEW");
    assertThat(scheduled.dueAt()).isAfter(now);
  }

  private void assertSchedule(MistakeNote note, ReviewRating rating) {
    FsrsReviewSchedulerService.Scheduled scheduled = scheduler.apply(
        note,
        rating,
        ReviewPreference.defaults(7L, ReviewSchedulerProperties.defaults()),
        now);

    assertThat(scheduled.rating()).isEqualTo(rating);
    assertThat(scheduled.state().fsrsState()).isIn("LEARNING", "RELEARNING", "REVIEW");
    assertThat(scheduled.dueAt()).isAfterOrEqualTo(now);
    assertThat(scheduled.state().intervalDays()).isGreaterThanOrEqualTo(0);
  }

  private MistakeNote note(SchedulingState state, Instant dueAt, Instant lastReviewedAt) {
    return new MistakeNote(
        1L,
        7L,
        "two-sum",
        MistakeSource.REVIEW_FAILED,
        Map.of(),
        null,
        null,
        null,
        state,
        dueAt,
        lastReviewedAt,
        null,
        false,
        "",
        null,
        now,
        now);
  }
}
