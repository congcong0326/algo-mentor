package org.congcong.algomentor.mentor.application.practice;

public interface PracticeCodeReviewMetrics {

  PracticeCodeReviewMetrics NOOP = new PracticeCodeReviewMetrics() {
  };

  default void recordCompletionGate(PracticeCompletionGate gate) {
  }
}
