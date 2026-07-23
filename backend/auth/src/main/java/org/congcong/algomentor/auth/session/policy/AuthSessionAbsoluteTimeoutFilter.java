package org.congcong.algomentor.auth.session.policy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/** 对已写入会话快照的认证请求执行不可延长的绝对到期控制。 */
public class AuthSessionAbsoluteTimeoutFilter extends OncePerRequestFilter {

  private final AuthenticationEntryPoint authenticationEntryPoint;
  private final AuthSessionPolicyMetrics metrics;
  private final Clock clock;
  private final Duration globalIdleTimeout;

  public AuthSessionAbsoluteTimeoutFilter(
      AuthenticationEntryPoint authenticationEntryPoint,
      AuthSessionPolicyMetrics metrics,
      Clock clock,
      AuthProperties properties
  ) {
    this.authenticationEntryPoint = authenticationEntryPoint;
    this.metrics = metrics;
    this.clock = clock;
    this.globalIdleTimeout = properties.getSessionTimeout();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    HttpSession session = request.getSession(false);
    if (!isAuthenticated(authentication) || session == null) {
      filterChain.doFilter(request, response);
      return;
    }

    Long absoluteExpiresAtEpochMillis = absoluteExpiresAtEpochMillis(session);
    if (absoluteExpiresAtEpochMillis == null) {
      filterChain.doFilter(request, response);
      return;
    }

    Instant now = Instant.now(clock);
    Instant absoluteExpiresAt;
    try {
      absoluteExpiresAt = Instant.ofEpochMilli(absoluteExpiresAtEpochMillis);
    } catch (RuntimeException exception) {
      expireSession(request, response, session);
      return;
    }
    if (!now.isBefore(absoluteExpiresAt)) {
      expireSession(request, response, session);
      return;
    }

    try {
      Duration remaining = Duration.between(now, absoluteExpiresAt);
      Duration effectiveTimeout = globalIdleTimeout.compareTo(remaining) <= 0
          ? globalIdleTimeout
          : remaining;
      session.setMaxInactiveInterval(UserSessionPolicyConstraints.toServletSessionSeconds(effectiveTimeout));
    } catch (IllegalArgumentException | IllegalStateException exception) {
      expireSession(request, response, session);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private void expireSession(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpSession session
  ) throws IOException, ServletException {
    try {
      session.invalidate();
    } catch (IllegalStateException ignored) {
      // A concurrent invalidation still has the intended unauthenticated outcome.
    }
    SecurityContextHolder.clearContext();
    metrics.recordAbsoluteExpiration();
    authenticationEntryPoint.commence(
        request,
        response,
        new InsufficientAuthenticationException("Authentication session absolute timeout elapsed."));
  }

  private static boolean isAuthenticated(Authentication authentication) {
    return authentication != null && authentication.isAuthenticated();
  }

  private static Long absoluteExpiresAtEpochMillis(HttpSession session) {
    Object value = session.getAttribute(AuthSessionAttributeNames.ABSOLUTE_EXPIRES_AT_EPOCH_MILLIS);
    if (value == null) {
      return null;
    }
    if (!(value instanceof Long epochMillis) || epochMillis < 1) {
      return Long.MIN_VALUE;
    }
    return epochMillis;
  }
}
