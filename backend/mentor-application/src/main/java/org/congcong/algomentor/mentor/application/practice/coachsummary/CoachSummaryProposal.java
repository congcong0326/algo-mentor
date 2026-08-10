package org.congcong.algomentor.mentor.application.practice.coachsummary;

import java.time.Instant;

public record CoachSummaryProposal(
    String id,
    long userId,
    String problemSlug,
    long practiceSessionId,
    long sourceRunId,
    String sourceToolCallId,
    String summaryMarkdown,
    long baseCoachSummaryRevision,
    CoachSummaryProposalStatus status,
    Long appliedCoachSummaryRevision,
    Instant createdAt,
    Instant appliedAt
) {

  public CoachSummaryProposalOperation operation() {
    return baseCoachSummaryRevision == 0
        ? CoachSummaryProposalOperation.CREATE
        : CoachSummaryProposalOperation.REPLACE;
  }
}
