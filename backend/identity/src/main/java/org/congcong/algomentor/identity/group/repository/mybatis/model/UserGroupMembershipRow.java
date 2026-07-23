package org.congcong.algomentor.identity.group.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.identity.group.model.UserGroupMembership;

public record UserGroupMembershipRow(
    long userId,
    long groupId,
    Instant joinedAt,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt
) {

  public UserGroupMembership toDomain() {
    return new UserGroupMembership(userId, groupId, joinedAt, expiresAt, createdAt, updatedAt);
  }
}
