package org.congcong.algomentor.auth.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * 仅在已配置 Google OAuth2 client ID 时启用客户端注册。
 */
final class GoogleOAuth2ClientConfiguredCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return StringUtils.hasText(context.getEnvironment().getProperty(
        AuthConfigurationKeys.GOOGLE_OAUTH2_CLIENT_ID_ENV));
  }
}
