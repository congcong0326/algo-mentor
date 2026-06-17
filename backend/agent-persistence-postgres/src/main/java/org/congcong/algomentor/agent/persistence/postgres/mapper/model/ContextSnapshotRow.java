package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ContextSnapshotRow(
    long taskId,
    long runId,
    int stepIndex,
    String requestId,
    String provider,
    String model,
    String modelSelector,
    String policyName,
    String policyVersion,
    int tokenBudget,
    Integer tokenEstimate,
    Integer reservedOutputTokens,
    String snapshotStorageMode,
    JsonNode requestSnapshotJson,
    JsonNode messagesJson,
    JsonNode toolsJson,
    JsonNode toolChoiceJson,
    JsonNode generationOptions,
    String requestHash,
    String redactionPolicyVersion,
    JsonNode metadata,
    Instant createdAt
) {
}
