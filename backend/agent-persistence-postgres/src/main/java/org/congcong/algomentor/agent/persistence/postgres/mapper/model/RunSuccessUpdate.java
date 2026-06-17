package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record RunSuccessUpdate(
    long runId,
    String provider,
    String model,
    String finishReason,
    JsonNode usage,
    Instant endedAt
) {
}
