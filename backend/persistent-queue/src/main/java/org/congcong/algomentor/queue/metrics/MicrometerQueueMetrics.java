package org.congcong.algomentor.queue.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.congcong.algomentor.queue.dispatch.QueueDispatchOutcome;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;
import org.congcong.algomentor.queue.runtime.QueueWorkerState;

/** Micrometer 适配，队列 backlog 查询按 topic 进行短时缓存。 */
public class MicrometerQueueMetrics implements QueueMetrics {

  private static final long BACKLOG_CACHE_MILLIS = 10_000L;
  private final MeterRegistry registry;
  private final ConcurrentMap<String, BacklogSnapshot> backlogSnapshots = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AtomicInteger> workerStates = new ConcurrentHashMap<>();

  public MicrometerQueueMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void bindTopics(Set<String> topics, QueueMessageRepository repository) {
    for (String topic : topics) {
      BacklogSnapshot snapshot = backlogSnapshots.computeIfAbsent(topic, ignored -> new BacklogSnapshot(repository, topic));
      Gauge.builder("learner.profile.queue.pending", snapshot, BacklogSnapshot::pending).tag("topic", topic).register(registry);
      Gauge.builder("learner.profile.queue.oldest_pending_age", snapshot, BacklogSnapshot::oldestAgeSeconds).tag("topic", topic).register(registry);
      AtomicInteger state = workerStates.computeIfAbsent(topic, ignored -> new AtomicInteger(QueueWorkerState.STOPPED.ordinal()));
      Gauge.builder("learner.profile.queue.worker.active", state, value -> value.get() == QueueWorkerState.RUNNING.ordinal() ? 1D : 0D)
          .tag("topic", topic).register(registry);
    }
  }

  @Override
  public void recordDispatch(String topic, QueueDispatchOutcome outcome) {
    Counter.builder("learner.profile.queue.dequeue").tag("topic", topic).tag("outcome", outcome.name()).register(registry).increment();
  }

  @Override
  public void recordWorkerState(String topic, QueueWorkerState state) {
    workerStates.computeIfAbsent(topic, ignored -> new AtomicInteger()).set(state.ordinal());
  }

  @Override
  public void recordWorkerFailure(String topic) {
    Counter.builder("learner.profile.queue.worker.failure").tag("topic", topic).register(registry).increment();
  }

  @Override
  public void recordTerminalFailure(String topic) {
    Counter.builder("learner.profile.queue.terminal_failure").tag("topic", topic).register(registry).increment();
  }

  @Override
  public void recordCleanup(int deletedCount, Duration duration) {
    Counter.builder("learner.profile.queue.cleanup").tag("outcome", "completed").register(registry).increment(deletedCount);
    registry.timer("learner.profile.queue.cleanup.duration").record(duration);
  }

  private static final class BacklogSnapshot {
    private final QueueMessageRepository repository;
    private final String topic;
    private final AtomicLong refreshedAt = new AtomicLong();
    private volatile long pending;
    private volatile double oldestAgeSeconds;

    private BacklogSnapshot(QueueMessageRepository repository, String topic) {
      this.repository = repository;
      this.topic = topic;
    }

    private double pending() {
      refresh();
      return pending;
    }

    private double oldestAgeSeconds() {
      refresh();
      return oldestAgeSeconds;
    }

    private void refresh() {
      long now = System.currentTimeMillis();
      long previous = refreshedAt.get();
      if (now - previous < BACKLOG_CACHE_MILLIS || !refreshedAt.compareAndSet(previous, now)) return;
      try {
        pending = repository.countPendingByTopic(topic);
        oldestAgeSeconds = repository.findOldestPendingCreatedAt(topic)
            .map(value -> Math.max(0D, Duration.between(value, Instant.now()).toMillis() / 1_000D))
            .orElse(0D);
      } catch (RuntimeException ignored) {
        // 指标采集不应影响业务路径，保留上一次快照。
      }
    }
  }
}
