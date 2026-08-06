package org.congcong.algomentor.mentor.application.learningplan;

/**
 * Metadata keys persisted inside {@link LearningPlanDraftPlan#metadata()}.
 */
public final class LearningPlanDraftMetadataKeys {

  public static final String DRAFT_SOURCE = "draftSource";
  public static final String DRAFT_SOURCE_TEMPLATE = "TEMPLATE";
  public static final String CONTENT_LOCALE = "contentLocale";
  /** Whether this AI plan may read aggregated learner data in later runs. */
  public static final String PERSONALIZATION_ENABLED = "personalizationEnabled";
  public static final String DAILY_PROBLEM_COUNT = "dailyProblemCount";
  public static final String TRAINING_DAYS_PER_WEEK = "trainingDaysPerWeek";
  public static final String COVERAGE_POLICY = "coveragePolicy";
  public static final String LOAD_SUMMARY = "loadSummary";
  public static final String TEMPLATE = "template";
  public static final String TEMPLATE_ID = "templateId";
  public static final String MATCHED_PROBLEM_COUNT = "matchedProblemCount";

  private LearningPlanDraftMetadataKeys() {
  }
}
