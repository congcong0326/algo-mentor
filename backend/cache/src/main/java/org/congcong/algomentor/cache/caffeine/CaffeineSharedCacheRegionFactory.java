package org.congcong.algomentor.cache.caffeine;

import com.github.benmanes.caffeine.cache.Ticker;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.cache.metrics.CacheMetrics;
import org.congcong.algomentor.cache.registry.CacheRegionDefinition;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CaffeineSharedCacheRegionFactory implements SharedCacheRegionFactory {

  private static final Logger log = LoggerFactory.getLogger(CaffeineSharedCacheRegionFactory.class);

  private final CacheRegionRegistry registry;
  private final CacheMetrics metrics;
  private final Ticker ticker;
  private final Map<CacheRegionName, SharedTtlCacheRegion<?, ?>> regions = new ConcurrentHashMap<>();

  public CaffeineSharedCacheRegionFactory(CacheRegionRegistry registry, CacheMetrics metrics) {
    this(registry, metrics, Ticker.systemTicker());
  }

  CaffeineSharedCacheRegionFactory(CacheRegionRegistry registry, CacheMetrics metrics, Ticker ticker) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
    log.info("Cache shared provider initialized: provider=caffeine scope=single-jvm");
  }

  @Override
  public <K, V> SharedTtlCacheRegion<K, V> createTtl(SharedTtlCacheSpec spec) {
    Objects.requireNonNull(spec, "spec must not be null");
    registry.register(CacheRegionDefinition.sharedTtl(spec));
    SharedTtlCacheRegion<?, ?> region = regions.computeIfAbsent(spec.name(), ignored ->
        new CaffeineSharedTtlCacheRegion<>(
            CaffeineCacheRegion.create(spec.name(), spec.maximumSize(), spec.ttl(), ticker, metrics)));
    return cast(region);
  }

  @SuppressWarnings("unchecked")
  private static <K, V> SharedTtlCacheRegion<K, V> cast(SharedTtlCacheRegion<?, ?> region) {
    return (SharedTtlCacheRegion<K, V>) region;
  }
}
