package org.congcong.algomentor.mentor.application.profile.review;

/** Code Review 到学习者记忆 v2 队列的稳定消息契约。 */
public final class LearnerMemoryCodeReviewQueueContracts {

  public static final String TOPIC = "learner-memory.code-review.v2";
  public static final String JSON_REVIEW_ID = "reviewId";

  private LearnerMemoryCodeReviewQueueContracts() {
  }

  public static String keyForUser(long userId) {
    if (userId < 1) {
      throw new IllegalArgumentException("Code review profile event userId must be positive");
    }
    return Long.toString(userId);
  }
}
