package org.congcong.algomentor.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.model.PasswordCredential;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.security.AuthSessionAuthenticationMethod;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentAuthenticationContext;
import org.congcong.algomentor.auth.security.CurrentAuthenticationContextResolver;
import org.congcong.algomentor.auth.service.AuthPermissionService;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.junit.jupiter.api.Test;

class CurrentUserResponseFactoryTest {

  @Test
  void reportsPasswordCredentialStateAndCurrentSessionMethod() {
    AuthUserRepository repository = mock(AuthUserRepository.class);
    CurrentAuthenticationContextResolver resolver = mock(CurrentAuthenticationContextResolver.class);
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
        42L,
        "member@example.com",
        "Member",
        null,
        List.of(AuthRole.USER),
        AuthUserStatus.ACTIVE);
    when(repository.findPasswordCredentialByUserId(42L)).thenReturn(Optional.of(new PasswordCredential(
        1L,
        42L,
        "hash",
        Instant.EPOCH,
        Instant.EPOCH)));
    when(resolver.resolve()).thenReturn(Optional.of(new CurrentAuthenticationContext(
        principal,
        AuthSessionAuthenticationMethod.OIDC)));

    var response = new CurrentUserResponseFactory(repository, resolver, new AuthPermissionService()).create(principal);

    assertThat(response.passwordConfigured()).isTrue();
    assertThat(response.sessionAuthenticationMethod()).isEqualTo(AuthSessionAuthenticationMethod.OIDC);
  }
}
