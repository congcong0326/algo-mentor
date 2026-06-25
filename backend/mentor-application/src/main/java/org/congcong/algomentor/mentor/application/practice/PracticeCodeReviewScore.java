package org.congcong.algomentor.mentor.application.practice;

import java.math.BigDecimal;

public record PracticeCodeReviewScore(
    BigDecimal correctness,
    BigDecimal complexity,
    BigDecimal edgeCases,
    BigDecimal codeQuality,
    BigDecimal problemFit,
    BigDecimal total
) {

  public PracticeCodeReviewScore {
    requireScore(correctness, "correctness");
    requireScore(complexity, "complexity");
    requireScore(edgeCases, "edge cases");
    requireScore(codeQuality, "code quality");
    requireScore(problemFit, "problem fit");
    requireScore(total, "total");
  }

  private static void requireScore(BigDecimal value, String fieldName) {
    if (value == null) {
      throw new IllegalArgumentException("Practice code review " + fieldName + " score must not be null");
    }
    if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.TEN) > 0) {
      throw new IllegalArgumentException("Practice code review " + fieldName + " score must be between 0 and 10");
    }
  }
}
