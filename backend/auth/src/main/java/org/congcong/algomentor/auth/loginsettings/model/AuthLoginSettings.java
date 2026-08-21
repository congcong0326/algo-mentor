package org.congcong.algomentor.auth.loginsettings.model;

import java.time.Instant;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.model.OAuthProvider;

/** 仅在创建新认证会话时生效的全局登录入口设置。 */
public record AuthLoginSettings(
    short id,
    boolean accountRegistrationEnabled,
    boolean passwordLoginEnabled,
    boolean passwordRegistrationEnabled,
    boolean googleLoginEnabled,
    boolean githubLoginEnabled,
    Long updatedBy,
    String updatedByDisplayName,
    Instant updatedAt
) {

  public static final short SINGLETON_ID = 1;

  public static AuthLoginSettings fromProperties(AuthProperties properties, Instant updatedAt) {
    return new AuthLoginSettings(
        SINGLETON_ID,
        properties.isAccountRegistrationEnabled(),
        properties.isPasswordLoginEnabled(),
        properties.isPasswordRegistrationEnabled(),
        true,
        true,
        null,
        null,
        updatedAt);
  }

  public boolean oauthLoginEnabled(OAuthProvider provider) {
    return switch (provider) {
      case GOOGLE -> googleLoginEnabled;
      case GITHUB -> githubLoginEnabled;
    };
  }
}
