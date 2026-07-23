package org.congcong.algomentor.identity.controller.group.model;

import org.congcong.algomentor.identity.group.model.UserGroupStatus;

public record AdminUserGroupUpdateRequest(
    String name,
    String description,
    UserGroupStatus status
) {
}
