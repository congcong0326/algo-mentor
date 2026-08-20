package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Micrometer revision 生成指标实现；不接受用户或草案等高基数标签。 */
public final class MicrometerLearningPlanDraftRevisionGenerationMetrics
    implements LearningPlanDraftRevisionGenerationMetrics {

  private static final String STARTED = "learning_plan_revision_generation_started";
  private static final String COMPLETED = "learning_plan_revision_generation_completed";
  private static final String FAILED = "learning_plan_revision_generation_failed";
  private static final String SUPERSEDED = "learning_plan_revision_generation_superseded";
  private static final String IDEMPOTENCY_REUSED = "learning_plan_revision_generation_idempotency_reused";
  private static final String DURATION = "learning_plan_revision_generation_duration";

  private final MeterRegistry registry;

  public MicrometerLearningPlanDraftRevisionGenerationMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  @Override
  public void recordStarted() {
    increment(STARTED);
  }

  @Override
  public void recordIdempotencyReused() {
    increment(IDEMPOTENCY_REUSED);
  }

  @Override
  public void recordCompleted(Instant startedAt, Instant completedAt) {
    recordTerminal(COMPLETED, "completed", startedAt, completedAt);
  }

  @Override
  public void recordFailed(Instant startedAt, Instant completedAt) {
    recordTerminal(FAILED, "failed", startedAt, completedAt);
  }

  @Override
  public void recordSuperseded(Instant startedAt, Instant completedAt) {
    recordTerminal(SUPERSEDED, "superseded", startedAt, completedAt);
  }

  private void increment(String metric) {
    Counter.builder(metric).register(registry).increment();
  }

  private void recordTerminal(String metric, String outcome, Instant startedAt, Instant completedAt) {
    increment(metric);
    if (startedAt == null || completedAt == null) {
      return;
    }
    Duration duration = Duration.between(startedAt, completedAt);
    if (!duration.isNegative()) {
      Timer.builder(DURATION).tag("outcome", outcome).register(registry).record(duration);
    }
  }
}
