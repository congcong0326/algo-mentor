package org.congcong.algomentor.auth.config;

import org.springframework.http.HttpMethod;

/**
 * 认证过滤链使用的固定路径契约。
 */
public final class AuthSecurityPaths {

  public static final String API_PATTERN = "/api/**";
  public static final String HEALTH_PATH = "/api/health";
  public static final String OAUTH2_AUTHORIZATION_PATTERN = "/oauth2/authorization/**";
  public static final String OAUTH2_CALLBACK_PATTERN = "/login/oauth2/code/**";
  public static final String OAUTH2_SESSION_POLICY_FAILURE_URL = "/login?auth=session-policy-unavailable";
  public static final String AUTH_REGISTER_PATH = "/api/auth/register";
  public static final String AUTH_LOGIN_PATH = "/api/auth/login";
  public static final String AUTH_CAPABILITIES_PATH = "/api/auth/capabilities";
  public static final String AUTH_ME_PATH = "/api/auth/me";
  public static final String AUTH_COMPLETE_RESET_PATH = "/api/auth/password/complete-reset";
  public static final String AUTH_LOGOUT_PATH = "/api/auth/logout";
  public static final String ADMIN_API_PATTERN = "/api/admin/**";
  /** Spring Session JDBC 使用的浏览器会话 Cookie 名称。 */
  public static final String SESSION_COOKIE_NAME = "SESSION";
  public static final String[] ACTUATOR_HEALTH_PATTERNS = {
      "/actuator/health",
      "/actuator/health/**"
  };
  public static final HttpMethod LOGOUT_METHOD = HttpMethod.POST;

  private AuthSecurityPaths() {
  }
}
