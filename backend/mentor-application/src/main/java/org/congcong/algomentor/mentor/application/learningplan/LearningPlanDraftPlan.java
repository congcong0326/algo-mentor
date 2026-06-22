package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;
import java.util.Map;

public record LearningPlanDraftPlan(
    String title,
    String summary,
    LearningPlanIntent intent,
    String goal,
    int durationWeeks,
    LearningPlanLevel level,
    int weeklyHours,
    String programmingLanguage,
    LearningPlanDifficultyPreference difficultyPreference,
    boolean interviewOriented,
    List<String> topicPreferences,
    String profileSummary,
    List<LearningPlanPhaseDraft> phases,
    Map<String, Object> metadata
) {

  public LearningPlanDraftPlan {
    topicPreferences = topicPreferences == null ? List.of() : List.copyOf(topicPreferences);
    phases = phases == null ? List.of() : List.copyOf(phases);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
