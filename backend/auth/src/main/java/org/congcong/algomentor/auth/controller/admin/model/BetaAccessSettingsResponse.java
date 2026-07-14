package org.congcong.algomentor.auth.controller.admin.model;

import java.time.Instant;
import org.congcong.algomentor.auth.betaaccess.model.BetaAccessSettings;

public record BetaAccessSettingsResponse(
    short id,
    boolean emailAllowlistEnabled,
    Long updatedBy,
    String updatedByDisplayName,
    Instant updatedAt
) {

  public static BetaAccessSettingsResponse from(BetaAccessSettings settings) {
    return new BetaAccessSettingsResponse(
        settings.id(),
        settings.emailAllowlistEnabled(),
        settings.updatedBy(),
        settings.updatedByDisplayName(),
        settings.updatedAt());
  }
}
