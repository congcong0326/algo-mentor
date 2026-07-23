package org.congcong.algomentor.auth.session.admin.controller.model;

import java.time.Instant;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionActivity;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminRecord;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public record AdminAuthSessionResponse(
    String sessionRef,
    long userId,
    String email,
    String displayName,
    AuthUserStatus userStatus,
    Instant createdAt,
    Instant lastAccessedAt,
    Instant expiresAt,
    AuthSessionActivity activity,
    boolean current
) {

  public static AdminAuthSessionResponse from(AuthSessionAdminRecord record) {
    return new AdminAuthSessionResponse(
        record.sessionRef(),
        record.userId(),
        record.email(),
        record.displayName(),
        record.userStatus(),
        record.createdAt(),
        record.lastAccessedAt(),
        record.expiresAt(),
        record.activity(),
        record.current());
  }
}
