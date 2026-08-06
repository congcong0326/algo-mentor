package org.congcong.algomentor.mentor.application.learningplan.template;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;

public record LearningPlanTemplate(
    Long id,
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
    List<String> topicPreferences,
    String targetAudience,
    String targetAudienceEn,
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
    int problemCount,
    int matchedProblemCount,
    int missingProblemCount,
    Map<String, Object> metadata,
    List<LearningPlanTemplatePhase> phases
) {

  public LearningPlanTemplate {
    topicPreferences = topicPreferences == null ? List.of() : List.copyOf(topicPreferences);
    prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
    prerequisitesEn = prerequisitesEn == null ? List.of() : List.copyOf(prerequisitesEn);
    recommendedFor = recommendedFor == null ? List.of() : List.copyOf(recommendedFor);
    recommendedForEn = recommendedForEn == null ? List.of() : List.copyOf(recommendedForEn);
    notRecommendedFor = notRecommendedFor == null ? List.of() : List.copyOf(notRecommendedFor);
    notRecommendedForEn = notRecommendedForEn == null ? List.of() : List.copyOf(notRecommendedForEn);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    phases = phases == null ? List.of() : List.copyOf(phases);
  }

  public LearningPlanContentLocale resolveContentLocale(LearningPlanContentLocale requestedLocale) {
    return requestedLocale == LearningPlanContentLocale.EN_US && englishContentReady
        ? LearningPlanContentLocale.EN_US
        : LearningPlanContentLocale.ZH_CN;
  }

  public String title(LearningPlanContentLocale locale) {
    return resolveContentLocale(locale) == LearningPlanContentLocale.EN_US ? titleEn : title;
  }

  public String summary(LearningPlanContentLocale locale) {
    return resolveContentLocale(locale) == LearningPlanContentLocale.EN_US ? summaryEn : summary;
  }

  public String goal(LearningPlanContentLocale locale) {
    return resolveContentLocale(locale) == LearningPlanContentLocale.EN_US ? goalEn : goal;
  }

  public String targetAudience(LearningPlanContentLocale locale) {
    return resolveContentLocale(locale) == LearningPlanContentLocale.EN_US ? targetAudienceEn : targetAudience;
  }

  public List<String> prerequisites(LearningPlanContentLocale locale) {
    return resolveContentLocale(locale) == LearningPlanContentLocale.EN_US ? prerequisitesEn : prerequisites;
  }

  public List<String> recommendedFor(LearningPlanContentLocale locale) {
    return resolveContentLocale(locale) == LearningPlanContentLocale.EN_US ? recommendedForEn : recommendedFor;
  }

  public List<String> notRecommendedFor(LearningPlanContentLocale locale) {
    return resolveContentLocale(locale) == LearningPlanContentLocale.EN_US ? notRecommendedForEn : notRecommendedFor;
  }

  public String expectedOutcome(LearningPlanContentLocale locale) {
    return resolveContentLocale(locale) == LearningPlanContentLocale.EN_US ? expectedOutcomeEn : expectedOutcome;
  }

  public LearningPlanTemplate withId(Long nextId) {
    return new LearningPlanTemplate(
        nextId,
        templateId,
        title,
        titleEn,
        summary,
        summaryEn,
        catalogCategory,
        recommendedOrder,
        intent,
        goal,
        goalEn,
        defaultDurationWeeks,
        level,
        defaultWeeklyHours,
        programmingLanguage,
        difficultyPreference,
        topicPreferences,
        targetAudience,
        targetAudienceEn,
        prerequisites,
        prerequisitesEn,
        recommendedFor,
        recommendedForEn,
        notRecommendedFor,
        notRecommendedForEn,
        expectedOutcome,
        expectedOutcomeEn,
        englishContentReady,
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
