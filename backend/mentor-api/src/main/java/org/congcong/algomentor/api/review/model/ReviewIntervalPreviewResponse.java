package org.congcong.algomentor.api.review.model;

import java.time.Instant;

public record ReviewIntervalPreviewResponse(
    String rating,
    Instant dueAt,
    int intervalDays
) {
}
