package org.congcong.algomentor.cache.coherence;

import java.time.Instant;
import java.util.Objects;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.SharedCacheKeyCodec;

/** PostgreSQL 失效事件的无业务 value 投影。 */
public record SharedCacheInvalidationEvent(
    long id,
    CacheRegionName cacheName,
    String namespace,
    int schemaVersion,
    String keyToken,
    CacheInvalidationEventType eventType,
    Instant createdAt) {

  public SharedCacheInvalidationEvent {
    if (id < 1) {
      throw new IllegalArgumentException("event id must be positive");
    }
    Objects.requireNonNull(cacheName, "cacheName must not be null");
    namespace = CacheRegionName.requireStableKebabCase(namespace, "namespace");
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("schemaVersion must be at least one");
    }
    Objects.requireNonNull(eventType, "eventType must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    if (eventType == CacheInvalidationEventType.KEY_INVALIDATE) {
      keyToken = SharedCacheKeyCodec.requireValidToken(keyToken);
    } else if (keyToken != null) {
      throw new IllegalArgumentException("region invalidation must not contain a key token");
    }
  }
}
