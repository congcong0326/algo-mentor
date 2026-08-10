package org.congcong.algomentor.api.practice.mapper.model;

import java.time.Instant;

public record CoachSummaryMessageActionRow(
    long assistantMessageId,
    String proposalId,
    String status,
    String operation,
    String summaryMarkdown,
    Long appliedCoachSummaryRevision,
    Instant createdAt,
    Instant appliedAt
) {
}
