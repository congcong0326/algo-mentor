package org.congcong.algomentor.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.congcong.algomentor.auth.config.AuthSecurityPaths;
import org.springframework.security.core.Authentication;

/**
 * 认证排查日志的安全摘要工具，避免输出 token、完整 session id 或用户隐私。
 */
public final class AuthDiagnosticSupport {

  private AuthDiagnosticSupport() {
  }

  public static String requestSummary(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    return "method=%s uri=%s sessionExists=%s requestedSessionIdPresent=%s requestedSessionIdValid=%s hasSessionCookie=%s"
        .formatted(
            request.getMethod(),
            request.getRequestURI(),
            session != null,
            request.getRequestedSessionId() != null,
            request.isRequestedSessionIdValid(),
            hasSessionCookie(request));
  }

  public static String authenticationSummary(Authentication authentication) {
    if (authentication == null) {
      return "authentication=null";
    }
    Object principal = authentication.getPrincipal();
    return "authenticationType=%s authenticated=%s principalType=%s authorities=%d"
        .formatted(
            authentication.getClass().getName(),
            authentication.isAuthenticated(),
            principal == null ? "null" : principal.getClass().getName(),
            authentication.getAuthorities() == null ? 0 : authentication.getAuthorities().size());
  }

  private static boolean hasSessionCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return false;
    }
    for (Cookie cookie : cookies) {
      if (AuthSecurityPaths.SESSION_COOKIE_NAME.equals(cookie.getName())) {
        return true;
      }
    }
    return false;
  }
}
