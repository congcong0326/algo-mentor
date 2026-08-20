package org.congcong.algomentor.mentor.application.learningplan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** AI 学习计划创建与修订共用的已规范化规划输入。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LearningPlanBrief(
    LearningPlanIntent intent,
    String objective,
    Integer targetProblemCount,
    Integer durationWeeks,
    LearningPlanLevel level,
    Integer weeklyHours,
    String programmingLanguage,
    LearningPlanDifficultyDistribution difficultyDistribution,
    List<String> topicPreferences,
    String additionalConstraints,
    boolean personalizationEnabled,
    LearningPlanContentLocale contentLocale
) {

  /** 兼容模板和既有内部调用；AI 创建入口必须提供 targetProblemCount。 */
  public LearningPlanBrief(
      LearningPlanIntent intent,
      String objective,
      Integer durationWeeks,
      LearningPlanLevel level,
      Integer weeklyHours,
      String programmingLanguage,
      LearningPlanDifficultyDistribution difficultyDistribution,
      List<String> topicPreferences,
      String additionalConstraints,
      boolean personalizationEnabled,
      LearningPlanContentLocale contentLocale
  ) {
    this(
        intent,
        objective,
        null,
        durationWeeks,
        level,
        weeklyHours,
        programmingLanguage,
        difficultyDistribution,
        topicPreferences,
        additionalConstraints,
        personalizationEnabled,
        contentLocale);
  }

  public LearningPlanBrief {
    contentLocale = contentLocale == null ? LearningPlanContentLocale.ZH_CN : contentLocale;
    objective = LearningPlanObjectiveDefaults.resolve(intent, objective, contentLocale);
    programmingLanguage = normalize(programmingLanguage);
    topicPreferences = normalizeList(topicPreferences);
    additionalConstraints = normalize(additionalConstraints);
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static List<String> normalizeList(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
  }
}
