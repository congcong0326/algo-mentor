package org.congcong.algomentor.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityContextCurrentUserIdProviderTest {

  private final SecurityContextCurrentUserIdProvider provider = new SecurityContextCurrentUserIdProvider();
  private final AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
      42L,
      "user@example.com",
      "User Name",
      "https://example.com/avatar.png",
      List.of(AuthRole.USER),
      AuthUserStatus.ACTIVE);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void returnsAuthenticatedPrincipalFromSecurityContext() {
    TestingAuthenticationToken authentication = new TestingAuthenticationToken(principal, null);
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    assertThat(provider.currentUser()).contains(principal);
  }

  @Test
  void returnsAuthenticatedPrincipalFromOAuth2Wrapper() {
    AuthenticatedOAuth2User oauth2User = new AuthenticatedOAuth2User(
        principal,
        Map.of("sub", "google-sub"),
        AuthAuthorities.fromRoles(principal.roles()));
    TestingAuthenticationToken authentication = new TestingAuthenticationToken(oauth2User, null);
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    assertThat(provider.currentUser()).contains(principal);
  }

  @Test
  void returnsEmptyWhenPrincipalDoesNotExposeCurrentUser() {
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user@example.com", null));

    assertThat(provider.currentUser()).isEmpty();
  }
}
