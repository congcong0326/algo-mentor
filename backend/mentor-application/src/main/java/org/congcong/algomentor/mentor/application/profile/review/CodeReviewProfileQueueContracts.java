package org.congcong.algomentor.mentor.application.profile.review;

/** Code Review 到学习者画像队列的稳定消息契约。 */
public final class CodeReviewProfileQueueContracts {

  public static final String TOPIC = "learner-profile.code-review.v1";
  public static final String JSON_REVIEW_ID = "reviewId";

  private CodeReviewProfileQueueContracts() {
  }

  public static String keyForUser(long userId) {
    if (userId < 1) {
      throw new IllegalArgumentException("Code review profile event userId must be positive");
    }
    return Long.toString(userId);
  }
}
