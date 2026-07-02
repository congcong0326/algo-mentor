package org.congcong.algomentor.mentor.application.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.junit.jupiter.api.Test;

class ReviewSessionServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void cardDetailAggregatesPersistentNoteAndLimitsRecentRecallHistory() {
    RecordingMistakeNoteRepository noteRepository = new RecordingMistakeNoteRepository();
    RecordingReviewLogRepository logRepository = new RecordingReviewLogRepository();
    ReviewSessionService service = service(noteRepository, logRepository);

    ReviewCardDetail detail = service.cardDetail(7L, 88L);

    assertThat(detail.userNotePersistent()).isEqualTo("总是忘记先查 complement。");
    assertThat(detail.card().problemRef().slug()).isEqualTo("two-sum");
    assertThat(detail.recentRecallHistory()).hasSize(5);
    assertThat(detail.recentRecallHistory()).extracting(ReviewRecallHistoryItem::id)
        .containsExactly(6L, 5L, 4L, 3L, 2L);
    assertThat(logRepository.requestedUserId).isEqualTo(7L);
    assertThat(logRepository.requestedNoteId).isEqualTo(88L);
    assertThat(logRepository.requestedLimit).isEqualTo(5);
  }

  private ReviewSessionService service(
      MistakeNoteRepository noteRepository,
      ReviewLogRepository logRepository
  ) {
    ReviewCardService cardService = new ReviewCardService(
        new FailingGateway(),
        objectMapper,
        new RuleBasedCardComposer(),
        ReviewCardProperties.defaults(),
        clock);
    return new ReviewSessionService(
        noteRepository,
        logRepository,
        new ReviewSchedulerService(ReviewSchedulerProperties.defaults()),
        cardService,
        new RecallJudgeService(new FailingGateway(), objectMapper, MistakeReviewMetrics.NOOP),
        new NoopReviewCardPregenerationService(noteRepository, cardService),
        ReviewCardProperties.defaults(),
        ReviewSchedulerProperties.defaults(),
        objectMapper,
        MistakeReviewMetrics.NOOP,
        clock);
  }

  private MistakeNote note() {
    Instant now = Instant.now(clock);
    return new MistakeNote(
        88L,
        7L,
        "two-sum",
        MistakeSource.REVIEW_FAILED,
        Map.of(
            MistakeReviewConstants.METADATA_TITLE_CN, "两数之和",
            "difficulty", "EASY",
            MistakeReviewConstants.METADATA_STATEMENT_SUMMARY, "给定整数数组和目标值，返回两数下标。"),
        null,
        null,
        null,
        new SchedulingState(1, new BigDecimal("2.50"), 1, MasteryState.LEARNING, 0),
        now,
        null,
        null,
        false,
        "总是忘记先查 complement。",
        null,
        now,
        now);
  }

  private final class RecordingMistakeNoteRepository implements MistakeNoteRepository {
    @Override
    public MistakeNote upsertForReview(PracticeCodeReview review, MistakeSource source, JsonNode sourceDetail, ReviewSeed seed) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MistakeNote mark(long userId, String problemSlug, MistakeSource source, JsonNode sourceDetail, Instant now) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<MistakeNote> findByUserAndSlug(long userId, String problemSlug) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<MistakeNote> findById(long noteId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<MistakeNote> findForUser(long userId, long noteId) {
      return userId == 7L && noteId == 88L ? Optional.of(note()) : Optional.empty();
    }

    @Override
    public List<MistakeNote> findDue(long userId, Instant now, int limit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<MistakeNote> list(
        long userId,
        MasteryState state,
        MistakeSource source,
        boolean mistakeOnly,
        String keyword,
        int limit,
        int offset
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int countDue(long userId, Instant now) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MistakeNote updateArchived(long userId, long noteId, boolean archived, Instant now) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MistakeNote updatePersistentNote(long userId, long noteId, String text, Instant now) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MistakeNote updateScheduling(long noteId, SchedulingState state, Instant dueAt, ReviewGrade lastGrade, Instant reviewedAt) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void savePendingCard(long noteId, JsonNode cardJson, CardVariant variant, String signature, Instant generatedAt) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class RecordingReviewLogRepository implements ReviewLogRepository {
    private long requestedUserId;
    private long requestedNoteId;
    private int requestedLimit;

    @Override
    public void append(ReviewLogEntry entry) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<ReviewRecallHistoryItem> findRecentRecallHistory(long userId, long noteId, int limit) {
      requestedUserId = userId;
      requestedNoteId = noteId;
      requestedLimit = limit;
      return List.of(
          history(6L),
          history(5L),
          history(4L),
          history(3L),
          history(2L),
          history(1L)).stream().limit(limit).toList();
    }

    private ReviewRecallHistoryItem history(long id) {
      return new ReviewRecallHistoryItem(
          id,
          ReviewGrade.MASTERED,
          "回忆内容 " + id,
          "本次备注 " + id,
          Instant.parse("2026-07-02T0%s:00:00Z".formatted(id)),
          (int) id);
    }
  }

  private static final class FailingGateway implements LlmGateway {
    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new AssertionError("LLM should not be called");
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }
  }
}
