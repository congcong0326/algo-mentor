package org.congcong.algomentor.api.learningplan.model;

public record TodayPackHomeActivePlanResponse(
    long planId,
    String title,
    int dailyProblemCount,
    int trainingDaysPerWeek,
    int remainingProblemCount
) {
}
