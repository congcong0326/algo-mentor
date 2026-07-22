package org.congcong.algomentor.cache.invalidation;

public interface CacheInvalidationExecutor {

  void afterCommit(Runnable invalidation);
}
