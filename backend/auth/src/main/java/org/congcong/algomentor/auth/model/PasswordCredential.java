package org.congcong.algomentor.auth.model;

import java.time.Instant;

public record PasswordCredential(
    Long id,
    long userId,
    String passwordHash,
    Instant createdAt,
    Instant updatedAt
) {

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
  }
}
