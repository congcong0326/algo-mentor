package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;

/** AI 学习计划创建与修订共用的已规范化规划输入。 */
public record LearningPlanBrief(
    LearningPlanIntent intent,
    String objective,
    Integer durationWeeks,
    LearningPlanLevel level,
    Integer weeklyHours,
    String programmingLanguage,
    LearningPlanDifficultyDistribution difficultyDistribution,
    Boolean interviewOriented,
    List<String> topicPreferences,
    String additionalConstraints,
    boolean personalizationEnabled,
    LearningPlanContentLocale contentLocale
) {

  public LearningPlanBrief {
    contentLocale = contentLocale == null ? LearningPlanContentLocale.ZH_CN : contentLocale;
    objective = LearningPlanObjectiveDefaults.resolve(intent, objective, contentLocale);
    programmingLanguage = normalize(programmingLanguage);
    interviewOriented = interviewOriented == null ? false : interviewOriented;
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
