package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import java.time.Instant;

/** Agent run 列表查询的低敏摘要行。 */
public record AgentAuditRunRow(
    long runId,
    String runUuid,
    long taskId,
    long turnId,
    Long userId,
    String scenario,
    String purpose,
    String source,
    String provider,
    String model,
    String status,
    String finishReason,
    int stepCount,
    int failedStepCount,
    int toolCallCount,
    int failedToolCallCount,
    Integer promptTokenBudget,
    Integer assemblyTokenEstimate,
    Integer finalRequestTokenEstimate,
    Long actualInputTokens,
    Long cachedTokens,
    Long overBudgetTokens,
    boolean compactionApplied,
    int compactionActionCount,
    boolean providerError,
    Instant startedAt,
    Instant endedAt
) {
}
