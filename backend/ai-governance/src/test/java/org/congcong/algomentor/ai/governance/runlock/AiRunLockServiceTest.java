package org.congcong.algomentor.ai.governance.runlock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.agent.core.runlock.InMemoryAgentRunLockManager;
import org.junit.jupiter.api.Test;

class AiRunLockServiceTest {

  @Test
  void usesFirstPerUserAiRunSlot() {
    InMemoryAgentRunLockManager manager = new InMemoryAgentRunLockManager();
    AiRunLockService service = new AiRunLockService(manager, () -> "node-1", Duration.ofMinutes(30));

    AgentRunLockToken token = service.tryAcquire(7L, "run-1", Map.of("purpose", "LEARNING_CHAT"))
        .orElseThrow();

    assertThat(token.lockKey()).isEqualTo("user:7:ai:all:slot:1");
    assertThat(token.ownerId()).isEqualTo("node-1");
    manager.release(token);
  }

  @Test
  void allowsTwoActiveAiRunsPerUserAndRejectsTheThird() {
    InMemoryAgentRunLockManager manager = new InMemoryAgentRunLockManager();
    AiRunLockService service = new AiRunLockService(manager, () -> "node-1", Duration.ofMinutes(30));
    AgentRunLockToken first = service.tryAcquire(7L, "run-1", Map.of()).orElseThrow();
    AgentRunLockToken second = service.tryAcquire(7L, "run-2", Map.of()).orElseThrow();

    assertThat(second.lockKey()).isEqualTo("user:7:ai:all:slot:2");
    assertThat(service.tryAcquire(7L, "run-3", Map.of())).isEmpty();

    manager.release(first);
    assertThat(service.tryAcquire(7L, "run-4", Map.of())).isPresent();
    manager.release(second);
  }
}
