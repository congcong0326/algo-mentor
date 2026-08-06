package org.congcong.algomentor.mentor.application.learningplan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LearningPlanDraftPlan(
    String title,
    String summary,
    LearningPlanIntent intent,
    String objective,
    int durationWeeks,
    LearningPlanLevel level,
    int weeklyHours,
    String programmingLanguage,
    LearningPlanDifficultyDistribution difficultyDistribution,
    List<String> topicPreferences,
    String additionalConstraints,
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
        objective,
        durationWeeks,
        level,
        weeklyHours,
        programmingLanguage,
        difficultyDistribution,
        topicPreferences,
        additionalConstraints,
        phases,
        nextMetadata);
  }
}
