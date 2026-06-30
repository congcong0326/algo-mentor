package org.congcong.algomentor.identity.service;

public class IdentityUserManagementException extends RuntimeException {

  private final IdentityUserErrorCode code;

  public IdentityUserManagementException(IdentityUserErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public IdentityUserErrorCode code() {
    return code;
  }
}
