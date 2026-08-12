package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** 单个 run 详情的基础行，不包含 steps 和 task turns。 */
public record AgentAuditRunDetailRow(
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
    Instant endedAt,
    int attemptNo,
    Long retryOfRunId,
    int maxSteps,
    JsonNode error,
    Instant diagnosticRetentionExpiresAt,
    Instant diagnosticRedactedAt
) {
}
