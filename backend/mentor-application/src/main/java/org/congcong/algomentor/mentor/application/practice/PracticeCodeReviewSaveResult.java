package org.congcong.algomentor.mentor.application.practice;

/** 正式 Review 保存结果；created 是队列发布的唯一可靠判断依据。 */
public record PracticeCodeReviewSaveResult(PracticeCodeReview review, boolean created) {
  public PracticeCodeReviewSaveResult {
    if (review == null) {
      throw new IllegalArgumentException("Practice code review save result requires a review");
    }
  }
}
