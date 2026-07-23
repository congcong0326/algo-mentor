package org.congcong.algomentor.identity.group.model;

import java.util.List;

public record UserGroupSearchQuery(
    int page,
    int pageSize,
    String keyword,
    UserGroupStatus status
) {

  public UserGroupSearchQuery {
    page = Math.max(page, 1);
    pageSize = Math.min(Math.max(pageSize, 1), 100);
    keyword = keyword == null ? "" : keyword.trim();
  }

  public int offset() {
    return (page - 1) * pageSize;
  }

  public List<UserGroupStatus> effectiveStatuses() {
    return status == null
        ? List.of(UserGroupStatus.ACTIVE, UserGroupStatus.DISABLED)
        : List.of(status);
  }
}
