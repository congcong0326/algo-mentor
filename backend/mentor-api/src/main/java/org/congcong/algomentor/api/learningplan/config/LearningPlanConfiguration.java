package org.congcong.algomentor.api.learningplan.config;

import java.time.Clock;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.api.learningplan.repository.UnavailableLearningPlanRepository;
import org.congcong.algomentor.api.learningplan.cleanup.LearningPlanDraftCleanupMetrics;
import org.congcong.algomentor.api.learningplan.cleanup.LearningPlanDraftCleanupScheduler;
import org.congcong.algomentor.api.learningplan.personalization.ApiLearningPlanPersonalizationDataProvider;
import org.congcong.algomentor.api.learningplan.policy.LearningPlanCreationPolicyContentValidator;
import org.congcong.algomentor.api.learningplan.policy.PolicyBackedLearningPlanCreationPolicyResolver;
import org.congcong.algomentor.api.learningplan.policy.LearningPlanAiRevisionPolicyContentValidator;
import org.congcong.algomentor.api.learningplan.policy.PolicyBackedLearningPlanAiRevisionPolicyResolver;
import org.congcong.algomentor.api.ability.service.AbilityProfileService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanAgentService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanActivationRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanActivationService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanService;
import org.congcong.algomentor.mentor.application.learningplan.cleanup.LearningPlanDraftCleanupService;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationContextService;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationDataProvider;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationMetrics;
import org.congcong.algomentor.mentor.application.learningplan.personalization.MicrometerLearningPlanPersonalizationMetrics;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackService;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicy;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyConstants;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyResolver;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionPolicy;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionPolicyConstants;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionPolicyResolver;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionAccessService;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionAccessMetrics;
import org.congcong.algomentor.mentor.application.learningplan.policy.MicrometerLearningPlanAiRevisionAccessMetrics;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionApplyService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionValidator;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalPromptBuilder;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionStreamService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionAgentDefinition;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.CompileLearningPlanRevisionAgentTool;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionBaselineResolver;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionCanonicalRestorer;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionModelViewProjector;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.QueryLearningPlanRevisionAgentTool;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanExtensionAgentDefinition;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanExtensionProposalStreamService;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftAgentDefinition;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftPromptBuilder;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStreamService;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateDraftService;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimQueryService;
import org.congcong.algomentor.mentor.application.review.card.ReviewQueueService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionOperations;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.congcong.algomentor.policy.type.GenericPolicyTypeExposure;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LearningPlanGovernanceProperties.class)
public class LearningPlanConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public Clock learningPlanClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanDraftValidator learningPlanDraftValidator() {
    return new LearningPlanDraftValidator();
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanLoadService learningPlanLoadService(Clock learningPlanClock) {
    return new LearningPlanLoadService(learningPlanClock);
  }

  @Bean("learningPlanCreationPolicyType")
  @ConditionalOnMissingBean(name = "learningPlanCreationPolicyType")
  public GenericPolicyType<LearningPlanCreationPolicy> learningPlanCreationPolicyType() {
    return GenericPolicyType.of(
        LearningPlanCreationPolicyConstants.TYPE_CODE,
        LearningPlanCreationPolicy.class,
        LearningPlanCreationPolicyContentValidator::validate,
        GenericPolicyTypeExposure.INTERNAL_ONLY);
  }

