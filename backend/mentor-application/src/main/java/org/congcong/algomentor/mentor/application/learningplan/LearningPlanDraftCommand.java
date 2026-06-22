package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;

public record LearningPlanDraftCommand(
    LearningPlanIntent intent,
    String goal,
    Integer durationWeeks,
    LearningPlanLevel level,
    Integer weeklyHours,
    String programmingLanguage,
    LearningPlanDifficultyPreference difficultyPreference,
    Boolean interviewOriented,
    List<String> topicPreferences
) {

  public LearningPlanDraftCommand {
    goal = normalize(goal);
    programmingLanguage = normalize(programmingLanguage);
    interviewOriented = interviewOriented == null ? false : interviewOriented;
    topicPreferences = normalizeList(topicPreferences);
  }

  LearningPlanDraftCommand withGoal(String nextGoal) {
    return new LearningPlanDraftCommand(
        intent,
        nextGoal,
        durationWeeks,
        level,
        weeklyHours,
        programmingLanguage,
        difficultyPreference,
        interviewOriented,
        topicPreferences);
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
