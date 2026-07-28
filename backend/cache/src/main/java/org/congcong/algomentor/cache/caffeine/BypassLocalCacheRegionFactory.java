package org.congcong.algomentor.cache.caffeine;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.congcong.algomentor.cache.api.LocalBoundedCacheRegion;
import org.congcong.algomentor.cache.api.LocalTtlCacheRegion;
import org.congcong.algomentor.cache.factory.LocalCacheRegionFactory;
import org.congcong.algomentor.cache.registry.CacheRegionDefinition;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.LocalBoundedCacheSpec;
import org.congcong.algomentor.cache.spec.LocalTtlCacheSpec;

public final class BypassLocalCacheRegionFactory implements LocalCacheRegionFactory {

  private final CacheRegionRegistry registry;
  private final Map<CacheRegionName, BypassLocalCacheRegion<?, ?>> regions = new ConcurrentHashMap<>();

  public BypassLocalCacheRegionFactory(CacheRegionRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  @Override
  public <K, V> LocalBoundedCacheRegion<K, V> createBounded(LocalBoundedCacheSpec spec) {
    registry.register(CacheRegionDefinition.bounded(Objects.requireNonNull(spec, "spec must not be null")));
    BypassLocalCacheRegion<?, ?> region = regions.computeIfAbsent(
        spec.name(), ignored -> new BypassLocalCacheRegion<>());
    registry.registerLocalRegion(spec.name(), region);
    return castBounded(region);
  }

  @Override
  public <K, V> LocalTtlCacheRegion<K, V> createTtl(LocalTtlCacheSpec spec) {
    registry.register(CacheRegionDefinition.ttl(Objects.requireNonNull(spec, "spec must not be null")));
    BypassLocalCacheRegion<?, ?> region = regions.computeIfAbsent(
        spec.name(), ignored -> new BypassLocalCacheRegion<>());
    registry.registerLocalRegion(spec.name(), region);
    return castTtl(region);
  }

  @SuppressWarnings("unchecked")
  private static <K, V> LocalBoundedCacheRegion<K, V> castBounded(BypassLocalCacheRegion<?, ?> region) {
    return (LocalBoundedCacheRegion<K, V>) region;
  }

  @SuppressWarnings("unchecked")
  private static <K, V> LocalTtlCacheRegion<K, V> castTtl(BypassLocalCacheRegion<?, ?> region) {
    return (LocalTtlCacheRegion<K, V>) region;
  }

  private static final class BypassLocalCacheRegion<K, V> extends BypassCacheRegion<K, V>
      implements LocalBoundedCacheRegion<K, V>, LocalTtlCacheRegion<K, V> {

    @Override
    public void invalidateAll() {
    }
  }
}
