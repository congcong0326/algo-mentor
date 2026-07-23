package org.congcong.algomentor.identity.autoconfigure;

import java.time.Clock;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.common.admin.audit.NoopAdminOperationAuditRecorder;
import org.congcong.algomentor.cache.config.CacheAutoConfiguration;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.identity.controller.AdminUserController;
import org.congcong.algomentor.identity.controller.AdminUserExceptionHandler;
import org.congcong.algomentor.identity.controller.group.AdminUserGroupController;
import org.congcong.algomentor.identity.controller.group.AdminUserGroupExceptionHandler;
import org.congcong.algomentor.identity.event.IdentityEventPublisher;
import org.congcong.algomentor.identity.event.SpringIdentityEventPublisher;
import org.congcong.algomentor.identity.group.repository.UserGroupRepository;
import org.congcong.algomentor.identity.group.repository.mybatis.MyBatisUserGroupRepository;
import org.congcong.algomentor.identity.group.repository.mybatis.UserGroupMapper;
import org.congcong.algomentor.identity.group.relation.CachedUserRelationProvider;
import org.congcong.algomentor.identity.group.relation.NoopUserRelationCacheInvalidator;
import org.congcong.algomentor.identity.group.relation.UserRelationCache;
import org.congcong.algomentor.identity.group.relation.UserRelationCacheInvalidator;
import org.congcong.algomentor.identity.group.relation.UserRelationCacheProperties;
import org.congcong.algomentor.identity.group.relation.UserRelationProvider;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = CacheAutoConfiguration.class)
@EnableConfigurationProperties(UserRelationCacheProperties.class)
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
  @ConditionalOnBean({SharedCacheRegionFactory.class, SharedCacheInvalidationCoordinator.class})
  @ConditionalOnMissingBean
  public UserRelationCache userRelationCache(
      SharedCacheRegionFactory cacheFactory,
      SharedCacheInvalidationCoordinator invalidationCoordinator,
      UserRelationCacheProperties properties
  ) {
    return new UserRelationCache(cacheFactory, invalidationCoordinator, properties);
  }

  @Bean
  @ConditionalOnBean({IdentityUserRepository.class, UserGroupRepository.class, UserRelationCache.class})
  @ConditionalOnMissingBean
  public UserRelationProvider userRelationProvider(
      IdentityUserRepository identityUserRepository,
      UserGroupRepository userGroupRepository,
      UserRelationCache userRelationCache,
      Clock identityClock
  ) {
    return new CachedUserRelationProvider(
        identityUserRepository, userGroupRepository, userRelationCache, identityClock);
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
      ObjectProvider<UserRelationCacheInvalidator> relationCacheInvalidatorProvider,
      Clock identityClock
  ) {
    return new UserGroupService(
        userGroupRepository,
        auditRecorderProvider.getIfAvailable(NoopAdminOperationAuditRecorder::new),
        relationCacheInvalidatorProvider.getIfAvailable(NoopUserRelationCacheInvalidator::new),
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
