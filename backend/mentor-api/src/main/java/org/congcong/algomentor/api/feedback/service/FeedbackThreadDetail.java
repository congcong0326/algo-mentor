package org.congcong.algomentor.api.feedback.service;

import java.util.List;
import org.congcong.algomentor.api.feedback.model.FeedbackMessage;
import org.congcong.algomentor.api.feedback.model.FeedbackThread;

public record FeedbackThreadDetail(FeedbackThread thread, long unreadMessageCount, List<FeedbackMessage> messages) {
  public FeedbackThreadDetail { messages = messages == null ? List.of() : List.copyOf(messages); }
}
