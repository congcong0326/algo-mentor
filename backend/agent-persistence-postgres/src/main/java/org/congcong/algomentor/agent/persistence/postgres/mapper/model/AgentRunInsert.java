package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

/** 插入一条 Agent run 审计记录所需的字段。 */
public record AgentRunInsert(
    long taskId,
    long turnId,
    String runUuid,
    String idempotencyKey,
    int maxSteps,
    String agentKey,
    String triggerType,
    Long parentRunId,
    Integer parentStepIndex,
    Long retryOfRunId
) {

  public AgentRunInsert {
    if (taskId < 1 || turnId < 1) {
      throw new IllegalArgumentException("Agent run task and turn ids must be positive");
    }
    if (runUuid == null || runUuid.isBlank()) {
      throw new IllegalArgumentException("Agent run uuid must not be blank");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Agent run idempotency key must not be blank");
    }
    if (maxSteps < 1) {
      throw new IllegalArgumentException("Agent run max steps must be positive");
    }
    if (triggerType == null || triggerType.isBlank()) {
      throw new IllegalArgumentException("Agent run trigger type must not be blank");
    }
    if ((parentRunId == null) != (parentStepIndex == null)) {
      throw new IllegalArgumentException("Agent run parent run and step index must be provided together");
    }
  }
}
