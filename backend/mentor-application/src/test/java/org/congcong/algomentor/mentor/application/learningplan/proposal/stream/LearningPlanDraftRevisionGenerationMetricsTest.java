package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LearningPlanDraftRevisionGenerationMetricsTest {

  @Test
  void recordsLifecycleCountersAndOnlyUsesFixedDurationOutcomeTags() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    LearningPlanDraftRevisionGenerationMetrics metrics =
        new MicrometerLearningPlanDraftRevisionGenerationMetrics(registry);
    Instant startedAt = Instant.parse("2026-08-20T08:00:00Z");
    Instant completedAt = Instant.parse("2026-08-20T08:00:03Z");

    metrics.recordStarted();
    metrics.recordIdempotencyReused();
    metrics.recordCompleted(startedAt, completedAt);
    metrics.recordFailed(startedAt, completedAt);
    metrics.recordSuperseded(startedAt, completedAt);

    assertThat(registry.get("learning_plan_revision_generation_started").counter().count()).isEqualTo(1D);
    assertThat(registry.get("learning_plan_revision_generation_idempotency_reused").counter().count())
        .isEqualTo(1D);
    assertThat(registry.get("learning_plan_revision_generation_completed").counter().count()).isEqualTo(1D);
    assertThat(registry.get("learning_plan_revision_generation_failed").counter().count()).isEqualTo(1D);
    assertThat(registry.get("learning_plan_revision_generation_superseded").counter().count()).isEqualTo(1D);
    assertThat(registry.get("learning_plan_revision_generation_duration")
        .tag("outcome", "completed").timer().count()).isEqualTo(1L);
    assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
        .allSatisfy(tag -> {
          assertThat(tag.getKey()).isEqualTo("outcome");
          assertThat(tag.getValue()).isIn("completed", "failed", "superseded");
        }));
  }
}
