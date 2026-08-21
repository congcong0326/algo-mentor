package org.congcong.algomentor.auth.loginsettings.service;

import java.time.Instant;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.loginsettings.model.AuthLoginSettings;
import org.congcong.algomentor.auth.model.OAuthProvider;

/** 登录和注册入口读取的运行时设置快照来源。 */
@FunctionalInterface
public interface AuthLoginSettingsProvider {

  AuthLoginSettings current();

  default boolean oauthLoginEnabled(OAuthProvider provider) {
    return current().oauthLoginEnabled(provider);
  }

  static AuthLoginSettingsProvider fromProperties(AuthProperties properties) {
    return () -> AuthLoginSettings.fromProperties(properties, Instant.EPOCH);
  }
}
