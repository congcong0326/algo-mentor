package org.congcong.algomentor.mentor.application.learningplan;

/**
 * 首页今日训练入口所需的激活计划摘要。
 */
public record TodayPackHomeActivePlan(
    long planId,
    String title,
    int dailyProblemCount,
    int trainingDaysPerWeek,
    int remainingProblemCount
) {
}
