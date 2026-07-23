package org.congcong.algomentor.identity.group.relation;

import java.util.Objects;
import java.util.function.Supplier;
import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;

/** identity-user-relations 的共享 TTL 缓存门面。 */
public final class UserRelationCache implements UserRelationCacheInvalidator {

  private static final int SCHEMA_VERSION = 1;
  private static final CacheRegionName CACHE_NAME = new CacheRegionName("identity-user-relations");

  private final SharedTtlCacheRegion<Long, CachedUserRelations> region;
  private final SharedCacheInvalidationCoordinator invalidationCoordinator;

  public UserRelationCache(
      SharedCacheRegionFactory cacheFactory,
      SharedCacheInvalidationCoordinator invalidationCoordinator,
      UserRelationCacheProperties properties
  ) {
    this.invalidationCoordinator = Objects.requireNonNull(
        invalidationCoordinator, "invalidationCoordinator must not be null");
    this.region = Objects.requireNonNull(cacheFactory, "cacheFactory must not be null").createTtl(
        new SharedTtlCacheSpec(
            CACHE_NAME,
            CACHE_NAME.value(),
            SCHEMA_VERSION,
            properties.getMaximumSize(),
            properties.getTtl()),
        userId -> Long.toString(requireUserId(userId)));
  }

  public CachedUserRelations get(long userId, Supplier<CachedUserRelations> loader) {
    Objects.requireNonNull(loader, "loader must not be null");
    return region.get(requireUserId(userId), ignored -> Objects.requireNonNull(
        loader.get(), "user relation cache loader result must not be null"));
  }

  @Override
  public void invalidate(long userId, String reason) {
    invalidationCoordinator.invalidate(region, requireUserId(userId));
  }

  private static long requireUserId(long userId) {
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return userId;
  }
}
