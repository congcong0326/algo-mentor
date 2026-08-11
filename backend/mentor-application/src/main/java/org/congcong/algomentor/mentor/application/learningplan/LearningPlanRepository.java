package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;
import java.util.Optional;

public interface LearningPlanRepository {

  LearningPlan save(LearningPlan plan);

  /** 在用户级并发锁保护下检查正式计划总量并创建；达到上限时返回空。 */
  default Optional<LearningPlan> createIfBelowLimit(LearningPlan plan, int maxSavedPlans) {
    if (maxSavedPlans < 1) {
      return Optional.empty();
    }
    return Optional.of(save(plan));
  }

  List<LearningPlan> findByUserId(long userId);

  default LearningPlanPage findPageByUserId(long userId, int page, int pageSize) {
    throw new LearningPlanRepositoryUnavailableException();
  }

  Optional<LearningPlan> findPlanByIdForUser(long planId, long userId);

  default Optional<LearningPlan> findPlanByIdForUserForUpdate(long planId, long userId) {
    return findPlanByIdForUser(planId, userId);
  }

  default LearningPlan appendPhases(long userId, long planId, List<LearningPlanPhaseDraft> newPhases) {
    throw new LearningPlanRepositoryUnavailableException();
  }

  default void clearConfirmedPlanReferences(long userId, long planId) {
    throw new LearningPlanRepositoryUnavailableException();
  }

  default boolean deletePlanByIdForUser(long planId, long userId) {
    throw new LearningPlanRepositoryUnavailableException();
  }

  default boolean deletePlanAndClearReferences(long userId, long planId) {
    throw new LearningPlanRepositoryUnavailableException();
  }
}
