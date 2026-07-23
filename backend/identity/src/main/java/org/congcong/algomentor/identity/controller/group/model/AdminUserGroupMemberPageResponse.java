package org.congcong.algomentor.identity.controller.group.model;

import java.util.List;
import org.congcong.algomentor.identity.group.model.UserGroupMemberPage;

public record AdminUserGroupMemberPageResponse(
    List<AdminUserGroupMemberResponse> items,
    long total,
    int page,
    int pageSize
) {

  public static AdminUserGroupMemberPageResponse from(UserGroupMemberPage page) {
    return new AdminUserGroupMemberPageResponse(
        page.items().stream().map(AdminUserGroupMemberResponse::from).toList(),
        page.total(),
        page.page(),
        page.pageSize());
  }
}
