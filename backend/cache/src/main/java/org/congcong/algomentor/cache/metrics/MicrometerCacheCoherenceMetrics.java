package org.congcong.algomentor.cache.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.congcong.algomentor.cache.coherence.CacheInvalidationEventType;
import org.congcong.algomentor.cache.coherence.SharedInvalidationDispatchResult;
import org.congcong.algomentor.cache.spec.CacheRegionName;

public final class MicrometerCacheCoherenceMetrics implements CacheCoherenceMetrics {

  private final MeterRegistry registry;
  private final AtomicLong pollLagNanos = new AtomicLong();
  private final AtomicLong lastSuccessEpochMillis = new AtomicLong();

  public MicrometerCacheCoherenceMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
    Gauge.builder(CacheMetricNames.COHERENCE_POLL_LAG, pollLagNanos,
            value -> value.get() / 1_000_000_000d)
        .register(registry);
    Gauge.builder(CacheMetricNames.COHERENCE_LAST_SUCCESS_AGE, this,
            ignored -> lastSuccessAgeSeconds())
        .register(registry);
  }

  @Override
  public void recordEventPublished(CacheRegionName cacheName, CacheOperationResult result) {
    Counter.builder(CacheMetricNames.COHERENCE_EVENTS_PUBLISHED)
        .tag(CacheMetricTags.CACHE, cacheName.value())
        .tag(CacheMetricTags.RESULT, result.tagValue())
        .register(registry)
        .increment();
  }

  @Override
  public void recordPoll(CacheOperationResult result) {
    Counter.builder(CacheMetricNames.COHERENCE_POLLS)
        .tag(CacheMetricTags.RESULT, result.tagValue())
        .register(registry)
        .increment();
  }

  @Override
  public void recordEventConsumed(
      CacheRegionName cacheName,
      CacheInvalidationEventType eventType,
      SharedInvalidationDispatchResult result) {
    Counter.builder(CacheMetricNames.COHERENCE_EVENTS_CONSUMED)
        .tag(CacheMetricTags.CACHE, cacheName.value())
        .tag(CacheMetricTags.TYPE, eventType.tagValue())
        .tag(CacheMetricTags.RESULT, result.tagValue())
        .register(registry)
        .increment();
  }

  @Override
  public void recordPollLag(Duration lag) {
    pollLagNanos.set(Math.max(0, lag.toNanos()));
  }

  @Override
  public void recordPollSuccess() {
    lastSuccessEpochMillis.set(Instant.now().toEpochMilli());
  }

  @Override
  public void recordGapRecovery() {
    Counter.builder(CacheMetricNames.COHERENCE_GAP_RECOVERIES)
        .register(registry)
        .increment();
  }

  @Override
  public void recordGenerationRetry(CacheRegionName cacheName) {
    Counter.builder(CacheMetricNames.COHERENCE_GENERATION_RETRIES)
        .tag(CacheMetricTags.CACHE, cacheName.value())
        .register(registry)
        .increment();
  }

  @Override
  public void recordCleanup(CacheOperationResult result) {
    Counter.builder(CacheMetricNames.COHERENCE_CLEANUP)
        .tag(CacheMetricTags.RESULT, result.tagValue())
        .register(registry)
        .increment();
  }

  private double lastSuccessAgeSeconds() {
    long lastSuccess = lastSuccessEpochMillis.get();
    if (lastSuccess == 0) {
      return Double.POSITIVE_INFINITY;
    }
    return Math.max(0, Instant.now().toEpochMilli() - lastSuccess) / 1_000d;
  }
}
