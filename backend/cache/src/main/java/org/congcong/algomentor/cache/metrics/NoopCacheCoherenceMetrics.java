package org.congcong.algomentor.cache.metrics;

import java.time.Duration;
import org.congcong.algomentor.cache.coherence.CacheInvalidationEventType;
import org.congcong.algomentor.cache.coherence.SharedInvalidationDispatchResult;
import org.congcong.algomentor.cache.spec.CacheRegionName;

final class NoopCacheCoherenceMetrics implements CacheCoherenceMetrics {

  static final NoopCacheCoherenceMetrics INSTANCE = new NoopCacheCoherenceMetrics();

  private NoopCacheCoherenceMetrics() {
  }

  @Override
  public void recordEventPublished(CacheRegionName cacheName, CacheOperationResult result) {
  }

  @Override
  public void recordPoll(CacheOperationResult result) {
  }

  @Override
  public void recordEventConsumed(
      CacheRegionName cacheName,
      CacheInvalidationEventType eventType,
      SharedInvalidationDispatchResult result) {
  }

  @Override
  public void recordPollLag(Duration lag) {
  }

  @Override
  public void recordPollSuccess() {
  }

  @Override
  public void recordGapRecovery() {
  }

  @Override
  public void recordGenerationRetry(CacheRegionName cacheName) {
  }

  @Override
  public void recordCleanup(CacheOperationResult result) {
  }
}
