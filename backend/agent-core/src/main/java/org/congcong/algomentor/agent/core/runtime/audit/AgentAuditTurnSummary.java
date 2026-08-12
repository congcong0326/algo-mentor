package org.congcong.algomentor.agent.core.runtime.audit;

import java.time.Instant;
import java.util.List;

/** 同一 task 中的 turn 摘要，消息正文在持久化查询层做长度限制。 */
public record AgentAuditTurnSummary(
    long turnId,
    long sequenceNo,
    String status,
    String userMessage,
    Instant userMessageAt,
    String assistantMessage,
    Instant assistantMessageAt,
    int runAttemptCount,
    List<AgentAuditRunAttempt> runAttempts,
    boolean hasTools,
    AgentAuditUsage usage,
    Long overBudgetTokens
) {

  public AgentAuditTurnSummary {
    runAttempts = runAttempts == null ? List.of() : List.copyOf(runAttempts);
  }
}
