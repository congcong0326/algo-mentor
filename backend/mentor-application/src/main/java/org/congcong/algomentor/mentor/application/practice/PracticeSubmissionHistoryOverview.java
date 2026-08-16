package org.congcong.algomentor.mentor.application.practice;

import java.time.Instant;
import java.util.Objects;

/** 同一用户、同一题跨计划正式提交的聚合总览。 */
public record PracticeSubmissionHistoryOverview(
    long formalSubmissionCount,
    long passedSubmissionCount,
    Instant firstSubmittedAt,
    PracticeSubmissionHistoryReview latestSubmission
) {

  public PracticeSubmissionHistoryOverview {
    if (formalSubmissionCount < 1 || passedSubmissionCount < 0 || passedSubmissionCount > formalSubmissionCount
        || firstSubmittedAt == null) {
      throw new IllegalArgumentException("Practice submission history overview is invalid");
    }
    latestSubmission = Objects.requireNonNull(latestSubmission, "latest submission must not be null");
  }
}
