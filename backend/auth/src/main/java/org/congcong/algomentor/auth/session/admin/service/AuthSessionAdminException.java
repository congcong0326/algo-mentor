package org.congcong.algomentor.auth.session.admin.service;

public class AuthSessionAdminException extends RuntimeException {

  private final AuthSessionAdminErrorCode code;

  public AuthSessionAdminException(AuthSessionAdminErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public AuthSessionAdminException(AuthSessionAdminErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public AuthSessionAdminErrorCode code() {
    return code;
  }
}
