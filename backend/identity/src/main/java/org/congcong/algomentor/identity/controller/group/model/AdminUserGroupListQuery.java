package org.congcong.algomentor.identity.controller.group.model;

import org.congcong.algomentor.identity.group.model.UserGroupSearchQuery;
import org.congcong.algomentor.identity.group.model.UserGroupStatus;

public record AdminUserGroupListQuery(
    int page,
    int pageSize,
    String keyword,
    UserGroupStatus status
) {

  public UserGroupSearchQuery toSearchQuery() {
    return new UserGroupSearchQuery(page, pageSize, keyword, status);
  }
}
