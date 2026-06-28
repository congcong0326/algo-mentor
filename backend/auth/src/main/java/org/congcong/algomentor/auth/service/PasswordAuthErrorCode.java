package org.congcong.algomentor.auth.service;

/**
 * 邮箱密码认证接口使用的稳定错误码。
 */
public final class PasswordAuthErrorCode {

  public static final String AUTH_INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS";
  public static final String AUTH_EMAIL_ALREADY_REGISTERED = "AUTH_EMAIL_ALREADY_REGISTERED";
  public static final String AUTH_REQUEST_INVALID = "AUTH_REQUEST_INVALID";
  public static final String AUTH_DISPLAY_NAME_REQUIRED = "AUTH_DISPLAY_NAME_REQUIRED";

  private PasswordAuthErrorCode() {
  }
}
