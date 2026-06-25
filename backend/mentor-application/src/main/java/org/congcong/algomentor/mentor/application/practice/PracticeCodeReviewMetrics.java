package org.congcong.algomentor.mentor.application.practice;

import java.time.Duration;

public interface PracticeCodeReviewMetrics {

  PracticeCodeReviewMetrics NOOP = new PracticeCodeReviewMetrics() {
  };

  default void recordCapability(
      boolean codeSubmissionCandidate,
      PracticeReviewStatus status,
      String failureCode,
      Duration duration
  ) {
  }

  default void recordCompletionGate(PracticeCompletionGate gate) {
  }
}
