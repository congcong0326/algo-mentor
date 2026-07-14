package org.congcong.algomentor.auth.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.model.PasswordCredential;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.common.admin.audit.AdminAuditOutcome;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");

  @Test
  void returnsPlaintextOnceButPassesOnlyHashToMutation() {
    AuthUserRepository authRepository = mock(AuthUserRepository.class);
    IdentityUserRepository identityRepository = mock(IdentityUserRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    TemporaryPasswordGenerator generator = mock(TemporaryPasswordGenerator.class);
    PasswordResetMutationExecutor mutationExecutor = mock(PasswordResetMutationExecutor.class);
    List<AdminOperationAuditEvent> audits = new ArrayList<>();
    when(identityRepository.findUserById(42L)).thenReturn(Optional.of(user()));
    when(authRepository.findPasswordCredentialByUserId(42L)).thenReturn(Optional.of(credential()));
    when(generator.generate()).thenReturn("Aa2!temporary-pass");
    when(encoder.encode("Aa2!temporary-pass")).thenReturn("bcrypt-hash");
    when(mutationExecutor.resetPassword(
        42L,
        "bcrypt-hash",
        NOW.plus(PasswordResetService.TEMPORARY_PASSWORD_TTL),
        1L,
        NOW)).thenReturn(3);
    PasswordResetService service = new PasswordResetService(
        authRepository,
        identityRepository,
        encoder,
        generator,
        mutationExecutor,
        audits::add,
        Clock.fixed(NOW, ZoneOffset.UTC));

    PasswordResetResult result = service.resetPassword(42L, 1L);

    assertThat(result.temporaryPassword()).isEqualTo("Aa2!temporary-pass");
    assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(24 * 60 * 60));
    verify(mutationExecutor).resetPassword(
        42L,
        "bcrypt-hash",
        result.expiresAt(),
        1L,
        NOW);
    assertThat(audits).singleElement().satisfies(event -> {
      assertThat(event.outcome()).isEqualTo(AdminAuditOutcome.SUCCESS);
      assertThat(event.metadata().values()).doesNotContain("Aa2!temporary-pass", "bcrypt-hash");
    });
  }

  @Test
  void rejectsSelfResetAndRecordsFailure() {
    List<AdminOperationAuditEvent> audits = new ArrayList<>();
    PasswordResetService service = new PasswordResetService(
        mock(AuthUserRepository.class),
        mock(IdentityUserRepository.class),
        mock(PasswordEncoder.class),
        mock(TemporaryPasswordGenerator.class),
        mock(PasswordResetMutationExecutor.class),
        audits::add,
        Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(() -> service.resetPassword(1L, 1L))
        .isInstanceOf(PasswordResetException.class)
        .extracting(exception -> ((PasswordResetException) exception).code())
        .isEqualTo(PasswordResetErrorCode.AUTH_PASSWORD_RESET_SELF_FORBIDDEN);
    assertThat(audits).singleElement().extracting(AdminOperationAuditEvent::outcome)
        .isEqualTo(AdminAuditOutcome.FAILURE);
  }

  @Test
  void doesNotReturnTemporaryPasswordWhenMutationOrRevocationFails() {
    AuthUserRepository authRepository = mock(AuthUserRepository.class);
    IdentityUserRepository identityRepository = mock(IdentityUserRepository.class);
    TemporaryPasswordGenerator generator = mock(TemporaryPasswordGenerator.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    PasswordResetMutationExecutor mutationExecutor = mock(PasswordResetMutationExecutor.class);
    List<AdminOperationAuditEvent> audits = new ArrayList<>();
    when(identityRepository.findUserById(42L)).thenReturn(Optional.of(user()));
    when(authRepository.findPasswordCredentialByUserId(42L)).thenReturn(Optional.of(credential()));
    when(generator.generate()).thenReturn("Aa2!temporary-pass");
    when(encoder.encode("Aa2!temporary-pass")).thenReturn("bcrypt-hash");
    when(mutationExecutor.resetPassword(
        42L,
        "bcrypt-hash",
        NOW.plus(PasswordResetService.TEMPORARY_PASSWORD_TTL),
        1L,
        NOW)).thenThrow(new IllegalStateException("session store unavailable"));
    PasswordResetService service = new PasswordResetService(
        authRepository,
        identityRepository,
        encoder,
        generator,
        mutationExecutor,
        audits::add,
        Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(() -> service.resetPassword(42L, 1L))
        .isInstanceOf(PasswordResetException.class)
        .extracting(exception -> ((PasswordResetException) exception).code())
        .isEqualTo(PasswordResetErrorCode.AUTH_PASSWORD_RESET_FAILED);
    assertThat(audits).singleElement().extracting(AdminOperationAuditEvent::outcome)
        .isEqualTo(AdminAuditOutcome.FAILURE);
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

  private static PasswordCredential credential() {
    return new PasswordCredential(1L, 42L, "old-hash", NOW, NOW);
  }
}
