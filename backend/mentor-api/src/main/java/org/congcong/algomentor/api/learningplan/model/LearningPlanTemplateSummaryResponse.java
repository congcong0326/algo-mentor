package org.congcong.algomentor.api.learningplan.model;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadSummary;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRhythmSettings;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateCatalogCategory;

public record LearningPlanTemplateSummaryResponse(
    String templateId,
    LearningPlanContentLocale contentLocale,
    String title,
    String summary,
    LearningPlanTemplateCatalogCategory catalogCategory,
    Integer recommendedOrder,
    LearningPlanIntent intent,
    int defaultDurationWeeks,
    LearningPlanLevel level,
    int defaultWeeklyHours,
    String programmingLanguage,
    LearningPlanDifficultyPreference difficultyPreference,
    boolean interviewOriented,
    List<String> topicPreferences,
    String targetAudience,
    String expectedOutcome,
    int plannedProblemCount,
    LearningPlanLoadSummary defaultLoadSummary,
    LearningPlanRhythmSettings defaultRhythmSettings
) {
}
