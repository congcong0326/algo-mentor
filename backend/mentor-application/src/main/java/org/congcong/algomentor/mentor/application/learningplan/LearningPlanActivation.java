package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Instant;

public record LearningPlanActivation(
    long userId,
    long planId,
    Instant activatedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
