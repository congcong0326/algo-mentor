package org.congcong.algomentor.api.learningplan.service;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;

public record LearningPlanTemplateSeedRecord(
    String templateId,
    String title,
    String summary,
    LearningPlanIntent intent,
    String goal,
    int defaultDurationWeeks,
    LearningPlanLevel level,
    int defaultWeeklyHours,
    String programmingLanguage,
    LearningPlanDifficultyPreference difficultyPreference,
    boolean interviewOriented,
    List<String> topicPreferences,
    String targetAudience,
    Map<String, Object> difficultyMix,
    List<String> prerequisites,
    List<String> recommendedFor,
    List<String> notRecommendedFor,
    String expectedOutcome,
    String sourceName,
    String sourceUrl,
    String sourceCommit,
    String sourceDataPath,
    String sourceDescription,
    String curationNotes,
    String licenseNotice,
    Map<String, Object> metadata,
    List<LearningPlanTemplatePhaseSeedRecord> phases
) {

  public LearningPlanTemplateSeedRecord {
    topicPreferences = topicPreferences == null ? List.of() : List.copyOf(topicPreferences);
    difficultyMix = difficultyMix == null ? Map.of() : Map.copyOf(difficultyMix);
    prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
    recommendedFor = recommendedFor == null ? List.of() : List.copyOf(recommendedFor);
    notRecommendedFor = notRecommendedFor == null ? List.of() : List.copyOf(notRecommendedFor);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    phases = phases == null ? List.of() : List.copyOf(phases);
  }
}
