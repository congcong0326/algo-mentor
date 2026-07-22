package org.congcong.algomentor.cache.spec;

import java.time.Duration;
import java.util.Objects;

public record SharedTtlCacheSpec(
    CacheRegionName name,
    String namespace,
    int schemaVersion,
    long maximumSize,
    Duration ttl) {

  public SharedTtlCacheSpec {
    Objects.requireNonNull(name, "name must not be null");
    namespace = CacheRegionName.requireStableKebabCase(namespace, "namespace");
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("schemaVersion must be at least one");
    }
    if (maximumSize <= 0) {
      throw new IllegalArgumentException("maximumSize must be greater than zero");
    }
    ttl = LocalTtlCacheSpec.requirePositiveTtl(ttl);
  }
}
