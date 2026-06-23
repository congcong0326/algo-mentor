package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;

public class LearningPlanService {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final int MAX_PAGE_SIZE = 50;

  private final LearningPlanRepository planRepository;

  public LearningPlanService(LearningPlanRepository planRepository) {
    this.planRepository = planRepository;
  }

  public List<LearningPlan> listPlans(long userId) {
    return planRepository.findByUserId(userId);
  }

  public LearningPlanPage listPlans(long userId, Integer page, Integer pageSize) {
    int normalizedPage = page == null || page < 1 ? DEFAULT_PAGE : page;
    int normalizedPageSize = pageSize == null || pageSize < 1
        ? DEFAULT_PAGE_SIZE
        : Math.min(pageSize, MAX_PAGE_SIZE);
    return planRepository.findPageByUserId(userId, normalizedPage, normalizedPageSize);
  }

  public LearningPlan getPlan(long userId, long planId) {
    return planRepository.findPlanByIdForUser(planId, userId)
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_NOT_FOUND", "学习计划不存在。"));
  }

  public void deletePlan(long userId, long planId) {
    getPlan(userId, planId);
    boolean deleted = planRepository.deletePlanAndClearReferences(userId, planId);
    if (!deleted) {
      throw new LearningPlanException("LEARNING_PLAN_NOT_FOUND", "学习计划不存在。");
    }
  }
}
