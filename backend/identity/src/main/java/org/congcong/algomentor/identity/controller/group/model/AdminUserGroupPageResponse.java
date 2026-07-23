package org.congcong.algomentor.identity.controller.group.model;

import java.util.List;
import org.congcong.algomentor.identity.group.model.UserGroupPage;

public record AdminUserGroupPageResponse(
    List<AdminUserGroupResponse> items,
    long total,
    int page,
    int pageSize
) {

  public static AdminUserGroupPageResponse from(UserGroupPage page) {
    return new AdminUserGroupPageResponse(
        page.items().stream().map(AdminUserGroupResponse::from).toList(),
        page.total(),
        page.page(),
        page.pageSize());
  }
}
