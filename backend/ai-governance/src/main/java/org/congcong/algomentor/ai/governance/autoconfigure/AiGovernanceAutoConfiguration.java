package org.congcong.algomentor.ai.governance.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockOwnerProvider;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.metrics.AiRunGovernanceObserver;
import org.congcong.algomentor.ai.governance.metrics.AiRunMetricsObserver;
import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiDailyUsageMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiRunAdmissionMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.PostgresAiRunAdmissionRepository;
import org.congcong.algomentor.ai.governance.runlock.AiRunLockService;
import org.congcong.algomentor.ai.governance.trace.AiTraceAccessPolicy;
import org.congcong.algomentor.ai.governance.trace.AiTraceRedactionPolicy;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.congcong.algomentor.ai.governance.usage.PostgresAiDailyUsageStore;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(AiGovernanceProperties.class)
public class AiGovernanceAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AiPurposePolicyResolver aiPurposePolicyResolver(AiGovernanceProperties properties) {
    return new AiPurposePolicyResolver(properties);
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
  @ConditionalOnBean(AiDailyUsageMapper.class)
  @ConditionalOnMissingBean
  public AiDailyUsageStore aiDailyUsageStore(AiDailyUsageMapper mapper) {
    return new PostgresAiDailyUsageStore(mapper);
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
      PostgresAiRunAdmissionRepository admissionRepository) {
    return new AiRunAdmissionService(properties, policyResolver, usageStore, runLockService, admissionRepository);
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
