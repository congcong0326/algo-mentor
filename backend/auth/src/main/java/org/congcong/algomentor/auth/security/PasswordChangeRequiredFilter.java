package org.congcong.algomentor.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.congcong.algomentor.auth.config.AuthSecurityPaths;
import org.congcong.algomentor.auth.passwordreset.PasswordResetErrorCode;
import org.congcong.algomentor.common.api.ApiErrorLocales;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

  private final ObjectMapper objectMapper;
  private final ApiErrorResponseFactory responseFactory;
  private final RequestMatcher apiMatcher = new AntPathRequestMatcher(AuthSecurityPaths.API_PATTERN);

  public PasswordChangeRequiredFilter(
      ObjectMapper objectMapper,
      ApiErrorResponseFactory responseFactory
  ) {
    this.objectMapper = objectMapper;
    this.responseFactory = responseFactory;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Optional<AuthenticatedUserPrincipal> principal = authenticatedPrincipal(authentication);
    if (!apiMatcher.matches(request)
        || principal.isEmpty()
        || !principal.get().passwordChangeRequired()
        || isAllowed(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getWriter(), responseFactory.failure(
        PasswordResetErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED.name(),
        "使用其他功能前必须先修改临时密码。",
        ApiErrorLocales.parse(request.getHeader("Accept-Language"))));
  }

  private static boolean isAllowed(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    return (HttpMethod.GET.matches(method) && AuthSecurityPaths.AUTH_ME_PATH.equals(path))
        || (HttpMethod.POST.matches(method) && AuthSecurityPaths.AUTH_COMPLETE_RESET_PATH.equals(path))
        || (HttpMethod.POST.matches(method) && AuthSecurityPaths.AUTH_LOGOUT_PATH.equals(path));
  }

  private static Optional<AuthenticatedUserPrincipal> authenticatedPrincipal(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }
    if (authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal) {
      return Optional.of(principal);
    }
    if (authentication.getPrincipal() instanceof AuthenticatedOAuth2User oauth2User) {
      return Optional.of(oauth2User.authenticatedUserPrincipal());
    }
    return Optional.empty();
  }
}
