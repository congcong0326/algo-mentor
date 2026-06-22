package org.congcong.algomentor.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityContextCurrentUserIdProviderTest {

  private final SecurityContextCurrentUserIdProvider provider = new SecurityContextCurrentUserIdProvider();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void returnsUserIdFromAuthenticatedPrincipal() {
    TestingAuthenticationToken authentication = new TestingAuthenticationToken(
        new AuthenticatedUserPrincipal(42L),
        null);
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    assertThat(provider.currentUserId()).contains(42L);
  }

  @Test
  void returnsEmptyWhenPrincipalDoesNotExposeUserId() {
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user@example.com", null));

    assertThat(provider.currentUserId()).isEmpty();
  }
}
