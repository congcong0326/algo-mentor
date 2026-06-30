package org.congcong.algomentor.identity.model;

import java.time.Instant;

public record AuthUser(
    Long id,
    String email,
    String emailNormalized,
    String displayName,
    String avatarUrl,
    AuthUserStatus status,
    Instant createdAt,
    Instant updatedAt,
    Instant lastLoginAt,
    Instant deletedAt,
    Long deletedBy
) {

  public AuthUser {
    if (id != null && id < 1) {
      throw new IllegalArgumentException("id must be positive when present.");
    }
    if (deletedBy != null && deletedBy < 1) {
      throw new IllegalArgumentException("deletedBy must be positive when present.");
    }
    status = status == null ? AuthUserStatus.ACTIVE : status;
  }
}
