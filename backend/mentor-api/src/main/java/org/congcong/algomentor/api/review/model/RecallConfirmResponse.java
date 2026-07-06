package org.congcong.algomentor.api.review.model;

import java.time.Instant;

public record RecallConfirmResponse(
    String rating,
    String suggestedRating,
    Instant nextDueAt,
    int intervalDays,
    int repetitions,
    boolean aiSuggested
) {
}
