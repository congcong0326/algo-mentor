package org.congcong.algomentor.api.learningplan.model;

import java.time.Instant;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;

public record LearningPlanSummaryResponse(
    long id,
    String title,
    LearningPlanIntent intent,
    String goal,
    int durationWeeks,
    LearningPlanLevel level,
    int weeklyHours,
    LearningPlanStatus status,
    Instant createdAt
) {
}
