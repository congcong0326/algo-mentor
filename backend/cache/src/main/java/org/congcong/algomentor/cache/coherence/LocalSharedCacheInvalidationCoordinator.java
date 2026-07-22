package org.congcong.algomentor.cache.coherence;

import java.util.Objects;
import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.invalidation.CacheInvalidationExecutor;

/** 单 JVM provider 或全局关闭 coherence 时使用的提交后本地失效协调器。 */
public final class LocalSharedCacheInvalidationCoordinator
    implements SharedCacheInvalidationCoordinator {

  private final CacheInvalidationExecutor invalidationExecutor;

  public LocalSharedCacheInvalidationCoordinator(CacheInvalidationExecutor invalidationExecutor) {
    this.invalidationExecutor = Objects.requireNonNull(
        invalidationExecutor, "invalidationExecutor must not be null");
  }

  @Override
  public <K, V> void invalidate(SharedTtlCacheRegion<K, V> region, K key) {
    Objects.requireNonNull(region, "region must not be null");
    Objects.requireNonNull(key, "key must not be null");
    invalidationExecutor.afterCommit(() -> region.invalidate(key));
  }
}
