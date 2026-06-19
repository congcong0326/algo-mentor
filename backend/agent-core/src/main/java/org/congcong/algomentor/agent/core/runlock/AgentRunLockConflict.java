package org.congcong.algomentor.agent.core.runlock;

import java.time.Instant;
import java.util.Map;

public record AgentRunLockConflict(
    String lockKey,
    String ownerId,
    Instant expiresAt,
    Map<String, Object> metadata
) {

  public AgentRunLockConflict {
    if (lockKey == null || lockKey.isBlank()) {
      throw new IllegalArgumentException("Agent run lock key must not be blank");
    }
    if (ownerId == null || ownerId.isBlank()) {
      throw new IllegalArgumentException("Agent run lock owner id must not be blank");
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
