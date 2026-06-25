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
    requireScore(correctness, "correctness", new BigDecimal("4"));
    requireScore(complexity, "complexity", new BigDecimal("2"));
    requireScore(edgeCases, "edge cases", new BigDecimal("2"));
    requireScore(codeQuality, "code quality", BigDecimal.ONE);
    requireScore(problemFit, "problem fit", BigDecimal.ONE);
    requireScore(total, "total", BigDecimal.TEN);
  }

  private static void requireScore(BigDecimal value, String fieldName, BigDecimal maximum) {
    if (value == null) {
      throw new IllegalArgumentException("Practice code review " + fieldName + " score must not be null");
    }
    if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(maximum) > 0) {
      throw new IllegalArgumentException(
          "Practice code review " + fieldName + " score must be between 0 and " + maximum);
    }
  }
}
