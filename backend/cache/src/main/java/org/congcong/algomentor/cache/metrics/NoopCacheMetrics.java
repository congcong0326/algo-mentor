package org.congcong.algomentor.cache.metrics;

import java.time.Duration;
import java.util.function.LongSupplier;
import org.congcong.algomentor.cache.spec.CacheRegionName;

public final class NoopCacheMetrics implements CacheMetrics {

  @Override
  public void recordRequest(CacheRegionName cacheName, CacheRequestResult result) {
  }

  @Override
  public void recordLoad(CacheRegionName cacheName, CacheLoadResult result, Duration duration) {
  }

  @Override
  public void recordInvalidation(
      CacheRegionName cacheName,
      CacheInvalidationType type,
      CacheOperationResult result) {
  }

  @Override
  public void recordEviction(CacheRegionName cacheName, CacheEvictionCause cause) {
  }

  @Override
  public void registerEstimatedSize(CacheRegionName cacheName, LongSupplier sizeSupplier) {
  }
}
