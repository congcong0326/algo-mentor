package org.congcong.algomentor.mentor.application.practice;

import java.math.BigDecimal;
import java.time.Instant;

public record PracticeCodeReviewSummary(
    long id,
    int versionNo,
    String language,
    BigDecimal totalScore,
    boolean passed,
    Instant createdAt
) {

  public PracticeCodeReviewSummary {
    if (id < 1) {
      throw new IllegalArgumentException("Practice code review id must be positive");
    }
    if (versionNo < 1) {
      throw new IllegalArgumentException("Practice code review version number must be positive");
    }
    if (language == null || language.isBlank()) {
      throw new IllegalArgumentException("Practice code review language must not be blank");
    }
    if (totalScore == null) {
      throw new IllegalArgumentException("Practice code review total score must not be null");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("Practice code review created time must not be null");
    }
    language = language.trim();
  }
}
