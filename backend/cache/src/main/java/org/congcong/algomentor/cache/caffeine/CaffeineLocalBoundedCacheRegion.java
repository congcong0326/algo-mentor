package org.congcong.algomentor.cache.caffeine;

import java.util.Optional;
import java.util.function.Function;
import org.congcong.algomentor.cache.api.LocalBoundedCacheRegion;

final class CaffeineLocalBoundedCacheRegion<K, V> implements LocalBoundedCacheRegion<K, V> {

  private final CaffeineCacheRegion<K, V> delegate;

  CaffeineLocalBoundedCacheRegion(CaffeineCacheRegion<K, V> delegate) {
    this.delegate = delegate;
  }

  @Override
  public Optional<V> getIfPresent(K key) {
    return delegate.getIfPresent(key);
  }

  @Override
  public V get(K key, Function<? super K, ? extends V> loader) {
    return delegate.get(key, loader);
  }

  @Override
  public void put(K key, V value) {
    delegate.put(key, value);
  }

  @Override
  public void invalidate(K key) {
    delegate.invalidate(key);
  }

  @Override
  public void invalidateAll() {
    delegate.invalidateAll();
  }
}
