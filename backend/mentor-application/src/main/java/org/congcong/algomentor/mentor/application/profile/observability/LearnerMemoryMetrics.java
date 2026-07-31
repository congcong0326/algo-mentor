package org.congcong.algomentor.mentor.application.profile.observability;

import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;

/** AI 记忆的低基数观测端口；调用方不得传入用户、claim 或内容标识。 */
public interface LearnerMemoryMetrics {

  LearnerMemoryMetrics NOOP = new LearnerMemoryMetrics() {
  };

  default void recordUpdateRun(
      LearnerMemoryRunContract.Trigger trigger,
      LearnerMemoryRunContract.Status status) {
  }

  default void recordOperation(
      LearnerMemoryClaimContract.OperationAction action,
      LearnerMemoryClaimContract.Kind kind,
      LearnerMemoryClaimContract.Dimension dimension) {
  }

  default void recordToolCall(String purpose, String tool, String status) {
  }

  default void recordEvidence(String pattern, String grade, int count) {
  }

  default void recordInvalidOutput(String reason) {
  }

  default void recordActiveClaimCount(
      LearnerMemoryClaimContract.Kind kind,
      LearnerMemoryClaimContract.Dimension dimension,
      int count) {
  }

  default void recordActiveLimit(String level) {
  }

  default void recordRecall(String scenario, LearnerMemoryClaimContract.Kind kind) {
  }

  default void recordBootstrap(String scenario, int tokenEstimate, int directClaimCount, boolean trimmed) {
  }

  default void recordRecallToolResultChars(String tool, int chars) {
  }

  default void recordRecallRangeRead(String status) {
  }

  default void recordProfileProjection(String status, String projectorVersion, int citationCount) {
  }
}
