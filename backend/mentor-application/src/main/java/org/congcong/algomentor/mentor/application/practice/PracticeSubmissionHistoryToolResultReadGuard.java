package org.congcong.algomentor.mentor.application.practice;

import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.toolresult.ToolResultProvenance;
import org.congcong.algomentor.agent.core.toolresult.ToolResultReadGuard;

/** 对历史提交详情源码的 {@code read_tool_result} 强制执行 run 级续读和字符预算。 */
public final class PracticeSubmissionHistoryToolResultReadGuard implements ToolResultReadGuard {

  private final PracticeSubmissionHistoryRunScopeRegistry scopeRegistry;
  private final PracticeSubmissionHistoryToolMetrics metrics;

  public PracticeSubmissionHistoryToolResultReadGuard(
      PracticeSubmissionHistoryRunScopeRegistry scopeRegistry,
      PracticeSubmissionHistoryToolMetrics metrics
  ) {
    this.scopeRegistry = java.util.Objects.requireNonNull(scopeRegistry, "scopeRegistry must not be null");
    this.metrics = metrics == null ? PracticeSubmissionHistoryToolMetrics.NOOP : metrics;
  }

  @Override
  public ToolResultReadPermit beforeRead(
      AgentExecutionContext context,
      ToolResultProvenance provenance,
      int requestedMaxChars
  ) {
    if (provenance == null
        || !PracticeSubmissionHistoryToolContracts.READ_PRACTICE_SUBMISSION_DETAIL.equals(provenance.toolName())) {
      return ToolResultReadPermit.allow(requestedMaxChars);
    }
    PracticeSubmissionHistoryRunScopeRegistry.DetailVisibleUse use = scopeRegistry.reserveDetailResultRead(
        PracticeSubmissionHistoryToolSupport.scopeRef(context), requestedMaxChars);
    if (use.granted()) {
      return ToolResultReadPermit.track(use.maxVisibleChars(), use);
    }
    metrics.recordDetailRangeRead("REJECTED");
    if (use.status() == PracticeSubmissionHistoryRunScopeRegistry.DetailVisibleUseStatus.BUDGET_EXHAUSTED) {
      return ToolResultReadPermit.reject(
          PracticeSubmissionHistoryToolContracts.STATUS_BUDGET_EXHAUSTED,
          "Practice submission detail result-read budget is exhausted.");
    }
    metrics.recordScopeRejected("UNAVAILABLE");
    return ToolResultReadPermit.reject(
        PracticeSubmissionHistoryToolContracts.STATUS_UNAVAILABLE,
        "Practice submission detail is unavailable in the current run.");
  }

  @Override
  public void afterRead(ToolResultReadPermit permit, int visibleChars) {
    if (permit != null && permit.trackingToken() instanceof PracticeSubmissionHistoryRunScopeRegistry.DetailVisibleUse use) {
      use.complete(visibleChars);
      metrics.recordDetailVisibleChars(Math.max(0, visibleChars));
      metrics.recordDetailRangeRead("SUCCEEDED");
    }
  }
}
