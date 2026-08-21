package org.congcong.algomentor.auth.loginsettings.repository;

import java.time.Instant;
import java.util.Optional;
import org.congcong.algomentor.auth.loginsettings.model.AuthLoginSettings;

/** 认证入口设置单例的持久化边界。 */
public interface AuthLoginSettingsRepository {

  Optional<AuthLoginSettings> findSettings();

  void insertIfAbsent(AuthLoginSettings settings);

  boolean updateSettings(
      boolean accountRegistrationEnabled,
      boolean passwordLoginEnabled,
      boolean passwordRegistrationEnabled,
      boolean googleLoginEnabled,
      boolean githubLoginEnabled,
      long updatedBy,
      Instant updatedAt);
}
