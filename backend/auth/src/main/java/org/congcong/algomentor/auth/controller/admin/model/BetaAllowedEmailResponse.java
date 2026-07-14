package org.congcong.algomentor.auth.controller.admin.model;

import java.time.Instant;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmail;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public record BetaAllowedEmailResponse(
    long id,
    String email,
    boolean registered,
    Long associatedUserId,
    AuthUserStatus associatedUserStatus,
    long createdBy,
    String createdByDisplayName,
    Instant createdAt
) {

  public static BetaAllowedEmailResponse from(BetaAllowedEmail email) {
    return new BetaAllowedEmailResponse(
        email.id(),
        email.email(),
        email.registered(),
        email.registeredUserId(),
        email.registeredUserStatus(),
        email.createdBy(),
        email.createdByDisplayName(),
        email.createdAt());
  }
}
