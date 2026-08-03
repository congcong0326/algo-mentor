package org.congcong.algomentor.mentor.application.learningplan;

/** AI 学习计划使用的精确题目难度占比。 */
public record LearningPlanDifficultyDistribution(
    int easyPercent,
    int mediumPercent,
    int hardPercent
) {

  public LearningPlanDifficultyDistribution {
    if (!isPercentage(easyPercent) || !isPercentage(mediumPercent) || !isPercentage(hardPercent)) {
      throw new LearningPlanException(
          "LEARNING_PLAN_DIFFICULTY_DISTRIBUTION_INVALID",
          "学习计划难度比例必须在 0 至 100 之间。");
    }
    if (easyPercent + mediumPercent + hardPercent != 100) {
      throw new LearningPlanException(
          "LEARNING_PLAN_DIFFICULTY_DISTRIBUTION_INVALID",
          "学习计划难度比例之和必须等于 100。");
    }
  }

  private static boolean isPercentage(int value) {
    return value >= 0 && value <= 100;
  }
}
