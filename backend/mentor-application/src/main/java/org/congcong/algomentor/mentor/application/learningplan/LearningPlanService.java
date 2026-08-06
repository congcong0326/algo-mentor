package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Clock;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class LearningPlanService {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final int MAX_PAGE_SIZE = 50;

  private final LearningPlanRepository planRepository;
  private final LearningPlanLoadService loadService;
  private final Clock clock;

  public LearningPlanService(LearningPlanRepository planRepository) {
    this(planRepository, new LearningPlanLoadService());
  }

  public LearningPlanService(LearningPlanRepository planRepository, LearningPlanLoadService loadService) {
    this(planRepository, loadService, Clock.systemUTC());
  }

  public LearningPlanService(LearningPlanRepository planRepository, LearningPlanLoadService loadService, Clock clock) {
    this.planRepository = planRepository;
    this.loadService = loadService == null ? new LearningPlanLoadService() : loadService;
    this.clock = clock == null ? Clock.systemUTC() : clock;
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

  public LearningPlan updateRhythm(long userId, long planId, Integer dailyProblemCount, Integer trainingDaysPerWeek) {
    loadService.validateRhythm(dailyProblemCount, trainingDaysPerWeek);
    LearningPlan current = getPlan(userId, planId);
    LearningPlanDraftPlan snapshot = current.plan();
    Map<String, Object> metadata = new LinkedHashMap<>(snapshot.metadata());
    metadata.put(LearningPlanDraftMetadataKeys.DAILY_PROBLEM_COUNT, dailyProblemCount);
    metadata.put(LearningPlanDraftMetadataKeys.TRAINING_DAYS_PER_WEEK, trainingDaysPerWeek);
    LearningPlanDraftPlan updatedSnapshot = new LearningPlanDraftPlan(
        snapshot.title(),
        snapshot.summary(),
        snapshot.intent(),
        snapshot.objective(),
        snapshot.durationWeeks(),
        snapshot.level(),
        snapshot.weeklyHours(),
        snapshot.programmingLanguage(),
        snapshot.difficultyDistribution(),
        snapshot.topicPreferences(),
        snapshot.additionalConstraints(),
        snapshot.phases(),
        metadata);
    return planRepository.save(new LearningPlan(
        current.id(),
        current.userId(),
        current.status(),
        updatedSnapshot,
        current.createdAt(),
        clock.instant()));
  }
}
