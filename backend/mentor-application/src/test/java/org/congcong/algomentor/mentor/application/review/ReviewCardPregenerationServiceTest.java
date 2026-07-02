package org.congcong.algomentor.mentor.application.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.junit.jupiter.api.Test;

class ReviewCardPregenerationServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void cacheHitDoesNotConsumeQuota() {
    RecordingUsageStore usageStore = new RecordingUsageStore(true);
    RecordingRepository repository = new RecordingRepository();
    ReviewCardService cardService = cardService();
    MistakeNote note = repository.note;
    String signature = cardService.signature(note);
    JsonNode cardJson = objectMapper.valueToTree(card());
    repository.note = withCache(note, cardJson, signature);
    ReviewCardPregenerationService service = service(repository, usageStore, cardService);

    service.enqueue(note.id());

    assertThat(usageStore.consumeCount).isZero();
    assertThat(repository.savedCards).isZero();
  }

  @Test
  void quotaExceededSkipsGeneration() {
    RecordingUsageStore usageStore = new RecordingUsageStore(false);
    RecordingRepository repository = new RecordingRepository();
    ReviewCardPregenerationService service = service(repository, usageStore, cardService());

    service.enqueue(repository.note.id());

    assertThat(usageStore.consumeCount).isEqualTo(1);
    assertThat(usageStore.scope).isEqualTo(MistakeReviewConstants.QUOTA_SCOPE);
    assertThat(repository.savedCards).isZero();
  }

  private ReviewCardPregenerationService service(
      RecordingRepository repository,
      RecordingUsageStore usageStore,
      ReviewCardService cardService
  ) {
    return new ReviewCardPregenerationService(
        Runnable::run,
        usageStore,
        repository,
        cardService,
        ReviewCardProperties.defaults(),
        MistakeReviewMetrics.NOOP,
        clock);
  }

  private ReviewCardService cardService() {
    return new ReviewCardService(new FailingGateway(), objectMapper, new RuleBasedCardComposer(),
        ReviewCardProperties.defaults(), clock);
  }

  private ReviewCard card() {
    return new ReviewCard(
        CardVariant.AI_GENERATED,
        new ProblemRef("two-sum", "两数之和", "EASY"),
        "context",
        List.of(new ReviewCardPrompt("key_step", "关键点", "")),
        new ReviewCardScaffold("1. 思路：", 400),
        MistakeReviewConstants.REVEAL_HIDE_PREVIOUS,
        "LIGHT");
  }

  private MistakeNote withCache(MistakeNote note, JsonNode cardJson, String signature) {
    return new MistakeNote(
        note.id(),
        note.userId(),
        note.problemSlug(),
        note.source(),
        note.sourceDetail(),
        note.originPlanId(),
        note.originPhaseIndex(),
        note.originPracticeSessionId(),
        note.scheduling(),
        note.dueAt(),
        note.lastReviewedAt(),
        note.lastGrade(),
        note.archived(),
        note.userNotePersistent(),
        new ReviewCardCache(cardJson, CardVariant.AI_GENERATED, signature, Instant.now(clock)),
        note.createdAt(),
        note.updatedAt());
  }

  private static final class RecordingUsageStore implements AiDailyUsageStore {
    private final boolean admitted;
    private int consumeCount;
    private String scope;

    private RecordingUsageStore(boolean admitted) {
      this.admitted = admitted;
    }

    @Override
    public boolean tryConsumeRequest(long userId, LocalDate quotaDate, String scope, long limitCount) {
      this.consumeCount++;
      this.scope = scope;
      return admitted;
    }

    @Override
    public void addUsage(long userId, LocalDate quotaDate, String scope, AiUsage usage) {
    }
  }

  private static final class RecordingRepository implements MistakeNoteRepository {
    private MistakeNote note = new MistakeNote(
        1L,
        7L,
        "two-sum",
        MistakeSource.REVIEW_FAILED,
        Map.of("latestReviewId", 3, "latestReviewScore", "7.0", "difficulty", "EASY"),
        null,
        null,
        null,
        new SchedulingState(0, new BigDecimal("2.50"), 0, MasteryState.NEW, 0),
        Instant.parse("2026-07-02T00:00:00Z"),
        null,
        null,
        false,
        "",
        null,
        Instant.parse("2026-07-02T00:00:00Z"),
        Instant.parse("2026-07-02T00:00:00Z"));
    private int savedCards;

    @Override
    public Optional<MistakeNote> findById(long noteId) {
      return Optional.of(note);
    }

    @Override
    public void savePendingCard(long noteId, JsonNode cardJson, CardVariant variant, String signature, Instant generatedAt) {
      savedCards++;
    }

    @Override
    public MistakeNote upsertForReview(
        PracticeCodeReview review,
        MistakeSource source,
        JsonNode sourceDetail,
        ReviewSeed seed
    ) {
      throw new UnsupportedOperationException();
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
