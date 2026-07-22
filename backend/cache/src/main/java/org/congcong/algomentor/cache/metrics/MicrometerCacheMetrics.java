package org.congcong.algomentor.cache.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.congcong.algomentor.cache.spec.CacheRegionName;

public final class MicrometerCacheMetrics implements CacheMetrics {

  private final MeterRegistry registry;
  private final Set<CacheRegionName> registeredSizeGauges = ConcurrentHashMap.newKeySet();

  public MicrometerCacheMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void recordRequest(CacheRegionName cacheName, CacheRequestResult result) {
    Counter.builder(CacheMetricNames.REQUESTS)
        .tag(CacheMetricTags.CACHE, cacheName.value())
        .tag(CacheMetricTags.RESULT, result.tagValue())
        .register(registry)
        .increment();
  }

  @Override
  public void recordLoad(CacheRegionName cacheName, CacheLoadResult result, Duration duration) {
    Counter.builder(CacheMetricNames.LOADS)
        .tag(CacheMetricTags.CACHE, cacheName.value())
        .tag(CacheMetricTags.RESULT, result.tagValue())
        .register(registry)
        .increment();
    Timer.builder(CacheMetricNames.LOAD_DURATION)
        .tag(CacheMetricTags.CACHE, cacheName.value())
        .register(registry)
        .record(duration);
  }

  @Override
  public void recordInvalidation(
      CacheRegionName cacheName,
      CacheInvalidationType type,
      CacheOperationResult result) {
    Counter.builder(CacheMetricNames.INVALIDATIONS)
        .tag(CacheMetricTags.CACHE, cacheName.value())
        .tag(CacheMetricTags.TYPE, type.tagValue())
        .tag(CacheMetricTags.RESULT, result.tagValue())
        .register(registry)
        .increment();
  }

  @Override
  public void recordEviction(CacheRegionName cacheName, CacheEvictionCause cause) {
    Counter.builder(CacheMetricNames.EVICTIONS)
        .tag(CacheMetricTags.CACHE, cacheName.value())
        .tag(CacheMetricTags.CAUSE, cause.tagValue())
        .register(registry)
        .increment();
  }

  @Override
  public void registerEstimatedSize(CacheRegionName cacheName, LongSupplier sizeSupplier) {
    if (registeredSizeGauges.add(cacheName)) {
      Gauge.builder(CacheMetricNames.ESTIMATED_SIZE, sizeSupplier, LongSupplier::getAsLong)
          .tag(CacheMetricTags.CACHE, cacheName.value())
          .register(registry);
    }
  }
}
