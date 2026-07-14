package org.congcong.algomentor.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessErrorCode;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessException;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessPolicy;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

class BetaAccessAuthenticationFlowTest {

  private static final Clock CLOCK = Clock.fixed(
      Instant.parse("2026-07-13T00:00:00Z"),
      ZoneOffset.UTC);

  @Test
  void passwordRegistrationChecksPolicyBeforeCreatingUser() {
    AuthUserRepository authRepository = mock(AuthUserRepository.class);
    IdentityUserRepository identityRepository = mock(IdentityUserRepository.class);
    PasswordUserService service = new PasswordUserService(
        authRepository,
        identityRepository,
        mock(PasswordEncoder.class),
        CLOCK,
        null,
        rejectingPolicy());

    assertThatThrownBy(() -> service.register("member@example.com", "password-123", "Member"))
        .isInstanceOf(BetaAccessException.class);
    verify(identityRepository, never()).createUser(any(), any(), any(), any(), any(), any());
  }

  @Test
  void oauthChecksProviderEmailBeforeCreatingOrSynchronizingAccount() {
    AuthUserRepository authRepository = mock(AuthUserRepository.class);
    IdentityUserRepository identityRepository = mock(IdentityUserRepository.class);
    when(authRepository.findOAuthAccount(any(), any())).thenReturn(Optional.empty());
    OAuth2LoginUserService service = new OAuth2LoginUserService(
        authRepository,
        identityRepository,
        CLOCK,
        null,
        rejectingPolicy());

    assertThatThrownBy(() -> service.syncGoogleUser(Map.of(
        "sub", "google-sub",
        "email", "member@example.com")))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .extracting(exception -> ((OAuth2AuthenticationException) exception).getError().getErrorCode())
        .isEqualTo(BetaAccessErrorCode.AUTH_BETA_ACCESS_DENIED.name());
    verify(identityRepository, never()).createUser(any(), any(), any(), any(), any(), any());
  }

  private static BetaAccessPolicy rejectingPolicy() {
    BetaAccessPolicy policy = mock(BetaAccessPolicy.class);
    doThrow(new BetaAccessException(
        BetaAccessErrorCode.AUTH_BETA_ACCESS_DENIED,
        "denied"))
        .when(policy).requireAllowed(any(), anyBoolean());
    return policy;
  }
}
