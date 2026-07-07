package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Instant;
import java.time.LocalDate;

public record LearningPlanContractState(
    long userId,
    long planId,
    boolean paused,
    boolean closedOut,
    LocalDate frozenEstimatedCompletionDate,
    Instant lastRebalanceNoticeAt,
    Instant createdAt,
    Instant updatedAt
) {

  public static LearningPlanContractState empty(long userId, long planId) {
    return new LearningPlanContractState(userId, planId, false, false, null, null, null, null);
  }
}
