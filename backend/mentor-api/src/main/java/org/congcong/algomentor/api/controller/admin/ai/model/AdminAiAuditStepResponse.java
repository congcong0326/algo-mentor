package org.congcong.algomentor.api.controller.admin.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;

/** run 详情时间线中的 step 摘要。 */
public record AdminAiAuditStepResponse(
    int stepIndex,
    String status,
    String provider,
    String model,
    String finishReason,
    Instant startedAt,
    Instant endedAt,
    Integer messageCount,
    Map<String, Integer> roleCounts,
    Integer messageTokenEstimate,
    Integer toolsCount,
    Integer toolsTokenEstimate,
    Integer providerOverheadTokenEstimate,
    Integer finalRequestTokenEstimate,
    Integer promptTokenBudget,
    Integer remainingBudgetTokens,
    AdminAiAuditUsageResponse usage,
    boolean compactionApplied,
    JsonNode compaction,
    boolean snapshotAvailable,
    int toolCallCount,
    int failedToolCallCount,
    String errorCode,
    String errorMessage
) {
}
