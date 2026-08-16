package org.congcong.algomentor.api.practice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import org.congcong.algomentor.mentor.application.practice.PracticeSubmissionHistoryToolContracts;
import org.congcong.algomentor.mentor.application.practice.PracticeSubmissionHistoryToolMetrics;

/** 历史正式提交 Tool 的 Micrometer 指标实现，严格限制 tag 取值为低基数枚举。 */
public final class MicrometerPracticeSubmissionHistoryToolMetrics implements PracticeSubmissionHistoryToolMetrics {

  private static final String OTHER = "OTHER";
  private static final Set<String> STATUSES = Set.of(
      PracticeSubmissionHistoryToolContracts.STATUS_OK,
      PracticeSubmissionHistoryToolContracts.STATUS_UNAVAILABLE,
      PracticeSubmissionHistoryToolContracts.STATUS_BUDGET_EXHAUSTED,
      PracticeSubmissionHistoryToolContracts.STATUS_USER_INTENT_REQUIRED,
      PracticeSubmissionHistoryToolContracts.STATUS_FAILED);
  private static final Set<String> REASONS = Set.of("UNAVAILABLE", "BUDGET_EXHAUSTED");
  private static final Set<String> RANGE_STATUSES = Set.of("SUCCEEDED", "REJECTED");

  private final MeterRegistry registry;

  public MicrometerPracticeSubmissionHistoryToolMetrics(MeterRegistry registry) {
    this.registry = java.util.Objects.requireNonNull(registry, "registry must not be null");
  }

  @Override
  public void recordToolCall(String tool, String status) {
    Counter.builder("practice_submission_history_tool_call_total")
        .tags("tool", allowedTool(tool), "status", allowed(STATUSES, status))
        .register(registry)
        .increment();
  }

  @Override
  public void recordScopeRejected(String reasonCategory) {
    Counter.builder("practice_submission_history_scope_rejected_total")
        .tag("reason_category", allowed(REASONS, reasonCategory))
        .register(registry)
        .increment();
  }

  @Override
  public void recordCodeIntentRejected() {
    Counter.builder("practice_submission_history_code_intent_rejected_total")
        .register(registry)
        .increment();
  }

  @Override
  public void recordDetailVisibleChars(int chars) {
    if (chars >= 0) {
      DistributionSummary.builder("practice_submission_history_detail_visible_chars")
          .register(registry)
          .record(chars);
    }
  }

  @Override
  public void recordDetailRangeRead(String status) {
    Counter.builder("practice_submission_history_detail_range_read_total")
        .tag("status", allowed(RANGE_STATUSES, status))
        .register(registry)
        .increment();
  }

  @Override
  public void recordToolDataAccessFailure() {
    Counter.builder("practice_submission_history_tool_data_access_failure_total")
        .register(registry)
        .increment();
  }

  private String allowedTool(String value) {
    return PracticeSubmissionHistoryToolContracts.TOOL_NAMES.contains(value) ? value : OTHER;
  }

  private String allowed(Set<String> allowed, String value) {
    return value != null && allowed.contains(value) ? value : OTHER;
  }
}
