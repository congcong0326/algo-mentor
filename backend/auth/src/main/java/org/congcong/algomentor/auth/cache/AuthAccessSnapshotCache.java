package org.congcong.algomentor.auth.cache;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.congcong.algomentor.cache.api.RedisTtlCacheRegion;
import org.congcong.algomentor.cache.codec.RedisValueCodecFactory;
import org.congcong.algomentor.cache.factory.RedisCacheRegionFactory;
import org.congcong.algomentor.cache.invalidation.CacheInvalidationExecutor;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.RedisTtlCacheSpec;

/** 认证访问快照的 Redis-only TTL 缓存门面。 */
public final class AuthAccessSnapshotCache {

  private static final int SCHEMA_VERSION = 1;
  private static final CacheRegionName CACHE_NAME = new CacheRegionName("auth-access-snapshot");

  private final RedisTtlCacheRegion<Long, AuthAccessSnapshotCacheEntry> region;
  private final CacheInvalidationExecutor invalidationExecutor;

  public AuthAccessSnapshotCache(
      RedisCacheRegionFactory factory,
      RedisValueCodecFactory valueCodecs,
      CacheInvalidationExecutor invalidationExecutor,
      AuthCacheProperties properties
  ) {
    Objects.requireNonNull(factory, "factory must not be null");
    Objects.requireNonNull(valueCodecs, "valueCodecs must not be null");
    this.invalidationExecutor = Objects.requireNonNull(
        invalidationExecutor, "invalidationExecutor must not be null");
    Objects.requireNonNull(properties, "properties must not be null");
    this.region = factory.createTtl(
        new RedisTtlCacheSpec(
            CACHE_NAME,
            CACHE_NAME.value(),
            SCHEMA_VERSION,
            properties.getAccessSnapshotTtl()),
        userId -> Long.toString(requireUserId(userId)),
        valueCodecs.json(AuthAccessSnapshotCacheEntry.class));
  }

  public Optional<AuthAccessSnapshot> get(long userId, Supplier<Optional<AuthAccessSnapshot>> loader) {
    Objects.requireNonNull(loader, "loader must not be null");
    return region.get(requireUserId(userId), ignored -> AuthAccessSnapshotCacheEntry.from(
        Objects.requireNonNull(loader.get(), "loader result must not be null"))).toOptional();
  }

  public void invalidate(long userId) {
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
