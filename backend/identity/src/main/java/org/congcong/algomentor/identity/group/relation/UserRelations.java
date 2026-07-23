package org.congcong.algomentor.identity.group.relation;

import java.util.Set;

/** 策略等消费方可见的当前有效用户关系快照。 */
public record UserRelations(long userId, Set<Long> activeGroupIds) {

  public UserRelations {
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    activeGroupIds = Set.copyOf(activeGroupIds);
  }
}
