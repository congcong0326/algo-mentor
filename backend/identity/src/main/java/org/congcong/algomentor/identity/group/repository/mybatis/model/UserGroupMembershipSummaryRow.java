package org.congcong.algomentor.identity.group.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.identity.group.model.UserGroupMembershipSummary;

public record UserGroupMembershipSummaryRow(
    long userId,
    long groupId,
    String code,
    String name,
    Instant joinedAt,
    Instant expiresAt
) {

  public UserGroupMembershipSummary toDomain() {
    return new UserGroupMembershipSummary(userId, groupId, code, name, joinedAt, expiresAt);
  }
}
