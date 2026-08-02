package org.congcong.algomentor.api.learningplan.service;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateCatalogCategory;

public record LearningPlanTemplateSeedRecord(
    String templateId,
    String title,
    String titleEn,
    String summary,
    String summaryEn,
    LearningPlanTemplateCatalogCategory catalogCategory,
    Integer recommendedOrder,
    LearningPlanIntent intent,
    String goal,
    String goalEn,
    int defaultDurationWeeks,
    LearningPlanLevel level,
    int defaultWeeklyHours,
    String programmingLanguage,
    LearningPlanDifficultyPreference difficultyPreference,
    boolean interviewOriented,
    List<String> topicPreferences,
    String targetAudience,
    String targetAudienceEn,
    Map<String, Object> difficultyMix,
    List<String> prerequisites,
    List<String> prerequisitesEn,
    List<String> recommendedFor,
    List<String> recommendedForEn,
    List<String> notRecommendedFor,
    List<String> notRecommendedForEn,
    String expectedOutcome,
    String expectedOutcomeEn,
    boolean englishContentReady,
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
    prerequisitesEn = prerequisitesEn == null ? List.of() : List.copyOf(prerequisitesEn);
    recommendedFor = recommendedFor == null ? List.of() : List.copyOf(recommendedFor);
    recommendedForEn = recommendedForEn == null ? List.of() : List.copyOf(recommendedForEn);
    notRecommendedFor = notRecommendedFor == null ? List.of() : List.copyOf(notRecommendedFor);
    notRecommendedForEn = notRecommendedForEn == null ? List.of() : List.copyOf(notRecommendedForEn);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    phases = phases == null ? List.of() : List.copyOf(phases);
  }
}
