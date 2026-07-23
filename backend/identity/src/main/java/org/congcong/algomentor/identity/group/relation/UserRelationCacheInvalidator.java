package org.congcong.algomentor.identity.group.relation;

/** 用户组写路径依赖的精确失效边界。 */
public interface UserRelationCacheInvalidator {

  void invalidate(long userId, String reason);
}
