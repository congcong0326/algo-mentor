package org.congcong.algomentor.api.learningplan.model;

public record LearningPlanRhythmUpdateRequest(
    Integer dailyProblemCount,
    Integer trainingDaysPerWeek
) {
}
