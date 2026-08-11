package org.congcong.algomentor.mentor.application.learningplan.policy;

/** 学习计划创建治理的策略类型、字段、默认值和稳定错误码。 */
public final class LearningPlanCreationPolicyConstants {

  public static final String TYPE_CODE = "learning-plan.creation.v1";

  public static final String MAX_SAVED_PLANS_FIELD = "maxSavedPlans";
  public static final String DAILY_DRAFT_CREATION_LIMIT_FIELD = "dailyDraftCreationLimit";
  public static final String DRAFT_RETENTION_DAYS_FIELD = "draftRetentionDays";

  public static final int DEFAULT_MAX_SAVED_PLANS = 30;
  public static final int DEFAULT_DAILY_DRAFT_CREATION_LIMIT = 5;
  public static final int DEFAULT_DRAFT_RETENTION_DAYS = 14;

  public static final int MIN_MAX_SAVED_PLANS = 0;
  public static final int MAX_MAX_SAVED_PLANS = 1_000;
  public static final int MIN_DAILY_DRAFT_CREATION_LIMIT = 0;
  public static final int MAX_DAILY_DRAFT_CREATION_LIMIT = 1_000;
  public static final int MIN_DRAFT_RETENTION_DAYS = 1;
  public static final int MAX_DRAFT_RETENTION_DAYS = 365;

  public static final String PLAN_LIMIT_EXCEEDED_CODE = "LEARNING_PLAN_LIMIT_EXCEEDED";
  public static final String DRAFT_DAILY_LIMIT_EXCEEDED_CODE =
      "LEARNING_PLAN_DRAFT_DAILY_LIMIT_EXCEEDED";
  public static final String POLICY_UNAVAILABLE_CODE = "LEARNING_PLAN_CREATION_POLICY_UNAVAILABLE";

  private LearningPlanCreationPolicyConstants() {
  }
}
