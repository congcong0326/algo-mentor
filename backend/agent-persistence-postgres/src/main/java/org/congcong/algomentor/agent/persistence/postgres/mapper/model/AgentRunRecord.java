package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import org.congcong.algomentor.agent.core.AgentLoopDefaults;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.persistence.postgres.AgentPersistenceStatuses;

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
    int maxSteps,
    String status,
    String errorCode
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
        AgentLoopDefaults.DEFAULT_MAX_STEPS,
        AgentPersistenceStatuses.SUCCEEDED,
        null);
  }

  /** 保留完整运行审计字段引入前的测试/调用方构造形式。 */
  public AgentRunRecord(
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
    this(
        runId,
        taskId,
        turnId,
        runUuid,
        idempotencyKey,
        systemPrompt,
        agentKey,
        triggerType,
        parentRunId,
        parentStepIndex,
        retryOfRunId,
        maxSteps,
        AgentPersistenceStatuses.SUCCEEDED,
        null);
  }
}
