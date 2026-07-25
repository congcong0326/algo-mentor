package org.congcong.algomentor.api.review.model;

import java.math.BigDecimal;

public record ReviewPreferenceResponse(
    BigDecimal desiredRetention,
    int dailyNewLimit,
    int dailyLearningLimit,
    int dailyReviewLimit,
    int maximumIntervalDays,
    boolean enableFuzzing
) {
}
