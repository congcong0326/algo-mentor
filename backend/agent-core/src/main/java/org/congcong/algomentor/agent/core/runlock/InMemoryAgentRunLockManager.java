package org.congcong.algomentor.agent.core.runlock;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAgentRunLockManager implements AgentRunLockManager {

  private final ConcurrentHashMap<String, LockEntry> locks = new ConcurrentHashMap<>();

  @Override
  public AgentRunLockAcquireResult tryAcquire(AgentRunLockRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Agent run lock request must not be null");
    }
    String tokenId = UUID.randomUUID().toString();
    Instant expiresAt = request.ttl() == null ? null : Instant.now().plus(request.ttl());
    LockEntry newEntry = new LockEntry(request.ownerId(), tokenId, expiresAt, request.metadata());
    LockEntry existing = locks.putIfAbsent(request.lockKey(), newEntry);
    if (existing == null) {
      return AgentRunLockAcquireResult.acquired(new AgentRunLockToken(
          request.lockKey(),
          request.ownerId(),
          tokenId,
          expiresAt));
    }
    return AgentRunLockAcquireResult.conflicted(new AgentRunLockConflict(
        request.lockKey(),
        existing.ownerId(),
        existing.expiresAt(),
        existing.metadata()));
  }

  @Override
  public boolean refresh(AgentRunLockToken token) {
    return false;
  }

  @Override
  public void release(AgentRunLockToken token) {
    if (token == null) {
      return;
    }
    locks.computeIfPresent(token.lockKey(), (ignored, existing) -> {
      if (existing.matches(token)) {
        return null;
      }
      return existing;
    });
  }

  private record LockEntry(
      String ownerId,
      String tokenId,
      Instant expiresAt,
      Map<String, Object> metadata
  ) {

    private LockEntry {
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private boolean matches(AgentRunLockToken token) {
      return ownerId.equals(token.ownerId()) && tokenId.equals(token.tokenId());
    }
  }
}
