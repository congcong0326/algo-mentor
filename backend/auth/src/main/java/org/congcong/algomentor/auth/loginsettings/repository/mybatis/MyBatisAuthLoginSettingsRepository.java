package org.congcong.algomentor.auth.loginsettings.repository.mybatis;

import java.time.Instant;
import java.util.Optional;
import org.congcong.algomentor.auth.loginsettings.model.AuthLoginSettings;
import org.congcong.algomentor.auth.loginsettings.repository.AuthLoginSettingsRepository;

public class MyBatisAuthLoginSettingsRepository implements AuthLoginSettingsRepository {

  private final AuthLoginSettingsMapper mapper;

  public MyBatisAuthLoginSettingsRepository(AuthLoginSettingsMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<AuthLoginSettings> findSettings() {
    return Optional.ofNullable(mapper.findSettings()).map(row -> row.toModel());
  }

  @Override
  public void insertIfAbsent(AuthLoginSettings settings) {
    mapper.insertIfAbsent(
        settings.accountRegistrationEnabled(),
        settings.passwordLoginEnabled(),
        settings.passwordRegistrationEnabled(),
        settings.googleLoginEnabled(),
        settings.githubLoginEnabled(),
        settings.updatedAt());
  }

  @Override
  public boolean updateSettings(
      boolean accountRegistrationEnabled,
      boolean passwordLoginEnabled,
      boolean passwordRegistrationEnabled,
      boolean googleLoginEnabled,
      boolean githubLoginEnabled,
      long updatedBy,
      Instant updatedAt
  ) {
    return mapper.updateSettings(
        accountRegistrationEnabled,
        passwordLoginEnabled,
        passwordRegistrationEnabled,
        googleLoginEnabled,
        githubLoginEnabled,
        updatedBy,
        updatedAt) == 1;
  }
}
