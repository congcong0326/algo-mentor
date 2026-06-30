package org.congcong.algomentor.identity.model;

import java.util.List;

public record IdentityUserPage(
    List<AuthUser> items,
    long total,
    int page,
    int pageSize
) {

  public IdentityUserPage {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
