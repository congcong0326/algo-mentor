package org.congcong.algomentor.auth.betaaccess.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.auth.betaaccess.model.BetaAccessSettings;

public record BetaAccessSettingsRow(
    short id,
    boolean emailAllowlistEnabled,
    Long updatedBy,
    String updatedByDisplayName,
    Instant updatedAt
) {

  public BetaAccessSettings toDomain() {
    return new BetaAccessSettings(id, emailAllowlistEnabled, updatedBy, updatedByDisplayName, updatedAt);
  }
}
