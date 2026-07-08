package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Instant;

public record TodayPackActivePlan(
    long planId,
    String title,
    Instant activatedAt,
    int dailyProblemCount,
    int trainingDaysPerWeek,
    int remainingProblemCount
) {
}
