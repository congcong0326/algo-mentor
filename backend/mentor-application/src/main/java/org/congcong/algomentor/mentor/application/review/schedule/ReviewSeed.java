package org.congcong.algomentor.mentor.application.review.schedule;

import java.time.Instant;

public record ReviewSeed(
    SchedulingState state,
    Instant dueAt,
    Instant reviewedAt,
    ReviewSeedBucket bucket,
    boolean lowConfidence,
    ReviewRating initialRating
) {
}
