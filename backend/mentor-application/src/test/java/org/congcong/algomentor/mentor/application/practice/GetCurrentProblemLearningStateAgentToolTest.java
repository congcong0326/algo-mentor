package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewEvidenceDetail;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistory;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistoryRepository;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewSubmissionVersion;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification;
import org.congcong.algomentor.mentor.application.review.card.ProblemReviewCard;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardRepository;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardSource;
import org.congcong.algomentor.mentor.application.review.note.ProblemAlgorithmKey;
import org.congcong.algomentor.mentor.application.review.note.ProblemComplexityKey;
import org.congcong.algomentor.mentor.application.review.note.ProblemComplexityValue;
import org.congcong.algomentor.mentor.application.review.note.ProblemDataStructureKey;
import org.congcong.algomentor.mentor.application.review.note.ProblemSolutionOutlineV1;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNote;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteRepository;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteSummary;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeed;
import org.congcong.algomentor.mentor.application.review.schedule.SchedulingState;
import org.junit.jupiter.api.Test;

class GetCurrentProblemLearningStateAgentToolTest {

  private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

  @Test
  void returnsCurrentStateAndOutlineWithoutReadingNoteBody() {
    Fixture fixture = new Fixture("我完成这题了吗，之前的 Review 和笔记提纲是什么？");

    JsonNode result = fixture.tool.execute(arguments(false), context("two-sum", false));

    assertThat(result.path(PracticeLearningStateAgentToolContracts.FIELD_STATUS).asText())
        .isEqualTo(PracticeLearningStateAgentToolContracts.STATUS_OK);
    assertThat(result.path(PracticeLearningStateAgentToolContracts.FIELD_PRACTICE)
        .path(PracticeLearningStateAgentToolContracts.FIELD_PROGRESS_STATUS).asText()).isEqualTo("COMPLETED");
    assertThat(result.path(PracticeLearningStateAgentToolContracts.FIELD_LATEST_FORMAL_REVIEW)
        .path(PracticeLearningStateAgentToolContracts.FIELD_VERSION_NO).asInt()).isEqualTo(3);
    assertThat(result.path(PracticeLearningStateAgentToolContracts.FIELD_REVIEW_SCHEDULE)
        .path(PracticeLearningStateAgentToolContracts.FIELD_DUE_AT).asText()).isEqualTo(NOW.plusSeconds(3600).toString());
    assertThat(result.path(PracticeLearningStateAgentToolContracts.FIELD_NOTE)
        .path(PracticeLearningStateAgentToolContracts.FIELD_OUTLINE)
        .path(PracticeLearningStateAgentToolContracts.FIELD_CORE_IDEA).asText()).isEqualTo("哈希表保存已遍历元素");
    assertThat(result.path(PracticeLearningStateAgentToolContracts.FIELD_NOTE)
        .path(PracticeLearningStateAgentToolContracts.FIELD_NOTE_BODY_STATUS).asText())
        .isEqualTo(PracticeLearningStateAgentToolContracts.NOTE_BODY_NOT_REQUESTED);
    assertThat(result.path(PracticeLearningStateAgentToolContracts.FIELD_NOTE)
        .has(PracticeLearningStateAgentToolContracts.FIELD_NOTE_MARKDOWN)).isFalse();
    assertThat(fixture.noteRepository.summaryCalls).isEqualTo(1);
    assertThat(fixture.noteRepository.fullCalls).isZero();
    assertThat(fixture.turnLookup.calls).isZero();
  }

  @Test
  void includesNoteBodyOnlyWhenTheCurrentUserMessageExplicitlyRequestsIt() {
    Fixture fixture = new Fixture("请读取这道题的完整笔记内容");

    JsonNode result = fixture.tool.execute(arguments(true), context("two-sum", true));

    assertThat(result.path(PracticeLearningStateAgentToolContracts.FIELD_NOTE)
        .path(PracticeLearningStateAgentToolContracts.FIELD_NOTE_BODY_STATUS).asText())
        .isEqualTo(PracticeLearningStateAgentToolContracts.NOTE_BODY_INCLUDED);
    assertThat(result.path(PracticeLearningStateAgentToolContracts.FIELD_NOTE)
        .path(PracticeLearningStateAgentToolContracts.FIELD_NOTE_MARKDOWN).asText())
        .isEqualTo("先查补数，再写入当前值，避免复用同一个元素。");
    assertThat(fixture.noteRepository.summaryCalls).isZero();
    assertThat(fixture.noteRepository.fullCalls).isEqualTo(1);
    assertThat(fixture.turnLookup.calls).isEqualTo(1);
  }

