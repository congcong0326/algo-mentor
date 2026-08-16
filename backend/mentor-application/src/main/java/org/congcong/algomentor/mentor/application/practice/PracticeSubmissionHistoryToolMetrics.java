package org.congcong.algomentor.mentor.application.practice;

/** 历史正式提交 Tool 的低基数观测端口，不接收用户或业务数据标识。 */
public interface PracticeSubmissionHistoryToolMetrics {

  PracticeSubmissionHistoryToolMetrics NOOP = new PracticeSubmissionHistoryToolMetrics() {
  };

  default void recordToolCall(String tool, String status) {
  }

  default void recordScopeRejected(String reasonCategory) {
  }

  default void recordCodeIntentRejected() {
  }

  default void recordDetailVisibleChars(int chars) {
  }

  default void recordDetailRangeRead(String status) {
  }

  default void recordToolDataAccessFailure() {
  }
}
