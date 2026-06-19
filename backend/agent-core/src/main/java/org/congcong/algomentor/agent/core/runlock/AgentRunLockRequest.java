package org.congcong.algomentor.agent.core.runlock;

import java.time.Duration;
import java.util.Map;

public record AgentRunLockRequest(
    String lockKey,
    String ownerId,
    Duration ttl,
    Map<String, Object> metadata
) {

  public AgentRunLockRequest {
    if (lockKey == null || lockKey.isBlank()) {
      throw new IllegalArgumentException("Agent run lock key must not be blank");
    }
    if (ownerId == null || ownerId.isBlank()) {
      throw new IllegalArgumentException("Agent run lock owner id must not be blank");
    }
    if (ttl != null && ttl.isNegative()) {
      throw new IllegalArgumentException("Agent run lock ttl must not be negative");
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
