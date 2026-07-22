package org.congcong.algomentor.ai.governance.policy.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.cache.caffeine.CaffeineSharedCacheRegionFactory;
import org.congcong.algomentor.cache.coherence.LocalSharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.invalidation.CacheInvalidationExecutor;
import org.congcong.algomentor.cache.metrics.NoopCacheMetrics;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.junit.jupiter.api.Test;

class AiRuntimeCacheTest {

  @Test
  void cachesMissingSettingsAndReloadsAfterInvalidationWithoutCachingLoaderFailures() {
    AiRuntimeCache cache = new AiRuntimeCache(
        new CaffeineSharedCacheRegionFactory(new CacheRegionRegistry(), new NoopCacheMetrics()),
        new LocalSharedCacheInvalidationCoordinator(CacheInvalidationExecutorTestSupport.IMMEDIATE),
        new AiRuntimeCacheProperties());
    AtomicInteger loads = new AtomicInteger();

    assertThat(cache.getSettings(() -> {
      loads.incrementAndGet();
      return Optional.empty();
    })).isEmpty();
    assertThat(cache.getSettings(() -> {
      loads.incrementAndGet();
      return Optional.of(new AiRuntimeSettings(true, 50, null, null));
    })).isEmpty();
    assertThat(loads).hasValue(1);

    cache.invalidateSettings();

    assertThat(cache.getSettings(() -> {
      loads.incrementAndGet();
      return Optional.of(new AiRuntimeSettings(false, 20, 1L, java.time.Instant.EPOCH));
    })).contains(new AiRuntimeSettings(false, 20, 1L, java.time.Instant.EPOCH));
    assertThat(loads).hasValue(2);
  }

  private static final class CacheInvalidationExecutorTestSupport {
    private static final CacheInvalidationExecutor IMMEDIATE = Runnable::run;
  }
}