  @Test
  void refusesBodyLookupWhenTheUserOnlyRequestsTheOutline() {
    Fixture fixture = new Fixture("请看看我的笔记提纲");

    JsonNode result = fixture.tool.execute(arguments(true), context("two-sum", true));

    assertThat(result.path(PracticeLearningStateAgentToolContracts.FIELD_NOTE)
        .path(PracticeLearningStateAgentToolContracts.FIELD_NOTE_BODY_STATUS).asText())
        .isEqualTo(PracticeLearningStateAgentToolContracts.NOTE_BODY_EXPLICIT_REQUEST_REQUIRED);
    assertThat(result.path(PracticeLearningStateAgentToolContracts.FIELD_NOTE)
        .has(PracticeLearningStateAgentToolContracts.FIELD_NOTE_MARKDOWN)).isFalse();
    assertThat(fixture.noteRepository.summaryCalls).isEqualTo(1);
    assertThat(fixture.noteRepository.fullCalls).isZero();
  }

  @Test
  void doesNotTreatGenericPreviousWritingQuestionsAsNoteBodyRequests() {
    Fixture fixture = new Fixture("这段代码之前写了什么？");

    JsonNode result = fixture.tool.execute(arguments(true), context("two-sum", true));

    assertThat(result.path(PracticeLearningStateAgentToolContracts.FIELD_NOTE)
        .path(PracticeLearningStateAgentToolContracts.FIELD_NOTE_BODY_STATUS).asText())
        .isEqualTo(PracticeLearningStateAgentToolContracts.NOTE_BODY_EXPLICIT_REQUEST_REQUIRED);
    assertThat(fixture.noteRepository.fullCalls).isZero();
  }

  @Test
  void refusesBodyLookupWhenTheUserExplicitlyRejectsReadingTheNoteBody() {
    Fixture fixture = new Fixture("不要读取笔记正文，只告诉我是否有笔记");

    JsonNode result = fixture.tool.execute(arguments(true), context("two-sum", true));

    assertThat(result.path(PracticeLearningStateAgentToolContracts.FIELD_NOTE)
        .path(PracticeLearningStateAgentToolContracts.FIELD_NOTE_BODY_STATUS).asText())
        .isEqualTo(PracticeLearningStateAgentToolContracts.NOTE_BODY_EXPLICIT_REQUEST_REQUIRED);
    assertThat(result.path(PracticeLearningStateAgentToolContracts.FIELD_NOTE)
        .has(PracticeLearningStateAgentToolContracts.FIELD_NOTE_MARKDOWN)).isFalse();
    assertThat(fixture.noteRepository.summaryCalls).isEqualTo(1);
    assertThat(fixture.noteRepository.fullCalls).isZero();
  }

  @Test
  void rejectsMismatchedCurrentProblemBeforeReadingLearningRecords() {
    Fixture fixture = new Fixture("看看状态");

    JsonNode result = fixture.tool.execute(arguments(false), context("three-sum", false));

    assertThat(result.path(PracticeLearningStateAgentToolContracts.FIELD_FAILURE_CODE).asText())
        .isEqualTo(PracticeLearningStateAgentToolContracts.FAILURE_CURRENT_PROBLEM_MISMATCH);
    assertThat(fixture.historyRepository.calls).isZero();
    assertThat(fixture.cardRepository.calls).isZero();
    assertThat(fixture.noteRepository.summaryCalls).isZero();
    assertThat(fixture.noteRepository.fullCalls).isZero();
  }

  private static JsonNode arguments(boolean includeNoteBody) {
    return JsonNodeFactory.instance.objectNode()
        .put(PracticeLearningStateAgentToolContracts.ARGUMENT_INCLUDE_NOTE_BODY, includeNoteBody);
  }

