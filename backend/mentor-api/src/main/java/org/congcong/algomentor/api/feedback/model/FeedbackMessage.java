package org.congcong.algomentor.api.feedback.model;

import java.time.Instant;

public record FeedbackMessage(
    long id,
    long threadId,
    FeedbackSenderType senderType,
    long senderUserId,
    String content,
    Instant readAt,
    Instant createdAt
) {
}
