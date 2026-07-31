package org.congcong.algomentor.mentor.application.profile.tool;

import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.toolresult.ToolResultProvenance;
import org.congcong.algomentor.agent.core.toolresult.ToolResultReadGuard;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;

/** 仅对当前 recall scope 内三项记忆工具的范围读取计数。 */
public final class LearnerMemoryToolResultReadGuard implements ToolResultReadGuard {

  private final LearnerMemoryRunScopeRegistry scopeRegistry;
  private final LearnerMemoryMetrics metrics;

  public LearnerMemoryToolResultReadGuard(LearnerMemoryRunScopeRegistry scopeRegistry) {
    this(scopeRegistry, LearnerMemoryMetrics.NOOP);
  }

  public LearnerMemoryToolResultReadGuard(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      LearnerMemoryMetrics metrics) {
    this.scopeRegistry = scopeRegistry;
    this.metrics = metrics == null ? LearnerMemoryMetrics.NOOP : metrics;
  }

  @Override
  public ToolResultReadPermit beforeRead(
      AgentExecutionContext context, ToolResultProvenance provenance, int requestedMaxChars) {
    if (provenance == null || !LearnerMemoryRecallToolContracts.TOOL_NAMES.contains(provenance.toolName())) {
      return ToolResultReadPermit.allow(requestedMaxChars);
    }
    LearnerMemoryRunScopeRegistry.RecallScopeUse use = scopeRegistry.reserveRecallToolResultRead(
        LearnerMemoryRecallToolSupport.scopeRef(context), requestedMaxChars);
    if (use.granted()) {
      return ToolResultReadPermit.track(use.maxVisibleChars(), use);
    }
    if (use.status() == LearnerMemoryRunScopeRegistry.RecallScopeUseStatus.BUDGET_EXHAUSTED) {
      metrics.recordRecallRangeRead("REJECTED");
      return ToolResultReadPermit.reject(
          LearnerMemoryRecallToolContracts.STATUS_BUDGET_EXHAUSTED,
          "Learner memory result-read budget is exhausted.");
    }
    metrics.recordRecallRangeRead("REJECTED");
    return ToolResultReadPermit.reject(
        LearnerMemoryRecallToolContracts.FAILURE_SCOPE_UNAVAILABLE,
        "Learner memory result is not readable in the current run.");
  }

  @Override
  public void afterRead(ToolResultReadPermit permit, int visibleChars) {
    if (permit != null && permit.trackingToken() instanceof LearnerMemoryRunScopeRegistry.RecallScopeUse use) {
      use.complete(visibleChars);
      metrics.recordRecallRangeRead("SUCCEEDED");
    }
  }
}
