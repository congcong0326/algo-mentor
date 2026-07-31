package org.congcong.algomentor.mentor.application.profile.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;
import org.junit.jupiter.api.Test;

class MicrometerLearnerMemoryMetricsTest {

  @Test
  void recordsContractedMetricsAndCollapsesUnknownDynamicLabels() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    LearnerMemoryMetrics metrics = new MicrometerLearnerMemoryMetrics(registry);

    metrics.recordUpdateRun(LearnerMemoryRunContract.Trigger.CODE_REVIEW_BATCH, LearnerMemoryRunContract.Status.SUCCEEDED);
    metrics.recordOperation(LearnerMemoryClaimContract.OperationAction.ADD,
        LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION,
        LearnerMemoryClaimContract.Dimension.PROBLEM_SOLVING_APPROACH);
    metrics.recordToolCall("malicious-purpose", "review-123", "raw provider error");
    metrics.recordProfileProjection("SUCCEEDED", "v1", 2);

    assertThat(registry.get("learner_memory_update_run_total")
        .tags("trigger", "CODE_REVIEW_BATCH", "status", "SUCCEEDED").counter().count()).isEqualTo(1D);
    assertThat(registry.get("learner_memory_tool_call_total")
        .tags("purpose", "OTHER", "tool", "OTHER", "status", "OTHER").counter().count()).isEqualTo(1D);
    assertThat(registry.get("learner_memory_profile_citation_count").summary().totalAmount()).isEqualTo(2D);
    assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
        .noneMatch(tag -> tag.getValue().contains("review-123") || tag.getValue().contains("provider error")));
  }
}
