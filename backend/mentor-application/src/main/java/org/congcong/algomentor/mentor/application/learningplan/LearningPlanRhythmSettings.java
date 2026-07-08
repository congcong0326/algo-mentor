package org.congcong.algomentor.mentor.application.learningplan;

public record LearningPlanRhythmSettings(
    int dailyProblemCount,
    int trainingDaysPerWeek,
    int totalProblemCount,
    int completedProblemCount,
    int skippedProblemCount,
    int remainingProblemCount,
    int estimatedRemainingWeeks
) {
}
