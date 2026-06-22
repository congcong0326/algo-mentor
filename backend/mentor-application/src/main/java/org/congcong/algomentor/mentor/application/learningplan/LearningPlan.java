package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Instant;

public record LearningPlan(
    Long id,
    long userId,
    LearningPlanStatus status,
    LearningPlanDraftPlan plan,
    Instant createdAt,
    Instant updatedAt
) {

  public LearningPlan withId(Long nextId) {
    return new LearningPlan(nextId, userId, status, plan, createdAt, updatedAt);
  }
}
