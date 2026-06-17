package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ToolCallErrorUpdate(
    long runId,
    int stepIndex,
    String toolCallId,
    String status,
    JsonNode error,
    Long durationMillis,
    Instant endedAt
) {
}
