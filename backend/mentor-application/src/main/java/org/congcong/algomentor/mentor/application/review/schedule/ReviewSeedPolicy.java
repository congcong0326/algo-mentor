package org.congcong.algomentor.mentor.application.review.schedule;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Scheduler;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewConstants;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewEvidence;

public class ReviewSeedPolicy {

  private static final BigDecimal MAX_TOTAL_SCORE = BigDecimal.TEN;

  private final ReviewSchedulerProperties properties;
  private final FsrsReviewBootstrap bootstrap;

  public ReviewSeedPolicy(ReviewSchedulerProperties properties) {
    this(properties, new FsrsReviewBootstrap(new ReviewDayBoundary()));
  }

  ReviewSeedPolicy(ReviewSchedulerProperties properties, FsrsReviewBootstrap bootstrap) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.bootstrap = Objects.requireNonNull(bootstrap, "bootstrap must not be null");
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
          null,
          ReviewSeedBucket.FAILED,
          false,
          ReviewRating.AGAIN);
    }
    if (lowConfidence(review)) {
      return fsrsSeed(
          ReviewRating.HARD,
          properties.lowConfidenceFirstIntervalDays(),
          ReviewSeedBucket.LOW_CONFIDENCE,
          true,
          now);
    }
    if (highScore(review) && !hard(difficulty)) {
      return fsrsSeed(
          ReviewRating.EASY,
          properties.passedHighScoreIntervalDays(),
          ReviewSeedBucket.HIGH_SCORE,
          false,
          now);
    }
    return fsrsSeed(
        ReviewRating.GOOD,
        properties.passedFirstIntervalDays(),
        ReviewSeedBucket.NORMAL,
        false,
        now);
  }

  private ReviewSeed fsrsSeed(
      ReviewRating rating,
      int intervalDays,
      ReviewSeedBucket bucket,
      boolean lowConfidence,
      Instant now
  ) {
    Scheduler scheduler = Scheduler.builder()
        .desiredRetention(properties.desiredRetention().doubleValue())
        .maximumInterval(properties.maximumIntervalDays())
        .enableFuzzing(false)
        .build();
    FsrsReviewBootstrap.Bootstrap result = bootstrap.bootstrap(
        scheduler,
        Card.builder().cardId(1).due(now).build(),
        rating,
        intervalDays,
        1,
        0,
        now,
        ZoneOffset.UTC);
    return new ReviewSeed(result.state(), result.dueAt(), result.reviewedAt(), bucket, lowConfidence, rating);
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
