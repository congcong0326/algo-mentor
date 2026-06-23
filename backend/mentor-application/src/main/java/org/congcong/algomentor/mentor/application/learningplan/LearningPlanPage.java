package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Instant;
import java.util.List;

public record LearningPlanPage(
    List<LearningPlan> items,
    long total,
    int page,
    int pageSize,
    long activeCount,
    long archivedCount,
    Instant latestCreatedAt) {

  public LearningPlanPage {
    items = List.copyOf(items);
  }
}
