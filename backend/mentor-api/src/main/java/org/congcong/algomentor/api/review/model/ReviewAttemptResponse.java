package org.congcong.algomentor.api.review.model;

import java.time.Instant;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.review.attempt.ReviewSchedulingSnapshot;

public record ReviewAttemptResponse(
    long id,
    long reviewCardId,
    UUID clientAttemptId,
    String rating,
    ReviewSchedulingSnapshot schedulingBefore,
    ReviewSchedulingSnapshot schedulingAfter,
    Instant reviewedAt,
    boolean duplicate
) {
}
