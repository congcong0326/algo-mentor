package org.congcong.algomentor.api.feedback.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.api.feedback.model.FeedbackMessage;
import org.congcong.algomentor.api.feedback.model.FeedbackSenderType;

public record FeedbackMessageRow(long id, long threadId, FeedbackSenderType senderType, long senderUserId,
    String content, Instant readAt, Instant createdAt) {
  public FeedbackMessage toDomain() {
    return new FeedbackMessage(id, threadId, senderType, senderUserId, content, readAt, createdAt);
  }
}
