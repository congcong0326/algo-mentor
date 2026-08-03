package org.congcong.algomentor.mentor.application.learningplan.personalization;

/** 学习计划个性化上下文的固定容量与选择上限。 */
public final class LearningPlanPersonalizationConstants {

  public static final int TOKEN_BUDGET = 1_000;
  public static final int CHARS_PER_TOKEN = 4;
  public static final int DECLARED_FACT_LIMIT = 8;
  public static final int GENERAL_OBSERVATION_LIMIT = 6;
  public static final int ABILITY_TAG_LIMIT = 3;

  private LearningPlanPersonalizationConstants() {
  }
}
