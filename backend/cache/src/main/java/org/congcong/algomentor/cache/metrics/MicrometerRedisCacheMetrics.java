package org.congcong.algomentor.cache.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import org.congcong.algomentor.cache.spec.CacheRegionName;

public final class MicrometerRedisCacheMetrics implements RedisCacheMetrics {

  private final MeterRegistry registry;

  public MicrometerRedisCacheMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  @Override
  public void recordCommand(CacheRegionName cacheName, RedisCacheOperation operation,
      CacheOperationResult result, Duration duration) {
    Counter.builder(CacheMetricNames.REDIS_COMMANDS)
        .tag(CacheMetricTags.CACHE, cacheName.value())
        .tag(CacheMetricTags.OPERATION, operation.tagValue())
        .tag(CacheMetricTags.RESULT, result.tagValue())
        .register(registry)
        .increment();
    Timer.builder(CacheMetricNames.REDIS_COMMAND_DURATION)
        .tag(CacheMetricTags.CACHE, cacheName.value())
        .tag(CacheMetricTags.OPERATION, operation.tagValue())
        .register(registry)
        .record(duration);
  }

  @Override
  public void recordFailure(CacheRegionName cacheName, RedisCacheOperation operation,
      RedisCacheFailureReason reason) {
    Counter.builder(CacheMetricNames.REDIS_FAILURES)
        .tag(CacheMetricTags.CACHE, cacheName.value())
        .tag(CacheMetricTags.OPERATION, operation.tagValue())
        .tag(CacheMetricTags.REASON, reason.tagValue())
        .register(registry)
        .increment();
  }

  @Override
  public void recordOversizeValue(CacheRegionName cacheName) {
    Counter.builder(CacheMetricNames.REDIS_OVERSIZE_VALUES)
        .tag(CacheMetricTags.CACHE, cacheName.value())
        .register(registry)
        .increment();
  }
}
