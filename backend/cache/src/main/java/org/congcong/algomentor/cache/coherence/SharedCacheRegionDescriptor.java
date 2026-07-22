package org.congcong.algomentor.cache.coherence;

import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;

/**
 * Shared region provider 的内部协调视图，业务代码只通过 {@code SharedTtlCacheRegion} 访问缓存。
 */
public interface SharedCacheRegionDescriptor<K, V> {

  SharedTtlCacheSpec specification();

  String encodeKey(K key);

  void invalidateKeyToken(String keyToken);

  void invalidateAllLocal();
}
