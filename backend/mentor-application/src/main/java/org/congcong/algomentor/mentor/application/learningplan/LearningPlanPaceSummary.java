package org.congcong.algomentor.mentor.application.learningplan;

public record LearningPlanPaceSummary(
    int currentWeek,
    int totalWeeks,
    LearningPlanWeeklyBucket currentBucket,
    int currentWeekCompletedProblemCount,
    int plannedProblemCountToDate,
    int completedProblemCountToDate,
    int skippedProblemCount,
    double plannedLoadPointsToDate,
    double completedLoadPoints,
    double loadGapPoints,
    LearningPlanPaceStatus status,
    String recommendation
) {
}
