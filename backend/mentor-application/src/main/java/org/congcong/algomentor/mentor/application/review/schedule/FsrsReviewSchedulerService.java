package org.congcong.algomentor.mentor.application.review.schedule;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.CardAndReviewLog;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.State;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.review.card.ProblemReviewCard;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreference;

public class FsrsReviewSchedulerService {

  private final ReviewSchedulerProperties defaults;
  private final AnkiLearningPolicy learningPolicy;
  private final FsrsReviewBootstrap bootstrap;
  private final ReviewDayBoundary dayBoundary;

  public FsrsReviewSchedulerService(ReviewSchedulerProperties defaults) {
    this(defaults, new ReviewDayBoundary());
  }

  private FsrsReviewSchedulerService(ReviewSchedulerProperties defaults, ReviewDayBoundary dayBoundary) {
    this(defaults, new AnkiLearningPolicy(defaults), new FsrsReviewBootstrap(dayBoundary), dayBoundary);
  }

  FsrsReviewSchedulerService(
      ReviewSchedulerProperties defaults,
      AnkiLearningPolicy learningPolicy,
      FsrsReviewBootstrap bootstrap,
      ReviewDayBoundary dayBoundary
  ) {
    this.defaults = Objects.requireNonNull(defaults, "defaults must not be null");
    this.learningPolicy = Objects.requireNonNull(learningPolicy, "learningPolicy must not be null");
    this.bootstrap = Objects.requireNonNull(bootstrap, "bootstrap must not be null");
    this.dayBoundary = Objects.requireNonNull(dayBoundary, "dayBoundary must not be null");
  }

  public Scheduled apply(ProblemReviewCard card, ReviewRating rating, ReviewPreference preference, Instant now) {
    return apply(card, rating, preference, now, ZoneOffset.UTC);
  }

  public Scheduled apply(
      ProblemReviewCard card,
      ReviewRating rating,
      ReviewPreference preference,
      Instant now,
      ZoneId userZone
  ) {
    Objects.requireNonNull(card, "card must not be null");
    Objects.requireNonNull(rating, "rating must not be null");
    Objects.requireNonNull(now, "now must not be null");
    Objects.requireNonNull(userZone, "userZone must not be null");
    Scheduler scheduler = scheduler(preference);
    if (card.scheduling().fsrsState() == FsrsState.LEARNING) {
      return applyLearningCard(card, scheduler, rating, now, userZone);
    }
    CardAndReviewLog result = scheduler.reviewCard(toCard(card), rating.fsrsRating(), now);
    Card nextCard = result.card();
    int intervalDays = intervalDays(nextCard, now);
    SchedulingState nextState = toSchedulingState(card.scheduling(), nextCard, rating, intervalDays);
    Instant dueAt = nextState.fsrsState() == FsrsState.REVIEW
        ? reviewDueAt(now, intervalDays, userZone)
        : nextCard.getDue();
    return new Scheduled(nextState, dueAt, rating);
  }

  public ReviewIntervalPreview preview(
      ProblemReviewCard card,
      ReviewRating rating,
      ReviewPreference preference,
      Instant now
  ) {
    return preview(card, rating, preference, now, ZoneOffset.UTC);
  }

  public ReviewIntervalPreview preview(
      ProblemReviewCard card,
      ReviewRating rating,
      ReviewPreference preference,
      Instant now,
      ZoneId userZone
  ) {
    Scheduled scheduled = apply(card, rating, preference, now, userZone);
    return new ReviewIntervalPreview(rating, scheduled.dueAt(), scheduled.state().intervalDays());
  }

  private Scheduled applyLearningCard(
      ProblemReviewCard card,
      Scheduler scheduler,
      ReviewRating rating,
      Instant now,
      ZoneId userZone
  ) {
    SchedulingState previous = card.scheduling();
    int repetitions = repetitions(previous, rating);
    int lapses = lapses(previous, rating);
    AnkiLearningPolicy.Decision decision = learningPolicy.schedule(previous, rating);
    if (decision.graduates()) {
      FsrsReviewBootstrap.Bootstrap result = bootstrap.bootstrap(
          scheduler,
          toCard(card),
          rating,
          decision.graduatingIntervalDays(),
          repetitions,
          lapses,
          now,
          userZone);
      return new Scheduled(result.state(), result.dueAt(), rating);
    }
    SchedulingState nextState = new SchedulingState(
        repetitions,
        0,
        lapses,
        FsrsState.LEARNING,
        decision.nextStep(),
        null,
        null);
    return new Scheduled(nextState, now.plus(decision.delay()), rating);
  }

  private Scheduler scheduler(ReviewPreference preference) {
    ReviewPreference effective = preference == null
        ? ReviewPreference.defaults(0L, defaults, Instant.now())
        : preference;
    return Scheduler.builder()
        .desiredRetention(effective.desiredRetention().doubleValue())
        .learningSteps(effective.learningSteps())
        .relearningSteps(effective.relearningSteps())
        .maximumInterval(effective.maximumIntervalDays())
        .enableFuzzing(effective.enableFuzzing())
        .build();
  }

  private Card toCard(ProblemReviewCard reviewCard) {
    SchedulingState scheduling = reviewCard.scheduling();
    State state = State.valueOf(scheduling.fsrsState().name());
    if ((state == State.REVIEW || state == State.RELEARNING)
        && (scheduling.fsrsStability() == null || scheduling.fsrsDifficulty() == null)) {
      state = State.LEARNING;
    }
    return Card.builder()
        .cardId(cardId(reviewCard.id()))
        .state(state)
        .step(scheduling.fsrsStep())
        .stability(toDouble(scheduling.fsrsStability()))
        .difficulty(toDouble(scheduling.fsrsDifficulty()))
        .due(reviewCard.dueAt())
        .lastReview(reviewCard.lastReviewedAt())
        .build();
  }

  private SchedulingState toSchedulingState(
      SchedulingState previous,
      Card card,
      ReviewRating rating,
      int intervalDays
  ) {
    return new SchedulingState(
        repetitions(previous, rating),
        intervalDays,
        lapses(previous, rating),
        FsrsState.valueOf(card.getState().name()),
        card.getStep(),
        toBigDecimal(card.getStability()),
        toBigDecimal(card.getDifficulty()));
  }

  private int intervalDays(Card card, Instant now) {
    if (card.getState() != State.REVIEW) {
      return 0;
    }
    return Math.max(1, (int) ChronoUnit.DAYS.between(now, card.getDue()));
  }

  private Instant reviewDueAt(Instant now, int intervalDays, ZoneId userZone) {
    return dayBoundary.dueAt(now, intervalDays, userZone);
  }

  private int repetitions(SchedulingState previous, ReviewRating rating) {
    return rating == ReviewRating.AGAIN ? 0 : previous.repetitions() + 1;
  }

  private int lapses(SchedulingState previous, ReviewRating rating) {
    return previous.lapses() + (rating == ReviewRating.AGAIN && previous.repetitions() > 0 ? 1 : 0);
  }

  private int cardId(long reviewCardId) {
    long normalized = Math.abs(reviewCardId % Integer.MAX_VALUE);
    return normalized == 0 ? 1 : (int) normalized;
  }

  private Double toDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }

  private BigDecimal toBigDecimal(Double value) {
    return value == null ? null : BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
  }

  public record Scheduled(SchedulingState state, Instant dueAt, ReviewRating rating) {
  }

  public record ReviewIntervalPreview(ReviewRating rating, Instant dueAt, int intervalDays) {
  }
}
