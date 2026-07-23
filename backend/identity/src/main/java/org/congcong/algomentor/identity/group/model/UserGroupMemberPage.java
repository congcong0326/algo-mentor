package org.congcong.algomentor.identity.group.model;

import java.util.List;

public record UserGroupMemberPage(
    List<UserGroupMember> items,
    long total,
    int page,
    int pageSize
) {

  public UserGroupMemberPage {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
