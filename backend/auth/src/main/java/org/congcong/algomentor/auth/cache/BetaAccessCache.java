package org.congcong.algomentor.auth.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;

/** 内测准入开关与规范化邮箱成员关系的 Shared TTL 缓存门面。 */
public final class BetaAccessCache {

  private static final String SINGLETON_KEY = "singleton";
  private static final int SCHEMA_VERSION = 1;
  private static final CacheRegionName SETTINGS_CACHE_NAME = new CacheRegionName("auth-beta-access-settings");
  private static final CacheRegionName EMAIL_MEMBERSHIP_CACHE_NAME = new CacheRegionName("auth-beta-email-membership");

  private final SharedTtlCacheRegion<String, Optional<Boolean>> settings;
  private final SharedTtlCacheRegion<String, Boolean> emailMembership;
  private final SharedCacheInvalidationCoordinator invalidationCoordinator;

  public BetaAccessCache(
      SharedCacheRegionFactory factory,
      SharedCacheInvalidationCoordinator invalidationCoordinator,
      AuthCacheProperties properties
  ) {
    Objects.requireNonNull(factory, "factory must not be null");
    this.invalidationCoordinator = Objects.requireNonNull(
        invalidationCoordinator, "invalidationCoordinator must not be null");
    Objects.requireNonNull(properties, "properties must not be null");
    this.settings = factory.createTtl(
        new SharedTtlCacheSpec(
            SETTINGS_CACHE_NAME,
            SETTINGS_CACHE_NAME.value(),
            SCHEMA_VERSION,
            1,
            properties.getBetaAccessSettingsTtl()),
        BetaAccessCache::encodeSingleton);
    this.emailMembership = factory.createTtl(
        new SharedTtlCacheSpec(
            EMAIL_MEMBERSHIP_CACHE_NAME,
            EMAIL_MEMBERSHIP_CACHE_NAME.value(),
            SCHEMA_VERSION,
            properties.getBetaEmailMembershipMaximumSize(),
            properties.getBetaEmailMembershipTtl()),
        BetaAccessCache::sha256);
  }

  public Optional<Boolean> getAllowlistEnabled(Supplier<Optional<Boolean>> loader) {
    Objects.requireNonNull(loader, "loader must not be null");
    return settings.get(SINGLETON_KEY, ignored -> Objects.requireNonNull(
        loader.get(), "loader result must not be null"));
  }

  public boolean isAllowedEmail(String normalizedEmail, Supplier<Boolean> loader) {
    String email = requireNormalizedEmail(normalizedEmail);
    Objects.requireNonNull(loader, "loader must not be null");
    return emailMembership.get(email, ignored -> Objects.requireNonNull(
        loader.get(), "loader result must not be null"));
  }

  public void invalidateSettings() {
    invalidationCoordinator.invalidate(settings, SINGLETON_KEY);
  }

  public void invalidateEmail(String normalizedEmail) {
    invalidationCoordinator.invalidate(emailMembership, requireNormalizedEmail(normalizedEmail));
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
