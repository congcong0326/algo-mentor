package org.congcong.algomentor.auth.config;

/**
 * 认证模块配置 key。
 */
public final class AuthConfigurationKeys {

  public static final String AUTH_PREFIX = "algo-mentor.auth";
  public static final String LOGIN_SUCCESS_URL = AUTH_PREFIX + ".login-success-url";
  public static final String LOGOUT_SUCCESS_URL = AUTH_PREFIX + ".logout-success-url";
  public static final String SESSION_TIMEOUT = AUTH_PREFIX + ".session-timeout";
  public static final String COOKIE_SECURE = AUTH_PREFIX + ".cookie-secure";
  public static final String COOKIE_SAME_SITE = AUTH_PREFIX + ".cookie-same-site";
  public static final String ADMIN_EMAILS = AUTH_PREFIX + ".admin-emails";

  private AuthConfigurationKeys() {
  }
}
