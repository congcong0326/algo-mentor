package org.congcong.algomentor.api.learningplan.model;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadSummary;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRhythmSettings;

public record LearningPlanTemplateSummaryResponse(
    String templateId,
    String title,
    String summary,
    LearningPlanIntent intent,
    int defaultDurationWeeks,
    LearningPlanLevel level,
    int defaultWeeklyHours,
    LearningPlanDifficultyPreference difficultyPreference,
    boolean interviewOriented,
    List<String> topicPreferences,
    String targetAudience,
    Map<String, Object> difficultyMix,
    String expectedOutcome,
    String sourceName,
    String sourceCommit,
    int problemCount,
    int matchedProblemCount,
    int missingProblemCount,
    LearningPlanLoadSummary defaultLoadSummary,
    LearningPlanRhythmSettings defaultRhythmSettings
) {
}
