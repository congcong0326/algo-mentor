package org.congcong.algomentor.mentor.application.practice;

public record PracticeCodeReviewEvidence(String type, String value) {

  public PracticeCodeReviewEvidence {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("Practice code review evidence type must not be blank");
    }
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Practice code review evidence value must not be blank");
    }
    type = type.trim();
    value = value.trim();
  }
}
