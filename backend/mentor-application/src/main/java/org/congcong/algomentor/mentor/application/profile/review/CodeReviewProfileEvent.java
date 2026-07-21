package org.congcong.algomentor.mentor.application.profile.review;

import com.fasterxml.jackson.annotation.JsonProperty;

/** v1 队列 payload，仅传递后续消费所需的 Review 标识。 */
public record CodeReviewProfileEvent(
    @JsonProperty(CodeReviewProfileQueueContracts.JSON_REVIEW_ID) long reviewId
) {
  public CodeReviewProfileEvent {
    if (reviewId < 1) {
      throw new IllegalArgumentException("Code review profile event reviewId must be positive");
    }
  }
}
