package org.congcong.algomentor.cache.api;

/** 仅以 Redis 保存 value 的 TTL 缓存区域，不隐含节点本地一级缓存。 */
public interface RedisTtlCacheRegion<K, V> extends CacheRegion<K, V> {
}
