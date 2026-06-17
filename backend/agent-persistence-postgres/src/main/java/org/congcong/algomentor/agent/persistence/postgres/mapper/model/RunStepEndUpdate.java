package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record RunStepEndUpdate(
    long runId,
    int stepIndex,
    String status,
    String provider,
    String model,
    String finishReason,
    JsonNode usage,
    Instant endedAt
) {
}
