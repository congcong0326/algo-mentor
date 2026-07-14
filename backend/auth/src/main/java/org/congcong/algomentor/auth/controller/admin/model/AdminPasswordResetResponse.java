package org.congcong.algomentor.auth.controller.admin.model;

import java.time.Instant;
import org.congcong.algomentor.auth.passwordreset.PasswordResetResult;

public record AdminPasswordResetResponse(String temporaryPassword, Instant expiresAt) {

  public static AdminPasswordResetResponse from(PasswordResetResult result) {
    return new AdminPasswordResetResponse(result.temporaryPassword(), result.expiresAt());
  }
}
