package org.congcong.algomentor.api.review.model;

import java.time.Instant;
import java.util.List;

public record RecallEvaluationResponse(
    long evaluationId,
    String suggestedRating,
    List<String> hitPoints,
    List<String> missedPoints,
    String gapSummary,
    boolean aiSuggested,
    Instant createdAt,
    List<ReviewIntervalPreviewResponse> intervals
) {
}
