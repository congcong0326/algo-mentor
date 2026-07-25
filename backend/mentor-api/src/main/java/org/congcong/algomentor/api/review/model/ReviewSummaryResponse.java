package org.congcong.algomentor.api.review.model;

import java.time.Instant;

public record ReviewSummaryResponse(
    int dueCount,
    int remainingTodayCount,
    Instant nextDueAt
) {
}
