package org.congcong.algomentor.mentor.application.practice;

import java.time.Instant;

/** 用户在一道题上的最新正式 Review 索引行。 */
public record PracticeSubmissionHistoryProblem(
    long reviewId,
    String problemSlug,
    String reviewHistorySummary,
    Instant createdAt
) {

  public PracticeSubmissionHistoryProblem {
    if (reviewId < 1 || problemSlug == null || problemSlug.isBlank() || createdAt == null) {
      throw new IllegalArgumentException("Practice submission history problem is invalid");
    }
    problemSlug = problemSlug.trim();
    reviewHistorySummary = reviewHistorySummary == null ? null : reviewHistorySummary.replaceAll("\\s+", " ").trim();
    if (reviewHistorySummary != null && reviewHistorySummary.isEmpty()) {
      reviewHistorySummary = null;
    }
  }
}
