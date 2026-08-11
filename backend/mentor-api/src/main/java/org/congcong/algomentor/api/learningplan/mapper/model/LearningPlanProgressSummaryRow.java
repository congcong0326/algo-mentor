package org.congcong.algomentor.api.learningplan.mapper.model;

public record LearningPlanProgressSummaryRow(
    Long planId,
    Integer totalProblemCount,
    Integer completedProblemCount
) {
}
