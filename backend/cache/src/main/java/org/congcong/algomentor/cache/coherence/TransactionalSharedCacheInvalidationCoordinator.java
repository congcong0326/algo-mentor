package org.congcong.algomentor.cache.coherence;

import java.util.Objects;
import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.invalidation.CacheInvalidationExecutor;
import org.congcong.algomentor.cache.metrics.CacheCoherenceMetrics;
import org.congcong.algomentor.cache.metrics.CacheOperationResult;

/** PostgreSQL event append and local after-commit invalidation implementation. */
public final class TransactionalSharedCacheInvalidationCoordinator
    implements SharedCacheInvalidationCoordinator {

  private final SharedCacheInvalidationEventStore eventStore;
  private final CacheInvalidationExecutor invalidationExecutor;
  private final CacheCoherenceMetrics metrics;

  public TransactionalSharedCacheInvalidationCoordinator(
      SharedCacheInvalidationEventStore eventStore,
      CacheInvalidationExecutor invalidationExecutor,
      CacheCoherenceMetrics metrics) {
    this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
    this.invalidationExecutor = Objects.requireNonNull(
        invalidationExecutor, "invalidationExecutor must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
  }

  @Override
  public <K, V> void invalidate(SharedTtlCacheRegion<K, V> region, K key) {
    SharedCacheRegionDescriptor<K, V> descriptor = descriptor(region);
    String keyToken = descriptor.encodeKey(key);
    try {
      eventStore.appendKeyInvalidation(descriptor.specification(), keyToken);
      metrics.recordEventPublished(descriptor.specification().name(), CacheOperationResult.SUCCESS);
    } catch (RuntimeException exception) {
      metrics.recordEventPublished(descriptor.specification().name(), CacheOperationResult.FAILURE);
      throw exception;
    }
    invalidationExecutor.afterCommit(() -> descriptor.invalidateKeyToken(keyToken));
  }

  @SuppressWarnings("unchecked")
  private static <K, V> SharedCacheRegionDescriptor<K, V> descriptor(
      SharedTtlCacheRegion<K, V> region) {
    Objects.requireNonNull(region, "region must not be null");
    if (!(region instanceof SharedCacheRegionDescriptor<?, ?> descriptor)) {
      throw new IllegalArgumentException("Shared region does not expose an invalidation descriptor");
    }
    return (SharedCacheRegionDescriptor<K, V>) descriptor;
  }
}
