package org.congcong.algomentor.cache.coherence;

import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;

/** 将共享失效事件、业务事务和当前节点提交后失效绑定在一起。 */
public interface SharedCacheInvalidationCoordinator {

  <K, V> void invalidate(SharedTtlCacheRegion<K, V> region, K key);
}
