package org.congcong.algomentor.api.learningplan.model;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;

/**
 * 学习计划草案面向普通用户的响应投影。
 */
public record LearningPlanDraftPlanResponse(
    String title,
    String summary,
    LearningPlanContentLocale contentLocale,
    LearningPlanIntent intent,
    String objective,
    int durationWeeks,
    LearningPlanLevel level,
    int weeklyHours,
    String programmingLanguage,
    LearningPlanDifficultyDistribution difficultyDistribution,
    boolean interviewOriented,
    List<String> topicPreferences,
    String additionalConstraints,
    List<LearningPlanPhaseDraft> phases,
    Map<String, Object> metadata
) {

  public LearningPlanDraftPlanResponse {
    topicPreferences = topicPreferences == null ? List.of() : List.copyOf(topicPreferences);
    phases = phases == null ? List.of() : List.copyOf(phases);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
