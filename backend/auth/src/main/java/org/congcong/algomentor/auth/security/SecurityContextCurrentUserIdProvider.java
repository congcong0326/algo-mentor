package org.congcong.algomentor.auth.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityContextCurrentUserIdProvider implements CurrentUserIdProvider {

  @Override
  public Optional<Long> currentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }
    if (authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal) {
      return Optional.of(principal.userId());
    }
    return Optional.empty();
  }
}
