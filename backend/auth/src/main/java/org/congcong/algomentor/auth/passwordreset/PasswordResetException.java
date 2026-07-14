package org.congcong.algomentor.auth.passwordreset;

public class PasswordResetException extends RuntimeException {

  private final PasswordResetErrorCode code;

  public PasswordResetException(PasswordResetErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public PasswordResetException(PasswordResetErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public PasswordResetErrorCode code() {
    return code;
  }
}
