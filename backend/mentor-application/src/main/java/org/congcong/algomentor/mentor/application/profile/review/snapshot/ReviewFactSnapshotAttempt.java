package org.congcong.algomentor.mentor.application.profile.review.snapshot;

import java.math.BigDecimal;
import java.time.Instant;

/** 快照中可读的单次正式 Review 摘要，不含代码和完整 Markdown。 */
public record ReviewFactSnapshotAttempt(
    long reviewId,
    int versionNo,
    boolean passed,
    BigDecimal score,
    String normalizedFindingSummary,
    Instant createdAt
) {

  public ReviewFactSnapshotAttempt {
    if (reviewId < 1 || versionNo < 1 || score == null || normalizedFindingSummary == null || createdAt == null) {
      throw new IllegalArgumentException("Review fact snapshot attempt is invalid");
    }
    normalizedFindingSummary = normalizedFindingSummary.trim();
  }
}
