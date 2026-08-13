package org.congcong.algomentor.cache.factory;

import org.congcong.algomentor.cache.api.RedisTtlCacheRegion;
import org.congcong.algomentor.cache.codec.RedisValueCodec;
import org.congcong.algomentor.cache.spec.RedisTtlCacheSpec;
import org.congcong.algomentor.cache.spec.SharedCacheKeyCodec;

/** 创建远程 Redis TTL 缓存区域的唯一业务依赖入口。 */
public interface RedisCacheRegionFactory {

  <K, V> RedisTtlCacheRegion<K, V> createTtl(
      RedisTtlCacheSpec specification,
      SharedCacheKeyCodec<K> keyCodec,
      RedisValueCodec<V> valueCodec);
}
