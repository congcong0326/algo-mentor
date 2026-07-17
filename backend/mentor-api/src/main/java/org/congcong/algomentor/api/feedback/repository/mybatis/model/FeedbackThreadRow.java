package org.congcong.algomentor.api.feedback.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.api.feedback.model.FeedbackCategory;
import org.congcong.algomentor.api.feedback.model.FeedbackStatus;
import org.congcong.algomentor.api.feedback.model.FeedbackThread;

public record FeedbackThreadRow(long id, long userId, FeedbackCategory category, FeedbackStatus status, String subject,
    String sourcePath, String sourceRequestId, String sourceRunId, Instant createdAt, Instant updatedAt,
    Instant closedAt, Long closedBy) {
  public FeedbackThread toDomain() {
    return new FeedbackThread(id, userId, category, status, subject, sourcePath, sourceRequestId, sourceRunId,
        createdAt, updatedAt, closedAt, closedBy);
  }
}
