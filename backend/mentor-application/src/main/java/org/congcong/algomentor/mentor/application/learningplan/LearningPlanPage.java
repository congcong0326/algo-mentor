package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record LearningPlanPage(
    List<LearningPlan> items,
    long total,
    int page,
    int pageSize,
    long activeCount,
    long archivedCount,
    Instant latestCreatedAt,
    Map<Long, LearningPlanProgressSummary> progressSummaries) {

  public LearningPlanPage(
      List<LearningPlan> items,
      long total,
      int page,
      int pageSize,
      long activeCount,
      long archivedCount,
      Instant latestCreatedAt
  ) {
    this(items, total, page, pageSize, activeCount, archivedCount, latestCreatedAt, Map.of());
  }

  public LearningPlanPage {
    items = List.copyOf(items);
    progressSummaries = progressSummaries == null ? Map.of() : Map.copyOf(progressSummaries);
  }
}
