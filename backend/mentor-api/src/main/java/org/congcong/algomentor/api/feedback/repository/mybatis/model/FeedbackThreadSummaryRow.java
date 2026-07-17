package org.congcong.algomentor.api.feedback.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.api.feedback.model.FeedbackCategory;
import org.congcong.algomentor.api.feedback.model.FeedbackSenderType;
import org.congcong.algomentor.api.feedback.model.FeedbackStatus;
import org.congcong.algomentor.api.feedback.model.FeedbackThreadSummary;

public record FeedbackThreadSummaryRow(long id, long userId, FeedbackCategory category, FeedbackStatus status,
    String subject, FeedbackSenderType lastSenderType, long unreadMessageCount, String sourceRunId,
    Instant createdAt, Instant updatedAt, Instant closedAt) {
  public FeedbackThreadSummary toDomain() {
    return new FeedbackThreadSummary(id, userId, category, status, subject, lastSenderType, unreadMessageCount,
        sourceRunId, createdAt, updatedAt, closedAt);
  }
}
