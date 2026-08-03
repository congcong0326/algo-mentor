package org.congcong.algomentor.mentor.application.learningplan;

import java.util.Map;
import java.util.Objects;

/** 模板难度枚举到 AI 计划精确难度比例的唯一映射。 */
public final class LearningPlanDifficultyDistributions {

  private static final Map<LearningPlanDifficultyPreference, LearningPlanDifficultyDistribution> TEMPLATE_VALUES = Map.of(
      LearningPlanDifficultyPreference.EASY, new LearningPlanDifficultyDistribution(60, 35, 5),
      LearningPlanDifficultyPreference.MEDIUM, new LearningPlanDifficultyDistribution(35, 55, 10),
      LearningPlanDifficultyPreference.HARD, new LearningPlanDifficultyDistribution(10, 55, 35),
      LearningPlanDifficultyPreference.MIXED, new LearningPlanDifficultyDistribution(25, 55, 20));

  private LearningPlanDifficultyDistributions() {
  }

  public static LearningPlanDifficultyDistribution forTemplate(
      LearningPlanDifficultyPreference difficultyPreference
  ) {
    return TEMPLATE_VALUES.get(Objects.requireNonNull(difficultyPreference, "difficultyPreference"));
  }
}
