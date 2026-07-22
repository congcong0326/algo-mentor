package org.congcong.algomentor.cache.api;

import java.util.Optional;
import java.util.function.Function;

public interface CacheRegion<K, V> {

  Optional<V> getIfPresent(K key);

  V get(K key, Function<? super K, ? extends V> loader);

  void put(K key, V value);

  void invalidate(K key);
}
