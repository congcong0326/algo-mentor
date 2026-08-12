package org.congcong.algomentor.agent.core.runtime.audit;

import java.time.Instant;
import java.util.List;

/** 单个 run 的时间线详情，不在此层携带 step 的大 JSON。 */
public record AgentAuditRunDetail(
    AgentAuditRunSummary summary,
    int attemptNo,
    Long retryOfRunId,
    int maxSteps,
    String errorCode,
    String errorMessage,
    Instant diagnosticRetentionExpiresAt,
    Instant diagnosticRedactedAt,
    AgentAuditTurnSummary currentTurn,
    List<AgentAuditTurnSummary> taskTurns,
    List<AgentAuditStepSummary> steps,
    AgentAuditUsage totalUsage
) {

  public AgentAuditRunDetail {
    taskTurns = taskTurns == null ? List.of() : List.copyOf(taskTurns);
    steps = steps == null ? List.of() : List.copyOf(steps);
    totalUsage = totalUsage == null ? AgentAuditUsage.empty() : totalUsage;
  }
}
