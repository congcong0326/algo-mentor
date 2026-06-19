package org.congcong.algomentor.agent.core.runlock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryAgentRunLockManagerTest {

  @Test
  void rejectsSecondAcquireUntilMatchingTokenReleasesLock() {
    InMemoryAgentRunLockManager manager = new InMemoryAgentRunLockManager();
    AgentRunLockRequest request = new AgentRunLockRequest(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + 42,
        "owner-a",
        null,
        Map.of("taskId", 42L));

    AgentRunLockAcquireResult acquired = manager.tryAcquire(request);
    AgentRunLockAcquireResult conflicted = manager.tryAcquire(request);

    assertThat(acquired.acquired()).isTrue();
    assertThat(acquired.token().tokenId()).isNotBlank();
    assertThat(conflicted.acquired()).isFalse();
    assertThat(conflicted.conflict().metadata()).containsEntry("taskId", 42L);

    manager.release(new AgentRunLockToken(acquired.token().lockKey(), acquired.token().ownerId(), "wrong-token", null));
    assertThat(manager.tryAcquire(request).acquired()).isFalse();

    manager.release(acquired.token());
    assertThat(manager.tryAcquire(request).acquired()).isTrue();
  }

  @Test
  void refreshIsNoopBeforeTtlSupport() {
    InMemoryAgentRunLockManager manager = new InMemoryAgentRunLockManager();

    assertThat(manager.refresh(new AgentRunLockToken(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + 42,
        "owner-a",
        "token-a",
        null))).isFalse();
  }
}
