package org.congcong.algomentor.mentor.application.practice.coachsummary;

import java.time.Instant;

public record CoachSummaryMessageAction(
    long assistantMessageId,
    String proposalId,
    CoachSummaryProposalStatus status,
    CoachSummaryProposalOperation operation,
    String summaryMarkdown,
    Long appliedCoachSummaryRevision,
    Instant createdAt,
    Instant appliedAt
) {
}
