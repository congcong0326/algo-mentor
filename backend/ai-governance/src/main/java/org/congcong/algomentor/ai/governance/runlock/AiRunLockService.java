package org.congcong.algomentor.ai.governance.runlock;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockAcquireResult;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockRequest;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;

public class AiRunLockService {

  private static final String AI_LOCK_KEY_PREFIX = "user:";
  /** 单个用户同时允许执行的 AI run 数。 */
  private static final int MAX_CONCURRENT_RUNS_PER_USER = 2;
  private static final String AI_LOCK_KEY_SUFFIX = ":ai:all:slot:";

  private final AgentRunLockManager lockManager;
  private final AgentRunLockOwnerProvider ownerProvider;
  private final Duration ttl;

  public AiRunLockService(AgentRunLockManager lockManager, AgentRunLockOwnerProvider ownerProvider, Duration ttl) {
    this.lockManager = lockManager;
    this.ownerProvider = ownerProvider;
    this.ttl = ttl;
  }

  public Optional<AgentRunLockToken> tryAcquire(long userId, String runId, Map<String, Object> metadata) {
    Map<String, Object> lockMetadata = new LinkedHashMap<>();
    lockMetadata.put(AiGovernanceMetadataKeys.USER_ID, userId);
    lockMetadata.put(AiGovernanceMetadataKeys.RUN_ID, runId);
    lockMetadata.putAll(metadata == null ? Map.of() : metadata);
    for (int slot = 1; slot <= MAX_CONCURRENT_RUNS_PER_USER; slot++) {
      AgentRunLockAcquireResult result = lockManager.tryAcquire(new AgentRunLockRequest(
          lockKey(userId, slot),
          ownerProvider.ownerId(),
          ttl,
          lockMetadata));
      if (result.acquired()) {
        return Optional.of(result.token());
      }
    }
    return Optional.empty();
  }

  public void release(AgentRunLockToken token) {
    lockManager.release(token);
  }

  public String lockKey(long userId, int slot) {
    if (slot < 1 || slot > MAX_CONCURRENT_RUNS_PER_USER) {
      throw new IllegalArgumentException("AI run lock slot is outside the supported range");
    }
    return AI_LOCK_KEY_PREFIX + userId + AI_LOCK_KEY_SUFFIX + slot;
  }
}
