package org.congcong.algomentor.mentor.application.profile.review;

/** 学习者记忆批量更新的受控结果，消费者据此记录 outcome，不影响已出队语义。 */
public record LearnerMemoryCodeReviewUpdateResult(
    Status status,
    Long updateRunId,
    int windowProblemCount,
    int appliedCount
) {

  public LearnerMemoryCodeReviewUpdateResult(Status status, int windowProblemCount, int appliedCount) {
    this(status, null, windowProblemCount, appliedCount);
  }

  public enum Status {
    UPDATED,
    NO_CHANGE,
    FAILED
  }

  public LearnerMemoryCodeReviewUpdateResult {
    if (status == null || (updateRunId != null && updateRunId < 1)
        || windowProblemCount < 0 || appliedCount < 0) {
      throw new IllegalArgumentException("Invalid code review profile update result");
    }
  }
}
