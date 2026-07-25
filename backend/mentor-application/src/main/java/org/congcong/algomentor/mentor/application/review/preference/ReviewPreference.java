package org.congcong.algomentor.mentor.application.review.preference;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSchedulerProperties;

public record ReviewPreference(
    long userId,
    BigDecimal desiredRetention,
    int dailyNewLimit,
    int dailyLearningLimit,
    int dailyReviewLimit,
    int maximumIntervalDays,
    boolean enableFuzzing,
    Instant createdAt,
    Instant updatedAt
) {
  public ReviewPreference {
    if (desiredRetention == null
        || desiredRetention.compareTo(BigDecimal.ZERO) <= 0
        || desiredRetention.compareTo(BigDecimal.ONE) >= 0) {
      desiredRetention = new BigDecimal("0.90");
    }
    dailyNewLimit = Math.max(0, dailyNewLimit);
    dailyLearningLimit = dailyLearningLimit <= 0 ? 50 : dailyLearningLimit;
    dailyReviewLimit = dailyReviewLimit <= 0 ? 30 : dailyReviewLimit;
    maximumIntervalDays = maximumIntervalDays <= 0 ? 36500 : maximumIntervalDays;
  }

  public Duration[] learningSteps() {
    return new Duration[] {Duration.ofMinutes(1), Duration.ofMinutes(10)};
  }

  public Duration[] relearningSteps() {
    return new Duration[] {Duration.ofMinutes(10)};
  }

  public static ReviewPreference defaults(long userId, ReviewSchedulerProperties properties, Instant now) {
    return new ReviewPreference(
        userId,
        properties.desiredRetention(),
        properties.dailyNewLimit(),
        properties.dailyLearningLimit(),
        properties.dailyReviewLimit(),
        properties.maximumIntervalDays(),
        properties.enableFuzzing(),
        now,
        now);
  }
}
