package org.congcong.algomentor.mentor.application.profile.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;

/** Micrometer 实现集中收敛 AI 记忆指标的动态 label，未知值归入 OTHER。 */
public final class MicrometerLearnerMemoryMetrics implements LearnerMemoryMetrics {

  private static final String OTHER = "OTHER";
  private static final Set<String> TOOL_PURPOSES = Set.of(
      "DECLARED_UPDATE", "REVIEW_UPDATE", "RECALL", "PRACTICE_CHAT");
  private static final Set<String> TOOL_STATUSES = Set.of("SUCCEEDED", "REJECTED", "FAILED");
  private static final Set<String> INVALID_OUTPUT_REASONS = Set.of("SCHEMA", "VALIDATION", "STALE", "TOOL_FAILURE");
  private static final Set<String> EVIDENCE_PATTERNS = Set.of(
      "USER_DECLARATION", "USER_CORRECTION", "SINGLE_REVIEW", "SAME_PROBLEM_PERSISTENCE",
      "SAME_PROBLEM_RECOVERY", "SAME_PROBLEM_REGRESSION", "CROSS_PROBLEM_RECOVERY", "CROSS_PROBLEM_RECURRENCE",
      "CROSS_PROBLEM_LONGITUDINAL", "TAG_BREADTH");
  private static final Set<String> EVIDENCE_GRADES = Set.of("LIMITED", "SUPPORTED", "STRONG", "USER_AUTHORED");
  private static final Set<String> RECALL_SCENARIOS = Set.of("PRACTICE_CHAT");
  private static final Set<String> RANGE_READ_STATUSES = Set.of("SUCCEEDED", "REJECTED");
  private static final Set<String> PROJECTION_STATUSES = Set.of("SUCCEEDED", "FAILED");
  private static final Set<String> ACTIVE_LIMIT_LEVELS = Set.of("SOFT", "HARD");
  private static final Set<String> REVIEW_OBSERVATION_TYPES = Set.of(
      "CURRENT_STRENGTH", "RECOVERED_CHALLENGE", "ACTIVE_RISK");
  private static final Set<String> REVIEW_OBSERVATION_STATUSES = Set.of("GENERATED", "REJECTED", "REVISED");

  private final MeterRegistry registry;

  public MicrometerLearnerMemoryMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  @Override
  public void recordUpdateRun(LearnerMemoryRunContract.Trigger trigger, LearnerMemoryRunContract.Status status) {
    Counter.builder("learner_memory_update_run_total")
        .tags("trigger", enumLabel(trigger), "status", enumLabel(status))
        .register(registry)
        .increment();
  }

  @Override
  public void recordOperation(
      LearnerMemoryClaimContract.OperationAction action,
      LearnerMemoryClaimContract.Kind kind,
      LearnerMemoryClaimContract.Dimension dimension) {
    Counter.builder("learner_memory_operation_total")
        .tags("action", enumLabel(action), "kind", enumLabel(kind), "dimension", enumLabel(dimension))
        .register(registry)
        .increment();
  }

  @Override
  public void recordToolCall(String purpose, String tool, String status) {
    Counter.builder("learner_memory_tool_call_total")
        .tags("purpose", allowed(TOOL_PURPOSES, purpose), "tool", allowedTool(tool), "status", allowed(TOOL_STATUSES, status))
        .register(registry)
        .increment();
  }

  @Override
  public void recordEvidence(String pattern, String grade, int count) {
    record("learner_memory_evidence_count", count, "pattern", allowed(EVIDENCE_PATTERNS, pattern),
        "grade", allowed(EVIDENCE_GRADES, grade));
  }

  @Override
  public void recordInvalidOutput(String reason) {
    Counter.builder("learner_memory_invalid_output_total")
        .tag("reason", allowed(INVALID_OUTPUT_REASONS, reason))
        .register(registry)
        .increment();
  }

