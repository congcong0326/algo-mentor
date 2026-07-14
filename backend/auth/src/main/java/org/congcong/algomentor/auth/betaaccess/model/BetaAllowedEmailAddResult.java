package org.congcong.algomentor.auth.betaaccess.model;

public record BetaAllowedEmailAddResult(
    String email,
    BetaAllowedEmailAddStatus status,
    Long allowedEmailId
) {
}
