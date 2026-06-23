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
  private static final String AI_LOCK_KEY_SUFFIX = ":ai:all";

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
    AgentRunLockAcquireResult result = lockManager.tryAcquire(new AgentRunLockRequest(
        lockKey(userId),
        ownerProvider.ownerId(),
        ttl,
        lockMetadata));
    return result.acquired() ? Optional.of(result.token()) : Optional.empty();
  }

  public void release(AgentRunLockToken token) {
    lockManager.release(token);
  }

  public String lockKey(long userId) {
    return AI_LOCK_KEY_PREFIX + userId + AI_LOCK_KEY_SUFFIX;
  }
}
