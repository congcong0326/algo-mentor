package org.congcong.algomentor.api.learningplan.mapper.model;

import java.time.Instant;

public record LearningPlanActivationRow(
    long userId,
    long planId,
    Instant activatedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
