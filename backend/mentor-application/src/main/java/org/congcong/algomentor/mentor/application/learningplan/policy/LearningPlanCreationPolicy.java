package org.congcong.algomentor.mentor.application.learningplan.policy;

/** 管理员通过通用策略配置的单用户学习计划创建容量。 */
public record LearningPlanCreationPolicy(
    int maxSavedPlans,
    int dailyDraftCreationLimit,
    int draftRetentionDays
) {

  public LearningPlanCreationPolicy {
    validateRange(
        LearningPlanCreationPolicyConstants.MAX_SAVED_PLANS_FIELD,
        maxSavedPlans,
        LearningPlanCreationPolicyConstants.MIN_MAX_SAVED_PLANS,
        LearningPlanCreationPolicyConstants.MAX_MAX_SAVED_PLANS);
    validateRange(
        LearningPlanCreationPolicyConstants.DAILY_DRAFT_CREATION_LIMIT_FIELD,
        dailyDraftCreationLimit,
        LearningPlanCreationPolicyConstants.MIN_DAILY_DRAFT_CREATION_LIMIT,
        LearningPlanCreationPolicyConstants.MAX_DAILY_DRAFT_CREATION_LIMIT);
    validateRange(
        LearningPlanCreationPolicyConstants.DRAFT_RETENTION_DAYS_FIELD,
        draftRetentionDays,
        LearningPlanCreationPolicyConstants.MIN_DRAFT_RETENTION_DAYS,
        LearningPlanCreationPolicyConstants.MAX_DRAFT_RETENTION_DAYS);
  }

  public static LearningPlanCreationPolicy defaults() {
    return new LearningPlanCreationPolicy(
        LearningPlanCreationPolicyConstants.DEFAULT_MAX_SAVED_PLANS,
        LearningPlanCreationPolicyConstants.DEFAULT_DAILY_DRAFT_CREATION_LIMIT,
        LearningPlanCreationPolicyConstants.DEFAULT_DRAFT_RETENTION_DAYS);
  }

  private static void validateRange(String field, int value, int minimum, int maximum) {
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(
          field + " must be between " + minimum + " and " + maximum + ".");
    }
  }
}
