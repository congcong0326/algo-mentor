package org.congcong.algomentor.identity.repository.mybatis.model;

import java.time.Instant;

public record AdminUserSearchRow(
    Long id,
    String email,
    String emailNormalized,
    String displayName,
    String avatarUrl,
    String status,
    Instant createdAt,
    Instant updatedAt,
    Instant lastLoginAt,
    Instant deletedAt,
    Long deletedBy
) {

  public AuthUserRow toUserRow() {
    return new AuthUserRow(
        id,
        email,
        emailNormalized,
        displayName,
        avatarUrl,
        status,
        createdAt,
        updatedAt,
        lastLoginAt,
        deletedAt,
        deletedBy);
  }
}
