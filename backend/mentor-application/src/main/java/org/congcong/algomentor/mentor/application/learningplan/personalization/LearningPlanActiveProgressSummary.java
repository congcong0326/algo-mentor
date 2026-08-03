package org.congcong.algomentor.mentor.application.learningplan.personalization;

import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPaceStatus;

/** 当前激活计划的低敏进度摘要。 */
public record LearningPlanActiveProgressSummary(
    String objective,
    int currentWeek,
    int totalWeeks,
    double progressPercent,
    LearningPlanPaceStatus paceStatus,
    int dailyProblemCount,
    int trainingDaysPerWeek,
    int remainingProblemCount
) {

  public LearningPlanActiveProgressSummary {
    objective = requireText(objective, "objective");
    if (currentWeek < 1 || totalWeeks < 1 || currentWeek > totalWeeks) {
      throw new IllegalArgumentException("plan week range is invalid");
    }
    if (Double.isNaN(progressPercent) || Double.isInfinite(progressPercent)
        || progressPercent < 0D || progressPercent > 100D) {
      throw new IllegalArgumentException("progress percent must be between 0 and 100");
    }
    if (paceStatus == null) {
      throw new IllegalArgumentException("pace status must not be null");
    }
    if (dailyProblemCount < 0 || trainingDaysPerWeek < 0 || remainingProblemCount < 0) {
      throw new IllegalArgumentException("plan progress counters must not be negative");
    }
  }

  private static String requireText(String value, String fieldName) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return normalized;
  }
}
