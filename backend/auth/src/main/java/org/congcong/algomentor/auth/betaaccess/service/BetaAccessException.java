package org.congcong.algomentor.auth.betaaccess.service;

public class BetaAccessException extends RuntimeException {

  private final BetaAccessErrorCode code;

  public BetaAccessException(BetaAccessErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public BetaAccessErrorCode code() {
    return code;
  }
}
