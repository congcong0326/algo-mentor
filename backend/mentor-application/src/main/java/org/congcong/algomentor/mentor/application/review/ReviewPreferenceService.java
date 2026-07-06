package org.congcong.algomentor.mentor.application.review;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class ReviewPreferenceService {

  private final ReviewPreferenceRepository repository;
  private final ReviewSchedulerProperties defaults;
  private final Clock clock;

  public ReviewPreferenceService(ReviewPreferenceRepository repository, ReviewSchedulerProperties defaults, Clock clock) {
    this.repository = repository == null ? ReviewPreferenceRepository.empty() : repository;
    this.defaults = Objects.requireNonNull(defaults, "defaults must not be null");
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public ReviewPreference get(long userId) {
    return repository.findByUserId(userId).orElseGet(() -> defaultPreference(userId));
  }

  public ReviewPreference update(long userId, ReviewPreferenceUpdate update) {
    ReviewPreference current = get(userId);
    Instant now = Instant.now(clock);
    return repository.upsert(new ReviewPreference(
        userId,
        desiredRetention(update == null ? null : update.desiredRetention(), current.desiredRetention()),
        nonNegative(update == null ? null : update.dailyNewLimit(), current.dailyNewLimit()),
        positive(update == null ? null : update.dailyLearningLimit(), current.dailyLearningLimit()),
        positive(update == null ? null : update.dailyReviewLimit(), current.dailyReviewLimit()),
        update == null || update.aiSuggestionEnabled() == null
            ? current.aiSuggestionEnabled()
            : update.aiSuggestionEnabled(),
        current.maximumIntervalDays(),
        current.enableFuzzing(),
        current.createdAt() == null ? now : current.createdAt(),
        now));
  }

  private ReviewPreference defaultPreference(long userId) {
    Instant now = Instant.now(clock);
    return new ReviewPreference(
        userId,
        defaults.desiredRetention(),
        defaults.dailyNewLimit(),
        defaults.dailyLearningLimit(),
        defaults.dailyReviewLimit(),
        true,
        defaults.maximumIntervalDays(),
        defaults.enableFuzzing(),
        now,
        now);
  }

  private BigDecimal desiredRetention(BigDecimal value, BigDecimal fallback) {
    return value == null || value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(BigDecimal.ONE) >= 0
        ? fallback
        : value;
  }

  private int positive(Integer value, int fallback) {
    return value == null || value <= 0 ? fallback : value;
  }

  private int nonNegative(Integer value, int fallback) {
    return value == null || value < 0 ? fallback : value;
  }
}
