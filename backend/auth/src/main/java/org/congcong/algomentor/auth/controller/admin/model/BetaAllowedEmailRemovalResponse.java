package org.congcong.algomentor.auth.controller.admin.model;

import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmailRemovalResult;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public record BetaAllowedEmailRemovalResponse(
    long allowedEmailId,
    Long associatedUserId,
    AuthUserStatus associatedUserStatus,
    int revokedSessionCount,
    boolean sessionRevocationSucceeded
) {

  public static BetaAllowedEmailRemovalResponse from(BetaAllowedEmailRemovalResult result) {
    return new BetaAllowedEmailRemovalResponse(
        result.allowedEmailId(),
        result.associatedUserId(),
        result.associatedUserStatus(),
        result.revokedSessionCount(),
        result.sessionRevocationSucceeded());
  }
}
