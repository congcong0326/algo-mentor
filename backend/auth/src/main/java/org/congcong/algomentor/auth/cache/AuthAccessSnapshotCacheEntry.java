package org.congcong.algomentor.auth.cache;

/** auth-access-snapshot Redis 负缓存 envelope，不暴露至 API。 */
public record AuthAccessSnapshotCacheEntry(boolean present, AuthAccessSnapshot value) {

  public AuthAccessSnapshotCacheEntry {
    if (present != (value != null)) {
      throw new IllegalArgumentException("present and value must agree");
    }
  }

  static AuthAccessSnapshotCacheEntry from(java.util.Optional<AuthAccessSnapshot> value) {
    return new AuthAccessSnapshotCacheEntry(value.isPresent(), value.orElse(null));
  }

  java.util.Optional<AuthAccessSnapshot> toOptional() {
    return present ? java.util.Optional.of(value) : java.util.Optional.empty();
  }
}
