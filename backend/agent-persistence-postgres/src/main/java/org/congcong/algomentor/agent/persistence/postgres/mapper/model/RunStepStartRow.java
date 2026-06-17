package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record RunStepStartRow(
    long taskId,
    long runId,
    int stepIndex,
    String status,
    JsonNode metadata,
    Instant startedAt
) {
}
