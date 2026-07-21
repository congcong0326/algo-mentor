package org.congcong.algomentor.mentor.application.profile.review;

import java.time.Duration;

/** Code Review 画像消费者的低基数观测端口，不接受用户或消息标识。 */
public interface CodeReviewProfileMetrics {

  CodeReviewProfileMetrics NOOP = new CodeReviewProfileMetrics() {
  };

  default void recordResult(CodeReviewProfileUpdateResult result, Duration duration) {
  }

  default void recordCallbackFailure(Duration duration) {
  }

  default void recordInvalidOutput() {
  }

  default void recordStaleRetry() {
  }
}
