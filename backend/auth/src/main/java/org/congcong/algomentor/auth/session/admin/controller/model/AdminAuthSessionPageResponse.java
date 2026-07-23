package org.congcong.algomentor.auth.session.admin.controller.model;

import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminPage;

public record AdminAuthSessionPageResponse(
    List<AdminAuthSessionResponse> items,
    long total,
    int page,
    int pageSize,
    AdminAuthSessionSummaryResponse summary,
    Instant checkedAt
) {

  public AdminAuthSessionPageResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }

  public static AdminAuthSessionPageResponse from(AuthSessionAdminPage page) {
    return new AdminAuthSessionPageResponse(
        page.items().stream().map(AdminAuthSessionResponse::from).toList(),
        page.total(),
        page.page(),
        page.pageSize(),
        AdminAuthSessionSummaryResponse.from(page.summary()),
        page.checkedAt());
  }
}
