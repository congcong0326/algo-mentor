package org.congcong.algomentor.mentor.application.learningplan;

public record LearningPlanRhythmOption(
    LearningPlanRhythmMode mode,
    int durationWeeks,
    int weeklyHours,
    int trainingDaysPerWeekMin,
    int trainingDaysPerWeekMax,
    int dailyProblemCountMin,
    int dailyProblemCountMax,
    LearningPlanCoveragePolicy coveragePolicy,
    LearningPlanLoadSummary loadSummary
) {
}
