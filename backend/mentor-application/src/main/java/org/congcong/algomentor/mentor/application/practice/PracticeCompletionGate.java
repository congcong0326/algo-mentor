package org.congcong.algomentor.mentor.application.practice;

import java.math.BigDecimal;
import java.util.Optional;

public record PracticeCompletionGate(
    boolean canComplete,
    ReasonCode reasonCode,
    String message,
    Optional<BigDecimal> latestScore,
    BigDecimal passScore
) {

  public PracticeCompletionGate {
    if (reasonCode == null) {
      throw new IllegalArgumentException("Practice completion gate reason code must not be null");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("Practice completion gate message must not be blank");
    }
    if (passScore == null) {
      throw new IllegalArgumentException("Practice completion gate pass score must not be null");
    }
    message = message.trim();
    latestScore = latestScore == null ? Optional.empty() : latestScore;
  }

  public enum ReasonCode {
    NO_REVIEW,
    LATEST_REVIEW_FAILED,
    PASSED,
    ALREADY_COMPLETED
  }
}
