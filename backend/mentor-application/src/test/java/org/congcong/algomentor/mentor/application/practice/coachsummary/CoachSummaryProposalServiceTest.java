package org.congcong.algomentor.mentor.application.practice.coachsummary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionStatus;
import org.congcong.algomentor.mentor.application.review.note.ProblemSolutionOutlineV1;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNote;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteRepository;
import org.junit.jupiter.api.Test;

class CoachSummaryProposalServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");
  private static final PracticeSession SESSION = new PracticeSession(
      50L,
      42L,
      900L,
      1,
      "two-sum",
      PracticeSessionStatus.ACTIVE,
      70L,
      80L,
      PracticeProgressStatus.IN_PROGRESS,
      NOW,
      NOW,
      NOW,
      "zh-CN");

  private final InMemoryProposalRepository proposalRepository = new InMemoryProposalRepository();
  private final InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
  private final CoachSummaryProposalService service = new CoachSummaryProposalService(
      proposalRepository,
      new SessionRepository(SESSION),
      noteRepository,
      Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void newerProposalSupersedesPendingProposalAndSourceRetryIsIdempotent() {
    noteRepository.notes.put("42:two-sum", note("旧总结", 4L, 2L));

    CoachSummaryProposal first = service.propose(42L, SESSION, 31L, "call-1", "第一版");
    CoachSummaryProposal second = service.propose(42L, SESSION, 32L, "call-2", "第二版");
    CoachSummaryProposal retried = service.propose(42L, SESSION, 32L, "call-2", "第二版");

    assertThat(proposalRepository.proposals.get(first.id()).status())
        .isEqualTo(CoachSummaryProposalStatus.SUPERSEDED);
    assertThat(second.status()).isEqualTo(CoachSummaryProposalStatus.PENDING);
    assertThat(second.operation()).isEqualTo(CoachSummaryProposalOperation.REPLACE);
    assertThat(second.baseCoachSummaryRevision()).isEqualTo(2L);
    assertThat(retried.id()).isEqualTo(second.id());
    assertThat(proposalRepository.proposals).hasSize(2);
  }

  @Test
  void appliesProposalOnceAndKeepsOutlineRevisionUnchanged() {
    noteRepository.notes.put("42:two-sum", note("旧总结", 4L, 2L));
    CoachSummaryProposal proposal = service.propose(42L, SESSION, 31L, "call-1", "# 新总结");

    CoachSummaryMessageAction applied = service.apply(42L, SESSION.id(), proposal.id());
    CoachSummaryMessageAction retried = service.apply(42L, SESSION.id(), proposal.id());
    UserProblemNote note = noteRepository.find(42L, "two-sum").orElseThrow();

    assertThat(applied.status()).isEqualTo(CoachSummaryProposalStatus.APPLIED);
    assertThat(applied.appliedCoachSummaryRevision()).isEqualTo(3L);
    assertThat(retried.status()).isEqualTo(CoachSummaryProposalStatus.APPLIED);
    assertThat(retried.appliedCoachSummaryRevision()).isEqualTo(3L);
    assertThat(note.noteMarkdown()).isEqualTo("# 新总结");
    assertThat(note.revision()).isEqualTo(4L);
    assertThat(note.coachSummaryRevision()).isEqualTo(3L);
  }

  @Test
  void staleProposalBecomesSupersededWithoutOverwritingNewerSummary() {
    noteRepository.notes.put("42:two-sum", note("旧总结", 4L, 2L));
    CoachSummaryProposal proposal = service.propose(42L, SESSION, 31L, "call-1", "过时候选");
    noteRepository.notes.put("42:two-sum", note("其他页面的新总结", 4L, 3L));

    CoachSummaryMessageAction result = service.apply(42L, SESSION.id(), proposal.id());

    assertThat(result.status()).isEqualTo(CoachSummaryProposalStatus.SUPERSEDED);
    assertThat(noteRepository.find(42L, "two-sum").orElseThrow().noteMarkdown())
        .isEqualTo("其他页面的新总结");
  }

  @Test
  void createsNoteWhenNoSavedSummaryExists() {
    CoachSummaryProposal proposal = service.propose(42L, SESSION, 31L, "call-1", "首次总结");

    CoachSummaryMessageAction result = service.apply(42L, SESSION.id(), proposal.id());
    UserProblemNote note = noteRepository.find(42L, "two-sum").orElseThrow();

    assertThat(proposal.operation()).isEqualTo(CoachSummaryProposalOperation.CREATE);
    assertThat(result.status()).isEqualTo(CoachSummaryProposalStatus.APPLIED);
    assertThat(note.noteMarkdown()).isEqualTo("首次总结");
    assertThat(note.coachSummaryRevision()).isEqualTo(1L);
  }

  private UserProblemNote note(String markdown, long revision, long coachSummaryRevision) {
    return new UserProblemNote(
        1L,
        42L,
        "two-sum",
        new ProblemSolutionOutlineV1(
            1,
            "哈希表保存补数",
            List.of(),
            List.of(),
            "",
            List.of(),
            List.of(),
            "",
            null,
            null,
            ""),
        markdown,
        revision,
        coachSummaryRevision,
        NOW.minusSeconds(3600),
        NOW.minusSeconds(1800),
        NOW.minusSeconds(1200));
  }

  private static final class InMemoryProposalRepository implements CoachSummaryProposalRepository {
    private final Map<String, CoachSummaryProposal> proposals = new LinkedHashMap<>();

    @Override
    public void lockScope(long userId, String problemSlug) {
    }

    @Override
    public Optional<CoachSummaryProposal> findBySource(long sourceRunId, String sourceToolCallId) {
      return proposals.values().stream()
          .filter(value -> value.sourceRunId() == sourceRunId
              && value.sourceToolCallId().equals(sourceToolCallId))
          .findFirst();
    }

    @Override
    public void supersedePending(long userId, String problemSlug) {
      proposals.replaceAll((id, value) -> value.userId() == userId
          && value.problemSlug().equals(problemSlug)
          && value.status() == CoachSummaryProposalStatus.PENDING
          ? withStatus(value, CoachSummaryProposalStatus.SUPERSEDED, null, null)
          : value);
    }

    @Override
    public CoachSummaryProposal insert(CoachSummaryProposal proposal) {
      proposals.put(proposal.id(), proposal);
      return proposal;
    }

    @Override
    public Optional<CoachSummaryProposal> findForUpdate(
        String proposalId,
        long userId,
        long practiceSessionId
    ) {
      return Optional.ofNullable(proposals.get(proposalId))
          .filter(value -> value.userId() == userId && value.practiceSessionId() == practiceSessionId);
    }

    @Override
    public CoachSummaryProposal markApplied(
        String proposalId,
        long appliedCoachSummaryRevision,
        Instant appliedAt
    ) {
      CoachSummaryProposal updated = withStatus(
          proposals.get(proposalId),
          CoachSummaryProposalStatus.APPLIED,
          appliedCoachSummaryRevision,
          appliedAt);
      proposals.put(proposalId, updated);
      return updated;
    }

    @Override
    public CoachSummaryProposal markSuperseded(String proposalId) {
      CoachSummaryProposal updated = withStatus(
          proposals.get(proposalId), CoachSummaryProposalStatus.SUPERSEDED, null, null);
      proposals.put(proposalId, updated);
      return updated;
    }

    @Override
    public List<CoachSummaryMessageAction> findMessageActions(
        long userId,
        long practiceSessionId,
        List<Long> assistantMessageIds
    ) {
      return List.of();
    }

    private static CoachSummaryProposal withStatus(
        CoachSummaryProposal value,
        CoachSummaryProposalStatus status,
        Long appliedRevision,
        Instant appliedAt
    ) {
      return new CoachSummaryProposal(
          value.id(),
          value.userId(),
          value.problemSlug(),
          value.practiceSessionId(),
          value.sourceRunId(),
          value.sourceToolCallId(),
          value.summaryMarkdown(),
          value.baseCoachSummaryRevision(),
          status,
          appliedRevision,
          value.createdAt(),
          appliedAt);
    }
  }

  private static final class InMemoryNoteRepository implements UserProblemNoteRepository {
    private final Map<String, UserProblemNote> notes = new LinkedHashMap<>();

    @Override
    public Optional<UserProblemNote> find(long userId, String problemSlug) {
      return Optional.ofNullable(notes.get(userId + ":" + problemSlug));
    }

    @Override
    public Optional<UserProblemNote> insert(
        long userId,
        String problemSlug,
        ProblemSolutionOutlineV1 outline,
        String noteMarkdown,
        Instant now
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<UserProblemNote> update(
        long userId,
        String problemSlug,
        ProblemSolutionOutlineV1 outline,
        long expectedRevision,
        Instant now
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<UserProblemNote> replaceCoachSummary(
        long userId,
        String problemSlug,
        ProblemSolutionOutlineV1 initialOutline,
        String summaryMarkdown,
        long expectedCoachSummaryRevision,
        Instant now
    ) {
      String key = userId + ":" + problemSlug;
      UserProblemNote current = notes.get(key);
      if (current == null) {
        if (expectedCoachSummaryRevision != 0) {
          return Optional.empty();
        }
        UserProblemNote created = new UserProblemNote(
            1L,
            userId,
            problemSlug,
            initialOutline,
            summaryMarkdown,
            1L,
            1L,
            now,
            now,
            now);
        notes.put(key, created);
        return Optional.of(created);
      }
      if (current.coachSummaryRevision() != expectedCoachSummaryRevision) {
        return Optional.empty();
      }
      UserProblemNote updated = new UserProblemNote(
          current.id(),
          current.userId(),
          current.problemSlug(),
          current.outline(),
          summaryMarkdown,
          current.revision(),
          current.coachSummaryRevision() + 1,
          current.createdAt(),
          now,
          current.updatedAt());
      notes.put(key, updated);
      return Optional.of(updated);
    }

    @Override
    public boolean delete(long userId, String problemSlug) {
      return false;
    }
  }

  private record SessionRepository(PracticeSession session) implements PracticeSessionRepository {
    @Override
    public Optional<PracticeSession> findSessionForUser(long sessionId, long userId) {
      return session.id() == sessionId && session.userId() == userId
          ? Optional.of(session)
          : Optional.empty();
    }

    @Override
    public PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex, String problemSlug) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeSession upsertAndLockSession(
        long userId,
        long planId,
        int phaseIndex,
        String problemSlug,
        String locale
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeSession attachAgentTask(long sessionId, long agentTaskId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeSession attachProblemStatementMessage(long sessionId, long messageId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeProgress updateProgressStatus(
        long sessionId,
        long userId,
        PracticeProgressStatus status
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void touchLastMessageAt(long sessionId) {
    }
  }
}
