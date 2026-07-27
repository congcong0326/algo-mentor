package org.congcong.algomentor.auth.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.model.PasswordCredential;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.security.AuthSessionAuthenticationMethod;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentAuthenticationContext;
import org.congcong.algomentor.auth.security.CurrentAuthenticationContextResolver;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserPasswordServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");
  private static final String CURRENT_SESSION_ID = "current-session";

  private final AuthUserRepository authUserRepository = mock(AuthUserRepository.class);
  private final IdentityUserRepository identityUserRepository = mock(IdentityUserRepository.class);
  private final CurrentAuthenticationContextResolver contextResolver = mock(CurrentAuthenticationContextResolver.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private final UserPasswordMutationExecutor mutationExecutor = mock(UserPasswordMutationExecutor.class);
  private final UserPasswordMetrics metrics = mock(UserPasswordMetrics.class);
  private UserPasswordService service;

  @BeforeEach
  void setUp() {
    service = new UserPasswordService(
        authUserRepository,
        identityUserRepository,
        contextResolver,
        passwordEncoder,
        mutationExecutor,
        metrics,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void passwordSessionVerifiesCurrentPasswordAndUsesCasUpdate() {
    PasswordCredential credential = credential("old-hash");
    when(contextResolver.resolve()).thenReturn(Optional.of(context(AuthSessionAuthenticationMethod.PASSWORD, false)));
    when(authUserRepository.findPasswordCredentialByUserId(42L)).thenReturn(Optional.of(credential));
    when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
    when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
    when(mutationExecutor.updatePasswordSession(
        42L,
        "old-hash",
        "new-hash",
        CURRENT_SESSION_ID,
        NOW)).thenReturn(new UserPasswordUpdateResult(true, UserPasswordUpdateOperation.UPDATED, 2));

    UserPasswordUpdateResult result = service.updatePassword(command("old-password"));

    assertThat(result.operation()).isEqualTo(UserPasswordUpdateOperation.UPDATED);
    assertThat(result.revokedSessionCount()).isEqualTo(2);
    verify(passwordEncoder).encode("new-password");
    verify(passwordEncoder).matches("old-password", "old-hash");
    verify(mutationExecutor).updatePasswordSession(42L, "old-hash", "new-hash", CURRENT_SESSION_ID, NOW);
    verify(metrics).recordSuccess(AuthSessionAuthenticationMethod.PASSWORD, UserPasswordUpdateOperation.UPDATED);
    verify(metrics).recordSessionRevocations(2);
  }

  @Test
  void passwordSessionRequiresCurrentPassword() {
    when(contextResolver.resolve()).thenReturn(Optional.of(context(AuthSessionAuthenticationMethod.PASSWORD, false)));
    when(authUserRepository.findPasswordCredentialByUserId(42L)).thenReturn(Optional.of(credential("old-hash")));
    when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

    assertThatThrownBy(() -> service.updatePassword(command(null)))
        .isInstanceOf(UserPasswordException.class)
        .extracting(exception -> ((UserPasswordException) exception).code())
        .isEqualTo(UserPasswordErrorCode.AUTH_CURRENT_PASSWORD_REQUIRED);
    verify(passwordEncoder, never()).matches(any(), any());
    verify(mutationExecutor, never()).updatePasswordSession(any(Long.class), any(), any(), any(), any());
  }

  @Test
  void passwordSessionRejectsInvalidCurrentPassword() {
    when(contextResolver.resolve()).thenReturn(Optional.of(context(AuthSessionAuthenticationMethod.PASSWORD, false)));
    when(authUserRepository.findPasswordCredentialByUserId(42L)).thenReturn(Optional.of(credential("old-hash")));
    when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
    when(passwordEncoder.matches("bad-password", "old-hash")).thenReturn(false);

    assertThatThrownBy(() -> service.updatePassword(command("bad-password")))
        .isInstanceOf(UserPasswordException.class)
        .extracting(exception -> ((UserPasswordException) exception).code())
        .isEqualTo(UserPasswordErrorCode.AUTH_CURRENT_PASSWORD_INVALID);
    verify(mutationExecutor, never()).updatePasswordSession(any(Long.class), any(), any(), any(), any());
  }

  @Test
  void oidcSessionCreatesPasswordWithoutCurrentPassword() {
    when(contextResolver.resolve()).thenReturn(Optional.of(context(AuthSessionAuthenticationMethod.OIDC, false)));
    when(authUserRepository.findPasswordCredentialByUserId(42L)).thenReturn(Optional.empty());
    when(identityUserRepository.findUserById(42L)).thenReturn(Optional.of(user()));
    when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
    when(mutationExecutor.updateOidcSession(42L, "new-hash", CURRENT_SESSION_ID, NOW))
        .thenReturn(new UserPasswordUpdateResult(true, UserPasswordUpdateOperation.CREATED, 0));

    UserPasswordUpdateResult result = service.updatePassword(command("ignored-current-password"));

    assertThat(result.operation()).isEqualTo(UserPasswordUpdateOperation.CREATED);
    verify(passwordEncoder, never()).matches(any(), any());
    verify(mutationExecutor).updateOidcSession(42L, "new-hash", CURRENT_SESSION_ID, NOW);
    verify(metrics).recordSuccess(AuthSessionAuthenticationMethod.OIDC, UserPasswordUpdateOperation.CREATED);
  }

  @Test
  void oidcSessionOverwritesExistingPasswordWithoutRequiringAnEmailLookup() {
    when(contextResolver.resolve()).thenReturn(Optional.of(context(AuthSessionAuthenticationMethod.OIDC, false)));
    when(authUserRepository.findPasswordCredentialByUserId(42L)).thenReturn(Optional.of(credential("old-hash")));
    when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
    when(mutationExecutor.updateOidcSession(42L, "new-hash", CURRENT_SESSION_ID, NOW))
        .thenReturn(new UserPasswordUpdateResult(true, UserPasswordUpdateOperation.UPDATED, 1));

    UserPasswordUpdateResult result = service.updatePassword(command(null));

    assertThat(result.operation()).isEqualTo(UserPasswordUpdateOperation.UPDATED);
    verify(identityUserRepository, never()).findUserById(any(Long.class));
    verify(passwordEncoder, never()).matches(any(), any());
  }

  @Test
  void oidcSessionRejectsFirstPasswordWhenNoEmailIsAvailable() {
    when(contextResolver.resolve()).thenReturn(Optional.of(context(AuthSessionAuthenticationMethod.OIDC, false)));
    when(authUserRepository.findPasswordCredentialByUserId(42L)).thenReturn(Optional.empty());
    when(identityUserRepository.findUserById(42L)).thenReturn(Optional.of(new AuthUser(
        42L, " ", " ", "Member", null, AuthUserStatus.ACTIVE, NOW, NOW, null, null, null)));
    when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

    assertThatThrownBy(() -> service.updatePassword(command(null)))
        .isInstanceOf(UserPasswordException.class)
        .extracting(exception -> ((UserPasswordException) exception).code())
        .isEqualTo(UserPasswordErrorCode.AUTH_PASSWORD_LOGIN_EMAIL_UNAVAILABLE);
    verify(mutationExecutor, never()).updateOidcSession(any(Long.class), any(), any(), any());
  }

  @Test
  void rejectsPasswordChangeRequiredSessionBeforeEncodingTheNewPassword() {
    when(contextResolver.resolve()).thenReturn(Optional.of(context(AuthSessionAuthenticationMethod.PASSWORD, true)));

    assertThatThrownBy(() -> service.updatePassword(command("old-password")))
        .isInstanceOf(UserPasswordException.class)
        .extracting(exception -> ((UserPasswordException) exception).code())
        .isEqualTo(UserPasswordErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED);
    verify(passwordEncoder, never()).encode(any());
  }

  @Test
  void preservesConcurrentModificationFailure() {
    when(contextResolver.resolve()).thenReturn(Optional.of(context(AuthSessionAuthenticationMethod.PASSWORD, false)));
    when(authUserRepository.findPasswordCredentialByUserId(42L)).thenReturn(Optional.of(credential("old-hash")));
    when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
    when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
    when(mutationExecutor.updatePasswordSession(eq(42L), eq("old-hash"), eq("new-hash"), eq(CURRENT_SESSION_ID), eq(NOW)))
        .thenThrow(new UserPasswordException(
            UserPasswordErrorCode.AUTH_PASSWORD_CHANGED_CONCURRENTLY,
            "concurrent"));

    assertThatThrownBy(() -> service.updatePassword(command("old-password")))
        .isInstanceOf(UserPasswordException.class)
        .extracting(exception -> ((UserPasswordException) exception).code())
        .isEqualTo(UserPasswordErrorCode.AUTH_PASSWORD_CHANGED_CONCURRENTLY);
    verify(metrics).recordFailure(
        AuthSessionAuthenticationMethod.PASSWORD,
        UserPasswordErrorCode.AUTH_PASSWORD_CHANGED_CONCURRENTLY);
  }

  @Test
  void rejectsUnsupportedAuthenticationType() {
    when(contextResolver.resolve()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.updatePassword(command("old-password")))
        .isInstanceOf(UserPasswordException.class)
        .extracting(exception -> ((UserPasswordException) exception).code())
        .isEqualTo(UserPasswordErrorCode.AUTH_PASSWORD_UPDATE_NOT_ALLOWED);
    verify(passwordEncoder, never()).encode(any());
  }

  private static UserPasswordUpdateCommand command(String currentPassword) {
    return new UserPasswordUpdateCommand(currentPassword, "new-password", "new-password", CURRENT_SESSION_ID);
  }

  private static CurrentAuthenticationContext context(
      AuthSessionAuthenticationMethod method,
      boolean passwordChangeRequired
  ) {
    return new CurrentAuthenticationContext(new AuthenticatedUserPrincipal(
        42L,
        "member@example.com",
        "Member",
        null,
        List.of(AuthRole.USER),
        AuthUserStatus.ACTIVE,
        passwordChangeRequired), method);
  }

  private static PasswordCredential credential(String hash) {
    return new PasswordCredential(1L, 42L, hash, NOW, NOW);
  }

  private static AuthUser user() {
    return new AuthUser(
        42L,
        "member@example.com",
        "member@example.com",
        "Member",
        null,
        AuthUserStatus.ACTIVE,
        NOW,
        NOW,
        null,
        null,
        null);
  }
}
