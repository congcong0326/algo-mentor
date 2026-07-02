package org.congcong.algomentor.mentor.application.review;

import java.util.List;

public record ReviewCardDetail(
    ReviewCard card,
    String userNotePersistent,
    List<ReviewRecallHistoryItem> recentRecallHistory
) {
  public ReviewCardDetail {
    recentRecallHistory = recentRecallHistory == null ? List.of() : List.copyOf(recentRecallHistory);
  }
}
