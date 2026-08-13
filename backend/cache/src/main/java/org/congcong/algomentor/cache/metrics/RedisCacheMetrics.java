package org.congcong.algomentor.cache.metrics;

import java.time.Duration;
import org.congcong.algomentor.cache.spec.CacheRegionName;

/** Redis 命令旁路观测指标。 */
public interface RedisCacheMetrics {

  void recordCommand(CacheRegionName cacheName, RedisCacheOperation operation,
      CacheOperationResult result, Duration duration);

  void recordFailure(CacheRegionName cacheName, RedisCacheOperation operation,
      RedisCacheFailureReason reason);

  void recordOversizeValue(CacheRegionName cacheName);

  static RedisCacheMetrics noop() { return NoopRedisCacheMetrics.INSTANCE; }
}
