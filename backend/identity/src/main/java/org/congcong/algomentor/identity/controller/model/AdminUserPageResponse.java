package org.congcong.algomentor.identity.controller.model;

import java.util.List;

public record AdminUserPageResponse(
    List<AdminUserSummaryResponse> items,
    long total,
    int page,
    int pageSize
) {

  public AdminUserPageResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
