package org.congcong.algomentor.mentor.application.review.attempt;

import java.time.Instant;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;

public record ProblemReviewAttempt(
    long id,
    long reviewCardId,
    long userId,
    UUID clientAttemptId,
    ReviewRating rating,
    ReviewSchedulingSnapshot schedulingBefore,
    ReviewSchedulingSnapshot schedulingAfter,
    Instant reviewedAt
) {
}
