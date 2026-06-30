package org.congcong.algomentor.identity.model;

import java.util.List;

public record IdentityUserSearchQuery(
    int page,
    int pageSize,
    String keyword,
    AuthUserStatus status
) {

  public IdentityUserSearchQuery {
    page = Math.max(page, 1);
    pageSize = Math.min(Math.max(pageSize, 1), 100);
    keyword = keyword == null ? "" : keyword.trim();
  }

  public int offset() {
    return (page - 1) * pageSize;
  }

  public List<AuthUserStatus> effectiveStatuses() {
    return status == null
        ? List.of(AuthUserStatus.ACTIVE, AuthUserStatus.DISABLED)
        : List.of(status);
  }
}
