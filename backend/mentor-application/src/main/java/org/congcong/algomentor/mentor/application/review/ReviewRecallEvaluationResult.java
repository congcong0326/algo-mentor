package org.congcong.algomentor.mentor.application.review;

import java.util.List;

public record ReviewRecallEvaluationResult(
    ReviewRecallEvaluation evaluation,
    List<FsrsReviewSchedulerService.ReviewIntervalPreview> intervals
) {
  public ReviewRecallEvaluationResult {
    intervals = intervals == null ? List.of() : List.copyOf(intervals);
  }
}
