package org.congcong.algomentor.auth.controller.admin.model;

import java.time.Instant;
import org.congcong.algomentor.auth.loginsettings.model.AuthLoginSettings;

public record AuthLoginSettingsResponse(
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

  public static AuthLoginSettingsResponse from(AuthLoginSettings settings) {
    return new AuthLoginSettingsResponse(
        settings.id(),
        settings.accountRegistrationEnabled(),
        settings.passwordLoginEnabled(),
        settings.passwordRegistrationEnabled(),
        settings.googleLoginEnabled(),
        settings.githubLoginEnabled(),
        settings.updatedBy(),
        settings.updatedByDisplayName(),
        settings.updatedAt());
  }
}
