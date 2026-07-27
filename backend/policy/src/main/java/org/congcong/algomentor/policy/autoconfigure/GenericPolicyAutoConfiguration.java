package org.congcong.algomentor.policy.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.congcong.algomentor.cache.config.CacheAutoConfiguration;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.common.admin.audit.NoopAdminOperationAuditRecorder;
import org.congcong.algomentor.identity.autoconfigure.IdentityAutoConfiguration;
import org.congcong.algomentor.identity.group.relation.UserRelationProvider;
import org.congcong.algomentor.identity.group.repository.UserGroupRepository;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.congcong.algomentor.policy.cache.GenericPolicyCacheProperties;
import org.congcong.algomentor.policy.cache.GenericPolicyCompiler;
import org.congcong.algomentor.policy.cache.PolicySetCache;
import org.congcong.algomentor.policy.controller.GenericPolicyExceptionHandler;
import org.congcong.algomentor.policy.controller.admin.AdminGenericPolicyController;
import org.congcong.algomentor.policy.controller.admin.AdminGenericPolicyOrderController;
import org.congcong.algomentor.policy.controller.runtime.EffectiveGenericPolicyController;
import org.congcong.algomentor.policy.metrics.GenericPolicyMetrics;
import org.congcong.algomentor.policy.repository.GenericPolicyRepository;
import org.congcong.algomentor.policy.repository.mybatis.GenericPolicyMapper;
import org.congcong.algomentor.policy.repository.mybatis.MyBatisGenericPolicyRepository;
import org.congcong.algomentor.policy.service.DefaultEffectiveGenericPolicyQueryService;
import org.congcong.algomentor.policy.service.DefaultGenericPolicyQueryService;
import org.congcong.algomentor.policy.service.EffectiveGenericPolicyQueryService;
import org.congcong.algomentor.policy.service.GenericPolicyManagementService;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.congcong.algomentor.policy.type.GenericPolicyTypeRegistry;
import org.congcong.algomentor.policy.type.GenericPolicyTypeContributor;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 通用策略底座的持久化、缓存、类型注册和 HTTP 自动配置。 */
@AutoConfiguration(after = {CacheAutoConfiguration.class, IdentityAutoConfiguration.class})
@EnableConfigurationProperties(GenericPolicyCacheProperties.class)
public class GenericPolicyAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public GenericPolicyTypeRegistry genericPolicyTypeRegistry(
      ObjectProvider<GenericPolicyType<?>> policyTypes,
      ObjectProvider<GenericPolicyTypeContributor> contributors
  ) {
    java.util.List<GenericPolicyType<?>> allTypes = new java.util.ArrayList<>(policyTypes.orderedStream().toList());
    contributors.orderedStream().forEach(contributor -> {
      if (contributor.policyTypes() != null) {
        allTypes.addAll(contributor.policyTypes());
      }
    });
    return new GenericPolicyTypeRegistry(allTypes);
  }

  @Bean
  @ConditionalOnMissingBean
  public GenericPolicyMetrics genericPolicyMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
    return new GenericPolicyMetrics(meterRegistryProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnBean({SharedCacheRegionFactory.class, SharedCacheInvalidationCoordinator.class})
  @ConditionalOnMissingBean
  public PolicySetCache policySetCache(
      SharedCacheRegionFactory cacheFactory,
      SharedCacheInvalidationCoordinator invalidationCoordinator,
      GenericPolicyCacheProperties properties
  ) {
    return new PolicySetCache(cacheFactory, invalidationCoordinator, properties);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public GenericPolicyMapper genericPolicyMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(GenericPolicyMapper.class);
  }

  @Bean
  @ConditionalOnBean(GenericPolicyMapper.class)
  @ConditionalOnMissingBean
  public GenericPolicyRepository genericPolicyRepository(
      GenericPolicyMapper mapper,
      ObjectMapper objectMapper
  ) {
    return new MyBatisGenericPolicyRepository(mapper, objectMapper);
  }

  @Bean
  @ConditionalOnBean(GenericPolicyRepository.class)
  @ConditionalOnMissingBean
  public GenericPolicyCompiler genericPolicyCompiler(
      GenericPolicyRepository repository,
      GenericPolicyTypeRegistry typeRegistry,
      ObjectMapper objectMapper,
      GenericPolicyMetrics metrics
  ) {
    return new GenericPolicyCompiler(repository, typeRegistry, objectMapper, metrics);
  }

  @Bean
  @ConditionalOnBean({GenericPolicyRepository.class, PolicySetCache.class, IdentityUserRepository.class,
      UserGroupRepository.class})
  @ConditionalOnMissingBean
  public GenericPolicyManagementService genericPolicyManagementService(
      GenericPolicyRepository repository,
      GenericPolicyTypeRegistry typeRegistry,
      IdentityUserRepository identityUserRepository,
      UserGroupRepository userGroupRepository,
      PolicySetCache policySetCache,
      ObjectMapper objectMapper,
      ObjectProvider<AdminOperationAuditRecorder> auditRecorderProvider,
      GenericPolicyMetrics metrics,
      ObjectProvider<Clock> clockProvider
  ) {
    return new GenericPolicyManagementService(
        repository,
        typeRegistry,
        identityUserRepository,
        userGroupRepository,
        policySetCache,
        objectMapper,
        auditRecorderProvider.getIfAvailable(NoopAdminOperationAuditRecorder::new),
        metrics,
        clockProvider.getIfAvailable(Clock::systemUTC));
  }

  @Bean
  @ConditionalOnBean({PolicySetCache.class, GenericPolicyCompiler.class, UserRelationProvider.class})
  @ConditionalOnMissingBean
  public GenericPolicyQueryService genericPolicyQueryService(
      GenericPolicyTypeRegistry typeRegistry,
      PolicySetCache policySetCache,
      GenericPolicyCompiler compiler,
      UserRelationProvider userRelationProvider,
      GenericPolicyMetrics metrics
  ) {
    return new DefaultGenericPolicyQueryService(
        typeRegistry, policySetCache, compiler, userRelationProvider, metrics);
  }

  @Bean
  @ConditionalOnBean({PolicySetCache.class, GenericPolicyCompiler.class, UserRelationProvider.class})
  @ConditionalOnMissingBean
  public EffectiveGenericPolicyQueryService effectiveGenericPolicyQueryService(
      GenericPolicyTypeRegistry typeRegistry,
      PolicySetCache policySetCache,
      GenericPolicyCompiler compiler,
      UserRelationProvider userRelationProvider,
      GenericPolicyMetrics metrics
  ) {
    return new DefaultEffectiveGenericPolicyQueryService(
        typeRegistry, policySetCache, compiler, userRelationProvider, metrics);
  }

  @Bean
  @ConditionalOnBean(GenericPolicyManagementService.class)
  @ConditionalOnMissingBean
  public AdminGenericPolicyController adminGenericPolicyController(
      GenericPolicyManagementService managementService
  ) {
    return new AdminGenericPolicyController(managementService);
  }

  @Bean
  @ConditionalOnBean(GenericPolicyManagementService.class)
  @ConditionalOnMissingBean
  public AdminGenericPolicyOrderController adminGenericPolicyOrderController(
      GenericPolicyManagementService managementService
  ) {
    return new AdminGenericPolicyOrderController(managementService);
  }

  @Bean
  @ConditionalOnBean(EffectiveGenericPolicyQueryService.class)
  @ConditionalOnMissingBean
  public EffectiveGenericPolicyController effectiveGenericPolicyController(
      EffectiveGenericPolicyQueryService queryService,
      GenericPolicyTypeRegistry typeRegistry
  ) {
    return new EffectiveGenericPolicyController(queryService, typeRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  public GenericPolicyExceptionHandler genericPolicyExceptionHandler(
      ObjectProvider<org.congcong.algomentor.common.api.ApiErrorResponseFactory> responseFactoryProvider
  ) {
    return new GenericPolicyExceptionHandler(responseFactoryProvider.getIfAvailable());
  }
}
