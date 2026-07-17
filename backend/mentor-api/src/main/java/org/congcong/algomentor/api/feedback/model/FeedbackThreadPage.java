package org.congcong.algomentor.api.feedback.model;

import java.util.List;

public record FeedbackThreadPage(
    List<FeedbackThreadSummary> items,
    long total,
    int page,
    int pageSize,
    long unreadMessageCount
) {
  public FeedbackThreadPage {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
