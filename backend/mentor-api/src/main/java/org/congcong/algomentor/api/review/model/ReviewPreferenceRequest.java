package org.congcong.algomentor.api.review.model;

import java.math.BigDecimal;

public record ReviewPreferenceRequest(
    BigDecimal desiredRetention,
    Integer dailyNewLimit,
    Integer dailyLearningLimit,
    Integer dailyReviewLimit,
    Integer maximumIntervalDays,
    Boolean enableFuzzing
) {
}