  private static AgentExecutionContext context(String problemSlug, boolean includeRunId) {
    java.util.HashMap<String, Object> metadata = new java.util.HashMap<>();
    metadata.put(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO);
    metadata.put(AgentRuntimeMetadataKeys.USER_ID, 7L);
    metadata.put(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, 8L);
    metadata.put(PracticeChatPromptConstants.METADATA_PLAN_ID, 12L);
    metadata.put(PracticeChatPromptConstants.METADATA_PHASE_INDEX, 1);
    metadata.put(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, problemSlug);
    if (includeRunId) {
      metadata.put(AgentRuntimeMetadataKeys.RUN_DB_ID, 31L);
    }
    return new AgentExecutionContext("run-31", 1, metadata, false);
  }

  private static final class Fixture {
    private final StubTurnMessageLookupRepository turnLookup;
    private final StubHistoryRepository historyRepository = new StubHistoryRepository();
    private final StubReviewCardRepository cardRepository = new StubReviewCardRepository();
    private final StubNoteRepository noteRepository = new StubNoteRepository();
    private final GetCurrentProblemLearningStateAgentTool tool;

    private Fixture(String userMessage) {
      turnLookup = new StubTurnMessageLookupRepository(userMessage);
      tool = new GetCurrentProblemLearningStateAgentTool(
          new StubSessionRepository(),
          turnLookup,
          historyRepository,
          cardRepository,
          noteRepository);
    }
  }

  private static final class StubSessionRepository implements PracticeSessionRepository {

    @Override
    public Optional<PracticeSession> findSessionForUser(long sessionId, long userId) {
      return Optional.of(new PracticeSession(
          8L, 7L, 12L, 1, "two-sum", PracticeSessionStatus.ACTIVE, 11L, null,
          PracticeProgressStatus.COMPLETED, NOW, NOW.minusSeconds(7200), NOW, "zh-CN"));
    }

