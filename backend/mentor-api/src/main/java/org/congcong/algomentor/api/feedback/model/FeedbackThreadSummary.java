package org.congcong.algomentor.api.feedback.model;

import java.time.Instant;

public record FeedbackThreadSummary(
    long id,
    long userId,
    FeedbackCategory category,
    FeedbackStatus status,
    String subject,
    FeedbackSenderType lastSenderType,
    long unreadMessageCount,
    String sourceRunId,
    Instant createdAt,
    Instant updatedAt,
    Instant closedAt
) {
}
