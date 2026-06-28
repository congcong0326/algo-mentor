package org.congcong.algomentor.api.ability.service;

/**
 * 能力画像实时聚合算法和接口作用域常量。
 */
public final class AbilityProfileConstants {

  /**
   * V1 雷达图只展示题库中题数不少于 20 的常见 tag。
   */
  public static final int MIN_PROBLEM_COUNT = 20;

  /**
   * 保守诊断公式中的样本数平滑权重。
   */
  public static final int CONSERVATIVE_WEIGHT = 4;

  /**
   * API 返回分数统一保留 1 位小数。
   */
  public static final int SCORE_SCALE = 1;

  /**
   * 同一道题只取最新一次 Review。
   */
  public static final boolean LATEST_REVIEW_ONLY = true;

  private AbilityProfileConstants() {
  }
}
