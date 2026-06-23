package org.congcong.algomentor.api.learningplan.model;

import java.time.Instant;
import java.util.List;

public record LearningPlanPageResponse(
    List<LearningPlanSummaryResponse> items,
    long total,
    int page,
    int pageSize,
    long activeCount,
    long archivedCount,
    Instant latestCreatedAt) {
}
