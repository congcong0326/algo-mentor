package org.congcong.algomentor.auth.loginsettings.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.auth.loginsettings.model.AuthLoginSettings;

/** MyBatis 行模型，隔离数据库列名与认证领域模型。 */
public record AuthLoginSettingsRow(
    short id,
    boolean accountRegistrationEnabled,
    boolean passwordLoginEnabled,
    boolean passwordRegistrationEnabled,
    boolean googleLoginEnabled,
    boolean githubLoginEnabled,
    Long updatedBy,
    String updatedByDisplayName,
    Instant updatedAt
) {

  public AuthLoginSettings toModel() {
    return new AuthLoginSettings(
        id,
        accountRegistrationEnabled,
        passwordLoginEnabled,
        passwordRegistrationEnabled,
        googleLoginEnabled,
        githubLoginEnabled,
        updatedBy,
        updatedByDisplayName,
        updatedAt);
  }
}
