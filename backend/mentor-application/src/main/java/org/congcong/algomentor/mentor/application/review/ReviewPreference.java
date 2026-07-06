package org.congcong.algomentor.mentor.application.review;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public record ReviewPreference(
    long userId,
    BigDecimal desiredRetention,
    int dailyNewLimit,
    int dailyLearningLimit,
    int dailyReviewLimit,
    boolean aiSuggestionEnabled,
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
    if (dailyNewLimit < 0) {
      dailyNewLimit = 10;
    }
    if (dailyLearningLimit <= 0) {
      dailyLearningLimit = 50;
    }
    if (dailyReviewLimit <= 0) {
      dailyReviewLimit = 30;
    }
    if (maximumIntervalDays <= 0) {
      maximumIntervalDays = 36500;
    }
  }

  public Duration[] learningSteps() {
    return new Duration[] {Duration.ofMinutes(1), Duration.ofMinutes(10)};
  }

  public Duration[] relearningSteps() {
    return new Duration[] {Duration.ofMinutes(10)};
  }

  public static ReviewPreference defaults(long userId, ReviewSchedulerProperties properties) {
    Instant now = Instant.now();
    return new ReviewPreference(
        userId,
        properties.desiredRetention(),
        properties.dailyNewLimit(),
        properties.dailyLearningLimit(),
        properties.dailyReviewLimit(),
        true,
        properties.maximumIntervalDays(),
        properties.enableFuzzing(),
        now,
        now);
  }
}
