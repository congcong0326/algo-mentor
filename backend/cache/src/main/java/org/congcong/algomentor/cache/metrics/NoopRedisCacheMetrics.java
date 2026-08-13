package org.congcong.algomentor.cache.metrics;

import java.time.Duration;
import org.congcong.algomentor.cache.spec.CacheRegionName;

enum NoopRedisCacheMetrics implements RedisCacheMetrics {
  INSTANCE;

  @Override public void recordCommand(CacheRegionName cacheName, RedisCacheOperation operation,
      CacheOperationResult result, Duration duration) { }
  @Override public void recordFailure(CacheRegionName cacheName, RedisCacheOperation operation,
      RedisCacheFailureReason reason) { }
  @Override public void recordOversizeValue(CacheRegionName cacheName) { }
}
