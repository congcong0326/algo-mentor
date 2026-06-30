package org.congcong.algomentor.identity.controller.model;

import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.model.IdentityUserSearchQuery;

public record AdminUserListQuery(
    int page,
    int pageSize,
    String keyword,
    AuthUserStatus status
) {

  public IdentityUserSearchQuery toSearchQuery() {
    return new IdentityUserSearchQuery(page, pageSize, keyword, status);
  }
}
