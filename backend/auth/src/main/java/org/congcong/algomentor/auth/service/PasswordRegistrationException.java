package org.congcong.algomentor.auth.service;

public class PasswordRegistrationException extends RuntimeException {

  private final String code;

  public PasswordRegistrationException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
