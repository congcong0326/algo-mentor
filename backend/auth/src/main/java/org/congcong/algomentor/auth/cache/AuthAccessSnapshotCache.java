package org.congcong.algomentor.auth.cache;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;

/** 认证访问快照的 Shared TTL 缓存门面。 */
public final class AuthAccessSnapshotCache {

  private static final int SCHEMA_VERSION = 1;
  private static final CacheRegionName CACHE_NAME = new CacheRegionName("auth-access-snapshot");

  private final SharedTtlCacheRegion<Long, Optional<AuthAccessSnapshot>> region;
  private final SharedCacheInvalidationCoordinator invalidationCoordinator;

  public AuthAccessSnapshotCache(
      SharedCacheRegionFactory factory,
      SharedCacheInvalidationCoordinator invalidationCoordinator,
      AuthCacheProperties properties
  ) {
    Objects.requireNonNull(factory, "factory must not be null");
    this.invalidationCoordinator = Objects.requireNonNull(
        invalidationCoordinator, "invalidationCoordinator must not be null");
    Objects.requireNonNull(properties, "properties must not be null");
    this.region = factory.createTtl(
        new SharedTtlCacheSpec(
            CACHE_NAME,
            CACHE_NAME.value(),
            SCHEMA_VERSION,
            properties.getAccessSnapshotMaximumSize(),
            properties.getAccessSnapshotTtl()),
        userId -> Long.toString(requireUserId(userId)));
  }

  public Optional<AuthAccessSnapshot> get(long userId, Supplier<Optional<AuthAccessSnapshot>> loader) {
    Objects.requireNonNull(loader, "loader must not be null");
    return region.get(requireUserId(userId), ignored -> Objects.requireNonNull(
        loader.get(), "loader result must not be null"));
  }

  public void invalidate(long userId) {
    invalidationCoordinator.invalidate(region, requireUserId(userId));
  }

  private static long requireUserId(long userId) {
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return userId;
  }
}
