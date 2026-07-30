package org.congcong.algomentor.auth.config;

/**
 * 认证模块配置 key。
 */
public final class AuthConfigurationKeys {

  public static final String AUTH_PREFIX = "algo-mentor.auth";
  public static final String LOGIN_SUCCESS_URL = AUTH_PREFIX + ".login-success-url";
  public static final String LOGOUT_SUCCESS_URL = AUTH_PREFIX + ".logout-success-url";
  public static final String SESSION_TIMEOUT = AUTH_PREFIX + ".session-timeout";
  public static final String SESSION_MONITORING_ACTIVE_WINDOW = AUTH_PREFIX + ".session-monitoring-active-window";
  public static final String COOKIE_SECURE = AUTH_PREFIX + ".cookie-secure";
  public static final String COOKIE_SAME_SITE = AUTH_PREFIX + ".cookie-same-site";
  public static final String ADMIN_EMAILS = AUTH_PREFIX + ".admin-emails";
  /** Google OAuth2 客户端凭据的环境变量。 */
  public static final String GOOGLE_OAUTH2_CLIENT_ID_ENV = "GOOGLE_CLIENT_ID";
  public static final String GOOGLE_OAUTH2_CLIENT_SECRET_ENV = "GOOGLE_CLIENT_SECRET";

  private AuthConfigurationKeys() {
  }
}
