package org.congcong.algomentor.auth.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/** 只要配置了任一受支持 OAuth2 provider 的 client ID，就启用客户端注册。 */
final class OAuth2ClientConfiguredCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return StringUtils.hasText(context.getEnvironment().getProperty(
        AuthConfigurationKeys.GOOGLE_OAUTH2_CLIENT_ID_ENV))
        || StringUtils.hasText(context.getEnvironment().getProperty(
            AuthConfigurationKeys.GITHUB_OAUTH2_CLIENT_ID_ENV));
  }
}
