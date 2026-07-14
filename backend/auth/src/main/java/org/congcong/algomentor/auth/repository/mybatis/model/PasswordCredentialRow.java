package org.congcong.algomentor.auth.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.auth.model.PasswordCredential;

public final class PasswordCredentialRow {

  private Long id;
  private final Long userId;
  private final String passwordHash;
  private final boolean resetRequired;
  private final Instant temporaryPasswordExpiresAt;
  private final Instant temporaryPasswordConsumedAt;
  private final Instant passwordChangedAt;
  private final Long resetBy;
  private final Instant createdAt;
  private final Instant updatedAt;

  public PasswordCredentialRow(
      Long id,
      Long userId,
      String passwordHash,
      boolean resetRequired,
      Instant temporaryPasswordExpiresAt,
      Instant temporaryPasswordConsumedAt,
      Instant passwordChangedAt,
      Long resetBy,
      Instant createdAt,
      Instant updatedAt
  ) {
    this.id = id;
    this.userId = userId;
    this.passwordHash = passwordHash;
    this.resetRequired = resetRequired;
    this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
    this.temporaryPasswordConsumedAt = temporaryPasswordConsumedAt;
    this.passwordChangedAt = passwordChangedAt;
    this.resetBy = resetBy;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public PasswordCredentialRow(
      Long id,
      Long userId,
      String passwordHash,
      Instant createdAt,
      Instant updatedAt
  ) {
    this(id, userId, passwordHash, false, null, null, null, null, createdAt, updatedAt);
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

  public boolean resetRequired() {
    return resetRequired;
  }

  public Instant temporaryPasswordExpiresAt() {
    return temporaryPasswordExpiresAt;
  }

  public Instant temporaryPasswordConsumedAt() {
    return temporaryPasswordConsumedAt;
  }

  public Instant passwordChangedAt() {
    return passwordChangedAt;
  }

  public Long resetBy() {
    return resetBy;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public PasswordCredential toDomain() {
    return new PasswordCredential(
        id,
        userId,
        passwordHash,
        resetRequired,
        temporaryPasswordExpiresAt,
        temporaryPasswordConsumedAt,
        passwordChangedAt,
        resetBy,
        createdAt,
        updatedAt);
  }
}
