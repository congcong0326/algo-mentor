package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

public record PracticeCodeReviewHistory(
    PracticeCodeReviewSummary latestReview,
    List<PracticeCodeReviewSummary> reviews,
    PracticeCompletionGate completionGate
) {

  public PracticeCodeReviewHistory {
    reviews = reviews == null ? List.of() : List.copyOf(reviews);
    if (completionGate == null) {
      throw new IllegalArgumentException("Practice code review history completion gate must not be null");
    }
  }
}
