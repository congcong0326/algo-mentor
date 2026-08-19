package org.congcong.algomentor.mentor.application.profile.review.snapshot;

import java.util.List;
import java.util.Objects;

/** 单题全量 Review 的压缩轨迹；保留最近版本详情，早期版本以计数参与聚合。 */
public record ProblemReviewTrajectory(
    String problemSlug,
    String problemTitle,
    List<Long> tagIds,
    int attemptCount,
    int passedAttemptCount,
    int failedAttemptCount,
    int functionalFailureCount,
    int recoveredFailureCount,
    int unresolvedFailureCount,
    int compressedEarlierAttemptCount,
    List<ReviewFactSnapshotAttempt> attempts,
    CurrentStatus currentStatus
) {

  public ProblemReviewTrajectory(
      String problemSlug,
      List<Long> tagIds,
      int attemptCount,
      int passedAttemptCount,
      int failedAttemptCount,
      int functionalFailureCount,
      int recoveredFailureCount,
      int unresolvedFailureCount,
      int compressedEarlierAttemptCount,
      List<ReviewFactSnapshotAttempt> attempts,
      CurrentStatus currentStatus
  ) {
    this(
        problemSlug, problemSlug, tagIds, attemptCount, passedAttemptCount, failedAttemptCount,
        functionalFailureCount, recoveredFailureCount, unresolvedFailureCount, compressedEarlierAttemptCount,
        attempts, currentStatus);
  }

  public ProblemReviewTrajectory {
    if (problemSlug == null || problemSlug.isBlank() || problemTitle == null || problemTitle.isBlank() || attemptCount < 1
        || passedAttemptCount < 0
        || failedAttemptCount < 0 || attemptCount != passedAttemptCount + failedAttemptCount
        || functionalFailureCount != failedAttemptCount || recoveredFailureCount < 0 || unresolvedFailureCount < 0
        || functionalFailureCount != recoveredFailureCount + unresolvedFailureCount || compressedEarlierAttemptCount < 0
        || currentStatus == null) {
      throw new IllegalArgumentException("Problem review trajectory is invalid");
    }
    problemSlug = problemSlug.trim();
    problemTitle = problemTitle.trim();
    tagIds = normalizedTagIds(tagIds);
    if (attempts == null || attempts.isEmpty() || attempts.stream().anyMatch(Objects::isNull)
        || attempts.size() + compressedEarlierAttemptCount != attemptCount) {
      throw new IllegalArgumentException("Problem review trajectory attempts are invalid");
    }
    attempts = List.copyOf(attempts);
  }

  public enum CurrentStatus {
    PASSED_FIRST_ATTEMPT,
    RECOVERED,
    RECOVERED_AFTER_REGRESSION,
    ACTIVE_RISK
  }

  public ReviewFactSnapshotAttempt latestAttempt() {
    return attempts.get(attempts.size() - 1);
  }

  public ReviewFactSnapshotAttempt firstRetainedAttempt() {
    return attempts.get(0);
  }

  private static List<Long> normalizedTagIds(List<Long> values) {
    return values == null ? List.of() : values.stream()
        .filter(value -> value != null && value > 0).distinct().sorted().toList();
  }
}
