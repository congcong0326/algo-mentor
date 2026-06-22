package org.congcong.algomentor.auth.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.auth.model.AuthUser;
import org.congcong.algomentor.auth.model.AuthUserStatus;

public record AuthUserRow(
    Long id,
    String email,
    String emailNormalized,
    String displayName,
    String avatarUrl,
    String status,
    Instant createdAt,
    Instant updatedAt,
    Instant lastLoginAt
) {

  public AuthUser toDomain() {
    return new AuthUser(
        id,
        email,
        emailNormalized,
        displayName,
        avatarUrl,
        AuthUserStatus.valueOf(status),
        createdAt,
        updatedAt,
        lastLoginAt);
  }
}
