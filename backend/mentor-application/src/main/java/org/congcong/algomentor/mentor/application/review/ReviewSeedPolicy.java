package org.congcong.algomentor.mentor.application.review;

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

  private static final BigDecimal DEFAULT_EASE_FACTOR = new BigDecimal("2.50");
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
      return seed(0, 0, MasteryState.NEW, now, ReviewSeedBucket.FAILED, false);
    }
    if (lowConfidence(review)) {
      int interval = properties.lowConfidenceIntervalDays();
      return seed(0, interval, MasteryState.LEARNING, now.plus(Duration.ofDays(interval)),
          ReviewSeedBucket.LOW_CONFIDENCE, true);
    }
    if (highScore(review) && !hard(difficulty)) {
      int interval = properties.passedHighScoreIntervalDays();
      return seed(1, interval, MasteryState.LEARNING, now.plus(Duration.ofDays(interval)),
          ReviewSeedBucket.HIGH_SCORE, false);
    }
    int interval = properties.passedFirstIntervalDays();
    return seed(1, interval, MasteryState.LEARNING, now.plus(Duration.ofDays(interval)),
        ReviewSeedBucket.NORMAL, false);
  }

  private ReviewSeed seed(
      int repetitions,
      int intervalDays,
      MasteryState masteryState,
      Instant dueAt,
      ReviewSeedBucket bucket,
      boolean lowConfidence
  ) {
    return new ReviewSeed(
        new SchedulingState(repetitions, DEFAULT_EASE_FACTOR, intervalDays, masteryState, 0),
        dueAt,
        bucket,
        lowConfidence);
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
