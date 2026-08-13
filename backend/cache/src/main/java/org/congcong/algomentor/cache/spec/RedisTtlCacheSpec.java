package org.congcong.algomentor.cache.spec;

import java.time.Duration;
import java.util.Objects;

/** Redis 单 key TTL 缓存的稳定物理 keyspace 定义。 */
public record RedisTtlCacheSpec(
    CacheRegionName name,
    String namespace,
    int schemaVersion,
    Duration ttl) {

  public RedisTtlCacheSpec {
    Objects.requireNonNull(name, "name must not be null");
    namespace = CacheRegionName.requireStableKebabCase(namespace, "namespace");
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("schemaVersion must be at least one");
    }
    ttl = LocalTtlCacheSpec.requirePositiveTtl(ttl);
    if (ttl.toMillis() < 1) {
      throw new IllegalArgumentException("ttl must be at least one millisecond");
    }
  }
}
