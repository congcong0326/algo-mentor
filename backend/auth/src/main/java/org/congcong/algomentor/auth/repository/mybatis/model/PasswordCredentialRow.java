package org.congcong.algomentor.auth.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.auth.model.PasswordCredential;

public final class PasswordCredentialRow {

  private Long id;
  private final Long userId;
  private final String passwordHash;
  private final Instant createdAt;
  private final Instant updatedAt;

  public PasswordCredentialRow(
      Long id,
      Long userId,
      String passwordHash,
      Instant createdAt,
      Instant updatedAt
  ) {
    this.id = id;
    this.userId = userId;
    this.passwordHash = passwordHash;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public Long id() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long userId() {
    return userId;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public PasswordCredential toDomain() {
    return new PasswordCredential(id, userId, passwordHash, createdAt, updatedAt);
  }
}
