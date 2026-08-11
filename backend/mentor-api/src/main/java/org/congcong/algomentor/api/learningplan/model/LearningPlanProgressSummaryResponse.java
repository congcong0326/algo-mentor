package org.congcong.algomentor.api.learningplan.model;

public record LearningPlanProgressSummaryResponse(
    int totalProblemCount,
    int completedProblemCount,
    double progressPercent
) {
}
