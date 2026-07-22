package org.congcong.algomentor.cache.registry;

import org.congcong.algomentor.cache.api.CacheRegionType;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.LocalBoundedCacheSpec;
import org.congcong.algomentor.cache.spec.LocalTtlCacheSpec;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;

public record CacheRegionDefinition(
    CacheRegionName name,
    CacheRegionType type,
    String specification,
    SharedCacheIdentity sharedIdentity) {

  public CacheRegionDefinition(CacheRegionName name, CacheRegionType type, String specification) {
    this(name, type, specification, null);
  }

  public static CacheRegionDefinition bounded(LocalBoundedCacheSpec spec) {
    return new CacheRegionDefinition(
        spec.name(),
        CacheRegionType.LOCAL_BOUNDED,
        "maximumSize=" + spec.maximumSize(),
        null);
  }

  public static CacheRegionDefinition ttl(LocalTtlCacheSpec spec) {
    return new CacheRegionDefinition(
        spec.name(),
        CacheRegionType.LOCAL_TTL,
        "maximumSize=" + spec.maximumSize() + ",ttl=" + spec.ttl(),
        null);
  }

  public static CacheRegionDefinition sharedTtl(SharedTtlCacheSpec spec) {
    return new CacheRegionDefinition(
        spec.name(),
        CacheRegionType.SHARED_TTL,
        "namespace=" + spec.namespace()
            + ",schemaVersion=" + spec.schemaVersion()
            + ",maximumSize=" + spec.maximumSize()
            + ",ttl=" + spec.ttl(),
        new SharedCacheIdentity(spec.namespace(), spec.schemaVersion()));
  }
}
