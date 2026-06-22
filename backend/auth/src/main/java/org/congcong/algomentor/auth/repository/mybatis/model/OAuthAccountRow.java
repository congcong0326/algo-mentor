package org.congcong.algomentor.auth.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.auth.model.OAuthAccount;
import org.congcong.algomentor.auth.model.OAuthProvider;

public record OAuthAccountRow(
    Long id,
    Long userId,
    String provider,
    String providerSubject,
    String emailAtProvider,
    String displayNameAtProvider,
    String avatarUrlAtProvider,
    Instant createdAt,
    Instant updatedAt
) {

  public OAuthAccount toDomain() {
    return new OAuthAccount(
        id,
        userId,
        OAuthProvider.valueOf(provider.toUpperCase(java.util.Locale.ROOT)),
        providerSubject,
        emailAtProvider,
        displayNameAtProvider,
        avatarUrlAtProvider,
        createdAt,
        updatedAt);
  }
}
