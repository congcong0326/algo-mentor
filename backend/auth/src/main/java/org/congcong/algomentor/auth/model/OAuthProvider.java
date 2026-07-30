package org.congcong.algomentor.auth.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * 第三方 OAuth2/OIDC 账号提供方标识。
 */
public enum OAuthProvider {
  GOOGLE("google", "sub", "email", "name", "picture", null),
  GITHUB("github", "id", "email", "name", "avatar_url", "login");

  private final String value;
  private final String subjectAttribute;
  private final String emailAttribute;
  private final String displayNameAttribute;
  private final String avatarUrlAttribute;
  private final String fallbackDisplayNameAttribute;

  OAuthProvider(
      String value,
      String subjectAttribute,
      String emailAttribute,
      String displayNameAttribute,
      String avatarUrlAttribute,
      String fallbackDisplayNameAttribute
  ) {
    this.value = value;
    this.subjectAttribute = subjectAttribute;
    this.emailAttribute = emailAttribute;
    this.displayNameAttribute = displayNameAttribute;
    this.avatarUrlAttribute = avatarUrlAttribute;
    this.fallbackDisplayNameAttribute = fallbackDisplayNameAttribute;
  }

  public String value() {
    return value;
  }

  public String subjectAttribute() {
    return subjectAttribute;
  }

  public String emailAttribute() {
    return emailAttribute;
  }

  public String displayNameAttribute() {
    return displayNameAttribute;
  }

  public String avatarUrlAttribute() {
    return avatarUrlAttribute;
  }

  public String fallbackDisplayNameAttribute() {
    return fallbackDisplayNameAttribute;
  }

  public static Optional<OAuthProvider> fromRegistrationId(String registrationId) {
    return Arrays.stream(values())
        .filter(provider -> provider.value.equals(registrationId))
        .findFirst();
  }
}
