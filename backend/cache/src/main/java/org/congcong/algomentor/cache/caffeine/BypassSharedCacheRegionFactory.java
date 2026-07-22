package org.congcong.algomentor.cache.caffeine;

import java.util.Objects;
import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.coherence.SharedCacheRegionDescriptor;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.cache.registry.CacheRegionDefinition;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.registry.SharedCacheInvalidationTargetRegistry;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;
import org.congcong.algomentor.cache.spec.SharedCacheKeyCodec;

public final class BypassSharedCacheRegionFactory implements SharedCacheRegionFactory {

  private final CacheRegionRegistry registry;
  private final SharedCacheInvalidationTargetRegistry invalidationTargets;

  public BypassSharedCacheRegionFactory(CacheRegionRegistry registry) {
    this(registry, null);
  }

  public BypassSharedCacheRegionFactory(
      CacheRegionRegistry registry,
      SharedCacheInvalidationTargetRegistry invalidationTargets) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
    this.invalidationTargets = invalidationTargets;
  }

  @Override
  public <K, V> SharedTtlCacheRegion<K, V> createTtl(
      SharedTtlCacheSpec spec,
      SharedCacheKeyCodec<K> keyCodec) {
    registry.register(CacheRegionDefinition.sharedTtl(Objects.requireNonNull(spec, "spec must not be null")));
    BypassSharedTtlCacheRegion<K, V> region = new BypassSharedTtlCacheRegion<>(keyCodec, spec);
    if (invalidationTargets != null) {
      invalidationTargets.register(region);
    }
    return region;
  }

  private static final class BypassSharedTtlCacheRegion<K, V> extends BypassCacheRegion<K, V>
      implements SharedTtlCacheRegion<K, V>, SharedCacheRegionDescriptor<K, V> {

    private final SharedCacheKeyCodec<K> keyCodec;
    private final SharedTtlCacheSpec specification;

    private BypassSharedTtlCacheRegion(SharedCacheKeyCodec<K> keyCodec, SharedTtlCacheSpec specification) {
      this.keyCodec = Objects.requireNonNull(keyCodec, "keyCodec must not be null");
      this.specification = Objects.requireNonNull(specification, "specification must not be null");
    }

    @Override
    public SharedTtlCacheSpec specification() {
      return specification;
    }

    @Override
    public String encodeKey(K key) {
      Objects.requireNonNull(key, "key must not be null");
      return SharedCacheKeyCodec.requireValidToken(keyCodec.encode(key));
    }

    @Override
    public void invalidateKeyToken(String keyToken) {
      SharedCacheKeyCodec.requireValidToken(keyToken);
    }

    @Override
    public void invalidateAllLocal() {
    }
  }
}
