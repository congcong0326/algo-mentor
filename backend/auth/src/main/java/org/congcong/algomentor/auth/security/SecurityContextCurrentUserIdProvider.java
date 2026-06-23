package org.congcong.algomentor.auth.security;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityContextCurrentUserIdProvider implements CurrentUserIdProvider {

  private static final Logger log = LoggerFactory.getLogger(SecurityContextCurrentUserIdProvider.class);

  @Override
  public Optional<AuthenticatedUserPrincipal> currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    log.info("Resolving current user from security context. {}", AuthDiagnosticSupport.authenticationSummary(authentication));
    if (authentication == null || !authentication.isAuthenticated()) {
      log.info("Current user resolution failed: authentication is missing or unauthenticated.");
      return Optional.empty();
    }
    if (authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal) {
      log.info("Current user resolved from direct principal. userId={}", principal.userId());
      return Optional.of(principal);
    }
    if (authentication.getPrincipal() instanceof AuthenticatedOAuth2User oauth2User) {
      AuthenticatedUserPrincipal principal = oauth2User.authenticatedUserPrincipal();
      log.info("Current user resolved from OAuth2 principal wrapper. userId={}", principal.userId());
      return Optional.of(principal);
    }
    log.info("Current user resolution failed: unsupported principal type.");
    return Optional.empty();
  }
}
