package org.congcong.algomentor.mentor.application.review.schedule;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.CardAndReviewLog;
import io.github.openspacedrepetition.Scheduler;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewConstants;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewEvidence;

public class ReviewSeedPolicy {

  private static final BigDecimal MAX_TOTAL_SCORE = BigDecimal.TEN;

  private final ReviewSchedulerProperties properties;

  public ReviewSeedPolicy(ReviewSchedulerProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  public ReviewSeed forReview(PracticeCodeReview review, boolean passed, Instant now) {
    return forReview(review, passed, now, null);
  }

  public ReviewSeed forReview(PracticeCodeReview review, boolean passed, Instant now, String difficulty) {
    Objects.requireNonNull(review, "review must not be null");
    Objects.requireNonNull(now, "now must not be null");
    if (!passed) {
      return new ReviewSeed(
          SchedulingState.initial(),
          now,
          ReviewSeedBucket.FAILED,
          false,
          ReviewRating.AGAIN);
    }
    if (lowConfidence(review)) {
      return fsrsSeed(ReviewRating.HARD, ReviewSeedBucket.LOW_CONFIDENCE, true, now);
    }
    if (highScore(review) && !hard(difficulty)) {
      return fsrsSeed(ReviewRating.EASY, ReviewSeedBucket.HIGH_SCORE, false, now);
    }
    return fsrsSeed(ReviewRating.GOOD, ReviewSeedBucket.NORMAL, false, now);
  }

  private ReviewSeed fsrsSeed(ReviewRating rating, ReviewSeedBucket bucket, boolean lowConfidence, Instant now) {
    Scheduler scheduler = Scheduler.builder()
        .desiredRetention(properties.desiredRetention().doubleValue())
        .learningSteps(new Duration[] {})
        .relearningSteps(new Duration[] {})
        .maximumInterval(properties.maximumIntervalDays())
        .enableFuzzing(false)
        .build();
    CardAndReviewLog result = scheduler.reviewCard(Card.builder().cardId(1).due(now).build(), rating.fsrsRating(), now);
    Card card = result.card();
    int interval = Math.max(1, (int) java.time.temporal.ChronoUnit.DAYS.between(now, card.getDue()));
    SchedulingState state = new SchedulingState(
        1,
        interval,
        0,
        FsrsState.valueOf(card.getState().name()),
        card.getStep(),
        BigDecimal.valueOf(card.getStability()).setScale(6, RoundingMode.HALF_UP),
        BigDecimal.valueOf(card.getDifficulty()).setScale(6, RoundingMode.HALF_UP));
    return new ReviewSeed(state, card.getDue(), bucket, lowConfidence, rating);
  }

  private boolean highScore(PracticeCodeReview review) {
    BigDecimal ratio = review.score().total().divide(MAX_TOTAL_SCORE, 4, RoundingMode.HALF_UP);
    return ratio.compareTo(properties.highScoreRatio()) >= 0
        && review.score().total().compareTo(PracticeCodeReviewConstants.PASS_SCORE) >= 0;
  }

  private boolean hard(String difficulty) {
    return difficulty != null && "HARD".equals(difficulty.trim().toUpperCase(Locale.ROOT));
  }

  private boolean lowConfidence(PracticeCodeReview review) {
    return review.evidence().stream().anyMatch(this::lowConfidenceEvidence);
  }

  private boolean lowConfidenceEvidence(PracticeCodeReviewEvidence evidence) {
    if (evidence == null) {
      return false;
    }
    String type = normalize(evidence.type());
    String value = normalize(evidence.value());
    return type.contains("LOW_CONFIDENCE")
        || type.contains("HINT")
        || type.contains("PASTE")
        || type.contains("AI_ASSIST")
        || value.contains("LOW_CONFIDENCE")
        || value.contains("HINT")
        || value.contains("PASTE")
        || value.contains("AI_ASSIST");
  }

  private String normalize(String value) {
    return value == null ? "" : value.toUpperCase(Locale.ROOT);
  }
}
