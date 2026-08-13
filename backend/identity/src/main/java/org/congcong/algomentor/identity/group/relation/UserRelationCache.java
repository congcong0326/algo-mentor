package org.congcong.algomentor.identity.group.relation;

import java.util.Objects;
import java.util.function.Supplier;
import org.congcong.algomentor.cache.api.RedisTtlCacheRegion;
import org.congcong.algomentor.cache.codec.RedisValueCodecFactory;
import org.congcong.algomentor.cache.factory.RedisCacheRegionFactory;
import org.congcong.algomentor.cache.invalidation.CacheInvalidationExecutor;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.RedisTtlCacheSpec;

/** identity-user-relations 的 Redis-only TTL 缓存门面。 */
public final class UserRelationCache implements UserRelationCacheInvalidator {

  private static final int SCHEMA_VERSION = 1;
  private static final CacheRegionName CACHE_NAME = new CacheRegionName("identity-user-relations");

  private final RedisTtlCacheRegion<Long, CachedUserRelations> region;
  private final CacheInvalidationExecutor invalidationExecutor;

  public UserRelationCache(
      RedisCacheRegionFactory cacheFactory,
      RedisValueCodecFactory valueCodecs,
      CacheInvalidationExecutor invalidationExecutor,
      UserRelationCacheProperties properties
  ) {
    Objects.requireNonNull(valueCodecs, "valueCodecs must not be null");
    this.invalidationExecutor = Objects.requireNonNull(
        invalidationExecutor, "invalidationExecutor must not be null");
    this.region = Objects.requireNonNull(cacheFactory, "cacheFactory must not be null").createTtl(
        new RedisTtlCacheSpec(
            CACHE_NAME,
            CACHE_NAME.value(),
            SCHEMA_VERSION,
            properties.getTtl()),
        userId -> Long.toString(requireUserId(userId)),
        valueCodecs.json(CachedUserRelations.class));
  }

  public CachedUserRelations get(long userId, Supplier<CachedUserRelations> loader) {
    Objects.requireNonNull(loader, "loader must not be null");
    return region.get(requireUserId(userId), ignored -> Objects.requireNonNull(
        loader.get(), "user relation cache loader result must not be null"));
  }

  @Override
  public void invalidate(long userId, String reason) {
    long resolvedUserId = requireUserId(userId);
    invalidationExecutor.afterCommit(() -> region.invalidate(resolvedUserId));
  }

  private static long requireUserId(long userId) {
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return userId;
  }
}
