package org.congcong.algomentor.auth.security;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 已认证用户响应头契约。
 */
public final class AuthenticatedUserResponseHeaders {

  /**
   * 当前登录用户的邮箱账号。
   */
  public static final String USER = "X-Authenticated-User";

  /**
   * 当前登录用户的稳定用户 ID。
   */
  public static final String USER_ID = "X-Authenticated-User-Id";

  private AuthenticatedUserResponseHeaders() {
  }

  public static void write(HttpServletResponse response, AuthenticatedUserPrincipal principal) {
    if (principal.email() != null && !principal.email().isBlank()) {
      response.setHeader(USER, principal.email());
    }
    response.setHeader(USER_ID, principal.userId().toString());
  }
}
