package org.congcong.algomentor.api.learningplan.model;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadSummary;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRhythmSettings;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateCatalogCategory;

public record LearningPlanTemplateDetailResponse(
    String templateId,
    LearningPlanContentLocale contentLocale,
    String title,
    String summary,
    LearningPlanTemplateCatalogCategory catalogCategory,
    Integer recommendedOrder,
    LearningPlanIntent intent,
    String goal,
    int defaultDurationWeeks,
    LearningPlanLevel level,
    int defaultWeeklyHours,
    String programmingLanguage,
    LearningPlanDifficultyPreference difficultyPreference,
    List<String> topicPreferences,
    String targetAudience,
    List<String> prerequisites,
    List<String> recommendedFor,
    List<String> notRecommendedFor,
    String expectedOutcome,
    String sourceName,
    String sourceUrl,
    int plannedProblemCount,
    LearningPlanLoadSummary defaultLoadSummary,
    LearningPlanRhythmSettings defaultRhythmSettings,
    List<LearningPlanTemplatePhaseResponse> phases
) {

  public LearningPlanTemplateDetailResponse {
    phases = phases == null ? List.of() : List.copyOf(phases);
  }
}
