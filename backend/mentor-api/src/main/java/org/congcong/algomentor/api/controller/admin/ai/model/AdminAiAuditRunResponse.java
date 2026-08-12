package org.congcong.algomentor.api.controller.admin.ai.model;

import java.time.Instant;

/** 审计 run 列表响应；不包含 prompt、schema 或工具结果正文。 */
public record AdminAiAuditRunResponse(
    long runId,
    String runUuid,
    long taskId,
    long turnId,
    Long userId,
    String userDisplayName,
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
