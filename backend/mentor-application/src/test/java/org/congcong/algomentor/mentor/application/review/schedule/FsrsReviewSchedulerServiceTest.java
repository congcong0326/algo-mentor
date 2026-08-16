package org.congcong.algomentor.mentor.application.review.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.State;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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

  @Test
  void goodOnANewLearningCardStaysInTheLearningSteps() {
    ProblemReviewCard card = card(SchedulingState.initial(), NOW, null);

    var preview = service.preview(card, ReviewRating.GOOD, preference(), NOW);

    assertThat(Duration.between(NOW, preview.dueAt())).isEqualTo(Duration.ofMinutes(10));
    assertThat(preview.intervalDays()).isZero();
  }

  @Test
  void followsTheFixedAnkiRatingsForTheFirstLearningStep() {
    ProblemReviewCard card = card(SchedulingState.initial(), NOW, null);

    assertLearning(service.apply(card, ReviewRating.AGAIN, preference(), NOW), 0, Duration.ofMinutes(1));
    assertLearning(service.apply(card, ReviewRating.HARD, preference(), NOW), 0, Duration.ofSeconds(330));
    assertLearning(service.apply(card, ReviewRating.GOOD, preference(), NOW), 1, Duration.ofMinutes(10));

    var easy = service.apply(card, ReviewRating.EASY, preference(), NOW, ZoneId.of("Asia/Shanghai"));
    assertThat(easy.state().fsrsState()).isEqualTo(FsrsState.REVIEW);
    assertThat(easy.state().fsrsStep()).isNull();
    assertThat(easy.state().intervalDays()).isEqualTo(4);
    assertThat(easy.dueAt()).isEqualTo(Instant.parse("2026-07-27T16:00:00Z"));
    assertThat(easy.state().fsrsStability()).isNotNull();
    assertThat(easy.state().fsrsDifficulty()).isNotNull();
  }

  @Test
  void followsTheFixedAnkiRatingsForTheSecondLearningStep() {
    SchedulingState secondStep = new SchedulingState(1, 0, 0, FsrsState.LEARNING, 1, null, null);
    ProblemReviewCard card = card(secondStep, NOW, NOW.minus(Duration.ofMinutes(10)));

    assertLearning(service.apply(card, ReviewRating.AGAIN, preference(), NOW), 0, Duration.ofMinutes(1));
    assertLearning(service.apply(card, ReviewRating.HARD, preference(), NOW), 1, Duration.ofMinutes(10));

    var good = service.apply(card, ReviewRating.GOOD, preference(), NOW, ZoneId.of("Asia/Shanghai"));
    assertThat(good.state().fsrsState()).isEqualTo(FsrsState.REVIEW);
    assertThat(good.state().intervalDays()).isEqualTo(1);
    assertThat(good.dueAt()).isEqualTo(Instant.parse("2026-07-24T16:00:00Z"));

    var easy = service.apply(card, ReviewRating.EASY, preference(), NOW, ZoneId.of("Asia/Shanghai"));
    assertThat(easy.state().fsrsState()).isEqualTo(FsrsState.REVIEW);
    assertThat(easy.state().intervalDays()).isEqualTo(4);
  }

  @Test
  void previewAndApplyUseTheSameCalibratedGraduationResult() {
    ProblemReviewCard card = card(SchedulingState.initial(), NOW, null);
    ZoneId userZone = ZoneId.of("America/Los_Angeles");

    var preview = service.preview(card, ReviewRating.EASY, preference(), NOW, userZone);
    var applied = service.apply(card, ReviewRating.EASY, preference(), NOW, userZone);

    assertThat(preview.dueAt()).isEqualTo(applied.dueAt());
    assertThat(preview.intervalDays()).isEqualTo(applied.state().intervalDays());
    assertThat(applied.state().fsrsState()).isEqualTo(FsrsState.REVIEW);
    assertThat(applied.state().fsrsStability()).isNotNull();
    assertThat(applied.state().fsrsDifficulty()).isNotNull();
  }

  @Test
  void calibratesGraduationStabilityForTheEffectiveDesiredRetention() {
    ReviewSchedulerProperties properties = new ReviewSchedulerProperties(
        new BigDecimal("0.90"),
        20,
        new BigDecimal("0.80"),
        null,
        null,
        36500,
        false,
        1,
        4,
        1,
        3,
        4,
        10,
        50,
        20);
    FsrsReviewSchedulerService retentionService = new FsrsReviewSchedulerService(properties);
    ReviewPreference preference = new ReviewPreference(
        42L,
        new BigDecimal("0.80"),
        10,
        50,
        30,
        36500,
        false,
        NOW,
        NOW);
    var scheduled = retentionService.apply(
        card(SchedulingState.initial(), NOW, null),
        ReviewRating.EASY,
        preference,
        NOW,
        ZoneId.of("UTC"));
    Scheduler scheduler = Scheduler.builder().desiredRetention(0.80d).enableFuzzing(false).build();
    Card memory = Card.builder()
        .cardId(1)
        .state(State.REVIEW)
        .stability(scheduled.state().fsrsStability().doubleValue())
        .difficulty(scheduled.state().fsrsDifficulty().doubleValue())
        .due(NOW)
        .lastReview(NOW)
        .build();

    assertThat(scheduler.getCardRetrievability(memory, NOW.plus(Duration.ofDays(4))))
        .isCloseTo(0.80d, offset(0.000001d));
  }

  @Test
  void matureAgainUsesFsrsRelearningWithoutDiscardingMemory() {
    SchedulingState reviewed = new SchedulingState(
        4,
        12,
        1,
        FsrsState.REVIEW,
        null,
        new BigDecimal("8.000000"),
        new BigDecimal("5.000000"));

    var scheduled = service.apply(card(reviewed, NOW, NOW.minus(Duration.ofDays(12))), ReviewRating.AGAIN, preference(), NOW);

    assertThat(scheduled.state().fsrsState()).isEqualTo(FsrsState.RELEARNING);
    assertThat(Duration.between(NOW, scheduled.dueAt())).isEqualTo(Duration.ofMinutes(10));
    assertThat(scheduled.state().fsrsStability()).isNotNull();
    assertThat(scheduled.state().fsrsDifficulty()).isNotNull();
  }

  private void assertLearning(
      FsrsReviewSchedulerService.Scheduled scheduled,
      int step,
      Duration expectedDelay
  ) {
    assertThat(scheduled.state().fsrsState()).isEqualTo(FsrsState.LEARNING);
    assertThat(scheduled.state().fsrsStep()).isEqualTo(step);
    assertThat(Duration.between(NOW, scheduled.dueAt())).isEqualTo(expectedDelay);
    assertThat(scheduled.state().intervalDays()).isZero();
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
