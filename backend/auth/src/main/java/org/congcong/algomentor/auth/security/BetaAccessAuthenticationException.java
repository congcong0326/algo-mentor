package org.congcong.algomentor.auth.security;

import org.congcong.algomentor.auth.betaaccess.service.BetaAccessErrorCode;
import org.springframework.security.core.AuthenticationException;

public class BetaAccessAuthenticationException extends AuthenticationException {

  private final BetaAccessErrorCode code;

  public BetaAccessAuthenticationException(BetaAccessErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public BetaAccessErrorCode code() {
    return code;
  }
}
