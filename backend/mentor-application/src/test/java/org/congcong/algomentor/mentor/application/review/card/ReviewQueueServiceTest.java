package org.congcong.algomentor.mentor.application.review.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.review.ReviewException;
import org.congcong.algomentor.mentor.application.review.attempt.ProblemReviewAttempt;
import org.congcong.algomentor.mentor.application.review.attempt.ReviewAttemptRepository;
import org.congcong.algomentor.mentor.application.review.catalog.ReviewProblemCatalog;
import org.congcong.algomentor.mentor.application.review.catalog.ReviewProblemSnapshot;
import org.congcong.algomentor.mentor.application.review.note.ProblemSolutionOutlineV1;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNote;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteRepository;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreferenceRepository;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreferenceService;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsReviewSchedulerService;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSchedulerProperties;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeed;
import org.congcong.algomentor.mentor.application.review.schedule.SchedulingState;
import org.junit.jupiter.api.Test;

class ReviewQueueServiceTest {

  private static final long USER_ID = 42L;
  private static final Instant NOW = Instant.parse("2026-07-24T15:50:00Z");
  private static final Instant SHANGHAI_TOMORROW_START = Instant.parse("2026-07-24T16:00:00Z");
  private static final Instant NEXT_DUE_AT = Instant.parse("2026-07-24T15:59:00Z");

  @Test
  void summaryDistinguishesCardsDueNowFromCardsDueLaterToday() {
    SummaryRepository repository = new SummaryRepository(0, 1, NEXT_DUE_AT);

    ReviewSummary summary = service(repository).summary(USER_ID, "Asia/Shanghai");

    assertThat(summary.dueCount()).isZero();
    assertThat(summary.remainingTodayCount()).isEqualTo(1);
    assertThat(summary.nextDueAt()).isEqualTo(NEXT_DUE_AT);
    assertThat(repository.dueNow).isEqualTo(NOW);
    assertThat(repository.summaryEnd).isEqualTo(SHANGHAI_TOMORROW_START);
    assertThat(repository.nextDueAfter).isEqualTo(NOW);
  }

  @Test
  void summaryRejectsInvalidTimezone() {
    assertThatThrownBy(() -> service(new SummaryRepository(0, 0, null))
        .summary(USER_ID, "Mars/Olympus"))
        .isInstanceOf(ReviewException.class)
        .hasMessage("时区参数无效。")
        .satisfies(exception -> assertThat(((ReviewException) exception).code())
            .isEqualTo("REVIEW_TIMEZONE_INVALID"));
  }

  @Test
  void contextPassesTheRequestedLocaleToTheProblemCatalog() {
    ProblemReviewCard card = card();
    RecordingProblemCatalog catalog = new RecordingProblemCatalog();

    ReviewCardContext context = service(
        new SummaryRepository(0, 0, null, card),
        catalog).context(USER_ID, card.id(), "en-US");

    assertThat(catalog.slug).isEqualTo("two-sum");
    assertThat(catalog.locale).isEqualTo("en-US");
    assertThat(context.problem().title()).isEqualTo("Two Sum");
  }

  private ReviewQueueService service(ReviewCardRepository repository) {
    return service(repository, (slug, locale) -> Optional.empty());
  }

  private ReviewQueueService service(
      ReviewCardRepository repository,
      ReviewProblemCatalog problemCatalog
  ) {
    ReviewSchedulerProperties properties = ReviewSchedulerProperties.defaults();
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    return new ReviewQueueService(
        repository,
        emptyAttemptRepository(),
        emptyNoteRepository(),
        problemCatalog,
        new FsrsReviewSchedulerService(properties),
        new ReviewPreferenceService(ReviewPreferenceRepository.empty(), properties, clock),
        properties,
        clock);
  }

