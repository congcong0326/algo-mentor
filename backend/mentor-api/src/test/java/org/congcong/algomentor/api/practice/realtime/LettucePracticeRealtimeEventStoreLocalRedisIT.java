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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * 显式接入本机独立 Streams Redis 的验证，不依赖 Docker。
 *
 * <p>默认跳过，避免任何单元测试意外连接开发基础设施；仅在
 * {@code -Dpractice.realtime.local-redis.it=true} 时执行。</p>
 */
@EnabledIfSystemProperty(named = "practice.realtime.local-redis.it", matches = "true")
class LettucePracticeRealtimeEventStoreLocalRedisIT {

  private static final Duration EVENTUAL_TIMEOUT = Duration.ofSeconds(5);

  @Test
  void appendsRunEventsReplaysStrictlyAfterCursorAndUsesTerminalRetention() {
    String runUuid = "local-it-" + UUID.randomUUID();
    try (LettucePracticeRealtimeEventStore store = new LettucePracticeRealtimeEventStore(
        properties(), new ObjectMapper(), new LlmStreamSseMapper(), NoopOpsRecorders.practiceRealtime())) {
      awaitWriteConnection(store);
      store.append(runUuid, new AgentStreamEvent.AgentStepStart(runUuid, 1));
      store.append(runUuid, new AgentStreamEvent.AgentRunEnd(
          runUuid, 1, LlmFinishReason.STOP, java.util.Map.of()));

      List<PracticeRealtimeEvent> events = awaitEvents(store, runUuid, 2);
      assertThat(events).extracting(PracticeRealtimeEvent::eventName)
          .containsExactly("agent_step_start", "agent_run_end");
      assertThat(events.get(0).cursor()).isNotEqualTo(events.get(1).cursor());
      assertThat(store.readAfter(runUuid, events.get(0).cursor(), false))
          .extracting(PracticeRealtimeEvent::eventName)
          .containsExactly("agent_run_end");
      assertThat(redisTtl(runUuid)).isGreaterThan(0L).isLessThanOrEqualTo(120L);
    }
  }

  private static PracticeRealtimeStreamProperties properties() {
    PracticeRealtimeStreamProperties properties = new PracticeRealtimeStreamProperties();
    properties.setHost(System.getProperty("practice.realtime.local-redis.host", "127.0.0.1"));
    properties.setPort(Integer.getInteger("practice.realtime.local-redis.port", 6380));
    properties.setCommandTimeout(Duration.ofSeconds(3));
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
      List<PracticeRealtimeEvent> events = store.readAfter(runUuid, PracticeRealtimeProtocol.INITIAL_AFTER, false);
      if (events.size() >= expectedCount) {
        return events;
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

  private static void awaitWriteConnection(LettucePracticeRealtimeEventStore store) {
    long deadline = System.nanoTime() + EVENTUAL_TIMEOUT.toNanos();
    while (!store.writeConnectionReady()) {
      if (System.nanoTime() >= deadline) {
        throw new AssertionError("Timed out waiting for Redis write connection");
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError(interrupted);
      }
    }
  }

  private static long redisTtl(String runUuid) {
    try (io.lettuce.core.RedisClient client = io.lettuce.core.RedisClient.create("redis://"
        + System.getProperty("practice.realtime.local-redis.host", "127.0.0.1")
        + ":" + Integer.getInteger("practice.realtime.local-redis.port", 6380));
        io.lettuce.core.api.StatefulRedisConnection<String, String> connection = client.connect()) {
      return connection.sync().ttl(PracticeRealtimeProtocol.streamKey(runUuid));
    }
  }
}
