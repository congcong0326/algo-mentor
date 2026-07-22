package org.congcong.algomentor.cache.registry;

import org.congcong.algomentor.cache.api.CacheRegionType;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.LocalBoundedCacheSpec;
import org.congcong.algomentor.cache.spec.LocalTtlCacheSpec;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;

public record CacheRegionDefinition(
    CacheRegionName name,
    CacheRegionType type,
    String specification) {

  public static CacheRegionDefinition bounded(LocalBoundedCacheSpec spec) {
    return new CacheRegionDefinition(
        spec.name(),
        CacheRegionType.LOCAL_BOUNDED,
        "maximumSize=" + spec.maximumSize());
  }

  public static CacheRegionDefinition ttl(LocalTtlCacheSpec spec) {
    return new CacheRegionDefinition(
        spec.name(),
        CacheRegionType.LOCAL_TTL,
        "maximumSize=" + spec.maximumSize() + ",ttl=" + spec.ttl());
  }

  public static CacheRegionDefinition sharedTtl(SharedTtlCacheSpec spec) {
    return new CacheRegionDefinition(
        spec.name(),
        CacheRegionType.SHARED_TTL,
        "namespace=" + spec.namespace()
            + ",schemaVersion=" + spec.schemaVersion()
            + ",maximumSize=" + spec.maximumSize()
            + ",ttl=" + spec.ttl());
  }
}
