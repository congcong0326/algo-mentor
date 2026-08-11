package org.congcong.algomentor.api.learningplan.model;

import java.time.Instant;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;

public record LearningPlanSummaryResponse(
    long id,
    LearningPlanContentLocale contentLocale,
    String title,
    LearningPlanIntent intent,
    String objective,
    int durationWeeks,
    LearningPlanLevel level,
    String programmingLanguage,
    int weeklyHours,
    LearningPlanProgressSummaryResponse progressSummary,
    LearningPlanStatus status,
    Instant createdAt
) {
}
