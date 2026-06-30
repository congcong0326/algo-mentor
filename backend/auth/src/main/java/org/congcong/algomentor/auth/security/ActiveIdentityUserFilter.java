package org.congcong.algomentor.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import org.congcong.algomentor.auth.config.AuthSecurityPaths;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public class ActiveIdentityUserFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(ActiveIdentityUserFilter.class);

  private final IdentityUserRepository identityUserRepository;
  private final AuthenticationEntryPoint authenticationEntryPoint;
  private final RequestMatcher apiRequestMatcher = new AntPathRequestMatcher(AuthSecurityPaths.API_PATTERN);

  public ActiveIdentityUserFilter(
      IdentityUserRepository identityUserRepository,
      AuthenticationEntryPoint authenticationEntryPoint
  ) {
    this.identityUserRepository = identityUserRepository;
    this.authenticationEntryPoint = authenticationEntryPoint;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Optional<AuthenticatedUserPrincipal> principal = authenticatedPrincipal(authentication);
    if (!apiRequestMatcher.matches(request)
        || authentication == null
        || !authentication.isAuthenticated()
        || principal.isEmpty()
        || isActive(principal.get())) {
      filterChain.doFilter(request, response);
      return;
    }

    SecurityContextHolder.clearContext();
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    authenticationEntryPoint.commence(
        request,
        response,
        new InsufficientAuthenticationException("Identity user is not active."));
  }

  private boolean isActive(AuthenticatedUserPrincipal principal) {
    try {
      return identityUserRepository.findUserById(principal.userId())
          .filter(user -> user.status() == AuthUserStatus.ACTIVE)
          .isPresent();
    } catch (RuntimeException exception) {
      log.warn("Failed to validate active identity user for authenticated request. userId={}",
          principal.userId(),
          exception);
      return false;
    }
  }

  private Optional<AuthenticatedUserPrincipal> authenticatedPrincipal(Authentication authentication) {
    if (authentication == null) {
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
