package org.congcong.algomentor.api.practice.model;

import java.time.Instant;

public record PracticeActiveRunResponse(
    long runId,
    long taskId,
    String runUuid,
    String idempotencyKey,
    Instant startedAt
) {
}
