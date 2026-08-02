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
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewEvidence;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;
import org.congcong.algomentor.mentor.application.review.catalog.ReviewProblemSnapshot;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSchedulerProperties;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeed;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeedPolicy;
import org.congcong.algomentor.mentor.application.review.schedule.SchedulingState;
import org.junit.jupiter.api.Test;

class ReviewCardServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-24T08:00:00Z");

  @Test
  void codeReviewIngestionOnlyWritesTheReviewCardAggregate() {
    RecordingRepository repository = new RecordingRepository();
    ReviewCardService service = new ReviewCardService(
        repository,
        new ReviewSeedPolicy(ReviewSchedulerProperties.defaults()),
        new ObjectMapper(),
        ReviewMetrics.NOOP,
        (slug, locale) -> Optional.of(
            new ReviewProblemSnapshot(slug, "两数之和", "EASY", "题面摘要", "完整题面")),
        Clock.fixed(NOW, ZoneOffset.UTC));

    service.ingestFromReview(review());

    assertThat(repository.upsertCalls).isEqualTo(1);
    assertThat(repository.source).isEqualTo(ReviewCardSource.REVIEW_FAILED);
    assertThat(repository.sourceDetail.path(ReviewContractConstants.METADATA_LATEST_REVIEW_ID).asLong())
        .isEqualTo(900L);
    assertThat(repository.sourceDetail.has("noteMarkdown")).isFalse();
    assertThat(repository.sourceDetail.has("outline")).isFalse();
  }

  private PracticeCodeReview review() {
    return new PracticeCodeReview(
        900L,
        42L,
        12L,
        1,
        "two-sum",
        50L,
        1,
        701L,
        702L,
        501L,
        "class Solution {}",
        "class Solution {}",
        "java",
        List.of(new PracticeCodeReviewEvidence("ENTRY_FUNCTION", "twoSum")),
        "",
        new PracticeCodeReviewScore(
            new BigDecimal("2"),
            new BigDecimal("1"),
            new BigDecimal("1"),
            BigDecimal.ONE,
            BigDecimal.ONE,
            new BigDecimal("5")),
        false,
        List.of("未通过"),
        List.of("修复边界"),
        "需要修改。",
        NOW);
  }

  private static final class RecordingRepository implements ReviewCardRepository {
    private int upsertCalls;
    private ReviewCardSource source;
    private JsonNode sourceDetail;

    @Override
    public ProblemReviewCard upsertForReview(
        long userId,
        String problemSlug,
        ReviewCardSource source,
        JsonNode sourceDetail,
        ReviewSeed seed
    ) {
      upsertCalls++;
      this.source = source;
      this.sourceDetail = sourceDetail;
      return new ProblemReviewCard(
          88L,
          userId,
          problemSlug,
          source,
          Map.of(),
          seed.state(),
          seed.dueAt(),
          null,
          null,
          false,
          NOW,
          NOW);
    }

    @Override public Optional<ProblemReviewCard> findByUserAndSlug(long userId, String problemSlug) { return Optional.empty(); }
    @Override public ProblemReviewCard mark(long userId, String slug, ReviewCardSource source, JsonNode detail, Instant now) { throw new UnsupportedOperationException(); }
    @Override public Optional<ProblemReviewCard> findForUser(long userId, long cardId) { return Optional.empty(); }
    @Override public Optional<ProblemReviewCard> findForUpdate(long userId, long cardId) { return Optional.empty(); }
    @Override public List<ProblemReviewCard> findDue(long userId, Instant now, int limit) { return List.of(); }
    @Override public List<ProblemReviewCard> list(long userId, ReviewCardSource source, boolean mistakeOnly, String keyword, int limit, int offset) { return List.of(); }
    @Override public int countDue(long userId, Instant now) { return 0; }
    @Override public int countScheduledBefore(long userId, Instant exclusiveEnd) { return 0; }
    @Override public Optional<Instant> findNextDueAt(long userId, Instant after, Instant exclusiveEnd) { return Optional.empty(); }
    @Override public ProblemReviewCard updateArchived(long userId, long cardId, boolean archived, Instant now) { throw new UnsupportedOperationException(); }
    @Override public ProblemReviewCard updateScheduling(long userId, long cardId, SchedulingState state, Instant dueAt, ReviewRating rating, Instant reviewedAt) { throw new UnsupportedOperationException(); }
  }
}
