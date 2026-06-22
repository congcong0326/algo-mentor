package org.congcong.algomentor.auth.autoconfigure;

import org.congcong.algomentor.auth.controller.CurrentUserController;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.auth.security.SecurityContextCurrentUserIdProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class AuthApiAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public CurrentUserIdProvider currentUserIdProvider() {
    return new SecurityContextCurrentUserIdProvider();
  }

  @Bean
  @ConditionalOnMissingBean
  public CurrentUserController currentUserController(CurrentUserIdProvider currentUserIdProvider) {
    return new CurrentUserController(currentUserIdProvider);
  }
}
