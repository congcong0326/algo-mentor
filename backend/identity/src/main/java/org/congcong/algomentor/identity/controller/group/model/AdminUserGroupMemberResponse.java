package org.congcong.algomentor.identity.controller.group.model;

import java.time.Instant;
import org.congcong.algomentor.identity.group.model.UserGroupMember;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public record AdminUserGroupMemberResponse(
    long userId,
    String email,
    String displayName,
    String avatarUrl,
    AuthUserStatus status,
    Instant joinedAt,
    Instant expiresAt
) {

  public static AdminUserGroupMemberResponse from(UserGroupMember member) {
    return new AdminUserGroupMemberResponse(
        member.userId(),
        member.email(),
        member.displayName(),
        member.avatarUrl(),
        member.status(),
        member.joinedAt(),
        member.expiresAt());
  }
}
