package org.congcong.algomentor.mentor.application.review;

import java.time.Instant;

public record RecallConfirmResult(
    ReviewRating rating,
    ReviewRating suggestedRating,
    Instant nextDueAt,
    SchedulingState scheduling,
    boolean aiSuggested
) {
}
