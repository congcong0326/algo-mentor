package org.congcong.algomentor.identity.controller.group.model;

import java.time.Instant;
import org.congcong.algomentor.identity.group.model.UserGroup;
import org.congcong.algomentor.identity.group.model.UserGroupStatus;

public record AdminUserGroupResponse(
    long id,
    String code,
    String name,
    String description,
    UserGroupStatus status,
    long activeMemberCount,
    Instant createdAt,
    Instant updatedAt
) {

  public static AdminUserGroupResponse from(UserGroup group) {
    return new AdminUserGroupResponse(
        group.id(),
        group.code(),
        group.name(),
        group.description(),
        group.status(),
        group.activeMemberCount(),
        group.createdAt(),
        group.updatedAt());
  }
}
