package org.congcong.algomentor.identity.group.relation;

/** 未配置共享缓存时保持身份写路径可运行的降级实现。 */
public final class NoopUserRelationCacheInvalidator implements UserRelationCacheInvalidator {

  @Override
  public void invalidate(long userId, String reason) {
    // No cache region is present in this runtime.
  }
}
