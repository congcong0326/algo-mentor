package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** 会话列表中的单个 turn 低敏行。 */
public record AgentAuditTurnRow(
    long turnId,
    long sequenceNo,
    String status,
    String userMessage,
    Instant userMessageAt,
    String assistantMessage,
    Instant assistantMessageAt,
    int runAttemptCount,
    JsonNode runAttempts,
    boolean hasTools,
    Long inputTokens,
    Long cachedTokens,
    Long outputTokens,
    Long reasoningTokens,
    Long totalTokens,
    Long overBudgetTokens
) {
}
