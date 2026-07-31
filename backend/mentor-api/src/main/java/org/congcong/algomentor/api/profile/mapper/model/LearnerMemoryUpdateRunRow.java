package org.congcong.algomentor.api.profile.mapper.model;

import java.time.Instant;

public record LearnerMemoryUpdateRunRow(
    long id,
    long userId,
    String triggerType,
    String status,
    String idempotencyKey,
    Long agentRunId,
    String promptVersion,
    String schemaVersion,
    int inputCount,
    int operationCount,
    int toolCallCount,
    String failureCode,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt
) {
}
