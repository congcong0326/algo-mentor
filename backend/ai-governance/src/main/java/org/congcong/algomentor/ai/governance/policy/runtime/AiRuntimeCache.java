package org.congcong.algomentor.ai.governance.policy.runtime;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;

/** AI 全局设置和用户覆盖的强类型缓存门面。 */
public final class AiRuntimeCache {

  private static final String SINGLETON_KEY = "singleton";
  private static final int SCHEMA_VERSION = 1;
  private static final CacheRegionName SETTINGS_CACHE_NAME = new CacheRegionName("ai-runtime-settings");
  private static final CacheRegionName USER_POLICY_CACHE_NAME = new CacheRegionName("ai-user-policy");

  private final SharedTtlCacheRegion<String, Optional<AiRuntimeSettings>> settings;
  private final SharedTtlCacheRegion<Long, AiUserPolicy> userPolicies;
  private final SharedCacheInvalidationCoordinator invalidationCoordinator;

  public AiRuntimeCache(
      SharedCacheRegionFactory cacheFactory,
      SharedCacheInvalidationCoordinator invalidationCoordinator,
      AiRuntimeCacheProperties properties
  ) {
    Objects.requireNonNull(cacheFactory, "cacheFactory must not be null");
    this.invalidationCoordinator = Objects.requireNonNull(
        invalidationCoordinator, "invalidationCoordinator must not be null");
    Objects.requireNonNull(properties, "properties must not be null");
    this.settings = cacheFactory.createTtl(
        new SharedTtlCacheSpec(
            SETTINGS_CACHE_NAME,
            SETTINGS_CACHE_NAME.value(),
            SCHEMA_VERSION,
            1,
            properties.getSettingsTtl()),
        key -> requireSingleton(key));
    this.userPolicies = cacheFactory.createTtl(
        new SharedTtlCacheSpec(
            USER_POLICY_CACHE_NAME,
            USER_POLICY_CACHE_NAME.value(),
            SCHEMA_VERSION,
            properties.getUserPolicyMaximumSize(),
            properties.getUserPolicyTtl()),
        userId -> Long.toString(requireUserId(userId)));
  }

  public Optional<AiRuntimeSettings> getSettings(Supplier<Optional<AiRuntimeSettings>> loader) {
    Objects.requireNonNull(loader, "loader must not be null");
    return settings.get(SINGLETON_KEY, ignored -> requireLoaderResult(loader.get()));
  }

  public AiUserPolicy getUserPolicy(long userId, Supplier<AiUserPolicy> loader) {
    Objects.requireNonNull(loader, "loader must not be null");
    return userPolicies.get(requireUserId(userId), ignored -> Objects.requireNonNull(
        loader.get(), "loader result must not be null"));
  }

  public void invalidateSettings() {
    invalidationCoordinator.invalidate(settings, SINGLETON_KEY);
  }

  public void invalidateUserPolicy(long userId) {
    invalidationCoordinator.invalidate(userPolicies, requireUserId(userId));
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