  @Bean("learningPlanAiRevisionPolicyType")
  @ConditionalOnMissingBean(name = "learningPlanAiRevisionPolicyType")
  public GenericPolicyType<LearningPlanAiRevisionPolicy> learningPlanAiRevisionPolicyType() {
    return GenericPolicyType.of(
        LearningPlanAiRevisionPolicyConstants.TYPE_CODE,
        LearningPlanAiRevisionPolicy.class,
        LearningPlanAiRevisionPolicyContentValidator::validate,
        GenericPolicyTypeExposure.INTERNAL_ONLY);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanAiRevisionPolicyResolver learningPlanAiRevisionPolicyResolver(
      ObjectProvider<GenericPolicyQueryService> queryServiceProvider,
      @Qualifier("learningPlanAiRevisionPolicyType") GenericPolicyType<LearningPlanAiRevisionPolicy> policyType) {
    GenericPolicyQueryService queryService = queryServiceProvider.getIfAvailable();
    return queryService == null
        ? LearningPlanAiRevisionPolicyResolver.defaults()
        : new PolicyBackedLearningPlanAiRevisionPolicyResolver(queryService, policyType);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanAiRevisionAccessService learningPlanAiRevisionAccessService(
      LearningPlanAiRevisionPolicyResolver resolver,
      LearningPlanAiRevisionAccessMetrics metrics) {
    return new LearningPlanAiRevisionAccessService(resolver, metrics);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanAiRevisionAccessMetrics learningPlanAiRevisionAccessMetrics(
      ObjectProvider<MeterRegistry> meterRegistryProvider) {
    MeterRegistry registry = meterRegistryProvider.getIfAvailable();
    return registry == null
        ? LearningPlanAiRevisionAccessMetrics.NOOP
        : new MicrometerLearningPlanAiRevisionAccessMetrics(registry);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanCreationPolicyResolver learningPlanCreationPolicyResolver(
      ObjectProvider<GenericPolicyQueryService> queryServiceProvider,
      @Qualifier("learningPlanCreationPolicyType") GenericPolicyType<LearningPlanCreationPolicy> policyType
  ) {
    GenericPolicyQueryService queryService = queryServiceProvider.getIfAvailable();
    return queryService == null
        ? LearningPlanCreationPolicyResolver.defaults()
        : new PolicyBackedLearningPlanCreationPolicyResolver(queryService, policyType);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanCreationPolicyService learningPlanCreationPolicyService(
      LearningPlanCreationPolicyResolver resolver,
      LearningPlanGovernanceProperties properties
  ) {
    return new LearningPlanCreationPolicyService(resolver, properties.quotaZoneId());
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanContractService learningPlanContractService(
      Clock learningPlanClock,
      LearningPlanLoadService loadService) {
    return new LearningPlanContractService(learningPlanClock, loadService);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanAgentService learningPlanAgentService(LearningPlanProblemCatalog problemCatalog) {
    return new LearningPlanAgentService(problemCatalog);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanDraftService learningPlanDraftService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanRepository planRepository,
      LearningPlanAgentService agentService,
      LearningPlanDraftValidator validator,
      LearningPlanLoadService loadService,
      LearningPlanCreationPolicyService creationPolicyService,
      Clock learningPlanClock) {
    return new LearningPlanDraftService(
        draftRepository,
        planRepository,
        agentService,
        validator,
        loadService,
        creationPolicyService,
        learningPlanClock);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanDraftPromptBuilder learningPlanDraftPromptBuilder(
      LearningPlanLoadService loadService,
      ObjectProvider<ManagedSystemPromptResolver> systemPromptResolver
  ) {
    return new LearningPlanDraftPromptBuilder(loadService, systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver));
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanDraftAgentDefinition learningPlanDraftAgentDefinition(
      LearningPlanDraftPromptBuilder promptBuilder
  ) {
    return new LearningPlanDraftAgentDefinition(promptBuilder);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanPersonalizationDataProvider learningPlanPersonalizationDataProvider(
      ObjectProvider<LearnerMemoryClaimQueryService> claimQueryService,
      ObjectProvider<AbilityProfileService> abilityProfileService,
      ObjectProvider<LearningPlanActivationService> activationService,
      ObjectProvider<LearningPlanRepository> planRepository,
      ObjectProvider<PracticeSessionRepository> practiceSessionRepository,
      ObjectProvider<ReviewQueueService> reviewQueueService,
      LearningPlanLoadService loadService,
      LearningPlanContractService contractService) {
    return new ApiLearningPlanPersonalizationDataProvider(
        claimQueryService, abilityProfileService, activationService, planRepository,
        practiceSessionRepository, reviewQueueService, loadService, contractService);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanPersonalizationMetrics learningPlanPersonalizationMetrics(
      ObjectProvider<MeterRegistry> meterRegistry
  ) {
    MeterRegistry registry = meterRegistry.getIfAvailable();
    return registry == null
        ? LearningPlanPersonalizationMetrics.NOOP
        : new MicrometerLearningPlanPersonalizationMetrics(registry);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanPersonalizationContextService learningPlanPersonalizationContextService(
      LearningPlanPersonalizationDataProvider provider,
      Clock learningPlanClock,
      LearningPlanPersonalizationMetrics metrics) {
    return new LearningPlanPersonalizationContextService(provider, null, learningPlanClock, metrics);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanDraftRevisionAgentDefinition learningPlanDraftRevisionAgentDefinition(
      LearningPlanProposalPromptBuilder promptBuilder,
      ObjectMapper objectMapper,
      LearningPlanRevisionModelViewProjector modelViewProjector
  ) {
    return new LearningPlanDraftRevisionAgentDefinition(promptBuilder, objectMapper, modelViewProjector);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanRevisionModelViewProjector learningPlanRevisionModelViewProjector(ObjectMapper objectMapper) {
    return new LearningPlanRevisionModelViewProjector(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanRevisionCanonicalRestorer learningPlanRevisionCanonicalRestorer(
      LearningPlanProblemCatalog problemCatalog,
      LearningPlanLoadService loadService,
      LearningPlanDraftValidator validator
  ) {
    return new LearningPlanRevisionCanonicalRestorer(problemCatalog, loadService, validator);
  }

  @Bean
  @ConditionalOnBean(LearningPlanProposalRepository.class)
  @ConditionalOnMissingBean
  public LearningPlanRevisionBaselineResolver learningPlanRevisionBaselineResolver(
      LearningPlanProposalRepository proposalRepository
  ) {
    return new LearningPlanRevisionBaselineResolver(proposalRepository);
  }

  @Bean
  @ConditionalOnBean(LearningPlanProposalRepository.class)
  @ConditionalOnMissingBean
  public QueryLearningPlanRevisionAgentTool queryLearningPlanRevisionAgentTool(
      LearningPlanProposalRepository proposalRepository,
      LearningPlanRevisionBaselineResolver baselineResolver
  ) {
    return new QueryLearningPlanRevisionAgentTool(proposalRepository, baselineResolver);
  }

  @Bean
  @ConditionalOnBean(LearningPlanProposalRepository.class)
  @ConditionalOnMissingBean
  public CompileLearningPlanRevisionAgentTool compileLearningPlanRevisionAgentTool(
      LearningPlanProposalRepository proposalRepository,
      LearningPlanRevisionBaselineResolver baselineResolver,
      LearningPlanRevisionCanonicalRestorer restorer,
      Clock learningPlanClock
  ) {
    return new CompileLearningPlanRevisionAgentTool(
        proposalRepository,
        baselineResolver,
        restorer,
        learningPlanClock);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanExtensionAgentDefinition learningPlanExtensionAgentDefinition(
      LearningPlanProposalPromptBuilder promptBuilder
  ) {
    return new LearningPlanExtensionAgentDefinition(promptBuilder);
  }

  @Bean
  @ConditionalOnBean(AgentRuntime.class)
  @ConditionalOnMissingBean
  public LearningPlanDraftStreamService learningPlanDraftStreamService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanDraftValidator validator,
      AgentRuntime agentRuntime,
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog,
      LearningPlanLoadService loadService,
      Clock learningPlanClock,
      LearningPlanPersonalizationContextService personalizationContextService,
      LearningPlanCreationPolicyService creationPolicyService) {
    return new LearningPlanDraftStreamService(
        draftRepository,
        validator,
        agentRuntime,
        objectMapper,
        problemCatalog,
        loadService,
        learningPlanClock,
        personalizationContextService,
        creationPolicyService);
  }

  @Bean
  @ConditionalOnBean(LearningPlanProposalRepository.class)
  @ConditionalOnMissingBean
  public LearningPlanProposalGroupService learningPlanProposalGroupService(
      LearningPlanProposalRepository proposalRepository,
      Clock learningPlanClock) {
    return new LearningPlanProposalGroupService(proposalRepository, learningPlanClock);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanExtensionValidator learningPlanExtensionValidator(LearningPlanProblemCatalog problemCatalog) {
    return new LearningPlanExtensionValidator(problemCatalog);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanProposalPromptBuilder learningPlanProposalPromptBuilder(
      ObjectMapper objectMapper,
      ObjectProvider<ManagedSystemPromptResolver> systemPromptResolver
  ) {
    return new LearningPlanProposalPromptBuilder(objectMapper, systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver));
  }

  @Bean
  @ConditionalOnBean({
      LearningPlanProposalRepository.class,
      LearningPlanProposalGroupService.class,
      AgentRuntime.class
  })
  @ConditionalOnMissingBean
  public LearningPlanDraftRevisionStreamService learningPlanDraftRevisionStreamService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanProposalRepository proposalRepository,
      LearningPlanProposalGroupService groupService,
      LearningPlanDraftValidator validator,
      AgentRuntime agentRuntime,
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog,
      LearningPlanLoadService loadService,
      TransactionOperations transactionOperations,
      Clock learningPlanClock,
      LearningPlanPersonalizationContextService personalizationContextService,
      LearningPlanAiRevisionAccessService aiRevisionAccessService) {
    return new LearningPlanDraftRevisionStreamService(
        draftRepository,
        proposalRepository,
        groupService,
        validator,
        agentRuntime,
        objectMapper,
        problemCatalog,
        loadService,
        transactionOperations,
        learningPlanClock,
        personalizationContextService,
        aiRevisionAccessService);
  }

  @Bean
  @ConditionalOnBean({
      LearningPlanProposalRepository.class,
      LearningPlanProposalGroupService.class,
      AgentRuntime.class
  })
  @ConditionalOnMissingBean
  public LearningPlanExtensionProposalStreamService learningPlanExtensionProposalStreamService(
      LearningPlanRepository planRepository,
      LearningPlanProposalRepository proposalRepository,
      LearningPlanProposalGroupService groupService,
      PracticeSessionRepository practiceSessionRepository,
      LearningPlanExtensionValidator validator,
      AgentRuntime agentRuntime,
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog,
      TransactionOperations transactionOperations,
      Clock learningPlanClock,
      LearningPlanPersonalizationContextService personalizationContextService,
      LearningPlanAiRevisionAccessService aiRevisionAccessService) {
    return new LearningPlanExtensionProposalStreamService(
        planRepository,
        proposalRepository,
        groupService,
        practiceSessionRepository,
        validator,
        agentRuntime,
        objectMapper,
        problemCatalog,
        transactionOperations,
        learningPlanClock,
        personalizationContextService,
        aiRevisionAccessService);
  }

  @Bean
  @ConditionalOnBean(LearningPlanProposalRepository.class)
  @ConditionalOnMissingBean
  public LearningPlanExtensionApplyService learningPlanExtensionApplyService(
      LearningPlanProposalRepository proposalRepository,
      LearningPlanRepository planRepository,
      PracticeSessionRepository practiceSessionRepository,
      LearningPlanExtensionValidator validator,
      Clock learningPlanClock) {
    return new LearningPlanExtensionApplyService(
        proposalRepository,
        planRepository,
        practiceSessionRepository,
        validator,
        learningPlanClock);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanService learningPlanService(
      LearningPlanRepository planRepository,
      LearningPlanLoadService loadService) {
    return new LearningPlanService(planRepository, loadService);
  }

  @Bean
  @ConditionalOnBean(LearningPlanActivationRepository.class)
  @ConditionalOnMissingBean
  public LearningPlanActivationService learningPlanActivationService(
      LearningPlanActivationRepository activationRepository,
      LearningPlanRepository planRepository,
      Clock learningPlanClock) {
    return new LearningPlanActivationService(activationRepository, planRepository, learningPlanClock);
  }

  @Bean
  @ConditionalOnBean({LearningPlanActivationService.class, PracticeSessionRepository.class})
  @ConditionalOnMissingBean
  public TodayPackService todayPackService(
      LearningPlanActivationService activationService,
      LearningPlanRepository planRepository,
      PracticeSessionRepository practiceSessionRepository,
      LearningPlanLoadService loadService,
      Clock learningPlanClock) {
    return new TodayPackService(
        activationService,
        planRepository,
        practiceSessionRepository,
        loadService,
        learningPlanClock);
  }

  @Bean
  @ConditionalOnBean(LearningPlanTemplateRepository.class)
  @ConditionalOnMissingBean
  public LearningPlanTemplateDraftService learningPlanTemplateDraftService(
      LearningPlanTemplateRepository templateRepository,
      LearningPlanDraftRepository draftRepository,
      LearningPlanProblemCatalog problemCatalog,
      LearningPlanDraftValidator validator,
      LearningPlanLoadService loadService,
      LearningPlanCreationPolicyService creationPolicyService,
      Clock learningPlanClock) {
    return new LearningPlanTemplateDraftService(
        templateRepository,
        draftRepository,
        problemCatalog,
        validator,
        loadService,
        creationPolicyService,
        learningPlanClock);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanDraftCleanupService learningPlanDraftCleanupService(
      LearningPlanDraftRepository draftRepository,
      Clock learningPlanClock,
      LearningPlanGovernanceProperties properties
  ) {
    return new LearningPlanDraftCleanupService(
        draftRepository,
        learningPlanClock,
        properties.quotaZoneId());
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanDraftCleanupMetrics learningPlanDraftCleanupMetrics(
      ObjectProvider<MeterRegistry> meterRegistryProvider
  ) {
    return new LearningPlanDraftCleanupMetrics(meterRegistryProvider.getIfAvailable());
  }

  @Bean(initMethod = "start", destroyMethod = "stop")
  @ConditionalOnMissingBean
  public LearningPlanDraftCleanupScheduler learningPlanDraftCleanupScheduler(
      LearningPlanDraftCleanupService cleanupService,
      LearningPlanGovernanceProperties properties,
      LearningPlanDraftCleanupMetrics metrics
  ) {
    return new LearningPlanDraftCleanupScheduler(cleanupService, properties.getCleanup(), metrics);
  }

  @Bean
  @ConditionalOnMissingBean({LearningPlanDraftRepository.class, LearningPlanRepository.class})
  public UnavailableLearningPlanRepository unavailableLearningPlanRepository() {
    return new UnavailableLearningPlanRepository();
  }
}
