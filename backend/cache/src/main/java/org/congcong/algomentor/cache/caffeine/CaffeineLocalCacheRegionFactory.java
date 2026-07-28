package org.congcong.algomentor.cache.caffeine;

import com.github.benmanes.caffeine.cache.Ticker;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.congcong.algomentor.cache.api.LocalBoundedCacheRegion;
import org.congcong.algomentor.cache.api.LocalCacheRegion;
import org.congcong.algomentor.cache.api.LocalTtlCacheRegion;
import org.congcong.algomentor.cache.factory.LocalCacheRegionFactory;
import org.congcong.algomentor.cache.metrics.CacheMetrics;
import org.congcong.algomentor.cache.registry.CacheRegionDefinition;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.LocalBoundedCacheSpec;
import org.congcong.algomentor.cache.spec.LocalTtlCacheSpec;

public final class CaffeineLocalCacheRegionFactory implements LocalCacheRegionFactory {

  private final CacheRegionRegistry registry;
  private final CacheMetrics metrics;
  private final Ticker ticker;
  private final Map<CacheRegionName, LocalCacheRegion<?, ?>> regions = new ConcurrentHashMap<>();

  public CaffeineLocalCacheRegionFactory(CacheRegionRegistry registry, CacheMetrics metrics) {
    this(registry, metrics, Ticker.systemTicker());
  }

  CaffeineLocalCacheRegionFactory(CacheRegionRegistry registry, CacheMetrics metrics, Ticker ticker) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
  }

  @Override
  public <K, V> LocalBoundedCacheRegion<K, V> createBounded(LocalBoundedCacheSpec spec) {
    Objects.requireNonNull(spec, "spec must not be null");
    registry.register(CacheRegionDefinition.bounded(spec));
    LocalCacheRegion<?, ?> region = regions.computeIfAbsent(spec.name(), ignored ->
        new CaffeineLocalBoundedCacheRegion<>(
            CaffeineCacheRegion.create(spec.name(), spec.maximumSize(), null, ticker, metrics)));
    registry.registerLocalRegion(spec.name(), region);
    return castBounded(region);
  }

  @Override
  public <K, V> LocalTtlCacheRegion<K, V> createTtl(LocalTtlCacheSpec spec) {
    Objects.requireNonNull(spec, "spec must not be null");
    registry.register(CacheRegionDefinition.ttl(spec));
    LocalCacheRegion<?, ?> region = regions.computeIfAbsent(spec.name(), ignored ->
        new CaffeineLocalTtlCacheRegion<>(
            CaffeineCacheRegion.create(spec.name(), spec.maximumSize(), spec.ttl(), ticker, metrics)));
    registry.registerLocalRegion(spec.name(), region);
    return castTtl(region);
  }

  @SuppressWarnings("unchecked")
  private static <K, V> LocalBoundedCacheRegion<K, V> castBounded(LocalCacheRegion<?, ?> region) {
    return (LocalBoundedCacheRegion<K, V>) region;
  }

  @SuppressWarnings("unchecked")
  private static <K, V> LocalTtlCacheRegion<K, V> castTtl(LocalCacheRegion<?, ?> region) {
    return (LocalTtlCacheRegion<K, V>) region;
  }
}
