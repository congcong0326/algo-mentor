package org.congcong.algomentor.api.practice.model;

import java.time.Instant;

public record CoachSummaryProposalActionResponse(
    String proposalId,
    String status,
    String operation,
    Long appliedCoachSummaryRevision,
    Instant createdAt,
    Instant appliedAt
) {
}
