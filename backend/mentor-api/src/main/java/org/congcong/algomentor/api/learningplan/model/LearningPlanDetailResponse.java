package org.congcong.algomentor.api.learningplan.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;

public record LearningPlanDetailResponse(
    long id,
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
    List<LearningPlanPhaseDraft> phases,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt
) {
}
