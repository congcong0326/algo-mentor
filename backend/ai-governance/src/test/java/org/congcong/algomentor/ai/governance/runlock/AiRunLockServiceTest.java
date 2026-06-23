package org.congcong.algomentor.ai.governance.runlock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.agent.core.runlock.InMemoryAgentRunLockManager;
import org.junit.jupiter.api.Test;

class AiRunLockServiceTest {

  @Test
  void usesPerUserAllAiLockKey() {
    InMemoryAgentRunLockManager manager = new InMemoryAgentRunLockManager();
    AiRunLockService service = new AiRunLockService(manager, () -> "node-1", Duration.ofMinutes(30));

    AgentRunLockToken token = service.tryAcquire(7L, "run-1", Map.of("purpose", "LEARNING_CHAT"))
        .orElseThrow();

    assertThat(token.lockKey()).isEqualTo("user:7:ai:all");
    assertThat(token.ownerId()).isEqualTo("node-1");
    manager.release(token);
  }

  @Test
  void returnsEmptyWhenSameUserAlreadyHasActiveAiRun() {
    InMemoryAgentRunLockManager manager = new InMemoryAgentRunLockManager();
    AiRunLockService service = new AiRunLockService(manager, () -> "node-1", Duration.ofMinutes(30));
    AgentRunLockToken token = service.tryAcquire(7L, "run-1", Map.of()).orElseThrow();

    assertThat(service.tryAcquire(7L, "run-2", Map.of())).isEmpty();

    manager.release(token);
  }
}
