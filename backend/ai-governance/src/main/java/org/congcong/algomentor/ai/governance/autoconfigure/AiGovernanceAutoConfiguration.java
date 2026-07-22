package org.congcong.algomentor.ai.governance.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Clock;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockOwnerProvider;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallAccountingService;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallContextResolver;
import org.congcong.algomentor.ai.governance.adminquery.AiAdminUsageQueryService;
import org.congcong.algomentor.ai.governance.metrics.AiRunGovernanceObserver;
import org.congcong.algomentor.ai.governance.metrics.AiRunMetricsObserver;
import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimeAdminService;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimeCache;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimeCacheProperties;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimePolicyService;
import org.congcong.algomentor.ai.governance.pricing.AiCostCalculator;
import org.congcong.algomentor.ai.governance.pricing.AiModelPriceAdminService;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiAdminUsageMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiDailyUsageMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiLlmCallUsageMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiModelPriceMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiRuntimeSettingsMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiUserPolicyMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiRunAdmissionMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.PostgresAiRunAdmissionRepository;
import org.congcong.algomentor.ai.governance.runlock.AiRunLockService;
import org.congcong.algomentor.ai.governance.trace.AiTraceAccessPolicy;
import org.congcong.algomentor.ai.governance.trace.AiTraceRedactionPolicy;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.congcong.algomentor.ai.governance.usage.PostgresAiDailyUsageStore;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.common.admin.audit.NoopAdminOperationAuditRecorder;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.config.CacheAutoConfiguration;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.identity.autoconfigure.IdentityAutoConfiguration;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = {CacheAutoConfiguration.class, IdentityAutoConfiguration.class})
@EnableConfigurationProperties({AiGovernanceProperties.class, AiRuntimeCacheProperties.class})
public class AiGovernanceAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AiPurposePolicyResolver aiPurposePolicyResolver(AiGovernanceProperties properties) {
    return new AiPurposePolicyResolver(properties);
  }

  @Bean
  @ConditionalOnBean({SharedCacheRegionFactory.class, SharedCacheInvalidationCoordinator.class})
  @ConditionalOnMissingBean
  public AiRuntimeCache aiRuntimeCache(
      SharedCacheRegionFactory cacheFactory,
      SharedCacheInvalidationCoordinator invalidationCoordinator,
      AiRuntimeCacheProperties properties) {
    return new AiRuntimeCache(cacheFactory, invalidationCoordinator, properties);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public AiDailyUsageMapper aiDailyUsageMapper(SqlSessionTemplate template) {
    return template.getMapper(AiDailyUsageMapper.class);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public AiRunAdmissionMapper aiRunAdmissionMapper(SqlSessionTemplate template) {
    return template.getMapper(AiRunAdmissionMapper.class);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public AiRuntimeSettingsMapper aiRuntimeSettingsMapper(SqlSessionTemplate template) {
    return template.getMapper(AiRuntimeSettingsMapper.class);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public AiUserPolicyMapper aiUserPolicyMapper(SqlSessionTemplate template) {
    return template.getMapper(AiUserPolicyMapper.class);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public AiLlmCallUsageMapper aiLlmCallUsageMapper(SqlSessionTemplate template) {
    return template.getMapper(AiLlmCallUsageMapper.class);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public AiModelPriceMapper aiModelPriceMapper(SqlSessionTemplate template) {
    return template.getMapper(AiModelPriceMapper.class);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public AiAdminUsageMapper aiAdminUsageMapper(SqlSessionTemplate template) {
    return template.getMapper(AiAdminUsageMapper.class);
  }

  @Bean
  @ConditionalOnBean({AiRuntimeSettingsMapper.class, AiUserPolicyMapper.class})
  @ConditionalOnMissingBean
  public AiRuntimePolicyService aiRuntimePolicyService(
      AiGovernanceProperties properties,
      AiRuntimeSettingsMapper settingsMapper,
      AiUserPolicyMapper userPolicyMapper,
      ObjectProvider<AiRuntimeCache> runtimeCacheProvider) {
    return new AiRuntimePolicyService(
        properties, settingsMapper, userPolicyMapper, runtimeCacheProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnBean({AiRuntimePolicyService.class, AiRuntimeSettingsMapper.class, AiUserPolicyMapper.class,
      IdentityUserRepository.class})
  @ConditionalOnMissingBean
  public AiRuntimeAdminService aiRuntimeAdminService(
      AiRuntimePolicyService runtimePolicyService,
      AiRuntimeSettingsMapper settingsMapper,
      AiUserPolicyMapper userPolicyMapper,
      IdentityUserRepository identityUserRepository,
      ObjectProvider<AdminOperationAuditRecorder> auditRecorderProvider,
      ObjectProvider<Clock> clockProvider,
      ObjectProvider<AiRuntimeCache> runtimeCacheProvider) {
    return new AiRuntimeAdminService(
        runtimePolicyService,
        settingsMapper,
        userPolicyMapper,
        identityUserRepository,
        auditRecorderProvider.getIfAvailable(NoopAdminOperationAuditRecorder::new),
        clockProvider.getIfAvailable(Clock::systemUTC),
        runtimeCacheProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnBean(AiDailyUsageMapper.class)
  @ConditionalOnMissingBean
  public AiDailyUsageStore aiDailyUsageStore(AiDailyUsageMapper mapper) {
    return new PostgresAiDailyUsageStore(mapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public AiLlmCallContextResolver aiLlmCallContextResolver() {
    return new AiLlmCallContextResolver();
  }

  @Bean
  @ConditionalOnBean({AiLlmCallUsageMapper.class, AiDailyUsageStore.class})
  @ConditionalOnMissingBean
  public AiLlmCallAccountingService aiLlmCallAccountingService(
      AiLlmCallUsageMapper mapper,
      AiDailyUsageStore dailyUsageStore,
      AiLlmCallContextResolver contextResolver,
      AiGovernanceProperties properties,
      ObjectProvider<Clock> clockProvider,
      ObjectProvider<MeterRegistry> meterRegistryProvider) {
    return new AiLlmCallAccountingService(
        mapper,
        dailyUsageStore,
        contextResolver,
        clockProvider.getIfAvailable(Clock::systemUTC),
        properties.getQuotaZone(),
        meterRegistryProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnMissingBean
  public AiCostCalculator aiCostCalculator() {
    return new AiCostCalculator();
  }

  @Bean
  @ConditionalOnBean(AiModelPriceMapper.class)
  @ConditionalOnMissingBean
  public AiModelPriceAdminService aiModelPriceAdminService(
      AiModelPriceMapper mapper,
      ObjectProvider<AdminOperationAuditRecorder> auditRecorderProvider,
      ObjectProvider<Clock> clockProvider) {
    return new AiModelPriceAdminService(
        mapper,
        auditRecorderProvider.getIfAvailable(NoopAdminOperationAuditRecorder::new),
        clockProvider.getIfAvailable(Clock::systemUTC));
  }

  @Bean
  @ConditionalOnBean({AiAdminUsageMapper.class, AiDailyUsageMapper.class, AiRuntimePolicyService.class,
      IdentityUserRepository.class})
  @ConditionalOnMissingBean
  public AiAdminUsageQueryService aiAdminUsageQueryService(
      AiAdminUsageMapper usageMapper,
      AiDailyUsageMapper dailyUsageMapper,
      AiCostCalculator costCalculator,
      AiRuntimePolicyService runtimePolicyService,
      AiPurposePolicyResolver policyResolver,
      IdentityUserRepository identityUserRepository) {
    return new AiAdminUsageQueryService(
        usageMapper,
        dailyUsageMapper,
        costCalculator,
        runtimePolicyService,
        policyResolver,
        identityUserRepository);
  }

  @Bean
  @ConditionalOnBean(AiRunAdmissionMapper.class)
  @ConditionalOnMissingBean
  public PostgresAiRunAdmissionRepository postgresAiRunAdmissionRepository(AiRunAdmissionMapper mapper) {
    return new PostgresAiRunAdmissionRepository(mapper);
  }

  @Bean
  @ConditionalOnBean({AgentRunLockManager.class, AgentRunLockOwnerProvider.class})
  @ConditionalOnMissingBean
  public AiRunLockService aiRunLockService(
      AgentRunLockManager lockManager,
      AgentRunLockOwnerProvider ownerProvider,
      AiGovernanceProperties properties) {
    Duration ttl = properties.getActiveRunTtl();
    return new AiRunLockService(lockManager, ownerProvider, ttl);
  }

  @Bean
  @ConditionalOnBean({AiDailyUsageStore.class, AiRunLockService.class, PostgresAiRunAdmissionRepository.class})
  @ConditionalOnMissingBean
  public AiRunAdmissionService aiRunAdmissionService(
      AiGovernanceProperties properties,
      AiPurposePolicyResolver policyResolver,
      AiDailyUsageStore usageStore,
      AiRunLockService runLockService,
      PostgresAiRunAdmissionRepository admissionRepository,
      ObjectProvider<AiRuntimePolicyService> runtimePolicyServiceProvider) {
    return new AiRunAdmissionService(
        properties,
        policyResolver,
        usageStore,
        runLockService,
        admissionRepository,
        runtimePolicyServiceProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnBean({PostgresAiRunAdmissionRepository.class, AiDailyUsageStore.class, AiRunLockService.class})
  @ConditionalOnMissingBean
  public AiRunLifecycleService aiRunLifecycleService(
      AiGovernanceProperties properties,
      PostgresAiRunAdmissionRepository admissionRepository,
      AiDailyUsageStore usageStore,
      AiRunLockService runLockService) {
    return new AiRunLifecycleService(properties, admissionRepository, usageStore, runLockService);
  }

  @Bean
  @ConditionalOnBean(AiRunLifecycleService.class)
  @ConditionalOnMissingBean
  public AiRunGovernanceObserver aiRunGovernanceObserver(AiRunLifecycleService lifecycleService) {
    return new AiRunGovernanceObserver(lifecycleService);
  }

  @Bean
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnMissingBean
  public AiRunMetricsObserver aiRunMetricsObserver(MeterRegistry registry) {
    return new AiRunMetricsObserver(registry);
  }

  @Bean
  @ConditionalOnMissingBean
  public AiTraceAccessPolicy aiTraceAccessPolicy() {
    return new AiTraceAccessPolicy();
  }

  @Bean
  @ConditionalOnMissingBean
  public AiTraceRedactionPolicy aiTraceRedactionPolicy() {
    return new AiTraceRedactionPolicy();
  }
}
