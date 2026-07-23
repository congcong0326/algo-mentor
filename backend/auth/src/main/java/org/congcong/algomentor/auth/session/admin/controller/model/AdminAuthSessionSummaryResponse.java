package org.congcong.algomentor.auth.session.admin.controller.model;

import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminSummary;

public record AdminAuthSessionSummaryResponse(
    long validSessionCount,
    long activeSessionCount,
    long validUserCount
) {

  public static AdminAuthSessionSummaryResponse from(AuthSessionAdminSummary summary) {
    return new AdminAuthSessionSummaryResponse(
        summary.validSessionCount(),
        summary.activeSessionCount(),
        summary.validUserCount());
  }
}
