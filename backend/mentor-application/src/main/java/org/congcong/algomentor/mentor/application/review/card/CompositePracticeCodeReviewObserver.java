package org.congcong.algomentor.mentor.application.review.card;

import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;

public class CompositePracticeCodeReviewObserver implements PracticeCodeReviewObserver {

  private final List<PracticeCodeReviewObserver> delegates;

  public CompositePracticeCodeReviewObserver(List<PracticeCodeReviewObserver> delegates) {
    this.delegates = delegates == null ? List.of() : List.copyOf(delegates);
  }

  @Override
  public void onReviewSaved(PracticeCodeReview review) {
    for (PracticeCodeReviewObserver delegate : delegates) {
      Objects.requireNonNull(delegate, "Practice code review observer must not be null").onReviewSaved(review);
    }
  }
}
