package org.congcong.algomentor.api.feedback.metrics;

import org.congcong.algomentor.api.feedback.model.FeedbackCategory;
import org.congcong.algomentor.api.feedback.model.FeedbackSenderType;
import org.congcong.algomentor.api.feedback.model.FeedbackStatus;

public class NoopFeedbackMetrics implements FeedbackMetrics {
  @Override public void threadCreated(FeedbackCategory category) { }
  @Override public void messageSent(FeedbackSenderType senderType) { }
  @Override public void statusChanged(FeedbackStatus status) { }
  @Override public void messagesRead(FeedbackSenderType viewerType, int count) { }
}
