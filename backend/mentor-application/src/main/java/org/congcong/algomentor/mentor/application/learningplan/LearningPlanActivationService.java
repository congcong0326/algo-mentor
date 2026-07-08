package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Clock;
import java.util.Optional;

public class LearningPlanActivationService {

  private final LearningPlanActivationRepository activationRepository;
  private final LearningPlanRepository planRepository;
  private final Clock clock;

  public LearningPlanActivationService(
      LearningPlanActivationRepository activationRepository,
      LearningPlanRepository planRepository,
      Clock clock) {
    this.activationRepository = activationRepository;
    this.planRepository = planRepository;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public Optional<LearningPlanActivation> findActiveSelection(long userId) {
    return activationRepository.findSelectionByUserId(userId);
  }

  public Optional<Long> findActivePlanId(long userId) {
    return findActiveSelection(userId).map(LearningPlanActivation::planId);
  }

  public LearningPlanActivation activate(long userId, long planId) {
    requirePlan(userId, planId);
    return activationRepository.upsert(userId, planId, clock.instant());
  }

  public LearningPlanActivation activateIfAbsent(long userId, long planId) {
    return activationRepository.findSelectionByUserId(userId)
        .orElseGet(() -> activate(userId, planId));
  }

  public LearningPlanActivation restart(long userId, long planId) {
    LearningPlanActivation current = activationRepository.findSelectionByUserId(userId)
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_ACTIVE_SELECTION_NOT_FOUND", "当前没有采用的学习计划。"));
    if (current.planId() != planId) {
      throw new LearningPlanException("LEARNING_PLAN_ACTIVE_SELECTION_MISMATCH", "只能重置当前采用计划的今日题包。");
    }
    requirePlan(userId, planId);
    return activationRepository.upsert(userId, planId, clock.instant());
  }

  private LearningPlan requirePlan(long userId, long planId) {
    return planRepository.findPlanByIdForUser(planId, userId)
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_NOT_FOUND", "学习计划不存在。"));
  }
}
