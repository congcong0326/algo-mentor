package org.congcong.algomentor.mentor.application.review.card;

import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeedBucket;

public interface ReviewMetrics {

  void recordAttemptSubmit();

  void recordCardIngest(ReviewCardSource source, ReviewCardIngestOutcome outcome);

  void recordSeed(ReviewSeedBucket bucket);

  default void recordCodeReviewIndexQuery(int cardCount, int reviewCount, long elapsedNanos) {
  }

  default void recordCodeReviewIndexMissingHistory() {
  }

  ReviewMetrics NOOP = new ReviewMetrics() {
    @Override
    public void recordAttemptSubmit() {
    }

    @Override
    public void recordCardIngest(ReviewCardSource source, ReviewCardIngestOutcome outcome) {
    }

    @Override
    public void recordSeed(ReviewSeedBucket bucket) {
    }
  };
}
