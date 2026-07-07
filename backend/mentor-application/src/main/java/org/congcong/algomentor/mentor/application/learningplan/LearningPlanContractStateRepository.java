package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface LearningPlanContractStateRepository {

  default Optional<LearningPlanContractState> findByPlan(long userId, long planId) {
    return Optional.empty();
  }

  default LearningPlanContractState pause(long userId, long planId, LocalDate frozenEstimatedCompletionDate) {
    return LearningPlanContractState.empty(userId, planId);
  }

  default LearningPlanContractState resume(long userId, long planId, Instant noticeAt) {
    return LearningPlanContractState.empty(userId, planId);
  }

  default LearningPlanContractState closeOut(long userId, long planId, LocalDate frozenEstimatedCompletionDate) {
    return LearningPlanContractState.empty(userId, planId);
  }
}
