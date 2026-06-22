package org.congcong.algomentor.auth.model;

/**
 * 第三方 OAuth2/OIDC 账号提供方标识。
 */
public enum OAuthProvider {
  GOOGLE("google");

  private final String value;

  OAuthProvider(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
