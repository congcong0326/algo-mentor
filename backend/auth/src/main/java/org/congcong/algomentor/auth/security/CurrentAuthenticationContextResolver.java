package org.congcong.algomentor.auth.security;

import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

/**
 * 仅从受信 Spring Security Authentication 推导当前 Session 的认证方式。
 */
public class CurrentAuthenticationContextResolver {

  public Optional<CurrentAuthenticationContext> resolve() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }
    if (authentication instanceof UsernamePasswordAuthenticationToken
        && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal) {
      return Optional.of(new CurrentAuthenticationContext(
          principal,
          AuthSessionAuthenticationMethod.PASSWORD));
    }
    if (authentication instanceof OAuth2AuthenticationToken
        && authentication.getPrincipal() instanceof AuthenticatedOidcUser oidcUser) {
      return Optional.of(new CurrentAuthenticationContext(
          oidcUser.authenticatedUserPrincipal(),
          AuthSessionAuthenticationMethod.OIDC));
    }
    if (authentication instanceof OAuth2AuthenticationToken
        && authentication.getPrincipal() instanceof AuthenticatedOAuth2User oauth2User) {
      return Optional.of(new CurrentAuthenticationContext(
          oauth2User.authenticatedUserPrincipal(),
          AuthSessionAuthenticationMethod.OAUTH2));
    }
    return Optional.empty();
  }
}
