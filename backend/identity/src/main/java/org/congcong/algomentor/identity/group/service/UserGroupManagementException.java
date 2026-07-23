package org.congcong.algomentor.identity.group.service;

public class UserGroupManagementException extends RuntimeException {

  private final UserGroupErrorCode code;

  public UserGroupManagementException(UserGroupErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public UserGroupManagementException(UserGroupErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public UserGroupErrorCode code() {
    return code;
  }
}
