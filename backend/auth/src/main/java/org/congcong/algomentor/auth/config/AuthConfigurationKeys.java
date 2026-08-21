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
  /** 是否允许邮箱密码登录。关闭后同时禁止用户设置或修改密码。 */
  public static final String PASSWORD_LOGIN_ENABLED = AUTH_PREFIX + ".password-login-enabled";
  /** 是否允许通过邮箱密码自助注册。 */
  public static final String PASSWORD_REGISTRATION_ENABLED = AUTH_PREFIX + ".password-registration-enabled";
  /** 是否允许创建全新本地账号；关闭后已有账号仍可登录。 */
  public static final String ACCOUNT_REGISTRATION_ENABLED = AUTH_PREFIX + ".account-registration-enabled";
  /** Google OAuth2 客户端凭据的环境变量。 */
  public static final String GOOGLE_OAUTH2_CLIENT_ID_ENV = "GOOGLE_CLIENT_ID";
  public static final String GOOGLE_OAUTH2_CLIENT_SECRET_ENV = "GOOGLE_CLIENT_SECRET";
  /** GitHub OAuth2 客户端凭据的环境变量。 */
  public static final String GITHUB_OAUTH2_CLIENT_ID_ENV = "GITHUB_CLIENT_ID";
  public static final String GITHUB_OAUTH2_CLIENT_SECRET_ENV = "GITHUB_CLIENT_SECRET";

  private AuthConfigurationKeys() {
  }
}
