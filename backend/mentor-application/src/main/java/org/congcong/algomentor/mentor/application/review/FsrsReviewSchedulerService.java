package org.congcong.algomentor.mentor.application.review;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.CardAndReviewLog;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.State;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class FsrsReviewSchedulerService {

  private final ReviewSchedulerProperties defaults;

  public FsrsReviewSchedulerService(ReviewSchedulerProperties defaults) {
    this.defaults = Objects.requireNonNull(defaults, "defaults must not be null");
  }

  public Scheduled apply(MistakeNote note, ReviewRating rating, ReviewPreference preference, Instant now) {
    Objects.requireNonNull(note, "note must not be null");
    Objects.requireNonNull(rating, "rating must not be null");
    Objects.requireNonNull(now, "now must not be null");
    Scheduler scheduler = scheduler(preference);
    CardAndReviewLog result = scheduler.reviewCard(toCard(note), rating.fsrsRating(), now);
    Card nextCard = result.card();
    SchedulingState nextState = toSchedulingState(note.scheduling(), nextCard, rating, now);
    return new Scheduled(nextState, nextCard.getDue(), rating);
  }

  public ReviewIntervalPreview preview(MistakeNote note, ReviewRating rating, ReviewPreference preference, Instant now) {
    Scheduled scheduled = apply(note, rating, preference, now);
    return new ReviewIntervalPreview(rating, scheduled.dueAt(), scheduled.state().intervalDays());
  }

  public double retrievability(MistakeNote note, ReviewPreference preference, Instant now) {
    Card card = toCard(note);
    if (card.getLastReview() == null || card.getStability() == null) {
      return 0.0;
    }
    return scheduler(preference).getCardRetrievability(card, now);
  }

  private Scheduler scheduler(ReviewPreference preference) {
    ReviewPreference effective = preference == null ? ReviewPreference.defaults(0L, defaults) : preference;
    return Scheduler.builder()
        .desiredRetention(effective.desiredRetention().doubleValue())
        .learningSteps(effective.learningSteps())
        .relearningSteps(effective.relearningSteps())
        .maximumInterval(effective.maximumIntervalDays())
        .enableFuzzing(effective.enableFuzzing())
        .build();
  }

  private Card toCard(MistakeNote note) {
    SchedulingState state = note.scheduling();
    State fsrsState = fsrsState(state.fsrsState());
    if ((fsrsState == State.REVIEW || fsrsState == State.RELEARNING)
        && (state.fsrsStability() == null || state.fsrsDifficulty() == null)) {
      fsrsState = State.LEARNING;
    }
    return Card.builder()
        .cardId(cardId(note.id()))
        .state(fsrsState)
        .step(state.fsrsStep())
        .stability(toDouble(state.fsrsStability()))
        .difficulty(toDouble(state.fsrsDifficulty()))
        .due(note.dueAt())
        .lastReview(note.lastReviewedAt())
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
        card.getState().name(),
        card.getStep(),
        toBigDecimal(card.getStability()),
        toBigDecimal(card.getDifficulty()));
  }

  private int cardId(long noteId) {
    long normalized = Math.abs(noteId % Integer.MAX_VALUE);
    return normalized == 0 ? 1 : (int) normalized;
  }

  private State fsrsState(String value) {
    if (value == null || value.isBlank()) {
      return State.LEARNING;
    }
    try {
      return State.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return State.LEARNING;
    }
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
