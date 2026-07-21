package org.congcong.algomentor.mentor.application.review;

import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;

/** 正式 Review 提交完成后的 best-effort Observer，绝不能参与关键事务。 */
public interface PracticeCodeReviewObserver {

  void onReviewSaved(PracticeCodeReview review);

  PracticeCodeReviewObserver NOOP = review -> {
  };
}
