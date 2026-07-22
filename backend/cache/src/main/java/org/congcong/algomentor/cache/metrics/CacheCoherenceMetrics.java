package org.congcong.algomentor.cache.metrics;

import java.time.Duration;
import org.congcong.algomentor.cache.coherence.CacheInvalidationEventType;
import org.congcong.algomentor.cache.coherence.SharedInvalidationDispatchResult;
import org.congcong.algomentor.cache.spec.CacheRegionName;

/** 共享失效链路的低基数指标端口。 */
public interface CacheCoherenceMetrics {

  void recordEventPublished(CacheRegionName cacheName, CacheOperationResult result);

  void recordPoll(CacheOperationResult result);

  void recordEventConsumed(
      CacheRegionName cacheName,
      CacheInvalidationEventType eventType,
      SharedInvalidationDispatchResult result);

  void recordPollLag(Duration lag);

  void recordPollSuccess();

  void recordGapRecovery();

  void recordGenerationRetry(CacheRegionName cacheName);

  void recordCleanup(CacheOperationResult result);

  static CacheCoherenceMetrics noop() {
    return NoopCacheCoherenceMetrics.INSTANCE;
  }
}
