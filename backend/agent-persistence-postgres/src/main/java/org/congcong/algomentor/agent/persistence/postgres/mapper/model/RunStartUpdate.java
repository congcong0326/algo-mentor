package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import java.time.Instant;

public record RunStartUpdate(
    long runId,
    int maxSteps,
    Instant startedAt
) {
}
