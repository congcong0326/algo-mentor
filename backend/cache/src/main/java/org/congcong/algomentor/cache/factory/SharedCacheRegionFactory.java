package org.congcong.algomentor.cache.factory;

import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.spec.SharedCacheKeyCodec;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;

public interface SharedCacheRegionFactory {

  <K, V> SharedTtlCacheRegion<K, V> createTtl(
      SharedTtlCacheSpec spec,
      SharedCacheKeyCodec<K> keyCodec);
}
