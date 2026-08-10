package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryMessageAction;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryProposal;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryProposalRepository;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryProposalService;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryProposalStatus;
import org.congcong.algomentor.mentor.application.review.note.ProblemSolutionOutlineV1;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNote;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteRepository;
import org.junit.jupiter.api.Test;

class ProposeCurrentProblemCoachSummaryAgentToolTest {

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

  private final StubSessionRepository sessionRepository = new StubSessionRepository(SESSION);
  private final RecordingProposalRepository proposalRepository = new RecordingProposalRepository();
  private final CoachSummaryProposalService proposalService = new CoachSummaryProposalService(
      proposalRepository,
      sessionRepository,
      new EmptyNoteRepository(),
      Clock.fixed(NOW, ZoneOffset.UTC));
  private final ProposeCurrentProblemCoachSummaryAgentTool tool =
      new ProposeCurrentProblemCoachSummaryAgentTool(sessionRepository, proposalService);

  @Test
  void createsProposalFromTrustedContextAndReturnsExactMarkdown() {
    String markdown = "# 教练总结\n\n- 先查补数";

    JsonNode result = tool.execute(arguments(markdown), context("call-1", "two-sum"));

    assertThat(result.path("type").asText()).isEqualTo("current_problem_coach_summary_proposed");
    assertThat(result.path("status").asText()).isEqualTo("PROPOSED");
    assertThat(result.path("proposalId").asText()).isNotBlank();
    assertThat(result.path("summaryMarkdown").asText()).isEqualTo(markdown);
    assertThat(result.path("operation").asText()).isEqualTo("CREATE");
    assertThat(result.path("baseCoachSummaryRevision").asLong()).isZero();
    assertThat(proposalRepository.inserted.sourceRunId()).isEqualTo(31L);
    assertThat(proposalRepository.inserted.sourceToolCallId()).isEqualTo("call-1");
  }

  @Test
  void rejectsMissingToolCallId() {
    JsonNode result = tool.execute(arguments("总结"), context("", "two-sum"));

    assertThat(result.path("status").asText()).isEqualTo("FAILED");
    assertThat(result.path("failureCode").asText()).isEqualTo("MISSING_TRUSTED_METADATA");
  }

  @Test
  void rejectsMismatchedProblemContext() {
    JsonNode result = tool.execute(arguments("总结"), context("call-1", "three-sum"));

    assertThat(result.path("status").asText()).isEqualTo("FAILED");
    assertThat(result.path("failureCode").asText()).isEqualTo("CURRENT_PROBLEM_MISMATCH");
  }

  private JsonNode arguments(String summaryMarkdown) {
    return JsonNodeFactory.instance.objectNode().put("summaryMarkdown", summaryMarkdown);
  }

  private AgentExecutionContext context(String toolCallId, String problemSlug) {
    return new AgentExecutionContext(
        "run-31",
        2,
        toolCallId,
        Map.of(
            PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO,
            AgentRuntimeMetadataKeys.USER_ID, 42L,
            PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, 50L,
            PracticeChatPromptConstants.METADATA_PLAN_ID, 900L,
            PracticeChatPromptConstants.METADATA_PHASE_INDEX, 1,
            PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, problemSlug,
            AgentRuntimeMetadataKeys.RUN_DB_ID, 31L),
        false);
  }

  private static final class RecordingProposalRepository implements CoachSummaryProposalRepository {
    private CoachSummaryProposal inserted;

    @Override
    public void lockScope(long userId, String problemSlug) {
    }

    @Override
    public Optional<CoachSummaryProposal> findBySource(long sourceRunId, String sourceToolCallId) {
      return Optional.empty();
    }

    @Override
    public void supersedePending(long userId, String problemSlug) {
    }

    @Override
    public CoachSummaryProposal insert(CoachSummaryProposal proposal) {
      inserted = proposal;
      return proposal;
    }

    @Override
    public Optional<CoachSummaryProposal> findForUpdate(
        String proposalId,
        long userId,
        long practiceSessionId
    ) {
      return Optional.empty();
    }

    @Override
    public CoachSummaryProposal markApplied(
        String proposalId,
        long appliedCoachSummaryRevision,
        Instant appliedAt
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CoachSummaryProposal markSuperseded(String proposalId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<CoachSummaryMessageAction> findMessageActions(
        long userId,
        long practiceSessionId,
        List<Long> assistantMessageIds
    ) {
      return List.of();
    }
  }

  private static final class EmptyNoteRepository implements UserProblemNoteRepository {
    @Override
    public Optional<UserProblemNote> find(long userId, String problemSlug) {
      return Optional.empty();
    }

    @Override
    public Optional<UserProblemNote> insert(
        long userId,
        String problemSlug,
        ProblemSolutionOutlineV1 outline,
        String noteMarkdown,
        Instant now
    ) {
      return Optional.empty();
    }

    @Override
    public Optional<UserProblemNote> update(
        long userId,
        String problemSlug,
        ProblemSolutionOutlineV1 outline,
        long expectedRevision,
        Instant now
    ) {
      return Optional.empty();
    }

    @Override
    public boolean delete(long userId, String problemSlug) {
      return false;
    }
  }

  private record StubSessionRepository(PracticeSession session) implements PracticeSessionRepository {
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
