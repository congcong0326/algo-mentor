package org.congcong.algomentor.mentor.application.profile.evidence.model;

import java.time.Instant;

/** Claim revision 使用的一条正式 Code Review 证据。 */
public record LearnerMemoryClaimReviewEvidence(
    long claimRevisionId,
    long reviewId,
    LearnerMemoryEvidenceContract.ReviewRole role,
    int sequenceNo,
    Instant createdAt
) {

  public LearnerMemoryClaimReviewEvidence {
    requirePositive(claimRevisionId, "claim revision id");
    requirePositive(reviewId, "review id");
    if (role == null || sequenceNo <= 0 || createdAt == null) {
      throw new IllegalArgumentException("review evidence 字段非法。");
    }
  }

  private static void requirePositive(long value, String field) {
    if (value <= 0) {
      throw new IllegalArgumentException(field + " 必须为正数。");
    }
  }
}
