package org.congcong.algomentor.auth.loginsettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.loginsettings.model.AuthLoginSettings;
import org.congcong.algomentor.auth.loginsettings.repository.AuthLoginSettingsRepository;
import org.congcong.algomentor.auth.loginsettings.service.AuthLoginSettingsErrorCode;
import org.congcong.algomentor.auth.loginsettings.service.AuthLoginSettingsException;
import org.congcong.algomentor.auth.loginsettings.service.AuthLoginSettingsService;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.junit.jupiter.api.Test;

class AuthLoginSettingsServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");

  @Test
  void initializesTheSingletonFromLegacyPropertiesOnFirstRead() {
    AuthLoginSettingsRepository repository = mock(AuthLoginSettingsRepository.class);
    AuthProperties properties = new AuthProperties();
    properties.setAccountRegistrationEnabled(false);
    properties.setPasswordLoginEnabled(true);
    properties.setPasswordRegistrationEnabled(false);
    when(repository.findSettings()).thenReturn(Optional.empty(), Optional.of(
        new AuthLoginSettings((short) 1, false, true, false, true, true, null, null, NOW)));
    AuthLoginSettingsService service = service(repository, properties);

    AuthLoginSettings result = service.current();

    assertThat(result.accountRegistrationEnabled()).isFalse();
    assertThat(result.passwordLoginEnabled()).isTrue();
    assertThat(result.passwordRegistrationEnabled()).isFalse();
    verify(repository).insertIfAbsent(any(AuthLoginSettings.class));
  }

  @Test
  void updatesAllSettingsAndReadsTheNewSnapshotImmediately() {
    AuthLoginSettingsRepository repository = mock(AuthLoginSettingsRepository.class);
    AuthLoginSettings updated = new AuthLoginSettings((short) 1, false, false, false, true, false, 7L, "Admin", NOW);
    when(repository.findSettings()).thenReturn(Optional.of(
        new AuthLoginSettings((short) 1, true, true, true, true, true, null, null, NOW)),
        Optional.of(updated));
    when(repository.updateSettings(false, false, false, true, false, 7L, NOW)).thenReturn(true);
    AdminOperationAuditRecorder auditRecorder = mock(AdminOperationAuditRecorder.class);

    AuthLoginSettings result = new AuthLoginSettingsService(
        repository,
        new AuthProperties(),
        auditRecorder,
        Clock.fixed(NOW, ZoneOffset.UTC)).update(false, false, false, true, false, 7L);

    assertThat(result).isEqualTo(updated);
    verify(auditRecorder).record(any(AdminOperationAuditEvent.class));
  }

  @Test
  void rejectsIncompleteUpdates() {
    AuthLoginSettingsRepository repository = mock(AuthLoginSettingsRepository.class);
    when(repository.findSettings()).thenReturn(Optional.of(
        new AuthLoginSettings((short) 1, true, true, true, true, true, null, null, NOW)));

    assertThatThrownBy(() -> service(repository, new AuthProperties())
        .update(null, true, true, true, true, 7L))
        .isInstanceOf(AuthLoginSettingsException.class)
        .extracting(exception -> ((AuthLoginSettingsException) exception).code())
        .isEqualTo(AuthLoginSettingsErrorCode.AUTH_LOGIN_SETTINGS_INVALID);
  }

  private static AuthLoginSettingsService service(
      AuthLoginSettingsRepository repository,
      AuthProperties properties
  ) {
    return new AuthLoginSettingsService(
        repository,
        properties,
        mock(AdminOperationAuditRecorder.class),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }
}
