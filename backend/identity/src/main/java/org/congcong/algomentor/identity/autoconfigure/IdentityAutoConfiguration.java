package org.congcong.algomentor.identity.autoconfigure;

import java.time.Clock;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.identity.controller.AdminUserController;
import org.congcong.algomentor.identity.controller.AdminUserExceptionHandler;
import org.congcong.algomentor.identity.event.IdentityEventPublisher;
import org.congcong.algomentor.identity.event.SpringIdentityEventPublisher;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.congcong.algomentor.identity.repository.mybatis.IdentityUserMapper;
import org.congcong.algomentor.identity.repository.mybatis.MyBatisIdentityUserRepository;
import org.congcong.algomentor.identity.service.IdentityUserService;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class IdentityAutoConfiguration {

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public IdentityUserMapper identityUserMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(IdentityUserMapper.class);
  }

  @Bean
  @ConditionalOnBean(IdentityUserMapper.class)
  @ConditionalOnMissingBean
  public IdentityUserRepository identityUserRepository(IdentityUserMapper identityUserMapper) {
    return new MyBatisIdentityUserRepository(identityUserMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public Clock identityClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean
  public IdentityEventPublisher identityEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    return new SpringIdentityEventPublisher(applicationEventPublisher);
  }

  @Bean
  @ConditionalOnBean(IdentityUserRepository.class)
  @ConditionalOnMissingBean
  public IdentityUserService identityUserService(
      IdentityUserRepository identityUserRepository,
      IdentityEventPublisher identityEventPublisher,
      Clock identityClock
  ) {
    return new IdentityUserService(identityUserRepository, identityEventPublisher, identityClock);
  }

  @Bean
  @ConditionalOnBean({IdentityUserService.class, IdentityUserRepository.class})
  @ConditionalOnMissingBean
  public AdminUserController adminUserController(
      IdentityUserService identityUserService,
      IdentityUserRepository identityUserRepository
  ) {
    return new AdminUserController(identityUserService, identityUserRepository);
  }

  @Bean
  @ConditionalOnBean(AdminUserController.class)
  @ConditionalOnMissingBean
  public AdminUserExceptionHandler adminUserExceptionHandler(
      ObjectProvider<ApiErrorResponseFactory> apiErrorResponseFactoryProvider
  ) {
    return new AdminUserExceptionHandler(apiErrorResponseFactoryProvider.getIfAvailable());
  }
}
