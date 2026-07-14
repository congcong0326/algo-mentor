package org.congcong.algomentor.auth.security;

import org.congcong.algomentor.auth.passwordreset.PasswordResetErrorCode;
import org.springframework.security.core.AuthenticationException;

public class TemporaryPasswordAuthenticationException extends AuthenticationException {

  private final PasswordResetErrorCode code;

  public TemporaryPasswordAuthenticationException(PasswordResetErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public PasswordResetErrorCode code() {
    return code;
  }
}
