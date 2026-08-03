package org.congcong.algomentor.mentor.application.learningplan.personalization;

import java.time.Instant;

/** 复习队列的低敏负载摘要。 */
public record LearningPlanReviewLoadSummary(
    int dueCount,
    int remainingTodayCount,
    Instant nextDueAt
) {

  public LearningPlanReviewLoadSummary {
    if (dueCount < 0 || remainingTodayCount < 0) {
      throw new IllegalArgumentException("review load counters must not be negative");
    }
  }
}
