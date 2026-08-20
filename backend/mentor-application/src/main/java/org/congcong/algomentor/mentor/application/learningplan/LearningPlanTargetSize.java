package org.congcong.algomentor.mentor.application.learningplan;

import java.util.Set;

/** AI 创建计划支持的题目规模及其默认派发节奏。 */
public final class LearningPlanTargetSize {

  /** 每个计划周对应的题目数：每天 1 题、每周训练 5 天。 */
  public static final int PROBLEMS_PER_WEEK = 5;
  public static final int DEFAULT_DAILY_PROBLEM_COUNT = 1;
  public static final int DEFAULT_TRAINING_DAYS_PER_WEEK = 5;

  /**
   * 遗留负载与预计时长字段的内部默认值，不作为 AI 选题条件或用户承诺。
   */
  public static final int LEGACY_WEEKLY_HOURS = 5;

  private static final Set<Integer> SUPPORTED_VALUES = Set.of(5, 10, 15, 20, 25, 30);

  private LearningPlanTargetSize() {
  }

  public static boolean isSupported(Integer value) {
    return value != null && SUPPORTED_VALUES.contains(value);
  }

  public static Integer durationWeeksFor(Integer targetProblemCount) {
    if (targetProblemCount == null) {
      return null;
    }
    return Math.max(1, (targetProblemCount + PROBLEMS_PER_WEEK - 1) / PROBLEMS_PER_WEEK);
  }

  public static int expectedPhaseCount(int targetProblemCount) {
    return durationWeeksFor(targetProblemCount);
  }
}
