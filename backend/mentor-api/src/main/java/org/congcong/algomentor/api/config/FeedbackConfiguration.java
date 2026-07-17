package org.congcong.algomentor.api.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.congcong.algomentor.api.feedback.metrics.FeedbackMetrics;
import org.congcong.algomentor.api.feedback.metrics.MicrometerFeedbackMetrics;
import org.congcong.algomentor.api.feedback.metrics.NoopFeedbackMetrics;
import org.congcong.algomentor.api.feedback.repository.FeedbackRepository;
import org.congcong.algomentor.api.feedback.repository.mybatis.FeedbackMapper;
import org.congcong.algomentor.api.feedback.repository.mybatis.MyBatisFeedbackRepository;
import org.congcong.algomentor.api.feedback.service.AdminFeedbackService;
import org.congcong.algomentor.api.feedback.service.FeedbackMutationExecutor;
import org.congcong.algomentor.api.feedback.service.FeedbackRunOwnershipVerifier;
import org.congcong.algomentor.api.feedback.service.UserFeedbackService;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.congcong.algomentor.api.admin.overview.AdminOverviewMetrics;
import org.congcong.algomentor.api.admin.overview.AdminOverviewService;
import org.congcong.algomentor.ai.governance.adminquery.AiAdminUsageQueryService;
import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimeAdminService;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessAdminService;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.datasource.url")
public class FeedbackConfiguration {
  @Bean @ConditionalOnMissingBean public FeedbackMapper feedbackMapper(SqlSessionTemplate template) { return template.getMapper(FeedbackMapper.class); }
  @Bean @ConditionalOnMissingBean public FeedbackRepository feedbackRepository(FeedbackMapper mapper) { return new MyBatisFeedbackRepository(mapper); }
  @Bean @ConditionalOnMissingBean public FeedbackMutationExecutor feedbackMutationExecutor(PlatformTransactionManager transactionManager) { return new FeedbackMutationExecutor(new TransactionTemplate(transactionManager)); }
  @Bean @ConditionalOnMissingBean public FeedbackRunOwnershipVerifier feedbackRunOwnershipVerifier() { return (runId, userId) -> false; }
  @Bean @ConditionalOnMissingBean public FeedbackMetrics feedbackMetrics(ObjectProvider<MeterRegistry> registry) { return registry.getIfAvailable() == null ? new NoopFeedbackMetrics() : new MicrometerFeedbackMetrics(registry.getIfAvailable()); }
  @Bean @ConditionalOnMissingBean public UserFeedbackService userFeedbackService(FeedbackRepository repository, FeedbackMutationExecutor executor, FeedbackRunOwnershipVerifier verifier, FeedbackMetrics metrics, ObjectProvider<Clock> clock) { return new UserFeedbackService(repository, executor, verifier, metrics, clock.getIfAvailable(Clock::systemUTC)); }
  @Bean @ConditionalOnMissingBean public AdminFeedbackService adminFeedbackService(FeedbackRepository repository, FeedbackMutationExecutor executor, IdentityUserRepository users, FeedbackMetrics metrics, ObjectProvider<Clock> clock) { return new AdminFeedbackService(repository, executor, users, metrics, clock.getIfAvailable(Clock::systemUTC)); }
  @Bean @ConditionalOnMissingBean public AdminOverviewMetrics adminOverviewMetrics(ObjectProvider<MeterRegistry> registry) { return new AdminOverviewMetrics(registry.getIfAvailable()); }
  @Bean @ConditionalOnMissingBean public AdminOverviewService adminOverviewService(BetaAccessAdminService betaAccess, AiRuntimeAdminService aiRuntime, AiAdminUsageQueryService aiUsage, AdminFeedbackService feedback, AiPurposePolicyResolver policyResolver, AiGovernanceProperties properties, ObjectProvider<Clock> clock, AdminOverviewMetrics metrics) { return new AdminOverviewService(betaAccess, aiRuntime, aiUsage, feedback, policyResolver, properties, clock.getIfAvailable(Clock::systemUTC), metrics); }
}
