package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** step 摘要及其请求快照字段；仓储层决定哪些字段向详情 API 暴露。 */
public record AgentAuditStepRow(
    int stepIndex,
    String status,
    String provider,
    String model,
    String finishReason,
    Instant startedAt,
    Instant endedAt,
    JsonNode usage,
    JsonNode error,
    Long snapshotId,
    Integer promptTokenBudget,
    JsonNode messages,
    JsonNode tools,
    JsonNode toolChoice,
    JsonNode generationOptions,
    JsonNode requestSnapshot,
    String requestHash,
    String redactionPolicyVersion,
    Instant snapshotRetentionExpiresAt,
    JsonNode snapshotMetadata,
    JsonNode stepMetadata,
    int toolCallCount,
    int failedToolCallCount
) {
}
