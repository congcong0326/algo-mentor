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
  public static final String AUTH_REGISTER_PATH = "/api/auth/register";
  public static final String AUTH_LOGIN_PATH = "/api/auth/login";
  public static final String AUTH_LOGOUT_PATH = "/api/auth/logout";
  public static final String ADMIN_API_PATTERN = "/api/admin/**";
  public static final String AGENT_CONVERSATIONS_API_PATTERN = "/api/agent/conversations/**";
  public static final String SESSION_COOKIE_NAME = "JSESSIONID";
  public static final String[] ACTUATOR_HEALTH_PATTERNS = {
      "/actuator/health",
      "/actuator/health/**"
  };
  public static final HttpMethod LOGOUT_METHOD = HttpMethod.POST;

  private AuthSecurityPaths() {
  }
}
