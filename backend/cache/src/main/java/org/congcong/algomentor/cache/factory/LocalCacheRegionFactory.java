package org.congcong.algomentor.cache.factory;

import org.congcong.algomentor.cache.api.LocalBoundedCacheRegion;
import org.congcong.algomentor.cache.api.LocalTtlCacheRegion;
import org.congcong.algomentor.cache.spec.LocalBoundedCacheSpec;
import org.congcong.algomentor.cache.spec.LocalTtlCacheSpec;

public interface LocalCacheRegionFactory {

  <K, V> LocalBoundedCacheRegion<K, V> createBounded(LocalBoundedCacheSpec spec);

  <K, V> LocalTtlCacheRegion<K, V> createTtl(LocalTtlCacheSpec spec);
}
