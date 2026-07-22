package org.congcong.algomentor.cache.metrics;

import java.time.Duration;
import java.util.function.LongSupplier;
import org.congcong.algomentor.cache.spec.CacheRegionName;

public interface CacheMetrics {

  default boolean isEnabled() {
    return false;
  }

  void recordRequest(CacheRegionName cacheName, CacheRequestResult result);

  void recordLoad(CacheRegionName cacheName, CacheLoadResult result, Duration duration);

  void recordInvalidation(
      CacheRegionName cacheName,
      CacheInvalidationType type,
      CacheOperationResult result);

  void recordEviction(CacheRegionName cacheName, CacheEvictionCause cause);

  void registerEstimatedSize(CacheRegionName cacheName, LongSupplier sizeSupplier);
}
