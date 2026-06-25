package org.congcong.algomentor.mentor.application.practice;

import java.util.Map;

public record PracticeTurnCapabilityResult(
    String capabilityName,
    PracticeReviewStatus status,
    Map<String, Object> metadata
) {

  public PracticeTurnCapabilityResult {
    if (capabilityName == null || capabilityName.isBlank()) {
      throw new IllegalArgumentException("Practice turn capability name must not be blank");
    }
    if (status == null) {
      throw new IllegalArgumentException("Practice turn capability status must not be null");
    }
    capabilityName = capabilityName.trim();
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
