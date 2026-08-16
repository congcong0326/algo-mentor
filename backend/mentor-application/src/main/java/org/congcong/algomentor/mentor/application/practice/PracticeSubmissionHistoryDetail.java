package org.congcong.algomentor.mentor.application.practice;

import java.util.Objects;

/** 单条正式 Review 的代码级展开数据，仅供已授权 detail Tool 使用。 */
public record PracticeSubmissionHistoryDetail(
    PracticeSubmissionHistoryReview review,
    String normalizedCode
) {

  public PracticeSubmissionHistoryDetail {
    review = Objects.requireNonNull(review, "review must not be null");
    if (normalizedCode == null || normalizedCode.isBlank()) {
      throw new IllegalArgumentException("Practice submission history detail code must not be blank");
    }
    normalizedCode = normalizedCode.strip();
  }
}
