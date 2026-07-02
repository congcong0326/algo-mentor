package org.congcong.algomentor.mentor.application.review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class ReviewSchedulerService {

  private static final BigDecimal MIN_EF = new BigDecimal("1.30");

  private final ReviewSchedulerProperties properties;

  public ReviewSchedulerService() {
    this(ReviewSchedulerProperties.defaults());
  }

  public ReviewSchedulerService(ReviewSchedulerProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  public Scheduled apply(SchedulingState state, ReviewGrade grade, Instant now) {
    Objects.requireNonNull(state, "state must not be null");
    Objects.requireNonNull(grade, "grade must not be null");
    Objects.requireNonNull(now, "now must not be null");
    int q = grade.q();
    int reps;
    int interval;
    int lapses = state.lapses();
    BigDecimal ef = updateEase(state.easeFactor(), q);
    MasteryState masteryState;
    if (q < ReviewGrade.BARELY.q()) {
      reps = 0;
      interval = 1;
      lapses = state.lapses() + 1;
      masteryState = MasteryState.LAPSED;
    } else {
      reps = state.repetitions() + 1;
      interval = switch (reps) {
        case 1 -> 1;
        case 2 -> 3;
        default -> Math.max(1, (int) Math.round(state.intervalDays() * ef.doubleValue()));
      };
      masteryState = interval >= properties.graduationIntervalDays()
          && reps >= properties.graduationRepetitions()
          ? MasteryState.MASTERED
          : MasteryState.LEARNING;
    }
    return new Scheduled(
        new SchedulingState(reps, ef, interval, masteryState, lapses),
        now.plus(Duration.ofDays(interval)));
  }

  private BigDecimal updateEase(BigDecimal easeFactor, int q) {
    BigDecimal effectiveEase = easeFactor == null ? new BigDecimal("2.50") : easeFactor;
    double delta = 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02);
    return effectiveEase.add(BigDecimal.valueOf(delta)).max(MIN_EF).setScale(2, RoundingMode.HALF_UP);
  }

  public record Scheduled(SchedulingState state, Instant dueAt) {
  }
}
