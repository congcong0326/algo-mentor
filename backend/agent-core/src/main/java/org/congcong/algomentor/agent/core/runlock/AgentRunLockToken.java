package org.congcong.algomentor.agent.core.runlock;

import java.time.Instant;
import java.util.Map;

public record AgentRunLockToken(
    String lockKey,
    String ownerId,
    String tokenId,
    Instant expiresAt
) {

  public AgentRunLockToken {
    if (lockKey == null || lockKey.isBlank()) {
      throw new IllegalArgumentException("Agent run lock key must not be blank");
    }
    if (ownerId == null || ownerId.isBlank()) {
      throw new IllegalArgumentException("Agent run lock owner id must not be blank");
    }
    if (tokenId == null || tokenId.isBlank()) {
      throw new IllegalArgumentException("Agent run lock token id must not be blank");
    }
  }

  public static AgentRunLockToken fromMetadataMap(Map<?, ?> metadata) {
    if (metadata == null) {
      throw new IllegalArgumentException("Agent run lock token metadata must not be null");
    }
    return new AgentRunLockToken(
        stringValue(metadata.get("lockKey")),
        stringValue(metadata.get("ownerId")),
        stringValue(metadata.get("tokenId")),
        instantValue(metadata.get("expiresAt")));
  }

  private static String stringValue(Object value) {
    return value instanceof String text ? text : null;
  }

  private static Instant instantValue(Object value) {
    if (value instanceof Instant instant) {
      return instant;
    }
    if (value instanceof String text && !text.isBlank()) {
      return Instant.parse(text);
    }
    return null;
  }
}
