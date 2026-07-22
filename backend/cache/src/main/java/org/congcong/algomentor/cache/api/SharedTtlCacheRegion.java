package org.congcong.algomentor.cache.api;

/**
 * 共享 TTL 缓存的迁移边界。第一版 Caffeine 实现只在单 JVM 内生效。
 */
public interface SharedTtlCacheRegion<K, V> extends CacheRegion<K, V> {
}
