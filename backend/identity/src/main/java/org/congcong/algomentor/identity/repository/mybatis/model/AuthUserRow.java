package org.congcong.algomentor.identity.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public final class AuthUserRow {

  private Long id;
  private final String email;
  private final String emailNormalized;
  private final String displayName;
  private final String avatarUrl;
  private final String status;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final Instant lastLoginAt;
  private final Instant deletedAt;
  private final Long deletedBy;

  public AuthUserRow(
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
    this.id = id;
    this.email = email;
    this.emailNormalized = emailNormalized;
    this.displayName = displayName;
    this.avatarUrl = avatarUrl;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.lastLoginAt = lastLoginAt;
    this.deletedAt = deletedAt;
    this.deletedBy = deletedBy;
  }

  public Long id() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String email() {
    return email;
  }

  public String emailNormalized() {
    return emailNormalized;
  }

  public String displayName() {
    return displayName;
  }

  public String avatarUrl() {
    return avatarUrl;
  }

  public String status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Instant lastLoginAt() {
    return lastLoginAt;
  }

  public Instant deletedAt() {
    return deletedAt;
  }

  public Long deletedBy() {
    return deletedBy;
  }

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
        lastLoginAt,
        deletedAt,
        deletedBy);
  }
}
