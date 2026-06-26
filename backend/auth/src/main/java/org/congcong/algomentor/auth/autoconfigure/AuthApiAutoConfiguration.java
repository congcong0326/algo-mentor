package org.congcong.algomentor.auth.autoconfigure;

import java.time.Clock;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.controller.CurrentUserController;
import org.congcong.algomentor.auth.controller.PasswordAuthController;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper;
import org.congcong.algomentor.auth.repository.mybatis.MyBatisAuthUserRepository;
import org.congcong.algomentor.auth.security.AuthenticatedDaoAuthenticationProvider;
import org.congcong.algomentor.auth.security.AuthenticatedOAuth2UserService;
import org.congcong.algomentor.auth.security.AuthenticatedOidcUserService;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.auth.security.PasswordUserDetailsService;
import org.congcong.algomentor.auth.security.SecurityContextCurrentUserIdProvider;
import org.congcong.algomentor.auth.service.AdminEmailRoleService;
import org.congcong.algomentor.auth.service.AuthPermissionService;
import org.congcong.algomentor.auth.service.OAuth2LoginUserService;
import org.congcong.algomentor.auth.service.PasswordUserService;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@AutoConfiguration
@EnableConfigurationProperties(AuthProperties.class)
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
      ObjectProvider<ApiErrorResponseFactory> apiErrorResponseFactoryProvider,
      AuthPermissionService authPermissionService
  ) {
    ApiErrorResponseFactory responseFactory = apiErrorResponseFactoryProvider.getIfAvailable();
    return responseFactory == null
        ? new CurrentUserController(currentUserIdProvider)
        : new CurrentUserController(currentUserIdProvider, responseFactory, authPermissionService);
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
  @ConditionalOnMissingBean
  public AuthPermissionService authPermissionService() {
    return new AuthPermissionService();
  }

  @Bean
  @ConditionalOnBean(AuthUserRepository.class)
  @ConditionalOnMissingBean
  public AdminEmailRoleService adminEmailRoleService(
      AuthUserRepository authUserRepository,
      AuthProperties authProperties
  ) {
    return new AdminEmailRoleService(authUserRepository, authProperties.getAdminEmails());
  }

  @Bean
  @ConditionalOnBean(AuthUserRepository.class)
  @ConditionalOnMissingBean
  public PasswordUserDetailsService passwordUserDetailsService(
      AuthUserRepository authUserRepository,
      Clock authClock,
      ObjectProvider<AdminEmailRoleService> adminEmailRoleServiceProvider
  ) {
    return new PasswordUserDetailsService(
        authUserRepository,
        authClock,
        adminEmailRoleServiceProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnBean(AuthUserRepository.class)
  @ConditionalOnMissingBean
  public PasswordUserService passwordUserService(
      AuthUserRepository authUserRepository,
      PasswordEncoder passwordEncoder,
      Clock authClock,
      ObjectProvider<AdminEmailRoleService> adminEmailRoleServiceProvider
  ) {
    return new PasswordUserService(
        authUserRepository,
        passwordEncoder,
        authClock,
        adminEmailRoleServiceProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnBean(PasswordUserDetailsService.class)
  @ConditionalOnMissingBean
  public AuthenticationManager passwordAuthenticationManager(
      PasswordUserDetailsService passwordUserDetailsService,
      PasswordEncoder passwordEncoder
  ) {
    return new ProviderManager(new AuthenticatedDaoAuthenticationProvider(passwordEncoder, passwordUserDetailsService));
  }

  @Bean
  @ConditionalOnMissingBean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  @ConditionalOnBean(AuthUserRepository.class)
  @ConditionalOnMissingBean
  public OAuth2LoginUserService oAuth2LoginUserService(
      AuthUserRepository authUserRepository,
      Clock authClock,
      ObjectProvider<AdminEmailRoleService> adminEmailRoleServiceProvider
  ) {
    return new OAuth2LoginUserService(
        authUserRepository,
        authClock,
        adminEmailRoleServiceProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnMissingBean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  @ConditionalOnBean({PasswordUserService.class, AuthenticationManager.class, SecurityContextRepository.class})
  @ConditionalOnMissingBean
  public PasswordAuthController passwordAuthController(
      PasswordUserService passwordUserService,
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      AuthPermissionService authPermissionService,
      ObjectProvider<ApiErrorResponseFactory> apiErrorResponseFactoryProvider
  ) {
    ApiErrorResponseFactory responseFactory = apiErrorResponseFactoryProvider.getIfAvailable();
    return responseFactory == null
        ? new PasswordAuthController(
            passwordUserService,
            authenticationManager,
            securityContextRepository,
            authPermissionService)
        : new PasswordAuthController(
            passwordUserService,
            authenticationManager,
            securityContextRepository,
            responseFactory,
            authPermissionService);
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
