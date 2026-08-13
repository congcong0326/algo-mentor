package org.congcong.algomentor.auth.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.congcong.algomentor.cache.api.RedisTtlCacheRegion;
import org.congcong.algomentor.cache.codec.RedisValueCodecFactory;
import org.congcong.algomentor.cache.factory.RedisCacheRegionFactory;
import org.congcong.algomentor.cache.invalidation.CacheInvalidationExecutor;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.RedisTtlCacheSpec;

/** 内测准入开关与规范化邮箱成员关系的 Redis-only TTL 缓存门面。 */
public final class BetaAccessCache {

  private static final String SINGLETON_KEY = "singleton";
  private static final int SCHEMA_VERSION = 1;
  private static final CacheRegionName SETTINGS_CACHE_NAME = new CacheRegionName("auth-beta-access-settings");
  private static final CacheRegionName EMAIL_MEMBERSHIP_CACHE_NAME = new CacheRegionName("auth-beta-email-membership");

  private final RedisTtlCacheRegion<String, BetaAccessSettingsCacheEntry> settings;
  private final RedisTtlCacheRegion<String, Boolean> emailMembership;
  private final CacheInvalidationExecutor invalidationExecutor;

  public BetaAccessCache(
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
    this.settings = factory.createTtl(
        new RedisTtlCacheSpec(
            SETTINGS_CACHE_NAME,
            SETTINGS_CACHE_NAME.value(),
            SCHEMA_VERSION,
            properties.getBetaAccessSettingsTtl()),
        BetaAccessCache::encodeSingleton,
        valueCodecs.json(BetaAccessSettingsCacheEntry.class));
    this.emailMembership = factory.createTtl(
        new RedisTtlCacheSpec(
            EMAIL_MEMBERSHIP_CACHE_NAME,
            EMAIL_MEMBERSHIP_CACHE_NAME.value(),
            SCHEMA_VERSION,
            properties.getBetaEmailMembershipTtl()),
        BetaAccessCache::sha256,
        valueCodecs.booleanAsZeroOrOne());
  }

  public Optional<Boolean> getAllowlistEnabled(Supplier<Optional<Boolean>> loader) {
    Objects.requireNonNull(loader, "loader must not be null");
    return settings.get(SINGLETON_KEY, ignored -> BetaAccessSettingsCacheEntry.from(
        Objects.requireNonNull(loader.get(), "loader result must not be null"))).toOptional();
  }

  public boolean isAllowedEmail(String normalizedEmail, Supplier<Boolean> loader) {
    String email = requireNormalizedEmail(normalizedEmail);
    Objects.requireNonNull(loader, "loader must not be null");
    return emailMembership.get(email, ignored -> Objects.requireNonNull(
        loader.get(), "loader result must not be null"));
  }

  public void invalidateSettings() {
    invalidationExecutor.afterCommit(() -> settings.invalidate(SINGLETON_KEY));
  }

  public void invalidateEmail(String normalizedEmail) {
    String email = requireNormalizedEmail(normalizedEmail);
    invalidationExecutor.afterCommit(() -> emailMembership.invalidate(email));
  }

  private static String encodeSingleton(String value) {
    if (!SINGLETON_KEY.equals(value)) {
      throw new IllegalArgumentException("Beta access settings cache key must be singleton");
    }
    return SINGLETON_KEY;
  }

  private static String requireNormalizedEmail(String normalizedEmail) {
    if (normalizedEmail == null || normalizedEmail.isBlank()) {
      throw new IllegalArgumentException("normalizedEmail must not be blank");
    }
    return normalizedEmail;
  }

  private static String sha256(String normalizedEmail) {
    String email = requireNormalizedEmail(normalizedEmail);
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(email.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 must be available", exception);
    }
  }
}
