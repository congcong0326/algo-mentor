package org.congcong.algomentor.auth.model;

import java.time.Instant;

public record OAuthAccount(
    Long id,
    Long userId,
    OAuthProvider provider,
    String providerSubject,
    String emailAtProvider,
    String displayNameAtProvider,
    String avatarUrlAtProvider,
    Instant createdAt,
    Instant updatedAt
) {

  public OAuthAccount {
    if (id != null && id < 1) {
      throw new IllegalArgumentException("id must be positive when present.");
    }
    if (userId == null || userId < 1) {
      throw new IllegalArgumentException("userId must be a positive number.");
    }
    if (provider == null) {
      throw new IllegalArgumentException("provider must not be null.");
    }
    if (providerSubject == null || providerSubject.isBlank()) {
      throw new IllegalArgumentException("providerSubject must not be blank.");
    }
  }
}
