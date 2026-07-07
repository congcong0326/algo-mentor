package org.congcong.algomentor.api.learningplan.model;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadSummary;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRhythmOption;

public record LearningPlanTemplateDetailResponse(
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
    int problemCount,
    int matchedProblemCount,
    int missingProblemCount,
    LearningPlanLoadSummary defaultLoadSummary,
    List<LearningPlanRhythmOption> rhythmOptions,
    Map<String, Object> metadata,
    List<LearningPlanTemplatePhaseResponse> phases
) {

  public LearningPlanTemplateDetailResponse {
    rhythmOptions = rhythmOptions == null ? List.of() : List.copyOf(rhythmOptions);
    phases = phases == null ? List.of() : List.copyOf(phases);
  }
}
