package org.congcong.algomentor.api.feedback.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.congcong.algomentor.api.feedback.model.FeedbackCategory;
import org.congcong.algomentor.api.feedback.model.FeedbackSenderType;
import org.congcong.algomentor.api.feedback.model.FeedbackStatus;

public class MicrometerFeedbackMetrics implements FeedbackMetrics {
  private final MeterRegistry registry;
  public MicrometerFeedbackMetrics(MeterRegistry registry) { this.registry = registry; }
  @Override public void threadCreated(FeedbackCategory category) { registry.counter("feedback.thread.created", "category", category.name()).increment(); }
  @Override public void messageSent(FeedbackSenderType senderType) { registry.counter("feedback.message.sent", "senderType", senderType.name()).increment(); }
  @Override public void statusChanged(FeedbackStatus status) { registry.counter("feedback.thread.status.changed", "status", status.name()).increment(); }
  @Override public void messagesRead(FeedbackSenderType viewerType, int count) { if (count > 0) registry.counter("feedback.message.read", "viewerType", viewerType.name()).increment(count); }
}
