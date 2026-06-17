package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record RunErrorUpdate(
    long runId,
    JsonNode error,
    Instant endedAt
) {
}
