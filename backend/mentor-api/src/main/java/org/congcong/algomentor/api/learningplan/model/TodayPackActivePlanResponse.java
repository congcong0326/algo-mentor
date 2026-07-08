package org.congcong.algomentor.api.learningplan.model;

import java.time.Instant;

public record TodayPackActivePlanResponse(
    long planId,
    String title,
    Instant activatedAt,
    int dailyProblemCount,
    int trainingDaysPerWeek,
    int remainingProblemCount
) {
}
