package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record RunErrorUpdate(
    long runId,
    String status,
    JsonNode error,
    Instant endedAt
) {
}
