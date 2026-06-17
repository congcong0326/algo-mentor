package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ToolCallStartRow(
    long taskId,
    long runId,
    int stepIndex,
    String toolCallId,
    String toolName,
    JsonNode argumentsJson,
    String status,
    Integer argumentCharCount,
    Integer argumentTokenEstimate,
    String redactionPolicyVersion,
    JsonNode metadata,
    Instant startedAt
) {
}
