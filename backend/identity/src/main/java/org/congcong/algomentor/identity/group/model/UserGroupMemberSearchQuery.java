package org.congcong.algomentor.identity.group.model;

public record UserGroupMemberSearchQuery(
    int page,
    int pageSize,
    String keyword
) {

  public UserGroupMemberSearchQuery {
    page = Math.max(page, 1);
    pageSize = Math.min(Math.max(pageSize, 1), 100);
    keyword = keyword == null ? "" : keyword.trim();
  }

  public int offset() {
    return (page - 1) * pageSize;
  }
}
