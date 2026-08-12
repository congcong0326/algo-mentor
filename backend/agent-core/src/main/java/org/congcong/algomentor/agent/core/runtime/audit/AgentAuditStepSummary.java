package org.congcong.algomentor.agent.core.runtime.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;

/** run 时间线中一个 step 的轻量摘要。 */
public record AgentAuditStepSummary(
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
    AgentAuditUsage usage,
    boolean compactionApplied,
    JsonNode compactionMetadata,
    boolean snapshotAvailable,
    int toolCallCount,
    int failedToolCallCount,
    String errorCode,
    String errorMessage
) {

  public AgentAuditStepSummary {
    roleCounts = roleCounts == null ? Map.of() : Map.copyOf(roleCounts);
    usage = usage == null ? AgentAuditUsage.empty() : usage;
  }
}
