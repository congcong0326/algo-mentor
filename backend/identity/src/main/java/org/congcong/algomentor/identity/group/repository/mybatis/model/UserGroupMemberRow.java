package org.congcong.algomentor.identity.group.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.identity.group.model.UserGroupMember;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public record UserGroupMemberRow(
    long userId,
    String email,
    String displayName,
    String avatarUrl,
    String status,
    Instant joinedAt,
    Instant expiresAt
) {

  public UserGroupMember toDomain() {
    return new UserGroupMember(
        userId,
        email,
        displayName,
        avatarUrl,
        AuthUserStatus.valueOf(status),
        joinedAt,
        expiresAt);
  }
}
