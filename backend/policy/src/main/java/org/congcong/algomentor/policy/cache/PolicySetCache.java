package org.congcong.algomentor.policy.cache;

import java.util.Objects;
import java.util.function.Supplier;
import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;
import org.congcong.algomentor.policy.model.CompiledPolicySet;

/** typeCode 到已启用策略快照的共享 TTL 缓存门面。 */
public final class PolicySetCache {

  private static final int SCHEMA_VERSION = 1;
  private static final CacheRegionName CACHE_NAME = new CacheRegionName("generic-policy-set");

  private final SharedTtlCacheRegion<String, CompiledPolicySet> policySets;
  private final SharedCacheInvalidationCoordinator invalidationCoordinator;

  public PolicySetCache(
      SharedCacheRegionFactory cacheFactory,
      SharedCacheInvalidationCoordinator invalidationCoordinator,
      GenericPolicyCacheProperties properties
  ) {
    Objects.requireNonNull(cacheFactory, "cacheFactory must not be null");
    this.invalidationCoordinator = Objects.requireNonNull(
        invalidationCoordinator, "invalidationCoordinator must not be null");
    Objects.requireNonNull(properties, "properties must not be null");
    this.policySets = cacheFactory.createTtl(
        new SharedTtlCacheSpec(
            CACHE_NAME,
            CACHE_NAME.value(),
            SCHEMA_VERSION,
            properties.getMaximumSize(),
            properties.getTtl()),
        PolicySetCache::validateTypeCode);
  }

  public CompiledPolicySet get(String typeCode, Supplier<CompiledPolicySet> loader) {
    Objects.requireNonNull(loader, "loader must not be null");
    return policySets.get(validateTypeCode(typeCode), ignored -> Objects.requireNonNull(
        loader.get(), "policy set loader result must not be null"));
  }

  public void invalidate(String typeCode) {
    invalidationCoordinator.invalidate(policySets, validateTypeCode(typeCode));
  }

  private static String validateTypeCode(String typeCode) {
    if (typeCode == null || typeCode.isBlank() || typeCode.length() > 64) {
      throw new IllegalArgumentException("policy cache typeCode must contain between 1 and 64 characters");
    }
    return typeCode;
  }
}
