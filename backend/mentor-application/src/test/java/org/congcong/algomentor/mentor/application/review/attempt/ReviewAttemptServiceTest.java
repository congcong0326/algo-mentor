package org.congcong.algomentor.mentor.application.review.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.review.card.ProblemReviewCard;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardIngestOutcome;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardRepository;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardSource;
import org.congcong.algomentor.mentor.application.review.card.ReviewMetrics;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreferenceRepository;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreferenceService;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsReviewSchedulerService;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSchedulerProperties;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeed;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeedBucket;
import org.congcong.algomentor.mentor.application.review.schedule.SchedulingState;
import org.junit.jupiter.api.Test;

class ReviewAttemptServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-24T08:00:00Z");
  private static final UUID ATTEMPT_ID = UUID.fromString("d42b6f40-5535-4fc4-bc07-6004bd758b25");

  @Test
  void insertsAttemptBeforeUpdatingScheduling() {
    RecordingCardRepository cards = new RecordingCardRepository(card());
    RecordingAttemptRepository attempts = new RecordingAttemptRepository(cards.events);
    RecordingMetrics metrics = new RecordingMetrics();
    ReviewAttemptService service = service(cards, attempts, metrics);

    ReviewAttemptResult result = service.submit(42L, 88L, ATTEMPT_ID, ReviewRating.GOOD);

    assertThat(result.duplicate()).isFalse();
    assertThat(result.attempt().id()).isEqualTo(501L);
    assertThat(cards.events).containsExactly("lock", "find-attempt", "insert-attempt", "update-scheduling");
    assertThat(cards.updatedRating).isEqualTo(ReviewRating.GOOD);
    assertThat(metrics.attemptSubmits).isEqualTo(1);
  }

  @Test
  void existingClientAttemptIsReturnedWithoutAdvancingFsrs() {
    RecordingCardRepository cards = new RecordingCardRepository(card());
    RecordingAttemptRepository attempts = new RecordingAttemptRepository(cards.events);
    attempts.existing = Optional.of(attempt(501L));
    RecordingMetrics metrics = new RecordingMetrics();

    ReviewAttemptResult result = service(cards, attempts, metrics)
        .submit(42L, 88L, ATTEMPT_ID, ReviewRating.EASY);

    assertThat(result.duplicate()).isTrue();
    assertThat(result.attempt().rating()).isEqualTo(ReviewRating.GOOD);
    assertThat(cards.events).containsExactly("lock", "find-attempt");
    assertThat(cards.updatedRating).isNull();
    assertThat(metrics.attemptSubmits).isZero();
  }

  @Test
  void insertRaceReturnsWinningAttemptWithoutUpdatingScheduling() {
    RecordingCardRepository cards = new RecordingCardRepository(card());
    RecordingAttemptRepository attempts = new RecordingAttemptRepository(cards.events);
    attempts.loseInsertRace = true;

    ReviewAttemptResult result = service(cards, attempts, new RecordingMetrics())
        .submit(42L, 88L, ATTEMPT_ID, ReviewRating.GOOD);

    assertThat(result.duplicate()).isTrue();
    assertThat(cards.events).containsExactly("lock", "find-attempt", "insert-attempt", "find-attempt");
    assertThat(cards.updatedRating).isNull();
  }

  private ReviewAttemptService service(
      ReviewCardRepository cards,
      ReviewAttemptRepository attempts,
      ReviewMetrics metrics
  ) {
    ReviewSchedulerProperties properties = ReviewSchedulerProperties.defaults();
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    return new ReviewAttemptService(
        cards,
        attempts,
        new FsrsReviewSchedulerService(properties),
        new ReviewPreferenceService(ReviewPreferenceRepository.empty(), properties, clock),
        metrics,
        clock);
  }

  private static ProblemReviewAttempt attempt(long id) {
    ProblemReviewCard card = card();
    return new ProblemReviewAttempt(
        id,
        card.id(),
        card.userId(),
        ATTEMPT_ID,
        ReviewRating.GOOD,
        ReviewSchedulingSnapshot.before(card),
        ReviewSchedulingSnapshot.before(card),
        NOW);
  }

  private static ProblemReviewCard card() {
    return new ProblemReviewCard(
        88L,
        42L,
        "two-sum",
        ReviewCardSource.REVIEW_FAILED,
        Map.of(),
        SchedulingState.initial(),
        NOW,
        null,
        null,
        false,
        NOW,
        NOW);
  }

  private static final class RecordingAttemptRepository implements ReviewAttemptRepository {
    private final List<String> events;
    private Optional<ProblemReviewAttempt> existing = Optional.empty();
    private boolean loseInsertRace;
    private int findCalls;

    private RecordingAttemptRepository(List<String> events) {
      this.events = events;
    }

    @Override
    public Optional<ProblemReviewAttempt> findByUserAndClientAttemptId(long userId, UUID clientAttemptId) {
      events.add("find-attempt");
      findCalls++;
      if (loseInsertRace && findCalls > 1) {
        return Optional.of(attempt(502L));
      }
      return existing;
    }

    @Override
    public Optional<ProblemReviewAttempt> insertIfAbsent(ProblemReviewAttempt attempt) {
      events.add("insert-attempt");
      if (loseInsertRace) {
        return Optional.empty();
      }
      return Optional.of(new ProblemReviewAttempt(
          501L,
          attempt.reviewCardId(),
          attempt.userId(),
          attempt.clientAttemptId(),
          attempt.rating(),
          attempt.schedulingBefore(),
          attempt.schedulingAfter(),
          attempt.reviewedAt()));
    }

    @Override
    public List<ProblemReviewAttempt> findRecent(long userId, long reviewCardId, int limit) {
      return List.of();
    }
  }

  private static final class RecordingCardRepository implements ReviewCardRepository {
    private final List<String> events = new ArrayList<>();
    private final ProblemReviewCard card;
    private ReviewRating updatedRating;

    private RecordingCardRepository(ProblemReviewCard card) {
      this.card = card;
    }

    @Override
    public Optional<ProblemReviewCard> findForUpdate(long userId, long cardId) {
      events.add("lock");
      return Optional.of(card);
    }

    @Override
    public ProblemReviewCard updateScheduling(
        long userId,
        long cardId,
        SchedulingState state,
        Instant dueAt,
        ReviewRating lastRating,
        Instant reviewedAt
    ) {
      events.add("update-scheduling");
      updatedRating = lastRating;
      return card;
    }

    @Override public Optional<ProblemReviewCard> findForUser(long userId, long cardId) { return Optional.of(card); }
    @Override public ProblemReviewCard upsertForReview(long userId, String slug, ReviewCardSource source, JsonNode detail, ReviewSeed seed) { throw new UnsupportedOperationException(); }
    @Override public ProblemReviewCard mark(long userId, String slug, ReviewCardSource source, JsonNode detail, Instant now) { throw new UnsupportedOperationException(); }
    @Override public Optional<ProblemReviewCard> findByUserAndSlug(long userId, String slug) { return Optional.empty(); }
    @Override public List<ProblemReviewCard> findDue(long userId, Instant now, int limit) { return List.of(); }
    @Override public List<ProblemReviewCard> list(long userId, ReviewCardSource source, boolean mistakeOnly, String keyword, int limit, int offset) { return List.of(); }
    @Override public int countDue(long userId, Instant now) { return 0; }
    @Override public int countScheduledBefore(long userId, Instant exclusiveEnd) { return 0; }
    @Override public Optional<Instant> findNextDueAt(long userId, Instant after, Instant exclusiveEnd) { return Optional.empty(); }
    @Override public ProblemReviewCard updateArchived(long userId, long cardId, boolean archived, Instant now) { throw new UnsupportedOperationException(); }
  }

  private static final class RecordingMetrics implements ReviewMetrics {
    private int attemptSubmits;
    @Override public void recordAttemptSubmit() { attemptSubmits++; }
    @Override public void recordCardIngest(ReviewCardSource source, ReviewCardIngestOutcome outcome) { }
    @Override public void recordSeed(ReviewSeedBucket bucket) { }
  }
}
