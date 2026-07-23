package org.congcong.algomentor.identity.group.model;

import java.util.List;

public record UserGroupPage(
    List<UserGroup> items,
    long total,
    int page,
    int pageSize
) {

  public UserGroupPage {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
