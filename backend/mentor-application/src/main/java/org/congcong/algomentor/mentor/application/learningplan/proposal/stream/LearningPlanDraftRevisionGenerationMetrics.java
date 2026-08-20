package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import java.time.Instant;

/** 草案修订异步生成的低基数生命周期观测端口。 */
public interface LearningPlanDraftRevisionGenerationMetrics {

  LearningPlanDraftRevisionGenerationMetrics NOOP = new LearningPlanDraftRevisionGenerationMetrics() {
  };

  default void recordStarted() {
  }

  default void recordIdempotencyReused() {
  }

  default void recordCompleted(Instant startedAt, Instant completedAt) {
  }

  default void recordFailed(Instant startedAt, Instant completedAt) {
  }

  default void recordSuperseded(Instant startedAt, Instant completedAt) {
  }
}
