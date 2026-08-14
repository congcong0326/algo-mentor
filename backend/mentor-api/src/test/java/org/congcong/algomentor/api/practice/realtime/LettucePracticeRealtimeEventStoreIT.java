package org.congcong.algomentor.api.practice.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.api.config.PracticeRealtimeStreamProperties;
import org.congcong.algomentor.api.service.LlmStreamSseMapper;
import org.congcong.algomentor.ops.observability.NoopOpsRecorders;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 使用本地独立 Streams Redis 验证 run 级回放、SSE envelope 和终态 TTL。 */
@Testcontainers(disabledWithoutDocker = true)
class LettucePracticeRealtimeEventStoreIT {

  private static final Duration EVENTUAL_TIMEOUT = Duration.ofSeconds(5);

  @Container
  static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
      .withExposedPorts(6379);

  @Test
  void appendsEventsInRunOrderReplaysStrictlyAfterCursorAndAppliesTerminalRetention() {
    String runUuid = "test-" + UUID.randomUUID();
    PracticeRealtimeStreamProperties properties = properties();
    try (LettucePracticeRealtimeEventStore store = new LettucePracticeRealtimeEventStore(
        properties, new ObjectMapper(), new LlmStreamSseMapper(), NoopOpsRecorders.practiceRealtime())) {
      store.append(runUuid, new AgentStreamEvent.AgentStepStart(runUuid, 1));
      store.append(runUuid, new AgentStreamEvent.AgentRunEnd(
          runUuid, 1, LlmFinishReason.STOP, java.util.Map.of()));

      List<PracticeRealtimeEvent> events = awaitEvents(store, runUuid, 2);
      assertThat(events).extracting(PracticeRealtimeEvent::eventName)
          .containsExactly("agent_step_start", "agent_run_end");
      assertThat(events).allSatisfy(event ->
          assertThat(PracticeRealtimeCursor.normalizeAfter(event.cursor())).isEqualTo(event.cursor()));
      assertThat(events.get(0).data().path("runId").asText()).isEqualTo(runUuid);
      assertThat(events.get(0).data().path("stepIndex").asInt()).isEqualTo(1);
      assertThat(store.readAfter(runUuid, events.get(0).cursor(), false))
          .extracting(PracticeRealtimeEvent::eventName)
          .containsExactly("agent_run_end");
    }
  }

  private static PracticeRealtimeStreamProperties properties() {
    PracticeRealtimeStreamProperties properties = new PracticeRealtimeStreamProperties();
    properties.setHost(REDIS.getHost());
    properties.setPort(REDIS.getMappedPort(6379));
    properties.setCommandTimeout(Duration.ofSeconds(1));
    properties.setConnectTimeout(Duration.ofSeconds(1));
    properties.setShutdownTimeout(Duration.ofSeconds(1));
    properties.setReadBlock(Duration.ofMillis(100));
    properties.setActiveRetention(Duration.ofMinutes(1));
    properties.setCompletedRetention(Duration.ofMinutes(2));
    return properties;
  }

  private static List<PracticeRealtimeEvent> awaitEvents(
      LettucePracticeRealtimeEventStore store,
      String runUuid,
      int expectedCount
  ) {
    long deadline = System.nanoTime() + EVENTUAL_TIMEOUT.toNanos();
    while (true) {
      List<PracticeRealtimeEvent> current = store.readAfter(
          runUuid, PracticeRealtimeProtocol.INITIAL_AFTER, false);
      if (current.size() >= expectedCount) {
        return current;
      }
      if (System.nanoTime() >= deadline) {
        throw new AssertionError("Timed out waiting for Redis Stream events");
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError(interrupted);
      }
    }
  }
}