  private ProblemReviewCard card() {
    return new ProblemReviewCard(
        88L,
        USER_ID,
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

  private ReviewAttemptRepository emptyAttemptRepository() {
    return new ReviewAttemptRepository() {
      @Override
      public Optional<ProblemReviewAttempt> findByUserAndClientAttemptId(long userId, UUID clientAttemptId) {
        return Optional.empty();
      }

      @Override
      public Optional<ProblemReviewAttempt> insertIfAbsent(ProblemReviewAttempt attempt) {
        return Optional.empty();
      }

      @Override
      public List<ProblemReviewAttempt> findRecent(long userId, long reviewCardId, int limit) {
        return List.of();
      }
    };
  }

  private UserProblemNoteRepository emptyNoteRepository() {
    return new UserProblemNoteRepository() {
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
          String noteMarkdown,
          long expectedRevision,
          Instant now
      ) {
        return Optional.empty();
      }

      @Override
      public boolean delete(long userId, String problemSlug) {
        return false;
      }
    };
  }

  private static final class SummaryRepository implements ReviewCardRepository {
    private final int dueCount;
    private final int remainingTodayCount;
    private final Instant nextDueAt;
    private final ProblemReviewCard card;
    private Instant dueNow;
    private Instant summaryEnd;
    private Instant nextDueAfter;

    private SummaryRepository(int dueCount, int remainingTodayCount, Instant nextDueAt) {
      this(dueCount, remainingTodayCount, nextDueAt, null);
    }

    private SummaryRepository(
        int dueCount,
        int remainingTodayCount,
        Instant nextDueAt,
        ProblemReviewCard card
    ) {
      this.dueCount = dueCount;
      this.remainingTodayCount = remainingTodayCount;
      this.nextDueAt = nextDueAt;
      this.card = card;
    }

    @Override
    public int countDue(long userId, Instant now) {
      dueNow = now;
      return dueCount;
    }

    @Override
    public int countScheduledBefore(long userId, Instant exclusiveEnd) {
      summaryEnd = exclusiveEnd;
      return remainingTodayCount;
    }

    @Override
    public Optional<Instant> findNextDueAt(long userId, Instant after, Instant exclusiveEnd) {
      nextDueAfter = after;
      summaryEnd = exclusiveEnd;
      return Optional.ofNullable(nextDueAt);
    }

    @Override public ProblemReviewCard upsertForReview(long userId, String slug, ReviewCardSource source, JsonNode detail, ReviewSeed seed) { throw new UnsupportedOperationException(); }
    @Override public ProblemReviewCard mark(long userId, String slug, ReviewCardSource source, JsonNode detail, Instant now) { throw new UnsupportedOperationException(); }
    @Override public Optional<ProblemReviewCard> findByUserAndSlug(long userId, String slug) { return Optional.empty(); }
    @Override public Optional<ProblemReviewCard> findForUser(long userId, long cardId) {
      return card != null && card.userId() == userId && card.id() == cardId ? Optional.of(card) : Optional.empty();
    }
    @Override public Optional<ProblemReviewCard> findForUpdate(long userId, long cardId) { return Optional.empty(); }
    @Override public List<ProblemReviewCard> findDue(long userId, Instant now, int limit) { return List.of(); }
    @Override public List<ProblemReviewCard> list(long userId, ReviewCardSource source, boolean mistakeOnly, String keyword, int limit, int offset) { return List.of(); }
    @Override public ProblemReviewCard updateArchived(long userId, long cardId, boolean archived, Instant now) { throw new UnsupportedOperationException(); }
    @Override public ProblemReviewCard updateScheduling(long userId, long cardId, SchedulingState state, Instant dueAt, ReviewRating rating, Instant reviewedAt) { throw new UnsupportedOperationException(); }
  }

  private static final class RecordingProblemCatalog implements ReviewProblemCatalog {
    private String slug;
    private String locale;

    @Override
    public Optional<ReviewProblemSnapshot> findBySlug(String slug, String locale) {
      this.slug = slug;
      this.locale = locale;
      return Optional.of(new ReviewProblemSnapshot(
          slug,
          "Two Sum",
          "EASY",
          "English statement.",
          "# Two Sum\n\nEnglish statement."));
    }
  }
}
