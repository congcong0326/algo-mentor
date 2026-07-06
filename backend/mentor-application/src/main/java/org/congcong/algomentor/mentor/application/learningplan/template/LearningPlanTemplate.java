package org.congcong.algomentor.mentor.application.learningplan.template;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;

public record LearningPlanTemplate(
    Long id,
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
    Map<String, Object> metadata,
    List<LearningPlanTemplatePhase> phases
) {

  public LearningPlanTemplate {
    topicPreferences = topicPreferences == null ? List.of() : List.copyOf(topicPreferences);
    difficultyMix = difficultyMix == null ? Map.of() : Map.copyOf(difficultyMix);
    prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
    recommendedFor = recommendedFor == null ? List.of() : List.copyOf(recommendedFor);
    notRecommendedFor = notRecommendedFor == null ? List.of() : List.copyOf(notRecommendedFor);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    phases = phases == null ? List.of() : List.copyOf(phases);
  }

  public LearningPlanTemplate withId(Long nextId) {
    return new LearningPlanTemplate(
        nextId,
        templateId,
        title,
        summary,
        intent,
        goal,
        defaultDurationWeeks,
        level,
        defaultWeeklyHours,
        programmingLanguage,
        difficultyPreference,
        interviewOriented,
        topicPreferences,
        targetAudience,
        difficultyMix,
        prerequisites,
        recommendedFor,
        notRecommendedFor,
        expectedOutcome,
        sourceName,
        sourceUrl,
        sourceCommit,
        sourceDataPath,
        sourceDescription,
        curationNotes,
        licenseNotice,
        problemCount,
        matchedProblemCount,
        missingProblemCount,
        metadata,
        phases);
  }
}
