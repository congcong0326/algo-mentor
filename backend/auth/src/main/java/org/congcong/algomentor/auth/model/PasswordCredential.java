package org.congcong.algomentor.auth.model;

import java.time.Instant;

public record PasswordCredential(
    Long id,
    long userId,
    String passwordHash,
    boolean resetRequired,
    Instant temporaryPasswordExpiresAt,
    Instant temporaryPasswordConsumedAt,
    Instant passwordChangedAt,
    Long resetBy,
    Instant createdAt,
    Instant updatedAt
) {

  public PasswordCredential(
      Long id,
      long userId,
      String passwordHash,
      Instant createdAt,
      Instant updatedAt
  ) {
    this(id, userId, passwordHash, false, null, null, null, null, createdAt, updatedAt);
  }

  public PasswordCredential {
    if (id != null && id < 1) {
      throw new IllegalArgumentException("id must be positive when present.");
    }
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive.");
    }
    if (passwordHash == null || passwordHash.isBlank()) {
      throw new IllegalArgumentException("passwordHash must not be blank.");
    }
    if (resetBy != null && resetBy < 1) {
      throw new IllegalArgumentException("resetBy must be positive when present.");
    }
  }
}
