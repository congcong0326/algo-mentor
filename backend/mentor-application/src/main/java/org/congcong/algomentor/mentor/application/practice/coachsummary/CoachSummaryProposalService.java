package org.congcong.algomentor.mentor.application.practice.coachsummary;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;
import org.congcong.algomentor.mentor.application.review.ReviewException;
import org.congcong.algomentor.mentor.application.review.note.ProblemSolutionOutlineV1;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNote;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteRepository;
import org.springframework.transaction.annotation.Transactional;

/** 管理教练总结候选的生成、消息投影和一次性采纳。 */
public class CoachSummaryProposalService {

  private final CoachSummaryProposalRepository proposalRepository;
  private final PracticeSessionRepository sessionRepository;
  private final UserProblemNoteRepository noteRepository;
  private final Clock clock;

  public CoachSummaryProposalService(
      CoachSummaryProposalRepository proposalRepository,
      PracticeSessionRepository sessionRepository,
      UserProblemNoteRepository noteRepository,
      Clock clock
  ) {
    this.proposalRepository = Objects.requireNonNull(proposalRepository, "proposalRepository must not be null");
    this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
    this.noteRepository = Objects.requireNonNull(noteRepository, "noteRepository must not be null");
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  @Transactional
  public CoachSummaryProposal propose(
      long userId,
      PracticeSession session,
      long sourceRunId,
      String sourceToolCallId,
      String summaryMarkdown
  ) {
    requireSessionOwner(userId, session);
    if (sourceRunId < 1 || sourceToolCallId == null || sourceToolCallId.isBlank()) {
      throw new ReviewException("COACH_SUMMARY_PROPOSAL_SOURCE_REQUIRED", "教练总结候选缺少运行来源。");
    }
    String normalizedMarkdown = normalizeMarkdown(summaryMarkdown);
    String toolCallId = sourceToolCallId.strip();
    proposalRepository.lockScope(userId, session.problemSlug());
    var existing = proposalRepository.findBySource(sourceRunId, toolCallId);
    if (existing.isPresent()) {
      return existing.orElseThrow();
    }

    long baseRevision = noteRepository.findSummary(userId, session.problemSlug())
        .map(summary -> summary.coachSummaryRevision())
        .orElse(0L);
    proposalRepository.supersedePending(userId, session.problemSlug());
    Instant now = Instant.now(clock);
    return proposalRepository.insert(new CoachSummaryProposal(
        UUID.randomUUID().toString(),
        userId,
        session.problemSlug(),
        session.id(),
        sourceRunId,
        toolCallId,
        normalizedMarkdown,
        baseRevision,
        CoachSummaryProposalStatus.PENDING,
        null,
        now,
        null));
  }

  @Transactional
  public CoachSummaryMessageAction apply(long userId, long practiceSessionId, String proposalId) {
    PracticeSession session = sessionRepository.findSessionForUser(practiceSessionId, userId)
        .orElseThrow(() -> new ReviewException(
            "PRACTICE_SESSION_NOT_FOUND", "题目练习会话不存在。"));
    CoachSummaryProposal proposal = proposalRepository.findForUpdate(proposalId, userId, practiceSessionId)
        .orElseThrow(() -> new ReviewException(
            "COACH_SUMMARY_PROPOSAL_NOT_FOUND", "教练总结候选不存在。"));
    if (!session.problemSlug().equals(proposal.problemSlug())) {
      throw new ReviewException("COACH_SUMMARY_PROPOSAL_CONTEXT_MISMATCH", "教练总结候选与当前题目不匹配。");
    }
    if (proposal.status() == CoachSummaryProposalStatus.APPLIED
        || proposal.status() == CoachSummaryProposalStatus.SUPERSEDED) {
      return toAction(0, proposal);
    }

    Instant now = Instant.now(clock);
    UserProblemNote updated = noteRepository.replaceCoachSummary(
            userId,
            proposal.problemSlug(),
            ProblemSolutionOutlineV1.empty(),
            proposal.summaryMarkdown(),
            proposal.baseCoachSummaryRevision(),
            now)
        .orElse(null);
    if (updated == null) {
      return toAction(0, proposalRepository.markSuperseded(proposal.id()));
    }
    return toAction(0, proposalRepository.markApplied(
        proposal.id(), updated.coachSummaryRevision(), now));
  }

  public List<CoachSummaryMessageAction> findMessageActions(
      long userId,
      long practiceSessionId,
      List<Long> assistantMessageIds
  ) {
    if (assistantMessageIds == null || assistantMessageIds.isEmpty()) {
      return List.of();
    }
    return proposalRepository.findMessageActions(userId, practiceSessionId, assistantMessageIds);
  }

  private CoachSummaryMessageAction toAction(long assistantMessageId, CoachSummaryProposal proposal) {
    return new CoachSummaryMessageAction(
        assistantMessageId,
        proposal.id(),
        proposal.status(),
        proposal.operation(),
        proposal.summaryMarkdown(),
        proposal.appliedCoachSummaryRevision(),
        proposal.createdAt(),
        proposal.appliedAt());
  }

  private void requireSessionOwner(long userId, PracticeSession session) {
    if (userId < 1 || session == null || session.userId() != userId) {
      throw new ReviewException("COACH_SUMMARY_PROPOSAL_CONTEXT_REQUIRED", "当前题目训练上下文不可用。");
    }
  }

  private String normalizeMarkdown(String summaryMarkdown) {
    String normalized = summaryMarkdown == null ? "" : summaryMarkdown.strip();
    if (normalized.isEmpty()) {
      throw new ReviewException("COACH_SUMMARY_PROPOSAL_CONTENT_REQUIRED", "教练总结不能为空。");
    }
    if (normalized.length() > ReviewContractConstants.NOTE_MARKDOWN_MAX_CHARS) {
      throw new ReviewException("COACH_SUMMARY_PROPOSAL_TOO_LONG", "教练总结不能超过 10000 个字符。");
    }
    return normalized;
  }
}
