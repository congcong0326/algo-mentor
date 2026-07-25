package org.congcong.algomentor.mentor.application.review.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.congcong.algomentor.mentor.application.review.card.ProblemReviewCard;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardSource;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreference;
import org.junit.jupiter.api.Test;

class FsrsReviewSchedulerServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-24T08:00:00Z");
  private final FsrsReviewSchedulerService service = new FsrsReviewSchedulerService(
      ReviewSchedulerProperties.defaults());

  @Test
  void previewsAllRatingsWithoutMutatingTheCard() {
    ProblemReviewCard card = card(SchedulingState.initial(), NOW, null);

    var again = service.preview(card, ReviewRating.AGAIN, preference(), NOW);
    var hard = service.preview(card, ReviewRating.HARD, preference(), NOW);
    var good = service.preview(card, ReviewRating.GOOD, preference(), NOW);
    var easy = service.preview(card, ReviewRating.EASY, preference(), NOW);

    assertThat(again.dueAt()).isAfter(NOW);
    assertThat(hard.dueAt()).isAfter(again.dueAt());
    assertThat(good.dueAt()).isAfterOrEqualTo(hard.dueAt());
    assertThat(easy.dueAt()).isAfter(good.dueAt());
    assertThat(card.scheduling()).isEqualTo(SchedulingState.initial());
  }

  @Test
  void againOnAReviewedCardIncrementsLapsesAndResetsRepetitions() {
    SchedulingState reviewed = new SchedulingState(
        4,
        12,
        1,
        FsrsState.REVIEW,
        null,
        new BigDecimal("8.000000"),
        new BigDecimal("5.000000"));
    ProblemReviewCard card = card(reviewed, NOW, NOW.minusSeconds(12L * 24 * 60 * 60));

    var scheduled = service.apply(card, ReviewRating.AGAIN, preference(), NOW);

    assertThat(scheduled.state().repetitions()).isZero();
    assertThat(scheduled.state().lapses()).isEqualTo(2);
    assertThat(scheduled.dueAt()).isAfter(NOW);
  }

  private ReviewPreference preference() {
    return new ReviewPreference(
        42L,
        new BigDecimal("0.90"),
        10,
        50,
        30,
        36500,
        false,
        NOW,
        NOW);
  }

  private ProblemReviewCard card(SchedulingState state, Instant dueAt, Instant lastReviewedAt) {
    return new ProblemReviewCard(
        88L,
        42L,
        "two-sum",
        ReviewCardSource.REVIEW_FAILED,
        Map.of(),
        state,
        dueAt,
        lastReviewedAt,
        null,
        false,
        NOW,
        NOW);
  }
}
