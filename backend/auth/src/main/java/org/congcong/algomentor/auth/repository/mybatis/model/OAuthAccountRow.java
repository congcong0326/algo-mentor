package org.congcong.algomentor.auth.repository.mybatis.model;

import java.time.Instant;
import java.util.Locale;
import org.congcong.algomentor.auth.model.OAuthAccount;
import org.congcong.algomentor.auth.model.OAuthProvider;

public final class OAuthAccountRow {

  private Long id;
  private final Long userId;
  private final String provider;
  private final String providerSubject;
  private final String emailAtProvider;
  private final String displayNameAtProvider;
  private final String avatarUrlAtProvider;
  private final Instant createdAt;
  private final Instant updatedAt;

  public OAuthAccountRow(
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
    this.id = id;
    this.userId = userId;
    this.provider = provider;
    this.providerSubject = providerSubject;
    this.emailAtProvider = emailAtProvider;
    this.displayNameAtProvider = displayNameAtProvider;
    this.avatarUrlAtProvider = avatarUrlAtProvider;
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

  public String provider() {
    return provider;
  }

  public String providerSubject() {
    return providerSubject;
  }

  public String emailAtProvider() {
    return emailAtProvider;
  }

  public String displayNameAtProvider() {
    return displayNameAtProvider;
  }

  public String avatarUrlAtProvider() {
    return avatarUrlAtProvider;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public OAuthAccount toDomain() {
    return new OAuthAccount(
        id,
        userId,
        OAuthProvider.valueOf(provider.toUpperCase(Locale.ROOT)),
        providerSubject,
        emailAtProvider,
        displayNameAtProvider,
        avatarUrlAtProvider,
        createdAt,
        updatedAt);
  }
}
