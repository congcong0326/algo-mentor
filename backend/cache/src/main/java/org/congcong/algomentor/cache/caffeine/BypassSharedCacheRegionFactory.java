package org.congcong.algomentor.cache.caffeine;

import java.util.Objects;
import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.cache.registry.CacheRegionDefinition;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;

public final class BypassSharedCacheRegionFactory implements SharedCacheRegionFactory {

  private final CacheRegionRegistry registry;

  public BypassSharedCacheRegionFactory(CacheRegionRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  @Override
  public <K, V> SharedTtlCacheRegion<K, V> createTtl(SharedTtlCacheSpec spec) {
    registry.register(CacheRegionDefinition.sharedTtl(Objects.requireNonNull(spec, "spec must not be null")));
    return new BypassSharedTtlCacheRegion<>();
  }

  private static final class BypassSharedTtlCacheRegion<K, V> extends BypassCacheRegion<K, V>
      implements SharedTtlCacheRegion<K, V> {
  }
}
