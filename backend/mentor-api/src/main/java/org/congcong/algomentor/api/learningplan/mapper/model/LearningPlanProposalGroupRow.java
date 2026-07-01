package org.congcong.algomentor.api.learningplan.mapper.model;

import java.time.Instant;

public record LearningPlanProposalGroupRow(
    Long id,
    Long userId,
    String proposalType,
    String targetType,
    Long targetId,
    String status,
    String initialInstruction,
    Long latestProposalId,
    Instant createdAt,
    Instant updatedAt
) {
}
