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
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.junit.jupiter.api.Test;

class MistakeNoteServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void passedReviewIsIngestedAsReviewPassed() {
    RecordingRepository repository = new RecordingRepository();
    RecordingMetrics metrics = new RecordingMetrics();
    MistakeNoteService service = service(repository, metrics);

    service.ingestFromReview(review("8.0", true));

    assertThat(repository.source).isEqualTo(MistakeSource.REVIEW_PASSED);
    assertThat(repository.seed.bucket()).isEqualTo(ReviewSeedBucket.NORMAL);
    assertThat(repository.seed.state().intervalDays()).isEqualTo(3);
    assertThat(repository.sourceDetail.get("seedBucket").asText()).isEqualTo("NORMAL");
    assertThat(repository.sourceDetail.get("lowConfidence").asBoolean()).isFalse();
    assertThat(metrics.ingestSource).isEqualTo(MistakeSource.REVIEW_PASSED);
    assertThat(metrics.seedBucket).isEqualTo(ReviewSeedBucket.NORMAL);
  }

  @Test
  void failedReviewIsStillIngestedAsReviewFailed() {
    RecordingRepository repository = new RecordingRepository();
    RecordingMetrics metrics = new RecordingMetrics();
    MistakeNoteService service = service(repository, metrics);

    service.ingestFromReview(review("5.0", false));

    assertThat(repository.source).isEqualTo(MistakeSource.REVIEW_FAILED);
    assertThat(repository.seed.bucket()).isEqualTo(ReviewSeedBucket.FAILED);
    assertThat(repository.seed.dueAt()).isEqualTo(Instant.parse("2026-07-02T00:00:00Z"));
    assertThat(metrics.ingestSource).isEqualTo(MistakeSource.REVIEW_FAILED);
  }

  private MistakeNoteService service(RecordingRepository repository, RecordingMetrics metrics) {
    ReviewCardService cardService = new ReviewCardService(
        new FailingGateway(),
        objectMapper,
        new RuleBasedCardComposer(),
        ReviewCardProperties.defaults(),
        clock);
    ReviewCardPregenerationService pregenerationService = new ReviewCardPregenerationService(
        Runnable::run,
        new RejectingUsageStore(),
        repository,
        cardService,
        ReviewCardProperties.defaults(),
        metrics,
        clock);
    return new MistakeNoteService(
        repository,
        pregenerationService,
        new ReviewSeedPolicy(ReviewSchedulerProperties.defaults()),
        objectMapper,
        metrics,
        clock);
  }

  private PracticeCodeReview review(String totalScore, boolean passed) {
    return new PracticeCodeReview(
        10L,
        7L,
        3L,
        1,
        "two-sum",
        50L,
        1,
        100L,
        101L,
        null,
        "class Solution {}",
        "class Solution {}",
        "java",
        List.of(),
        "context",
        score(totalScore),
        passed,
        List.of("reason"),
        List.of("suggestion"),
        "review",
        Instant.now(clock));
  }

  private PracticeCodeReviewScore score(String totalScore) {
    return new PracticeCodeReviewScore(
        new BigDecimal("4.0"),
        new BigDecimal("2.0"),
        new BigDecimal("2.0"),
        new BigDecimal("1.0"),
        new BigDecimal("1.0"),
        new BigDecimal(totalScore));
  }

  private static final class RecordingRepository implements MistakeNoteRepository {
    private MistakeSource source;
    private JsonNode sourceDetail;
    private ReviewSeed seed;
    private MistakeNote note;

    @Override
    public MistakeNote upsertForReview(
        PracticeCodeReview review,
        MistakeSource source,
        JsonNode sourceDetail,
        ReviewSeed seed
    ) {
      this.source = source;
      this.sourceDetail = sourceDetail;
      this.seed = seed;
      this.note = new MistakeNote(
          1L,
          review.userId(),
          review.problemSlug(),
          source,
          Map.of("latestReviewId", review.id()),
          review.planId(),
          review.phaseIndex(),
          review.sessionId(),
          seed.state(),
          seed.dueAt(),
          null,
          null,
          false,
          "",
          null,
          review.createdAt(),
          review.createdAt());
      return note;
    }

    @Override
    public Optional<MistakeNote> findById(long noteId) {
      return Optional.ofNullable(note);
    }

    @Override
    public MistakeNote mark(long userId, String problemSlug, MistakeSource source, JsonNode sourceDetail, Instant now) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<MistakeNote> findForUser(long userId, long noteId) {
      throw new UnsupportedOperationException();
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

  private static final class RecordingMetrics implements MistakeReviewMetrics {
    private MistakeSource ingestSource;
    private ReviewSeedBucket seedBucket;

    @Override
    public void recordCardGeneration(CardGenerationOutcome outcome) {
    }

    @Override
    public void recordRecallJudge(ReviewGrade grade, RecallJudgeOutcome outcome) {
    }

    @Override
    public void recordSessionSubmit() {
    }

    @Override
    public void recordNoteIngest(MistakeSource source) {
      this.ingestSource = source;
    }

    @Override
    public void recordSeed(ReviewSeedBucket bucket) {
      this.seedBucket = bucket;
    }
  }

  private static final class RejectingUsageStore implements AiDailyUsageStore {
    @Override
    public boolean tryConsumeRequest(long userId, java.time.LocalDate quotaDate, String scope, long limitCount) {
      return false;
    }

    @Override
    public void addUsage(long userId, java.time.LocalDate quotaDate, String scope, org.congcong.algomentor.ai.governance.model.AiUsage usage) {
    }
  }

  private static final class FailingGateway implements LlmGateway {
    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new AssertionError("LLM should not be called");
    }

    @Override
    public java.util.concurrent.Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }
  }
}
