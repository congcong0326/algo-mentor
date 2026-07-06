package org.congcong.algomentor.api.review.model;

import java.time.Instant;
import java.util.List;

public record RecallReviewResponse(
    String suggestedRating,
    List<String> hitPoints,
    List<String> missedPoints,
    String gapSummary,
    Instant nextDueAt,
    int intervalDays,
    int repetitions
) {
}
