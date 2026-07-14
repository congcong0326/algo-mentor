package org.congcong.algomentor.auth.betaaccess.model;

import org.congcong.algomentor.identity.model.AuthUserStatus;

public record BetaAllowedEmailRemovalResult(
    long allowedEmailId,
    Long associatedUserId,
    AuthUserStatus associatedUserStatus,
    int revokedSessionCount,
    boolean sessionRevocationSucceeded
) {
}
