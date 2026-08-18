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
    metrics.recordEvidence("CROSS_PROBLEM_RECOVERY", "STRONG", 4);
    metrics.recordProfileProjection("SUCCEEDED", "v1", 2);
    metrics.recordReviewSnapshot(9, 3, 0);
    metrics.recordReviewObservation("RECOVERED_CHALLENGE", "GENERATED");
    metrics.recordLanguageGuardRejected();

    assertThat(registry.get("learner_memory_update_run_total")
        .tags("trigger", "CODE_REVIEW_BATCH", "status", "SUCCEEDED").counter().count()).isEqualTo(1D);
    assertThat(registry.get("learner_memory_tool_call_total")
        .tags("purpose", "OTHER", "tool", "OTHER", "status", "OTHER").counter().count()).isEqualTo(1D);
    assertThat(registry.get("learner_memory_profile_citation_count").summary().totalAmount()).isEqualTo(2D);
    assertThat(registry.get("learner_memory_evidence_count")
        .tags("pattern", "CROSS_PROBLEM_RECOVERY", "grade", "STRONG").summary().totalAmount()).isEqualTo(4D);
    assertThat(registry.get("learner_memory_review_snapshot_review_count").summary().totalAmount()).isEqualTo(9D);
    assertThat(registry.get("learner_memory_profile_claim_type_total")
        .tags("type", "RECOVERED_CHALLENGE", "status", "GENERATED").counter().count()).isEqualTo(1D);
    assertThat(registry.get("learner_memory_profile_language_guard_rejected_total").counter().count()).isEqualTo(1D);
    assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
        .noneMatch(tag -> tag.getValue().contains("review-123") || tag.getValue().contains("provider error")));
  }
}
