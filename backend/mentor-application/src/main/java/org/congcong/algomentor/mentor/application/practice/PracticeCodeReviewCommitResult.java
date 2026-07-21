package org.congcong.algomentor.mentor.application.practice;

/** Review 关键提交结果；复用记录没有新建队列消息。 */
public record PracticeCodeReviewCommitResult(
    PracticeCodeReview review,
    boolean created,
    Long queueMessageId
) {
  public PracticeCodeReviewCommitResult {
    if (review == null || (created && (queueMessageId == null || queueMessageId < 1))
        || (!created && queueMessageId != null)) {
      throw new IllegalArgumentException("Invalid practice code review commit result");
    }
  }
}
