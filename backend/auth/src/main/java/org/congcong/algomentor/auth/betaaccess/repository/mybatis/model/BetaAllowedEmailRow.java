package org.congcong.algomentor.auth.betaaccess.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmail;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public record BetaAllowedEmailRow(
    long id,
    String email,
    String emailNormalized,
    long createdBy,
    String createdByDisplayName,
    Instant createdAt,
    Long registeredUserId,
    String registeredUserStatus
) {

  public BetaAllowedEmail toDomain() {
    return new BetaAllowedEmail(
        id,
        email,
        emailNormalized,
        createdBy,
        createdByDisplayName,
        createdAt,
        registeredUserId,
        registeredUserStatus == null ? null : AuthUserStatus.valueOf(registeredUserStatus));
  }
}
