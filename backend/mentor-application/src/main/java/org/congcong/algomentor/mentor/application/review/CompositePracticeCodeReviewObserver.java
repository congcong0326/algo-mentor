package org.congcong.algomentor.mentor.application.review;

import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;

/** 仅组合提交后 best-effort Observer，不能承载关键持久化副作用。 */
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
