package org.congcong.algomentor.api.review.mapper.model;

import java.math.BigDecimal;
import java.time.Instant;

public record ReviewPreferenceRow(
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
}
