package org.congcong.algomentor.auth.passwordreset;

import java.time.Instant;

public record PasswordResetResult(
    String temporaryPassword,
    Instant expiresAt,
    int revokedSessionCount
) {
}
