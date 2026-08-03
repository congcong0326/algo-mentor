package org.congcong.algomentor.mentor.application.review.card;

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
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewIndexEntry;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewIndexRepository;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSchedulerProperties;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeed;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeedPolicy;
import org.congcong.algomentor.mentor.application.review.schedule.SchedulingState;
import org.junit.jupiter.api.Test;

class ReviewCardOverviewServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");

  @Test
  void assemblesCardsInRepositoryOrderWithOneCrossProblemIndexQuery() {
    ListingCardRepository cardRepository = new ListingCardRepository(List.of(
        card(88L, "two-sum", ReviewCardSource.REVIEW_FAILED),
        card(89L, "valid-parentheses", ReviewCardSource.USER_MARKED)));
    RecordingIndexRepository indexRepository = new RecordingIndexRepository(List.of(
        entry(100L, "two-sum", NOW.minusSeconds(60)),
        entry(101L, "two-sum", NOW)));
    RecordingMetrics metrics = new RecordingMetrics();
    ReviewCardOverviewService service = new ReviewCardOverviewService(
        cardService(cardRepository), indexRepository, metrics);

    List<ReviewCardOverview> overviews = service.list(42L, null, false, null, 80, 0);

    assertThat(overviews).extracting(overview -> overview.card().id()).containsExactly(88L, 89L);
    assertThat(overviews.get(0).recentCodeReviews()).extracting(PracticeCodeReviewIndexEntry::reviewId)
        .containsExactly(100L, 101L);
    assertThat(overviews.get(1).recentCodeReviews()).isEmpty();
    assertThat(indexRepository.userId).isEqualTo(42L);
    assertThat(indexRepository.problemSlugs).containsExactly("two-sum", "valid-parentheses");
    assertThat(indexRepository.perProblemLimit).isEqualTo(10);
    assertThat(metrics.cardCount).isEqualTo(2);
    assertThat(metrics.reviewCount).isEqualTo(2);
  }

  private ReviewCardService cardService(ReviewCardRepository repository) {
    return new ReviewCardService(
        repository,
        new ReviewSeedPolicy(ReviewSchedulerProperties.defaults()),
        new ObjectMapper(),
        ReviewMetrics.NOOP,
        (slug, locale) -> Optional.empty(),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static ProblemReviewCard card(long id, String problemSlug, ReviewCardSource source) {
    return new ProblemReviewCard(
        id,
        42L,
        problemSlug,
        source,
        Map.of(),
        SchedulingState.initial(),
        NOW,
        null,
        null,
        false,
        NOW,
        NOW);
  }

  private static PracticeCodeReviewIndexEntry entry(long id, String problemSlug, Instant createdAt) {
    return new PracticeCodeReviewIndexEntry(
        id,
        12L,
        1,
        problemSlug,
        50L,
        1,
        "java",
        "zh-CN",
        new BigDecimal("7.5"),
        true,
        "边界条件处理不完整",
        createdAt);
  }

  private static final class RecordingIndexRepository implements PracticeCodeReviewIndexRepository {
    private final List<PracticeCodeReviewIndexEntry> entries;
    private long userId;
    private List<String> problemSlugs = List.of();
    private int perProblemLimit;

    private RecordingIndexRepository(List<PracticeCodeReviewIndexEntry> entries) {
      this.entries = entries;
    }

    @Override
    public List<PracticeCodeReviewIndexEntry> findRecentByProblemSlugs(
        long userId,
        List<String> problemSlugs,
        int perProblemLimit
    ) {
      this.userId = userId;
      this.problemSlugs = List.copyOf(problemSlugs);
      this.perProblemLimit = perProblemLimit;
      return entries;
    }
  }

  private static final class RecordingMetrics implements ReviewMetrics {
    private int cardCount;
    private int reviewCount;

    @Override public void recordAttemptSubmit() {
    }
    @Override public void recordCardIngest(ReviewCardSource source, ReviewCardIngestOutcome outcome) {
    }
    @Override public void recordSeed(org.congcong.algomentor.mentor.application.review.schedule.ReviewSeedBucket bucket) {
    }
    @Override public void recordCodeReviewIndexQuery(int cardCount, int reviewCount, long elapsedNanos) {
      this.cardCount = cardCount;
      this.reviewCount = reviewCount;
    }
  }

  private static final class ListingCardRepository implements ReviewCardRepository {
    private final List<ProblemReviewCard> cards;

    private ListingCardRepository(List<ProblemReviewCard> cards) {
      this.cards = cards;
    }

    @Override public ProblemReviewCard upsertForReview(long userId, String problemSlug, ReviewCardSource source, JsonNode sourceDetail, ReviewSeed seed) { throw unsupported(); }
    @Override public ProblemReviewCard mark(long userId, String problemSlug, ReviewCardSource source, JsonNode sourceDetail, Instant now) { throw unsupported(); }
    @Override public Optional<ProblemReviewCard> findByUserAndSlug(long userId, String problemSlug) { return Optional.empty(); }
    @Override public Optional<ProblemReviewCard> findForUser(long userId, long cardId) { return Optional.empty(); }
    @Override public Optional<ProblemReviewCard> findForUpdate(long userId, long cardId) { return Optional.empty(); }
    @Override public List<ProblemReviewCard> findDue(long userId, Instant now, int limit) { return List.of(); }
    @Override public List<ProblemReviewCard> list(long userId, ReviewCardSource source, boolean mistakeOnly, String keyword, int limit, int offset) { return cards; }
    @Override public int countDue(long userId, Instant now) { return 0; }
    @Override public int countScheduledBefore(long userId, Instant exclusiveEnd) { return 0; }
    @Override public Optional<Instant> findNextDueAt(long userId, Instant after, Instant exclusiveEnd) { return Optional.empty(); }
    @Override public ProblemReviewCard updateArchived(long userId, long cardId, boolean archived, Instant now) { throw unsupported(); }
    @Override public ProblemReviewCard updateScheduling(long userId, long cardId, SchedulingState state, Instant dueAt, ReviewRating lastRating, Instant reviewedAt) { throw unsupported(); }

    private UnsupportedOperationException unsupported() {
      return new UnsupportedOperationException();
    }
  }
}
