package org.congcong.algomentor.auth.password;

public class UserPasswordException extends RuntimeException {

  private final UserPasswordErrorCode code;

  public UserPasswordException(UserPasswordErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public UserPasswordException(UserPasswordErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public UserPasswordErrorCode code() {
    return code;
  }
}
