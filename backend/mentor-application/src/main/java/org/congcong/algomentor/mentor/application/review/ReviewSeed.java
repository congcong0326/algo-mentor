package org.congcong.algomentor.mentor.application.review;

import java.time.Instant;

public record ReviewSeed(
    SchedulingState state,
    Instant dueAt,
    ReviewSeedBucket bucket,
    boolean lowConfidence,
    ReviewRating initialRating
) {
  public ReviewSeed {
    if (state == null) {
      throw new IllegalArgumentException("Review seed scheduling state must not be null");
    }
    if (dueAt == null) {
      throw new IllegalArgumentException("Review seed due time must not be null");
    }
    if (bucket == null) {
      throw new IllegalArgumentException("Review seed bucket must not be null");
    }
    if (initialRating == null) {
      initialRating = ReviewRating.HARD;
    }
  }
}
