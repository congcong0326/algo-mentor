package org.congcong.algomentor.identity.group.relation;

import java.time.Instant;
import java.util.Objects;

/** 缓存中的成员关系保留时间边界，以便自然到期不等待缓存 TTL。 */
public record CachedGroupMembership(long groupId, Instant joinedAt, Instant expiresAt) {

  public CachedGroupMembership {
    if (groupId < 1) {
      throw new IllegalArgumentException("groupId must be positive");
    }
    joinedAt = Objects.requireNonNull(joinedAt, "joinedAt must not be null");
  }

  public boolean activeAt(Instant now) {
    return !joinedAt.isAfter(now) && (expiresAt == null || expiresAt.isAfter(now));
  }
}