    @Override public PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex,
        String problemSlug) { throw new UnsupportedOperationException(); }
    @Override public PracticeSession upsertAndLockSession(long userId, long planId, int phaseIndex,
        String problemSlug, String locale) { throw new UnsupportedOperationException(); }
    @Override public PracticeSession attachAgentTask(long sessionId, long agentTaskId) {
      throw new UnsupportedOperationException();
    }
    @Override public PracticeSession attachProblemStatementMessage(long sessionId, long messageId) {
      throw new UnsupportedOperationException();
    }
    @Override public PracticeProgress updateProgressStatus(long sessionId, long userId,
        PracticeProgressStatus status) { throw new UnsupportedOperationException(); }
    @Override public void touchLastMessageAt(long sessionId) { }
  }

  private static final class StubTurnMessageLookupRepository implements AgentTurnMessageLookupRepository {
    private final String userMessage;
    private int calls;

    private StubTurnMessageLookupRepository(String userMessage) {
      this.userMessage = userMessage;
    }

    @Override
    public Optional<AgentTurnMessages> findByRunId(long runId) {
      calls++;
      return Optional.of(new AgentTurnMessages(
          31L,
          21L,
          new AgentMessage(41L, 11L, 1L, AgentMessage.Role.USER, userMessage, NOW),
          null));
    }
  }

  private static final class StubHistoryRepository implements CodeReviewHistoryRepository {
    private int calls;

    @Override
    public List<CodeReviewHistory> findLatestForProblem(long userId, String problemSlug, int limit) {
      calls++;
      return List.of(new CodeReviewHistory(
          101L,
          "two-sum",
          3,
          new PracticeCodeReviewScore(
              new BigDecimal("3.5"),
              new BigDecimal("1.5"),
              new BigDecimal("1.0"),
              new BigDecimal("1.0"),
              new BigDecimal("1.0"),
              new BigDecimal("8.0")),
          true,
          List.of("边界条件说明不足"),
          List.of("补充空数组处理说明"),
          List.of(9L),
          NOW.minusSeconds(3600)));
    }

    @Override public Optional<CodeReviewEvidenceDetail> findEvidenceDetail(long userId, long reviewId) {
      return Optional.empty();
    }
    @Override public List<CodeReviewSubmissionVersion> findNormalizedSubmissionVersions(
        long userId, List<Long> reviewIds) { return List.of(); }
    @Override public List<CodeReviewVerification> verifyReviews(long userId, List<Long> reviewIds) {
      return List.of();
    }
  }

  private static final class StubReviewCardRepository implements ReviewCardRepository {
    private int calls;

    @Override
    public Optional<ProblemReviewCard> findByUserAndSlug(long userId, String problemSlug) {
      calls++;
      return Optional.of(new ProblemReviewCard(
          88L,
          7L,
          "two-sum",
          ReviewCardSource.REVIEW_PASSED,
          Map.of(),
          new SchedulingState(2, 4, 0, null, null, null, null),
          NOW.plusSeconds(3600),
          NOW.minusSeconds(86400),
          ReviewRating.GOOD,
          false,
          NOW.minusSeconds(172800),
          NOW));
    }

    @Override public ProblemReviewCard upsertForReview(long userId, String problemSlug, ReviewCardSource source,
        JsonNode sourceDetail, ReviewSeed seed) { throw new UnsupportedOperationException(); }
    @Override public ProblemReviewCard mark(long userId, String problemSlug, ReviewCardSource source,
        JsonNode sourceDetail, Instant now) { throw new UnsupportedOperationException(); }
    @Override public Optional<ProblemReviewCard> findForUser(long userId, long cardId) { return Optional.empty(); }
    @Override public Optional<ProblemReviewCard> findForUpdate(long userId, long cardId) { return Optional.empty(); }
    @Override public List<ProblemReviewCard> findDue(long userId, Instant now, int limit) { return List.of(); }
    @Override public List<ProblemReviewCard> list(long userId, ReviewCardSource source, boolean mistakeOnly,
        String keyword, int limit, int offset) { return List.of(); }
    @Override public int countDue(long userId, Instant now) { return 0; }
    @Override public int countScheduledBefore(long userId, Instant exclusiveEnd) { return 0; }
    @Override public Optional<Instant> findNextDueAt(long userId, Instant after, Instant exclusiveEnd) {
      return Optional.empty();
    }
    @Override public ProblemReviewCard updateArchived(long userId, long cardId, boolean archived, Instant now) {
      throw new UnsupportedOperationException();
    }
    @Override public ProblemReviewCard updateScheduling(long userId, long cardId, SchedulingState state,
        Instant dueAt, ReviewRating lastRating, Instant reviewedAt) { throw new UnsupportedOperationException(); }
  }

  private static final class StubNoteRepository implements UserProblemNoteRepository {
    private int summaryCalls;
    private int fullCalls;

    @Override
    public Optional<UserProblemNoteSummary> findSummary(long userId, String problemSlug) {
      summaryCalls++;
      return Optional.of(UserProblemNoteSummary.from(note()));
    }

    @Override
    public Optional<UserProblemNote> find(long userId, String problemSlug) {
      fullCalls++;
      return Optional.of(note());
    }

    private UserProblemNote note() {
      ProblemSolutionOutlineV1 outline = new ProblemSolutionOutlineV1(
          1,
          "哈希表保存已遍历元素",
          List.of(ProblemDataStructureKey.HASH_MAP),
          List.of(),
          "value -> index",
          List.of(ProblemAlgorithmKey.OTHER),
          List.of("补数查找"),
          "先查询再写入",
          new ProblemComplexityValue(ProblemComplexityKey.O_N, null),
          new ProblemComplexityValue(ProblemComplexityKey.O_N, null),
          "重复值和同一元素复用");
      return new UserProblemNote(
          66L,
          7L,
          "two-sum",
          outline,
          "先查补数，再写入当前值，避免复用同一个元素。",
          4L,
          NOW.minusSeconds(7200),
          NOW.minusSeconds(1800));
    }

    @Override public Optional<UserProblemNote> insert(long userId, String problemSlug,
        ProblemSolutionOutlineV1 outline, String noteMarkdown, Instant now) { return Optional.empty(); }
    @Override public Optional<UserProblemNote> update(long userId, String problemSlug,
        ProblemSolutionOutlineV1 outline, String noteMarkdown, long expectedRevision, Instant now) {
      return Optional.empty();
    }
    @Override public boolean delete(long userId, String problemSlug) { return false; }
  }
}
