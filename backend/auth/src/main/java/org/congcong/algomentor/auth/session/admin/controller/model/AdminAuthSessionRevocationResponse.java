package org.congcong.algomentor.auth.session.admin.controller.model;

import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminRevocation;

public record AdminAuthSessionRevocationResponse(
    String sessionRef,
    Long userId,
    boolean revoked,
    boolean alreadyOffline
) {

  public static AdminAuthSessionRevocationResponse from(AuthSessionAdminRevocation revocation) {
    return new AdminAuthSessionRevocationResponse(
        revocation.sessionRef(),
        revocation.userId(),
        revocation.revoked(),
        revocation.alreadyOffline());
  }
}
