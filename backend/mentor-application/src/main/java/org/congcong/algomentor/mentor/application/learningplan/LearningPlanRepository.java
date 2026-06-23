package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;
import java.util.Optional;

public interface LearningPlanRepository {

  LearningPlan save(LearningPlan plan);

  List<LearningPlan> findByUserId(long userId);

  default LearningPlanPage findPageByUserId(long userId, int page, int pageSize) {
    throw new LearningPlanRepositoryUnavailableException();
  }

  Optional<LearningPlan> findPlanByIdForUser(long planId, long userId);

  default void clearConfirmedPlanReferences(long userId, long planId) {
    throw new LearningPlanRepositoryUnavailableException();
  }

  default boolean deletePlanByIdForUser(long planId, long userId) {
    throw new LearningPlanRepositoryUnavailableException();
  }
}
