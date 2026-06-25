package org.congcong.algomentor.agent.core.runtime.model;

import java.time.Instant;

public record AgentActiveRun(
    long runId,
    long taskId,
    String runUuid,
    String idempotencyKey,
    Instant startedAt
) {

  public AgentActiveRun {
    if (runId < 1) {
      throw new IllegalArgumentException("Agent active run id must be positive");
    }
    if (taskId < 1) {
      throw new IllegalArgumentException("Agent active run task id must be positive");
    }
    if (runUuid == null || runUuid.isBlank()) {
      throw new IllegalArgumentException("Agent active run uuid must not be blank");
    }
    if (idempotencyKey != null && idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Agent active run idempotency key must not be blank");
    }
    startedAt = startedAt == null ? Instant.EPOCH : startedAt;
  }
}
