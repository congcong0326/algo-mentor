package org.congcong.algomentor.agent.core.runtime.audit;

import java.time.Instant;

/** 不含 messages、tools schema 和工具结果正文的 run 列表摘要。 */
public record AgentAuditRunSummary(
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
