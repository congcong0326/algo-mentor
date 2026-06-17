package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

public record AgentRunRecord(
    long runId,
    long taskId,
    long turnId,
    String runUuid,
    String idempotencyKey,
    String systemPrompt
) {
}
