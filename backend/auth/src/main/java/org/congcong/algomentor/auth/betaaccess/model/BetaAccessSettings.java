package org.congcong.algomentor.auth.betaaccess.model;

import java.time.Instant;

public record BetaAccessSettings(
    short id,
    boolean emailAllowlistEnabled,
    Long updatedBy,
    String updatedByDisplayName,
    Instant updatedAt
) {
}
