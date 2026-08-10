package org.congcong.algomentor.api.practice.mapper.model;

import java.time.Instant;

public record CoachSummaryProposalRow(
    String id,
    long userId,
    String problemSlug,
    long practiceSessionId,
    long sourceRunId,
    String sourceToolCallId,
    String summaryMarkdown,
    long baseCoachSummaryRevision,
    String status,
    Long appliedCoachSummaryRevision,
    Instant createdAt,
    Instant appliedAt
) {
}
