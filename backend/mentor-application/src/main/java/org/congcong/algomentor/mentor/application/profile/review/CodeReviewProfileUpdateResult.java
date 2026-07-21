package org.congcong.algomentor.mentor.application.profile.review;

/** 画像批量观察的受控结果，消费者据此记录 outcome，不影响已出队语义。 */
public record CodeReviewProfileUpdateResult(Status status, int windowProblemCount, int appliedCount) {

  public enum Status {
    UPDATED,
    NO_CHANGE,
    FAILED
  }

  public CodeReviewProfileUpdateResult {
    if (status == null || windowProblemCount < 0 || appliedCount < 0) {
      throw new IllegalArgumentException("Invalid code review profile update result");
    }
  }
}
