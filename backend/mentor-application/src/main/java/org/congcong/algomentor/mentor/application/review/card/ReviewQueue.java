package org.congcong.algomentor.mentor.application.review.card;

import java.util.List;

public record ReviewQueue(List<ProblemReviewCard> items, int dueCount) {
  public ReviewQueue {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
