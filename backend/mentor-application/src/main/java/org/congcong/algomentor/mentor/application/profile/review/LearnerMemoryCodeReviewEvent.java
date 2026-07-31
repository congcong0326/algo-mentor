package org.congcong.algomentor.mentor.application.profile.review;

import com.fasterxml.jackson.annotation.JsonProperty;

/** v2 队列 payload，仅传递后续消费所需的 Review 标识。 */
public record LearnerMemoryCodeReviewEvent(
    @JsonProperty(LearnerMemoryCodeReviewQueueContracts.JSON_REVIEW_ID) long reviewId
) {
  public LearnerMemoryCodeReviewEvent {
    if (reviewId < 1) {
      throw new IllegalArgumentException("Code review profile event reviewId must be positive");
    }
  }
}
