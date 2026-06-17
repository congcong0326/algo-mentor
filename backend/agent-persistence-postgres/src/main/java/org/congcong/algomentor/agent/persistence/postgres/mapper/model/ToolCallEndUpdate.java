package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ToolCallEndUpdate(
    long runId,
    int stepIndex,
    String toolCallId,
    String status,
    JsonNode resultJson,
    Long durationMillis,
    Integer resultCharCount,
    Integer resultTokenEstimate,
    Instant endedAt
) {
}
