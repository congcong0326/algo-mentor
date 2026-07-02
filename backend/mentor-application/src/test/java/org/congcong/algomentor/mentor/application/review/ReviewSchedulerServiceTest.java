package org.congcong.algomentor.mentor.application.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReviewSchedulerServiceTest {

  private final ReviewSchedulerService scheduler = new ReviewSchedulerService();
  private final Instant now = Instant.parse("2026-07-02T00:00:00Z");

  @Test
  void firstFluentReviewKeepsOneDayInterval() {
    ReviewSchedulerService.Scheduled scheduled = scheduler.apply(newState(), ReviewGrade.FLUENT, now);

    assertThat(scheduled.state().repetitions()).isEqualTo(1);
    assertThat(scheduled.state().intervalDays()).isEqualTo(1);
    assertThat(scheduled.state().masteryState()).isEqualTo(MasteryState.LEARNING);
    assertThat(scheduled.dueAt()).isEqualTo(Instant.parse("2026-07-03T00:00:00Z"));
  }

  @Test
  void masteredReviewsGrowWithSm2EaseFactor() {
    SchedulingState state = newState();
    ReviewSchedulerService.Scheduled first = scheduler.apply(state, ReviewGrade.MASTERED, now);
    ReviewSchedulerService.Scheduled second = scheduler.apply(first.state(), ReviewGrade.MASTERED, now);
    ReviewSchedulerService.Scheduled third = scheduler.apply(second.state(), ReviewGrade.MASTERED, now);
    ReviewSchedulerService.Scheduled fourth = scheduler.apply(third.state(), ReviewGrade.MASTERED, now);

    assertThat(first.state().intervalDays()).isEqualTo(1);
    assertThat(second.state().intervalDays()).isEqualTo(3);
    assertThat(third.state().intervalDays()).isEqualTo(8);
    assertThat(fourth.state().intervalDays()).isEqualTo(20);
    assertThat(fourth.state().repetitions()).isEqualTo(4);
    assertThat(fourth.state().easeFactor()).isEqualByComparingTo("2.50");
  }

  @Test
  void forgotResetsLearningStateAndAddsLapse() {
    SchedulingState learning = new SchedulingState(2, new BigDecimal("2.50"), 3, MasteryState.LEARNING, 1);

    ReviewSchedulerService.Scheduled scheduled = scheduler.apply(learning, ReviewGrade.FORGOT, now);

    assertThat(scheduled.state().repetitions()).isZero();
    assertThat(scheduled.state().intervalDays()).isEqualTo(1);
    assertThat(scheduled.state().masteryState()).isEqualTo(MasteryState.LAPSED);
    assertThat(scheduled.state().lapses()).isEqualTo(2);
  }

  @Test
  void easeFactorDoesNotGoBelowMinimum() {
    SchedulingState state = new SchedulingState(0, new BigDecimal("1.35"), 0, MasteryState.NEW, 0);

    for (int i = 0; i < 5; i++) {
      state = scheduler.apply(state, ReviewGrade.FORGOT, now).state();
    }

    assertThat(state.easeFactor()).isEqualByComparingTo("1.30");
  }

  private SchedulingState newState() {
    return new SchedulingState(0, new BigDecimal("2.50"), 0, MasteryState.NEW, 0);
  }
}
