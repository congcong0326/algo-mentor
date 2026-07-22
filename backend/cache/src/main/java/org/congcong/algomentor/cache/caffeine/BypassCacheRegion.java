package org.congcong.algomentor.cache.caffeine;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.congcong.algomentor.cache.api.CacheRegion;

class BypassCacheRegion<K, V> implements CacheRegion<K, V> {

  @Override
  public Optional<V> getIfPresent(K key) {
    Objects.requireNonNull(key, "key must not be null");
    return Optional.empty();
  }

  @Override
  public V get(K key, Function<? super K, ? extends V> loader) {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(loader, "loader must not be null");
    return Objects.requireNonNull(loader.apply(key), "loader result must not be null");
  }

  @Override
  public void put(K key, V value) {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public void invalidate(K key) {
    Objects.requireNonNull(key, "key must not be null");
  }
}
