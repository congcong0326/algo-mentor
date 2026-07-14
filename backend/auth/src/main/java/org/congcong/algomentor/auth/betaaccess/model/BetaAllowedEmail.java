package org.congcong.algomentor.auth.betaaccess.model;

import java.time.Instant;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public record BetaAllowedEmail(
    long id,
    String email,
    String emailNormalized,
    long createdBy,
    String createdByDisplayName,
    Instant createdAt,
    Long registeredUserId,
    AuthUserStatus registeredUserStatus
) {

  public boolean registered() {
    return registeredUserId != null;
  }
}
