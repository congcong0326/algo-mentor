package org.congcong.algomentor.mentor.application.review;

import java.util.List;

public record ReviewQueue(List<MistakeNote> items, int dueCount) {
  public ReviewQueue {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
