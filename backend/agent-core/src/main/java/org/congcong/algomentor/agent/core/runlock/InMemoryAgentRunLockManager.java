package org.congcong.algomentor.agent.core.runlock;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class InMemoryAgentRunLockManager implements AgentRunLockManager {

  private final ConcurrentHashMap<String, LockEntry> locks = new ConcurrentHashMap<>();

  @Override
  public AgentRunLockAcquireResult tryAcquire(AgentRunLockRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Agent run lock request must not be null");
    }
    Instant now = Instant.now();
    String tokenId = UUID.randomUUID().toString();
    Instant expiresAt = request.ttl() == null ? null : now.plus(request.ttl());
    LockEntry newEntry = new LockEntry(request.ownerId(), tokenId, expiresAt, request.metadata());
    AtomicReference<LockEntry> conflict = new AtomicReference<>();
    // locks.compute 会针对entry节点加锁，然后在 remappingFunction 会在锁的保护中执行，此时拿到锁的去修改原子变量，获取锁失败的拿不到原子变量
    locks.compute(request.lockKey(), (ignored, existing) -> {
      if (existing == null || existing.expiredAt(now)) {
        return newEntry;
      }
      conflict.set(existing);
      return existing;
    });
    // 后续通过判断是否成功修改原子类来看枷锁成功还是失败
    if (conflict.get() == null) {
      return AgentRunLockAcquireResult.acquired(new AgentRunLockToken(
          request.lockKey(),
          request.ownerId(),
          tokenId,
          expiresAt));
    }
    LockEntry existing = conflict.get();
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

    private boolean expiredAt(Instant now) {
      return expiresAt != null && !expiresAt.isAfter(now);
    }
  }
}