  @Override
  public void recordActiveClaimCount(
      LearnerMemoryClaimContract.Kind kind,
      LearnerMemoryClaimContract.Dimension dimension,
      int count) {
    record("learner_memory_claim_active_count", count, "kind", enumLabel(kind), "dimension", enumLabel(dimension));
  }

  @Override
  public void recordActiveLimit(String level) {
    Counter.builder("learner_memory_active_limit_total")
        .tag("level", allowed(ACTIVE_LIMIT_LEVELS, level))
        .register(registry)
        .increment();
  }

  @Override
  public void recordRecall(String scenario, LearnerMemoryClaimContract.Kind kind) {
    Counter.builder("learner_memory_recall_count")
        .tags("scenario", allowed(RECALL_SCENARIOS, scenario), "kind", enumLabel(kind))
        .register(registry)
        .increment();
  }

  @Override
  public void recordBootstrap(String scenario, int tokenEstimate, int directClaimCount, boolean trimmed) {
    String normalizedScenario = allowed(RECALL_SCENARIOS, scenario);
    record("learner_memory_bootstrap_token_estimate", tokenEstimate, "scenario", normalizedScenario);
    record("learner_memory_bootstrap_direct_claim_count", directClaimCount, "scenario", normalizedScenario);
    if (trimmed) {
      Counter.builder("learner_memory_bootstrap_trimmed_total")
          .tag("scenario", normalizedScenario)
          .register(registry)
          .increment();
    }
  }

  @Override
  public void recordRecallToolResultChars(String tool, int chars) {
    record("learner_memory_recall_tool_result_chars", chars, "tool", allowedTool(tool));
  }

  @Override
  public void recordRecallRangeRead(String status) {
    Counter.builder("learner_memory_recall_range_read_total")
        .tag("status", allowed(RANGE_READ_STATUSES, status))
        .register(registry)
        .increment();
  }

  @Override
  public void recordProfileProjection(String status, String projectorVersion, int citationCount) {
    Counter.builder("learner_memory_profile_projection_total")
        .tags("status", allowed(PROJECTION_STATUSES, status), "projector_version", allowedProjectorVersion(projectorVersion))
        .register(registry)
        .increment();
    record("learner_memory_profile_citation_count", citationCount);
  }

  @Override
  public void recordReviewSnapshot(int reviewCount, int recoveredCount, int unresolvedCount) {
    record("learner_memory_review_snapshot_review_count", reviewCount);
    record("learner_memory_review_snapshot_recovered_count", recoveredCount);
    record("learner_memory_review_snapshot_unresolved_count", unresolvedCount);
  }

  @Override
  public void recordReviewObservation(String observationType, String status) {
    Counter.builder("learner_memory_profile_claim_type_total")
        .tags(
            "type", allowed(REVIEW_OBSERVATION_TYPES, observationType),
            "status", allowed(REVIEW_OBSERVATION_STATUSES, status))
        .register(registry)
        .increment();
  }

  @Override
  public void recordLanguageGuardRejected() {
    Counter.builder("learner_memory_profile_language_guard_rejected_total").register(registry).increment();
  }

  private void record(String metric, int value, String... tags) {
    if (value >= 0) {
      DistributionSummary.builder(metric).tags(tags).register(registry).record(value);
    }
  }

  private String allowedTool(String value) {
    return switch (value == null ? "" : value) {
      case "update_learner_declared_profile", "get_problem_review_trajectory", "get_code_review_evidence",
          "compare_submission_versions", "search_learner_memory", "read_learner_memory_section",
          "get_learner_memory_evidence" -> value;
      default -> OTHER;
    };
  }

  private String allowedProjectorVersion(String value) {
    return "v1".equals(value) ? value : OTHER;
  }

  private String allowed(Set<String> allowed, String value) {
    return value != null && allowed.contains(value) ? value : OTHER;
  }

  private String enumLabel(Enum<?> value) {
    return value == null ? OTHER : value.name().toUpperCase(Locale.ROOT);
  }
}
