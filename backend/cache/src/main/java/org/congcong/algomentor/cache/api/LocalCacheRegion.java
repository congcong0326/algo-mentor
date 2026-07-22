package org.congcong.algomentor.cache.api;

public interface LocalCacheRegion<K, V> extends CacheRegion<K, V> {

  void invalidateAll();
}
