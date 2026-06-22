package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;

public class LearningPlanService {

  private final LearningPlanRepository planRepository;

  public LearningPlanService(LearningPlanRepository planRepository) {
    this.planRepository = planRepository;
  }

  public List<LearningPlan> listPlans(long userId) {
    return planRepository.findByUserId(userId);
  }

  public LearningPlan getPlan(long userId, long planId) {
    return planRepository.findPlanByIdForUser(planId, userId)
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_NOT_FOUND", "学习计划不存在。"));
  }
}
