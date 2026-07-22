package org.congcong.algomentor.cache.spec;

import java.util.Objects;

public record LocalBoundedCacheSpec(CacheRegionName name, long maximumSize) {

  public LocalBoundedCacheSpec {
    Objects.requireNonNull(name, "name must not be null");
    if (maximumSize <= 0) {
      throw new IllegalArgumentException("maximumSize must be greater than zero");
    }
  }
}
