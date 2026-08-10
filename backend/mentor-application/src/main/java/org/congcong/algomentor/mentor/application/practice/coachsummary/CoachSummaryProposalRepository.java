package org.congcong.algomentor.mentor.application.practice.coachsummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CoachSummaryProposalRepository {

  void lockScope(long userId, String problemSlug);

  Optional<CoachSummaryProposal> findBySource(long sourceRunId, String sourceToolCallId);

  void supersedePending(long userId, String problemSlug);

  CoachSummaryProposal insert(CoachSummaryProposal proposal);

  Optional<CoachSummaryProposal> findForUpdate(String proposalId, long userId, long practiceSessionId);

  CoachSummaryProposal markApplied(
      String proposalId,
      long appliedCoachSummaryRevision,
      Instant appliedAt
  );

  CoachSummaryProposal markSuperseded(String proposalId);

  List<CoachSummaryMessageAction> findMessageActions(
      long userId,
      long practiceSessionId,
      List<Long> assistantMessageIds
  );
}
