package org.congcong.algomentor.mentor.application.profile.review.history;

import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewEvidence;

/** 单条正式 Review 的受限证据详情，不携带代码或完整 Review Markdown。 */
public record CodeReviewEvidenceDetail(
    CodeReviewHistory review,
    List<PracticeCodeReviewEvidence> detectionEvidence,
    String contextSummary
) {

  public CodeReviewEvidenceDetail {
    if (review == null) {
      throw new IllegalArgumentException("Code review evidence detail review must not be null");
    }
    detectionEvidence = detectionEvidence == null ? List.of() : List.copyOf(detectionEvidence);
    contextSummary = contextSummary == null ? "" : contextSummary.trim();
  }

  public long reviewId() {
    return review.reviewId();
  }

  public String problemSlug() {
    return review.problemSlug();
  }

  public int versionNo() {
    return review.versionNo();
  }

  public Instant createdAt() {
    return review.createdAt();
  }
}
