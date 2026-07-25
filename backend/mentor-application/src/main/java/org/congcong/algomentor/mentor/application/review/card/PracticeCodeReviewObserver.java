package org.congcong.algomentor.mentor.application.review.card;

import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;

public interface PracticeCodeReviewObserver {

  void onReviewSaved(PracticeCodeReview review);

  PracticeCodeReviewObserver NOOP = review -> {
  };
}
