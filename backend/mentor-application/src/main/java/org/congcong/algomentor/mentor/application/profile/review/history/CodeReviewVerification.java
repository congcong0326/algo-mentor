package org.congcong.algomentor.mentor.application.profile.review.history;

import java.time.Instant;
import java.util.List;

/** 后续 evidence 校验复核的 Review 归属、范围和时间事实。 */
public record CodeReviewVerification(
    long reviewId,
    String problemSlug,
    int versionNo,
    List<Long> affectedTagIds,
    Instant createdAt
) {

  public CodeReviewVerification {
    if (reviewId < 1 || problemSlug == null || problemSlug.isBlank() || versionNo < 1 || createdAt == null) {
      throw new IllegalArgumentException("Invalid code review verification");
    }
    problemSlug = problemSlug.trim();
    affectedTagIds = affectedTagIds == null ? List.of() : affectedTagIds.stream()
        .filter(tagId -> tagId != null && tagId > 0).distinct().sorted().toList();
  }
}
