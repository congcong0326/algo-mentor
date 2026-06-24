package org.congcong.algomentor.mentor.application.practice;

import java.time.Instant;

public record PracticeProgress(
    long id,
    long userId,
    long planId,
    int phaseIndex,
    String problemSlug,
    PracticeProgressStatus status,
    Instant createdAt,
    Instant updatedAt
) {

  public PracticeProgress {
    if (id < 1) {
      throw new IllegalArgumentException("Practice progress id must be positive");
    }
    if (userId < 1) {
      throw new IllegalArgumentException("Practice progress user id must be positive");
    }
    if (planId < 1) {
      throw new IllegalArgumentException("Practice progress plan id must be positive");
    }
    if (phaseIndex < 1) {
      throw new IllegalArgumentException("Practice progress phase index must be positive");
    }
    if (problemSlug == null || problemSlug.isBlank()) {
      throw new IllegalArgumentException("Practice progress problem slug must not be blank");
    }
    if (status == null) {
      throw new IllegalArgumentException("Practice progress status must not be null");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("Practice progress created time must not be null");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("Practice progress updated time must not be null");
    }
    problemSlug = problemSlug.trim();
  }
}
