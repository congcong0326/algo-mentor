package org.congcong.algomentor.mentor.application.learningplan;

import java.util.Map;

/** 按计划类型与正文语言解析缺省学习目标。 */
public final class LearningPlanObjectiveDefaults {

  private static final Map<LearningPlanIntent, String> ZH_CN = Map.of(
      LearningPlanIntent.PRACTICE_GOAL, "建立稳定的算法练习节奏",
      LearningPlanIntent.ABILITY_DIAGNOSIS, "识别并改善当前算法能力短板",
      LearningPlanIntent.INTERVIEW_SPRINT, "提升算法面试中的解题稳定性",
      LearningPlanIntent.TOPIC_BREAKTHROUGH, "系统掌握所选算法专题",
      LearningPlanIntent.MISTAKE_REVIEW, "通过错题复盘减少重复错误",
      LearningPlanIntent.LONG_TERM_LEARNING, "持续提升算法与数据结构能力");

  private static final Map<LearningPlanIntent, String> EN_US = Map.of(
      LearningPlanIntent.PRACTICE_GOAL, "Build a consistent algorithm practice routine",
      LearningPlanIntent.ABILITY_DIAGNOSIS, "Identify and improve current algorithm skill gaps",
      LearningPlanIntent.INTERVIEW_SPRINT, "Improve problem-solving consistency for coding interviews",
      LearningPlanIntent.TOPIC_BREAKTHROUGH, "Systematically master the selected algorithm topics",
      LearningPlanIntent.MISTAKE_REVIEW, "Reduce repeated mistakes through focused review",
      LearningPlanIntent.LONG_TERM_LEARNING, "Continuously improve algorithms and data structures");

  private LearningPlanObjectiveDefaults() {
  }

  public static String resolve(
      LearningPlanIntent intent,
      String objective,
      LearningPlanContentLocale contentLocale
  ) {
    String normalized = normalize(objective);
    if (normalized != null || intent == null) {
      return normalized;
    }
    LearningPlanContentLocale locale = contentLocale == null ? LearningPlanContentLocale.ZH_CN : contentLocale;
    return (locale == LearningPlanContentLocale.EN_US ? EN_US : ZH_CN).get(intent);
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
