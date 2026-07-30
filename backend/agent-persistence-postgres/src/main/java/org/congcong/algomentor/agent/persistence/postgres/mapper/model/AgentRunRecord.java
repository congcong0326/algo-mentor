package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import org.congcong.algomentor.agent.core.AgentLoopDefaults;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;

public record AgentRunRecord(
    long runId,
    long taskId,
    long turnId,
    String runUuid,
    String idempotencyKey,
    String systemPrompt,
    String agentKey,
    String triggerType,
    Long parentRunId,
    Integer parentStepIndex,
    Long retryOfRunId,
    int maxSteps
) {

  public AgentRunRecord(
      long runId,
      long taskId,
      long turnId,
      String runUuid,
      String idempotencyKey,
      String systemPrompt
  ) {
    this(
        runId,
        taskId,
        turnId,
        runUuid,
        idempotencyKey,
        systemPrompt,
        null,
        AgentInvocationMode.USER_ENTRY.databaseValue(),
        null,
        null,
        null,
        AgentLoopDefaults.DEFAULT_MAX_STEPS);
  }
}
