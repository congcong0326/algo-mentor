package org.congcong.algomentor.cache.coherence.postgres;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationEvent;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationEventStore;
import org.congcong.algomentor.cache.coherence.SharedInvalidationDispatchResult;
import org.congcong.algomentor.cache.config.CacheProperties;
import org.congcong.algomentor.cache.metrics.CacheCoherenceMetrics;
import org.congcong.algomentor.cache.metrics.CacheOperationResult;
import org.congcong.algomentor.cache.registry.SharedCacheInvalidationTargetRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/** A single node-local cursor polls the shared PostgreSQL invalidation event stream. */
public final class PostgresSharedInvalidationPoller implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(PostgresSharedInvalidationPoller.class);

  private final SharedCacheInvalidationEventStore eventStore;
  private final SharedCacheInvalidationTargetRegistry invalidationTargets;
  private final CacheCoherenceMetrics metrics;
  private final CacheProperties.Coherence properties;
  private final Clock clock;
  private final ScheduledExecutorService executor;
  private final AtomicBoolean running = new AtomicBoolean();
  private volatile long cursor;
  private volatile Instant lastCleanupAt = Instant.EPOCH;
  private volatile Instant lastGapCheckAt = Instant.EPOCH;
  private volatile boolean initialized;

  public PostgresSharedInvalidationPoller(
      SharedCacheInvalidationEventStore eventStore,
      SharedCacheInvalidationTargetRegistry invalidationTargets,
      CacheCoherenceMetrics metrics,
      CacheProperties.Coherence properties) {
    this(eventStore, invalidationTargets, metrics, properties, Clock.systemUTC(),
        Executors.newSingleThreadScheduledExecutor(runnable -> {
          Thread thread = new Thread(runnable, "algo-mentor-cache-coherence");
          thread.setDaemon(true);
          return thread;
        }));
  }

  PostgresSharedInvalidationPoller(
      SharedCacheInvalidationEventStore eventStore,
      SharedCacheInvalidationTargetRegistry invalidationTargets,
      CacheCoherenceMetrics metrics,
      CacheProperties.Coherence properties,
      Clock clock,
      ScheduledExecutorService executor) {
    this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
    this.invalidationTargets = Objects.requireNonNull(
        invalidationTargets, "invalidationTargets must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
  }

  @Override
  public void start() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    try {
      initializeCursor();
      log.info("Cache coherence poller started: pollInterval={}, batchSize={}",
          properties.getPollInterval(), properties.getBatchSize());
    } catch (RuntimeException exception) {
      running.set(false);
      executor.shutdownNow();
      throw exception;
    }
    scheduleNext(Duration.ZERO);
  }

  @Override
  public void stop() {
    if (running.compareAndSet(true, false)) {
      executor.shutdownNow();
    }
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public int getPhase() {
    return Integer.MIN_VALUE;
  }

  /** Performs one poll synchronously, primarily useful for integration tests and diagnostics. */
  public void pollOnce() {
    try {
      if (!initialized) {
        initializeCursor();
      }
      if (shouldCheckForGap() && recoverGapWhenNeeded()) {
        recordPollSuccess();
        return;
      }
      drainAvailableEvents();
      recordPollSuccess();
    } catch (RuntimeException exception) {
      metrics.recordPoll(CacheOperationResult.FAILURE);
      log.warn("Cache coherence poll failed at cursor={}", cursor, exception);
    }
  }

  /** Deletes events outside the configured retention period. */
  public void cleanupOnce() {
    try {
      eventStore.deleteCreatedBefore(clock.instant().minus(properties.getEventRetention()));
      lastCleanupAt = clock.instant();
      metrics.recordCleanup(CacheOperationResult.SUCCESS);
    } catch (RuntimeException exception) {
      metrics.recordCleanup(CacheOperationResult.FAILURE);
      log.warn("Cache coherence event cleanup failed", exception);
    }
  }

  long cursor() {
    return cursor;
  }

  private void initializeCursor() {
    cursor = eventStore.findHighWatermark();
    initialized = true;
  }

  private boolean recoverGapWhenNeeded() {
    lastGapCheckAt = clock.instant();
    OptionalLong lowestId = eventStore.findLowestId();
    if (lowestId.isEmpty() || cursor >= lowestId.getAsLong() - 1) {
      return false;
    }
    invalidationTargets.invalidateAll();
    cursor = eventStore.findHighWatermark();
    metrics.recordGapRecovery();
    log.warn("Cache coherence cursor gap recovered by clearing all local shared cache regions");
    return true;
  }

  private void scheduleNext(Duration delay) {
    long delayMillis = Math.max(0, delay.toMillis());
    executor.schedule(this::pollAndSchedule, delayMillis, TimeUnit.MILLISECONDS);
  }

  private void pollAndSchedule() {
    if (!running.get()) {
      return;
    }
    pollOnce();
    if (Duration.between(lastCleanupAt, clock.instant()).compareTo(properties.getCleanupInterval()) >= 0) {
      cleanupOnce();
    }
    if (running.get()) {
      scheduleNext(jitteredPollInterval());
    }
  }

  private boolean shouldCheckForGap() {
    return Duration.between(lastGapCheckAt, clock.instant())
        .compareTo(properties.getCleanupInterval()) >= 0;
  }

  private void drainAvailableEvents() {
    while (true) {
      List<SharedCacheInvalidationEvent> events = eventStore.findAfter(cursor, properties.getBatchSize());
      dispatch(events);
      if (events.size() < properties.getBatchSize()) {
        return;
      }
    }
  }

  private void dispatch(List<SharedCacheInvalidationEvent> events) {
    for (SharedCacheInvalidationEvent event : events) {
      SharedInvalidationDispatchResult result = invalidationTargets.dispatch(event);
      CacheRegionName metricName = result == SharedInvalidationDispatchResult.UNKNOWN_CACHE
          ? SharedCacheInvalidationTargetRegistry.UNKNOWN_CACHE_NAME
          : event.cacheName();
      metrics.recordEventConsumed(metricName, event.eventType(), result);
    }
    if (!events.isEmpty()) {
      cursor = events.get(events.size() - 1).id();
      Instant newestCreatedAt = events.get(events.size() - 1).createdAt();
      metrics.recordPollLag(Duration.between(newestCreatedAt, clock.instant()));
    }
  }

  private void recordPollSuccess() {
    metrics.recordPoll(CacheOperationResult.SUCCESS);
    metrics.recordPollSuccess();
  }

  private Duration jitteredPollInterval() {
    double jitter = (Math.random() * 2 - 1) * properties.getJitterRatio();
    long nanos = Math.max(1, Math.round(properties.getPollInterval().toNanos() * (1 + jitter)));
    return Duration.ofNanos(nanos);
  }
}
