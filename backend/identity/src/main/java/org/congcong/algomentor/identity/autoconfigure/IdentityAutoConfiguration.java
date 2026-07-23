package org.congcong.algomentor.identity.autoconfigure;

import java.time.Clock;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.common.admin.audit.NoopAdminOperationAuditRecorder;
import org.congcong.algomentor.identity.controller.AdminUserController;
import org.congcong.algomentor.identity.controller.AdminUserExceptionHandler;
import org.congcong.algomentor.identity.controller.group.AdminUserGroupController;
import org.congcong.algomentor.identity.controller.group.AdminUserGroupExceptionHandler;
import org.congcong.algomentor.identity.event.IdentityEventPublisher;
import org.congcong.algomentor.identity.event.SpringIdentityEventPublisher;
import org.congcong.algomentor.identity.group.repository.UserGroupRepository;
import org.congcong.algomentor.identity.group.repository.mybatis.MyBatisUserGroupRepository;
import org.congcong.algomentor.identity.group.repository.mybatis.UserGroupMapper;
import org.congcong.algomentor.identity.group.service.UserGroupService;
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
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public UserGroupMapper userGroupMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(UserGroupMapper.class);
  }

  @Bean
  @ConditionalOnBean(UserGroupMapper.class)
  @ConditionalOnMissingBean
  public UserGroupRepository userGroupRepository(UserGroupMapper userGroupMapper) {
    return new MyBatisUserGroupRepository(userGroupMapper);
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
  @ConditionalOnBean(UserGroupRepository.class)
  @ConditionalOnMissingBean
  public UserGroupService userGroupService(
      UserGroupRepository userGroupRepository,
      ObjectProvider<AdminOperationAuditRecorder> auditRecorderProvider,
      Clock identityClock
  ) {
    return new UserGroupService(
        userGroupRepository,
        auditRecorderProvider.getIfAvailable(NoopAdminOperationAuditRecorder::new),
        identityClock);
  }

  @Bean
  @ConditionalOnBean({IdentityUserService.class, IdentityUserRepository.class, UserGroupRepository.class})
  @ConditionalOnMissingBean
  public AdminUserController adminUserController(
      IdentityUserService identityUserService,
      IdentityUserRepository identityUserRepository,
      UserGroupRepository userGroupRepository,
      Clock identityClock
  ) {
    return new AdminUserController(
        identityUserService,
        identityUserRepository,
        userGroupRepository,
        identityClock);
  }

  @Bean
  @ConditionalOnBean(UserGroupService.class)
  @ConditionalOnMissingBean
  public AdminUserGroupController adminUserGroupController(UserGroupService userGroupService) {
    return new AdminUserGroupController(userGroupService);
  }

  @Bean
  @ConditionalOnBean(AdminUserController.class)
  @ConditionalOnMissingBean
  public AdminUserExceptionHandler adminUserExceptionHandler(
      ObjectProvider<ApiErrorResponseFactory> apiErrorResponseFactoryProvider
  ) {
    return new AdminUserExceptionHandler(apiErrorResponseFactoryProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnBean(AdminUserGroupController.class)
  @ConditionalOnMissingBean
  public AdminUserGroupExceptionHandler adminUserGroupExceptionHandler(
      ObjectProvider<ApiErrorResponseFactory> apiErrorResponseFactoryProvider
  ) {
    return new AdminUserGroupExceptionHandler(apiErrorResponseFactoryProvider.getIfAvailable());
  }
}
