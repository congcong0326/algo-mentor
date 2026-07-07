package org.congcong.algomentor.api.learningplan.mapper.model;

import java.time.Instant;
import java.time.LocalDate;

public record LearningPlanContractStateRow(
    long userId,
    long planId,
    boolean paused,
    boolean closedOut,
    LocalDate frozenEstimatedCompletionDate,
    Instant lastRebalanceNoticeAt,
    Instant createdAt,
    Instant updatedAt
) {
}
