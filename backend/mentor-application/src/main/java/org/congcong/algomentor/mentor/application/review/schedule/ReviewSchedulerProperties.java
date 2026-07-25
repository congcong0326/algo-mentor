package org.congcong.algomentor.mentor.application.review.schedule;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;

public record ReviewSchedulerProperties(
    BigDecimal highScoreRatio,
    int queueDailyCap,
    BigDecimal desiredRetention,
    Duration[] learningSteps,
    Duration[] relearningSteps,
    int maximumIntervalDays,
    boolean enableFuzzing,
    int dailyNewLimit,
    int dailyLearningLimit,
    int dailyReviewLimit
) {
  public ReviewSchedulerProperties {
    if (highScoreRatio == null || highScoreRatio.compareTo(BigDecimal.ZERO) <= 0) {
      highScoreRatio = new BigDecimal("0.90");
    }
    if (queueDailyCap <= 0) {
      queueDailyCap = 20;
    }
    if (desiredRetention == null
        || desiredRetention.compareTo(BigDecimal.ZERO) <= 0
        || desiredRetention.compareTo(BigDecimal.ONE) >= 0) {
      desiredRetention = new BigDecimal("0.90");
    }
    learningSteps = normalizeSteps(learningSteps, new Duration[] {Duration.ofMinutes(1), Duration.ofMinutes(10)});
    relearningSteps = normalizeSteps(relearningSteps, new Duration[] {Duration.ofMinutes(10)});
    if (maximumIntervalDays <= 0) {
      maximumIntervalDays = 36500;
    }
    if (dailyNewLimit < 0) {
      dailyNewLimit = 10;
    }
    if (dailyLearningLimit <= 0) {
      dailyLearningLimit = 50;
    }
    if (dailyReviewLimit <= 0) {
      dailyReviewLimit = queueDailyCap;
    }
  }

  public static ReviewSchedulerProperties defaults() {
    return new ReviewSchedulerProperties(
        new BigDecimal("0.90"),
        20,
        new BigDecimal("0.90"),
        null,
        null,
        36500,
        true,
        10,
        50,
        20);
  }

  private static Duration[] normalizeSteps(Duration[] steps, Duration[] defaults) {
    if (steps == null) {
      return defaults;
    }
    return Arrays.stream(steps)
        .filter(step -> step != null && !step.isNegative() && !step.isZero())
        .toArray(Duration[]::new);
  }
}
