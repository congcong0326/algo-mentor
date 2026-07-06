package org.congcong.algomentor.mentor.application.review;

import java.math.BigDecimal;

public record ReviewPreferenceUpdate(
    BigDecimal desiredRetention,
    Integer dailyNewLimit,
    Integer dailyLearningLimit,
    Integer dailyReviewLimit,
    Boolean aiSuggestionEnabled
) {
}
