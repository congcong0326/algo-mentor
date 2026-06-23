package org.congcong.algomentor.ai.governance.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runlock.InMemoryAgentRunLockManager;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.metrics.AiRunGovernanceObserver;
import org.congcong.algomentor.ai.governance.metrics.AiRunMetricsObserver;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiDailyUsageMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiRunAdmissionMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiRunAdmissionRow;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiRunStatusUpdate;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiGovernanceAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(AiGovernanceAutoConfiguration.class));

  @Test
  void loadsWithoutDataSourceAndExposesProperties() {
    contextRunner
        .withPropertyValues("algo-mentor.ai-governance.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(AiGovernanceProperties.class);
          assertThat(context).hasSingleBean(AiPurposePolicyResolver.class);
        });
  }

  @Test
  void configuresGovernanceServicesWhenMyBatisAndLockBeansAreAvailable() {
    contextRunner
        .withBean(AiDailyUsageMapper.class, FakeAiDailyUsageMapper::new)
        .withBean(AiRunAdmissionMapper.class, FakeAiRunAdmissionMapper::new)
        .withBean(AgentRunLockManager.class, InMemoryAgentRunLockManager::new)
        .withBean(AgentRunLockOwnerProvider.class, () -> () -> "node-1")
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(context -> {
          assertThat(context).hasSingleBean(AiDailyUsageStore.class);
          assertThat(context).hasSingleBean(AiRunAdmissionService.class);
          assertThat(context).hasSingleBean(AiRunLifecycleService.class);
          assertThat(context).hasSingleBean(AiRunGovernanceObserver.class);
          assertThat(context).hasSingleBean(AiRunMetricsObserver.class);
        });
  }

  private static final class FakeAiDailyUsageMapper implements AiDailyUsageMapper {

    @Override
    public int insertIfAbsent(long userId, LocalDate quotaDate, String scope, long limitCount) {
      return 1;
    }

    @Override
    public int incrementRequestIfWithinLimit(long userId, LocalDate quotaDate, String scope, long limitCount) {
      return 1;
    }

    @Override
    public int addUsage(long userId, LocalDate quotaDate, String scope, AiUsage usage) {
      return 1;
    }
  }

  private static final class FakeAiRunAdmissionMapper implements AiRunAdmissionMapper {

    @Override
    public long insertAdmission(AiRunAdmissionRow row) {
      return 1;
    }

    @Override
    public int updateStatus(AiRunStatusUpdate update) {
      return 1;
    }

    @Override
    public AiRunAdmissionRow findByRunId(String runId) {
      return null;
    }
  }
}
