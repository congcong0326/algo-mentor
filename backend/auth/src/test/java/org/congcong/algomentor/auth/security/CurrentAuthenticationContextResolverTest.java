package org.congcong.algomentor.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

class CurrentAuthenticationContextResolverTest {

  private final CurrentAuthenticationContextResolver resolver = new CurrentAuthenticationContextResolver();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void resolvesPasswordSessionOnlyFromPasswordAuthenticationToken() {
    AuthenticatedUserPrincipal principal = principal();
    SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
        principal,
        null,
        AuthAuthorities.fromRoles(principal.roles())));

    assertThat(resolver.resolve()).contains(new CurrentAuthenticationContext(
        principal,
        AuthSessionAuthenticationMethod.PASSWORD));
  }

  @Test
  void resolvesOidcSessionOnlyFromAuthenticatedOidcUser() {
    AuthenticatedOidcUser oidcUser = mock(AuthenticatedOidcUser.class);
    when(oidcUser.authenticatedUserPrincipal()).thenReturn(principal());
    SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(
        oidcUser,
        List.of(),
        "google"));

    assertThat(resolver.resolve()).isPresent().get()
        .extracting(CurrentAuthenticationContext::method)
        .isEqualTo(AuthSessionAuthenticationMethod.OIDC);
  }

  @Test
  void resolvesOAuth2SessionFromAuthenticatedOAuth2User() {
    AuthenticatedUserPrincipal principal = principal();
    AuthenticatedOAuth2User oauth2User = new AuthenticatedOAuth2User(
        principal,
        Map.of("id", 123456L),
        List.of());
    SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(
        oauth2User,
        List.of(),
        "github"));

    assertThat(resolver.resolve()).contains(new CurrentAuthenticationContext(
        principal,
        AuthSessionAuthenticationMethod.OAUTH2));
  }

  @Test
  void rejectsAnOidcPrincipalCarriedByTheWrongAuthenticationType() {
    AuthenticatedOidcUser oidcUser = mock(AuthenticatedOidcUser.class);
    SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
        oidcUser,
        null,
        List.of()));

    assertThat(resolver.resolve()).isEmpty();
  }

  private static AuthenticatedUserPrincipal principal() {
    return new AuthenticatedUserPrincipal(
        42L,
        "member@example.com",
        "Member",
        null,
        List.of(AuthRole.USER),
        AuthUserStatus.ACTIVE);
  }
}
