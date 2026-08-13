package org.congcong.algomentor.ai.governance.policy.runtime;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.congcong.algomentor.cache.api.RedisTtlCacheRegion;
import org.congcong.algomentor.cache.codec.RedisValueCodecFactory;
import org.congcong.algomentor.cache.factory.RedisCacheRegionFactory;
import org.congcong.algomentor.cache.invalidation.CacheInvalidationExecutor;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.RedisTtlCacheSpec;

/** AI 全局设置和用户覆盖的 Redis-only TTL 缓存门面。 */
public final class AiRuntimeCache {

  private static final String SINGLETON_KEY = "singleton";
  private static final int SCHEMA_VERSION = 1;
  private static final CacheRegionName SETTINGS_CACHE_NAME = new CacheRegionName("ai-runtime-settings");
  private static final CacheRegionName USER_POLICY_CACHE_NAME = new CacheRegionName("ai-user-policy");

  private final RedisTtlCacheRegion<String, AiRuntimeSettingsCacheEntry> settings;
  private final RedisTtlCacheRegion<Long, AiUserPolicy> userPolicies;
  private final CacheInvalidationExecutor invalidationExecutor;

  public AiRuntimeCache(
      RedisCacheRegionFactory cacheFactory,
      RedisValueCodecFactory valueCodecs,
      CacheInvalidationExecutor invalidationExecutor,
      AiRuntimeCacheProperties properties
  ) {
    Objects.requireNonNull(cacheFactory, "cacheFactory must not be null");
    Objects.requireNonNull(valueCodecs, "valueCodecs must not be null");
    this.invalidationExecutor = Objects.requireNonNull(
        invalidationExecutor, "invalidationExecutor must not be null");
    Objects.requireNonNull(properties, "properties must not be null");
    this.settings = cacheFactory.createTtl(
        new RedisTtlCacheSpec(
            SETTINGS_CACHE_NAME,
            SETTINGS_CACHE_NAME.value(),
            SCHEMA_VERSION,
            properties.getSettingsTtl()),
        AiRuntimeCache::requireSingleton,
        valueCodecs.json(AiRuntimeSettingsCacheEntry.class));
    this.userPolicies = cacheFactory.createTtl(
        new RedisTtlCacheSpec(
            USER_POLICY_CACHE_NAME,
            USER_POLICY_CACHE_NAME.value(),
            SCHEMA_VERSION,
            properties.getUserPolicyTtl()),
        userId -> Long.toString(requireUserId(userId)),
        valueCodecs.json(AiUserPolicy.class));
  }

  public Optional<AiRuntimeSettings> getSettings(Supplier<Optional<AiRuntimeSettings>> loader) {
    Objects.requireNonNull(loader, "loader must not be null");
    return settings.get(SINGLETON_KEY, ignored -> AiRuntimeSettingsCacheEntry.from(
        requireLoaderResult(loader.get()))).toOptional();
  }

  public AiUserPolicy getUserPolicy(long userId, Supplier<AiUserPolicy> loader) {
    Objects.requireNonNull(loader, "loader must not be null");
    return userPolicies.get(requireUserId(userId), ignored -> Objects.requireNonNull(
        loader.get(), "loader result must not be null"));
  }

  public void invalidateSettings() {
    invalidationExecutor.afterCommit(() -> settings.invalidate(SINGLETON_KEY));
  }

  public void invalidateUserPolicy(long userId) {
    long resolvedUserId = requireUserId(userId);
    invalidationExecutor.afterCommit(() -> userPolicies.invalidate(resolvedUserId));
  }

  private static Optional<AiRuntimeSettings> requireLoaderResult(Optional<AiRuntimeSettings> result) {
    return Objects.requireNonNull(result, "loader result must not be null");
  }

  private static String requireSingleton(String key) {
    if (!SINGLETON_KEY.equals(key)) {
      throw new IllegalArgumentException("AI runtime settings cache key must be singleton");
    }
    return SINGLETON_KEY;
  }

  private static long requireUserId(long userId) {
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return userId;
  }
}
