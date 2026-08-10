package org.congcong.algomentor.api.practice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.practice.mapper.CoachSummaryProposalMapper;
import org.congcong.algomentor.api.practice.mapper.model.CoachSummaryMessageActionRow;
import org.congcong.algomentor.api.practice.mapper.model.CoachSummaryProposalRow;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryMessageAction;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryProposal;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryProposalOperation;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryProposalRepository;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryProposalStatus;

public class MyBatisCoachSummaryProposalRepository implements CoachSummaryProposalRepository {

  private final CoachSummaryProposalMapper mapper;

  public MyBatisCoachSummaryProposalRepository(CoachSummaryProposalMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void lockScope(long userId, String problemSlug) {
    mapper.lockScope(userId, problemSlug);
  }

  @Override
  public Optional<CoachSummaryProposal> findBySource(long sourceRunId, String sourceToolCallId) {
    return Optional.ofNullable(mapper.findBySource(sourceRunId, sourceToolCallId)).map(this::toProposal);
  }

  @Override
  public void supersedePending(long userId, String problemSlug) {
    mapper.supersedePending(userId, problemSlug);
  }

  @Override
  public CoachSummaryProposal insert(CoachSummaryProposal proposal) {
    return toProposal(mapper.insert(toRow(proposal)));
  }

  @Override
  public Optional<CoachSummaryProposal> findForUpdate(String proposalId, long userId, long practiceSessionId) {
    return Optional.ofNullable(mapper.findForUpdate(proposalId, userId, practiceSessionId)).map(this::toProposal);
  }

  @Override
  public CoachSummaryProposal markApplied(
      String proposalId,
      long appliedCoachSummaryRevision,
      Instant appliedAt
  ) {
    return toProposal(mapper.markApplied(proposalId, appliedCoachSummaryRevision, appliedAt));
  }

  @Override
  public CoachSummaryProposal markSuperseded(String proposalId) {
    return toProposal(mapper.markSuperseded(proposalId));
  }

  @Override
  public List<CoachSummaryMessageAction> findMessageActions(
      long userId,
      long practiceSessionId,
      List<Long> assistantMessageIds
  ) {
    return mapper.findMessageActions(userId, practiceSessionId, assistantMessageIds).stream()
        .map(this::toAction)
        .toList();
  }

  private CoachSummaryProposalRow toRow(CoachSummaryProposal proposal) {
    return new CoachSummaryProposalRow(
        proposal.id(),
        proposal.userId(),
        proposal.problemSlug(),
        proposal.practiceSessionId(),
        proposal.sourceRunId(),
        proposal.sourceToolCallId(),
        proposal.summaryMarkdown(),
        proposal.baseCoachSummaryRevision(),
        proposal.status().name(),
        proposal.appliedCoachSummaryRevision(),
        proposal.createdAt(),
        proposal.appliedAt());
  }

  private CoachSummaryProposal toProposal(CoachSummaryProposalRow row) {
    if (row == null) {
      throw new IllegalStateException("Coach summary proposal update returned no row");
    }
    return new CoachSummaryProposal(
        row.id(),
        row.userId(),
        row.problemSlug(),
        row.practiceSessionId(),
        row.sourceRunId(),
        row.sourceToolCallId(),
        row.summaryMarkdown(),
        row.baseCoachSummaryRevision(),
        CoachSummaryProposalStatus.valueOf(row.status()),
        row.appliedCoachSummaryRevision(),
        row.createdAt(),
        row.appliedAt());
  }

  private CoachSummaryMessageAction toAction(CoachSummaryMessageActionRow row) {
    return new CoachSummaryMessageAction(
        row.assistantMessageId(),
        row.proposalId(),
        CoachSummaryProposalStatus.valueOf(row.status()),
        CoachSummaryProposalOperation.valueOf(row.operation()),
        row.summaryMarkdown(),
        row.appliedCoachSummaryRevision(),
        row.createdAt(),
        row.appliedAt());
  }
}
