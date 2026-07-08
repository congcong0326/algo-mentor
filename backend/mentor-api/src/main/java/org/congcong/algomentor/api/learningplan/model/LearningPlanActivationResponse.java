package org.congcong.algomentor.api.learningplan.model;

import java.time.Instant;

public record LearningPlanActivationResponse(
    long planId,
    Instant activatedAt
) {
}
