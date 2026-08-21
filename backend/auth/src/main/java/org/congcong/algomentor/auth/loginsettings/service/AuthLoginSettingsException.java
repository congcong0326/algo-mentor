package org.congcong.algomentor.auth.loginsettings.service;

public class AuthLoginSettingsException extends RuntimeException {

  private final AuthLoginSettingsErrorCode code;

  public AuthLoginSettingsException(AuthLoginSettingsErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public AuthLoginSettingsErrorCode code() {
    return code;
  }
}
