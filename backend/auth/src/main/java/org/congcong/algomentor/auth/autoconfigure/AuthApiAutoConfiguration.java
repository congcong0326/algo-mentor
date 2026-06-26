package org.congcong.algomentor.auth.autoconfigure;

import java.time.Clock;
import org.congcong.algomentor.auth.controller.CurrentUserController;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper;
import org.congcong.algomentor.auth.repository.mybatis.MyBatisAuthUserRepository;
import org.congcong.algomentor.auth.security.AuthenticatedOAuth2UserService;
import org.congcong.algomentor.auth.security.AuthenticatedOidcUserService;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.auth.security.SecurityContextCurrentUserIdProvider;
import org.congcong.algomentor.auth.service.OAuth2LoginUserService;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
  public CurrentUserController currentUserController(
      CurrentUserIdProvider currentUserIdProvider,
      ObjectProvider<ApiErrorResponseFactory> apiErrorResponseFactoryProvider
  ) {
    ApiErrorResponseFactory responseFactory = apiErrorResponseFactoryProvider.getIfAvailable();
    return responseFactory == null
        ? new CurrentUserController(currentUserIdProvider)
        : new CurrentUserController(currentUserIdProvider, responseFactory);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public AuthUserMapper authUserMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(AuthUserMapper.class);
  }

  @Bean
  @ConditionalOnBean(AuthUserMapper.class)
  @ConditionalOnMissingBean
  public AuthUserRepository authUserRepository(AuthUserMapper authUserMapper) {
    return new MyBatisAuthUserRepository(authUserMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public Clock authClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnBean(AuthUserRepository.class)
  @ConditionalOnMissingBean
  public OAuth2LoginUserService oAuth2LoginUserService(AuthUserRepository authUserRepository, Clock authClock) {
    return new OAuth2LoginUserService(authUserRepository, authClock);
  }

  @Bean
  @ConditionalOnBean(OAuth2LoginUserService.class)
  @ConditionalOnMissingBean
  public AuthenticatedOAuth2UserService authenticatedOAuth2UserService(
      OAuth2LoginUserService oAuth2LoginUserService
  ) {
    return new AuthenticatedOAuth2UserService(oAuth2LoginUserService);
  }

  @Bean
  @ConditionalOnBean(OAuth2LoginUserService.class)
  @ConditionalOnMissingBean
  public AuthenticatedOidcUserService authenticatedOidcUserService(
      OAuth2LoginUserService oAuth2LoginUserService
  ) {
    return new AuthenticatedOidcUserService(oAuth2LoginUserService);
  }
}
