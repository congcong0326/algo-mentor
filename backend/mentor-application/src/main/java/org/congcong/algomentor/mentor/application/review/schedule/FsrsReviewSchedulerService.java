package org.congcong.algomentor.mentor.application.review.schedule;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.CardAndReviewLog;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.State;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.review.card.ProblemReviewCard;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreference;

public class FsrsReviewSchedulerService {

  private final ReviewSchedulerProperties defaults;

  public FsrsReviewSchedulerService(ReviewSchedulerProperties defaults) {
    this.defaults = Objects.requireNonNull(defaults, "defaults must not be null");
  }

  public Scheduled apply(ProblemReviewCard card, ReviewRating rating, ReviewPreference preference, Instant now) {
    Objects.requireNonNull(card, "card must not be null");
    Objects.requireNonNull(rating, "rating must not be null");
    Objects.requireNonNull(now, "now must not be null");
    CardAndReviewLog result = scheduler(preference).reviewCard(toCard(card), rating.fsrsRating(), now);
    Card nextCard = result.card();
    SchedulingState nextState = toSchedulingState(card.scheduling(), nextCard, rating, now);
    return new Scheduled(nextState, nextCard.getDue(), rating);
  }

  public ReviewIntervalPreview preview(
      ProblemReviewCard card,
      ReviewRating rating,
      ReviewPreference preference,
      Instant now
  ) {
    Scheduled scheduled = apply(card, rating, preference, now);
    return new ReviewIntervalPreview(rating, scheduled.dueAt(), scheduled.state().intervalDays());
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
      Instant now
  ) {
    int intervalDays = Math.max(0, (int) ChronoUnit.DAYS.between(now, card.getDue()));
    int repetitions = rating == ReviewRating.AGAIN ? 0 : previous.repetitions() + 1;
    int lapses = previous.lapses() + (rating == ReviewRating.AGAIN && previous.repetitions() > 0 ? 1 : 0);
    return new SchedulingState(
        repetitions,
        intervalDays,
        lapses,
        FsrsState.valueOf(card.getState().name()),
        card.getStep(),
        toBigDecimal(card.getStability()),
        toBigDecimal(card.getDifficulty()));
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
