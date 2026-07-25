package org.congcong.algomentor.mentor.application.review.card;

import java.time.Instant;
import java.util.Map;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.congcong.algomentor.mentor.application.review.schedule.SchedulingState;

public record ProblemReviewCard(
    long id,
    long userId,
    String problemSlug,
    ReviewCardSource source,
    Map<String, Object> sourceDetail,
    SchedulingState scheduling,
    Instant dueAt,
    Instant lastReviewedAt,
    ReviewRating lastRating,
    boolean archived,
    Instant createdAt,
    Instant updatedAt
) {
  public ProblemReviewCard {
    sourceDetail = sourceDetail == null ? Map.of() : Map.copyOf(sourceDetail);
  }
}
