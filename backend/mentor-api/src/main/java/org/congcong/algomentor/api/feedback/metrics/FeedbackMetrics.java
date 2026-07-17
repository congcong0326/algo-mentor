package org.congcong.algomentor.api.feedback.metrics;

import org.congcong.algomentor.api.feedback.model.FeedbackCategory;
import org.congcong.algomentor.api.feedback.model.FeedbackSenderType;
import org.congcong.algomentor.api.feedback.model.FeedbackStatus;

public interface FeedbackMetrics {
  void threadCreated(FeedbackCategory category);
  void messageSent(FeedbackSenderType senderType);
  void statusChanged(FeedbackStatus status);
  void messagesRead(FeedbackSenderType viewerType, int count);
}
