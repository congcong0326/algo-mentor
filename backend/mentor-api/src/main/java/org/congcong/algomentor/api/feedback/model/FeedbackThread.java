package org.congcong.algomentor.api.feedback.model;

import java.time.Instant;

public record FeedbackThread(
    long id,
    long userId,
    FeedbackCategory category,
    FeedbackStatus status,
    String subject,
    String sourcePath,
    String sourceRequestId,
    String sourceRunId,
    Instant createdAt,
    Instant updatedAt,
    Instant closedAt,
    Long closedBy
) {
}
