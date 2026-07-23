package org.congcong.algomentor.identity.controller.group.model;

import org.congcong.algomentor.identity.group.model.UserGroupMemberSearchQuery;

public record AdminUserGroupMemberListQuery(
    int page,
    int pageSize,
    String keyword
) {

  public UserGroupMemberSearchQuery toSearchQuery() {
    return new UserGroupMemberSearchQuery(page, pageSize, keyword);
  }
}
