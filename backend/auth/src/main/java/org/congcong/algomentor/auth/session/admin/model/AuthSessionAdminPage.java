package org.congcong.algomentor.auth.session.admin.model;

import java.time.Instant;
import java.util.List;

public record AuthSessionAdminPage(
    List<AuthSessionAdminRecord> items,
    long total,
    int page,
    int pageSize,
    AuthSessionAdminSummary summary,
    Instant checkedAt
) {

  public AuthSessionAdminPage {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
