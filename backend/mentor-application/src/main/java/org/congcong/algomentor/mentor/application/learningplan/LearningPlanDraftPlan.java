package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;
import java.util.LinkedHashMap;
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

  public LearningPlanContentLocale contentLocale() {
    return LearningPlanContentLocale.fromMetadata(metadata);
  }

  public LearningPlanDraftPlan withContentLocale(LearningPlanContentLocale locale) {
    LearningPlanContentLocale resolvedLocale = locale == null ? LearningPlanContentLocale.ZH_CN : locale;
    Map<String, Object> nextMetadata = new LinkedHashMap<>(metadata);
    nextMetadata.put(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, resolvedLocale.languageTag());
    return new LearningPlanDraftPlan(
        title,
        summary,
        intent,
        goal,
        durationWeeks,
        level,
        weeklyHours,
        programmingLanguage,
        difficultyPreference,
        interviewOriented,
        topicPreferences,
        profileSummary,
        phases,
        nextMetadata);
  }
}
