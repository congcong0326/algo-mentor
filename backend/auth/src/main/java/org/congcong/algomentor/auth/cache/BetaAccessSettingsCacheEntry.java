package org.congcong.algomentor.auth.cache;

/** auth-beta-access-settings Redis 负缓存 envelope，不暴露至 API。 */
public record BetaAccessSettingsCacheEntry(boolean present, Boolean value) {

  public BetaAccessSettingsCacheEntry {
    if (present != (value != null)) {
      throw new IllegalArgumentException("present and value must agree");
    }
  }

  static BetaAccessSettingsCacheEntry from(java.util.Optional<Boolean> value) {
    return new BetaAccessSettingsCacheEntry(value.isPresent(), value.orElse(null));
  }

  java.util.Optional<Boolean> toOptional() {
    return present ? java.util.Optional.of(value) : java.util.Optional.empty();
  }
}
