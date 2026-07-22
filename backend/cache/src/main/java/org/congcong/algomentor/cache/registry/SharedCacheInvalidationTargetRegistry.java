package org.congcong.algomentor.cache.registry;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.congcong.algomentor.cache.coherence.CacheInvalidationEventType;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationEvent;
import org.congcong.algomentor.cache.coherence.SharedCacheRegionDescriptor;
import org.congcong.algomentor.cache.coherence.SharedInvalidationDispatchResult;
import org.congcong.algomentor.cache.spec.CacheRegionName;

/** 当前 JVM 的共享缓存失效目标注册表，不暴露业务 key 或 value。 */
public final class SharedCacheInvalidationTargetRegistry {

  public static final CacheRegionName UNKNOWN_CACHE_NAME = new CacheRegionName("unknown-cache");

  private final Map<CacheRegionName, SharedCacheRegionDescriptor<?, ?>> targets = new ConcurrentHashMap<>();

  public void register(SharedCacheRegionDescriptor<?, ?> target) {
    Objects.requireNonNull(target, "target must not be null");
    CacheRegionName name = target.specification().name();
    SharedCacheRegionDescriptor<?, ?> existing = targets.putIfAbsent(name, target);
    if (existing != null && existing != target
        && !existing.specification().equals(target.specification())) {
      throw new IllegalStateException("Shared cache invalidation target already registered: " + name.value());
    }
  }

  public SharedInvalidationDispatchResult dispatch(SharedCacheInvalidationEvent event) {
    Objects.requireNonNull(event, "event must not be null");
    SharedCacheRegionDescriptor<?, ?> target = targets.get(event.cacheName());
    if (target == null) {
      return SharedInvalidationDispatchResult.UNKNOWN_CACHE;
    }
    if (!target.specification().namespace().equals(event.namespace())
        || target.specification().schemaVersion() != event.schemaVersion()) {
      return SharedInvalidationDispatchResult.SCHEMA_MISMATCH;
    }
    if (event.eventType() == CacheInvalidationEventType.KEY_INVALIDATE) {
      target.invalidateKeyToken(event.keyToken());
    } else {
      target.invalidateAllLocal();
    }
    return SharedInvalidationDispatchResult.SUCCESS;
  }

  public void invalidateAll() {
    RuntimeException failure = null;
    for (SharedCacheRegionDescriptor<?, ?> target : targets.values()) {
      try {
        target.invalidateAllLocal();
      } catch (RuntimeException exception) {
        if (failure == null) {
          failure = exception;
        } else {
          failure.addSuppressed(exception);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }
}
