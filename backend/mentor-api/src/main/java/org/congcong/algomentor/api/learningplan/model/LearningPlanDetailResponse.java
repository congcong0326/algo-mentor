package org.congcong.algomentor.api.learningplan.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLivingContractSummary;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadSummary;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPaceSummary;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRhythmSettings;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanTrainingPackage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanWeeklyBucket;

public record LearningPlanDetailResponse(
    long id,
    LearningPlanContentLocale contentLocale,
    String title,
    String summary,
    LearningPlanIntent intent,
    String goal,
    int durationWeeks,
    LearningPlanLevel level,
    int weeklyHours,
    String programmingLanguage,
    LearningPlanDifficultyPreference difficultyPreference,
    boolean interviewOriented,
    List<String> topicPreferences,
    String profileSummary,
    LearningPlanStatus status,
    List<LearningPlanDetailPhaseResponse> phases,
    Map<String, Object> metadata,
    LearningPlanLoadSummary loadSummary,
    List<LearningPlanWeeklyBucket> weeklyBuckets,
    LearningPlanTrainingPackage nextTrainingPackage,
    LearningPlanRhythmSettings rhythmSettings,
    LearningPlanPaceSummary paceSummary,
    LearningPlanLivingContractSummary livingContractSummary,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {

  public LearningPlanDetailResponse {
    topicPreferences = topicPreferences == null ? List.of() : List.copyOf(topicPreferences);
    phases = phases == null ? List.of() : List.copyOf(phases);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    weeklyBuckets = weeklyBuckets == null ? List.of() : List.copyOf(weeklyBuckets);
  }
}
