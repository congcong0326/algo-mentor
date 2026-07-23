package org.congcong.algomentor.identity.group.relation;

import java.util.List;

/** 身份关系缓存内部值，不在模块边界外暴露。 */
public record CachedUserRelations(long userId, List<CachedGroupMembership> memberships) {

  public CachedUserRelations {
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    memberships = List.copyOf(memberships);
  }
}
