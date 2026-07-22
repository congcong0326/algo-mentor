package org.congcong.algomentor.cache.spec;

import java.time.Duration;
import java.util.Objects;

public record LocalTtlCacheSpec(CacheRegionName name, long maximumSize, Duration ttl) {

  public LocalTtlCacheSpec {
    Objects.requireNonNull(name, "name must not be null");
    if (maximumSize <= 0) {
      throw new IllegalArgumentException("maximumSize must be greater than zero");
    }
    ttl = requirePositiveTtl(ttl);
  }

  static Duration requirePositiveTtl(Duration ttl) {
    Objects.requireNonNull(ttl, "ttl must not be null");
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
    return ttl;
  }
}
