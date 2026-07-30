package org.congcong.algomentor.mentor.api.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.congcong.algomentor.agent.core.prompt.DefaultPromptAssembler;
import org.congcong.algomentor.agent.core.prompt.PromptAssembler;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssemblyPolicy;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTaskMessageRepository;
import org.congcong.algomentor.api.config.ApiSseProperties;
import org.congcong.algomentor.api.config.CodeReviewProfileConsumerProperties;
import org.congcong.algomentor.api.config.LearnerProfileAgentProperties;
import org.congcong.algomentor.api.config.LearnerProfileRecallProperties;
import org.congcong.algomentor.api.config.PracticeCodeReviewProperties;
import org.congcong.algomentor.api.config.PracticeChatPromptProperties;
import org.congcong.algomentor.agent.persistence.postgres.config.AgentPostgresPersistenceConfiguration;
import org.congcong.algomentor.ai.governance.autoconfigure.AiGovernanceAutoConfiguration;
import org.congcong.algomentor.api.controller.AgentConversationController;
import org.congcong.algomentor.api.controller.practice.PracticeSessionController;
import org.congcong.algomentor.api.problem.mapper.ProblemTagMapper;
import org.congcong.algomentor.api.practice.service.MyBatisTrustedProblemTagCatalog;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisCodeReviewProfileFactRepository;
import org.congcong.algomentor.api.service.AiActorResolver;
import org.congcong.algomentor.api.service.LlmStreamSseMapper;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationService;
import org.congcong.algomentor.mentor.application.conversation.MentorConversationAgentDefinition;
import org.congcong.algomentor.mentor.application.conversation.MentorConversationRunAdapter;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceRepository;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceService;
import org.congcong.algomentor.mentor.application.practice.MicrometerPracticeCodeReviewMetrics;
import org.congcong.algomentor.mentor.application.practice.PracticeChatAgentDefinition;
import org.congcong.algomentor.mentor.application.practice.PracticeChatRunAdapter;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemCatalog;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptProfileResolver;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptSectionProvider;
import org.congcong.algomentor.mentor.application.practice.PracticeCompletionGate;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewMetrics;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewMetricStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentTool;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeMessageStreamService;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionService;
import org.congcong.algomentor.mentor.application.practice.PracticeTurnOrchestrator;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTagCatalog;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileQueryService;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerProfilePolicyResolver;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerProfilePromptSectionProvider;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerProfileRecallService;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileBatchConsumer;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileFactRepository;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileMetrics;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfilePromptBuilder;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileStructuredOutputMapper;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileUpdateAgentDefinition;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.review.MicrometerCodeReviewProfileMetrics;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdatePromptBuilder;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateAgentDefinition;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateService;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.profile.tool.UpdateLearnerDeclaredProfileAgentTool;
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.OpsStatus;
import org.congcong.algomentor.ops.observability.autoconfigure.OpsObservabilityAutoConfiguration;
import org.congcong.algomentor.queue.config.PersistentQueueAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Qualifier;

@AutoConfiguration(after = {
    AgentPostgresPersistenceConfiguration.class,
    AiGovernanceAutoConfiguration.class,
    PersistentQueueAutoConfiguration.class,
    OpsObservabilityAutoConfiguration.class
})
@Import(PracticeCodeReviewConfiguration.class)
@EnableConfigurationProperties({
    LearnerProfileAgentProperties.class,
    LearnerProfileRecallProperties.class,
    PracticeCodeReviewProperties.class,
    PracticeChatPromptProperties.class,
    CodeReviewProfileConsumerProperties.class
})
public class AgentConversationApiAutoConfiguration {

  @Bean
  @ConditionalOnBean({AgentConversationRepository.class, ContextAssembler.class})
  @ConditionalOnMissingBean
  public AgentConversationService agentConversationService(
      AgentConversationRepository conversationRepository,
      ContextAssembler contextAssembler,
      ObjectProvider<LearningPlanRepository> learningPlanRepository,
      ObjectProvider<PracticeChatProblemCatalog> practiceProblemCatalog,
      PracticeChatPromptProperties promptProperties,
      @Qualifier("practiceChatPromptAssembler") PromptAssembler practicePromptAssembler,
      LearnerProfilePromptSectionProvider learnerProfilePromptSectionProvider,
      ObjectProvider<LearnerProfileRecallService> learnerProfileRecallService,
      ObjectProvider<ManagedSystemPromptResolver> systemPromptResolver
  ) {
    LearningPlanRepository planRepository = learningPlanRepository.getIfAvailable();
    PracticeChatProblemCatalog problemCatalog = practiceProblemCatalog.getIfAvailable();
    ContextAssemblyPolicy defaultPolicy = ContextAssemblyPolicy.defaultPolicy();
    ContextAssemblyPolicy practiceContextPolicy = new ContextAssemblyPolicy(
        defaultPolicy.recentTurns(),
        promptProperties.getTotalTokenBudget(),
        defaultPolicy.policyName(),
        defaultPolicy.policyVersion());
    if (planRepository != null && problemCatalog != null) {
      return new AgentConversationService(
          conversationRepository,
          contextAssembler,
          practiceContextPolicy,
          planRepository,
          problemCatalog,
          practicePromptAssembler,
          learnerProfileRecallService.getIfAvailable(),
          learnerProfilePromptSectionProvider,
          systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver));
    }
    return new AgentConversationService(
        conversationRepository,
        contextAssembler,
        practiceContextPolicy,
        null,
        null,
        practicePromptAssembler,
        learnerProfileRecallService.getIfAvailable(),
        learnerProfilePromptSectionProvider,
        systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver));
  }

  @Bean("practiceChatPromptAssembler")
  @ConditionalOnMissingBean(name = "practiceChatPromptAssembler")
  public PromptAssembler practiceChatPromptAssembler(
      PracticeChatPromptProperties promptProperties,
      LearnerProfilePromptSectionProvider learnerProfilePromptSectionProvider,
      ObjectProvider<ManagedSystemPromptResolver> systemPromptResolver
  ) {
    return new DefaultPromptAssembler(
        new PracticeChatPromptProfileResolver(promptProperties.getTotalTokenBudget()),
        java.util.List.of(new PracticeChatPromptSectionProvider(
            systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver)), learnerProfilePromptSectionProvider));
  }

  @Bean
  @ConditionalOnMissingBean
  public LearnerProfilePromptSectionProvider learnerProfilePromptSectionProvider(
      LearnerProfileRecallProperties properties,
      ObjectProvider<ManagedSystemPromptResolver> systemPromptResolver
  ) {
    return new LearnerProfilePromptSectionProvider(
        properties.getMaxTokenBudget(), systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver));
  }

  @Bean
  @ConditionalOnMissingBean
  public LearnerProfilePolicyResolver learnerProfilePolicyResolver(
      LearnerProfileRecallProperties properties
  ) {
    return new LearnerProfilePolicyResolver(properties.isEnabled(), properties.getMaxTokenBudget());
  }

  /** 开关开启后使用强依赖注入，避免条件评估顺序导致画像召回被静默跳过。 */
  @Bean
  @ConditionalOnProperty(
      prefix = LearnerProfileRecallProperties.PREFIX,
      name = "enabled",
      havingValue = "true")
  @ConditionalOnMissingBean
  public LearnerProfileRecallService learnerProfileRecallService(
      LearnerProfilePolicyResolver policyResolver,
      LearnerProfileQueryService queryService,
      TrustedProblemTagCatalog trustedProblemTagCatalog
  ) {
    return new LearnerProfileRecallService(policyResolver, queryService, trustedProblemTagCatalog);
  }

  @Bean
  @ConditionalOnBean({PracticeCodeReviewMapper.class, ObjectMapper.class})
  @ConditionalOnMissingBean
  public CodeReviewProfileFactRepository codeReviewProfileFactRepository(
      PracticeCodeReviewMapper mapper,
      ObjectMapper objectMapper
  ) {
    return new MyBatisCodeReviewProfileFactRepository(mapper, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public CodeReviewProfilePromptBuilder codeReviewProfilePromptBuilder(
      ObjectProvider<ManagedSystemPromptResolver> systemPromptResolver
  ) {
    return new CodeReviewProfilePromptBuilder(systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver));
  }

  @Bean
  @ConditionalOnProperty(
      prefix = CodeReviewProfileConsumerProperties.PREFIX,
      name = "enabled",
      havingValue = "true")
  @ConditionalOnMissingBean
  public CodeReviewProfileUpdateAgentDefinition codeReviewProfileUpdateAgentDefinition(
      CodeReviewProfilePromptBuilder promptBuilder
  ) {
    return new CodeReviewProfileUpdateAgentDefinition(promptBuilder);
  }

  @Bean
  @ConditionalOnMissingBean
  public CodeReviewProfileStructuredOutputMapper codeReviewProfileStructuredOutputMapper() {
    return new CodeReviewProfileStructuredOutputMapper();
  }

  @Bean
  @ConditionalOnMissingBean
  public CodeReviewProfileMetrics codeReviewProfileMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
    MeterRegistry registry = meterRegistry.getIfAvailable();
    return registry == null ? CodeReviewProfileMetrics.NOOP : new MicrometerCodeReviewProfileMetrics(registry);
  }

  @Bean
  @ConditionalOnBean({
      CodeReviewProfileFactRepository.class,
      LearnerProfileQueryService.class,
      LearnerProfileUpdateService.class,
      AgentRuntime.class,
      CodeReviewProfileUpdateAgentDefinition.class
  })
  @ConditionalOnProperty(
      prefix = CodeReviewProfileConsumerProperties.PREFIX,
      name = "enabled",
      havingValue = "true")
  @ConditionalOnMissingBean
  public CodeReviewProfileUpdateService codeReviewProfileUpdateService(
      CodeReviewProfileFactRepository factRepository,
      LearnerProfileQueryService queryService,
      LearnerProfileUpdateService updateService,
      @Lazy AgentRuntime agentRuntime,
      CodeReviewProfileStructuredOutputMapper outputMapper,
      CodeReviewProfileMetrics metrics,
      CodeReviewProfileConsumerProperties properties
  ) {
    return new CodeReviewProfileUpdateService(
        factRepository,
        queryService,
        updateService,
        agentRuntime,
        outputMapper,
        properties.getMaxStaleRetries(),
        metrics);
  }

  @Bean
  @ConditionalOnBean({
      ObjectMapper.class,
      CodeReviewProfileFactRepository.class,
      CodeReviewProfileUpdateAgentDefinition.class,
      CodeReviewProfileUpdateService.class
  })
  @ConditionalOnProperty(
      prefix = CodeReviewProfileConsumerProperties.PREFIX,
      name = "enabled",
      havingValue = "true")
  @ConditionalOnMissingBean
  public CodeReviewProfileBatchConsumer codeReviewProfileBatchConsumer(
      ObjectMapper objectMapper,
      CodeReviewProfileFactRepository factRepository,
      CodeReviewProfileUpdateService updateService,
      CodeReviewProfileMetrics metrics
  ) {
    return new CodeReviewProfileBatchConsumer(objectMapper, factRepository, updateService, metrics);
  }

  @Bean
  @ConditionalOnBean({
      AgentConversationService.class,
      AgentRunLockManager.class,
      AgentRunLockOwnerProvider.class
  })
  @ConditionalOnMissingBean
  public MentorConversationAgentDefinition mentorConversationAgentDefinition(
      AgentConversationService conversationService,
      AgentRunLockManager lockManager,
      AgentRunLockOwnerProvider lockOwnerProvider
  ) {
    return new MentorConversationAgentDefinition(new MentorConversationRunAdapter(
        conversationService,
        lockManager,
        lockOwnerProvider));
  }

  @Bean
  @ConditionalOnBean({
      AgentConversationService.class,
      PracticeSessionRepository.class,
      AgentRunLockManager.class,
      AgentRunLockOwnerProvider.class
  })
  @ConditionalOnMissingBean
  public PracticeChatAgentDefinition practiceChatAgentDefinition(
      AgentConversationService conversationService,
      AgentRunLockManager lockManager,
      AgentRunLockOwnerProvider lockOwnerProvider,
      ObjectProvider<PracticeCodeReviewAgentTool> practiceCodeReviewTool,
      ObjectProvider<UpdateLearnerDeclaredProfileAgentTool> declaredProfileTool
  ) {
    java.util.List<String> toolNames = new java.util.ArrayList<>();
    practiceCodeReviewTool.ifAvailable(tool -> toolNames.add(tool.spec().name()));
    declaredProfileTool.ifAvailable(tool -> toolNames.add(tool.spec().name()));
    return new PracticeChatAgentDefinition(
        new PracticeChatRunAdapter(conversationService, lockManager, lockOwnerProvider),
        toolNames);
  }

  @Bean
  @ConditionalOnBean({
      AgentRuntime.class,
      LlmStreamSseMapper.class,
      AiActorResolver.class
  })
  @ConditionalOnMissingBean
  public AgentConversationController agentConversationController(
      AgentRuntime agentRuntime,
      LlmStreamSseMapper sseMapper,
      AiActorResolver actorResolver,
      ObjectProvider<PracticeMessageStreamService> practiceMessageStreamService,
      ApiSseProperties sseProperties
  ) {
    return new AgentConversationController(
        agentRuntime,
        sseMapper,
        actorResolver,
        practiceMessageStreamService,
        sseProperties);
  }

  @Bean
  @ConditionalOnBean({
      LearningPlanRepository.class,
      PracticeChatProblemCatalog.class,
      PracticeSessionRepository.class,
      AgentTaskMessageRepository.class
  })
  @ConditionalOnMissingBean
  public PracticeSessionService practiceSessionService(
      LearningPlanRepository learningPlanRepository,
      PracticeChatProblemCatalog problemCatalog,
      PracticeSessionRepository practiceSessionRepository,
      AgentTaskMessageRepository agentTaskMessageRepository,
      ObjectProvider<PracticeCodeReviewRepository> reviewRepository,
      ObjectProvider<PracticeCodeReviewMetrics> reviewMetrics,
      ObjectProvider<ManagedSystemPromptResolver> systemPromptResolver
  ) {
    return new PracticeSessionService(
        learningPlanRepository,
        problemCatalog,
        practiceSessionRepository,
        agentTaskMessageRepository,
        reviewRepository.getIfAvailable(PracticeCodeReviewRepository::empty),
        reviewMetrics.getIfAvailable(() -> PracticeCodeReviewMetrics.NOOP),
        systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver));
  }

  @Bean
  @ConditionalOnBean(LearningOpsRecorder.class)
  @ConditionalOnMissingBean
  public PracticeCodeReviewMetrics opsPracticeCodeReviewMetrics(
      LearningOpsRecorder learningOpsRecorder,
      ObjectProvider<MeterRegistry> meterRegistry
  ) {
    MeterRegistry registry = meterRegistry.getIfAvailable();
    PracticeCodeReviewMetrics delegate = registry == null
        ? PracticeCodeReviewMetrics.NOOP
        : new MicrometerPracticeCodeReviewMetrics(registry);
    return new OpsPracticeCodeReviewMetrics(learningOpsRecorder, delegate);
  }

  @Bean
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnMissingBean(PracticeCodeReviewMetrics.class)
  public PracticeCodeReviewMetrics practiceCodeReviewMetrics(MeterRegistry registry) {
    return new MicrometerPracticeCodeReviewMetrics(registry);
  }

  @Bean
  @ConditionalOnBean(ProblemTagMapper.class)
  @ConditionalOnMissingBean
  public TrustedProblemTagCatalog trustedProblemTagCatalog(ProblemTagMapper mapper) {
    return new MyBatisTrustedProblemTagCatalog(mapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public DeclaredProfileUpdatePromptBuilder declaredProfileUpdatePromptBuilder(
      ObjectProvider<ManagedSystemPromptResolver> systemPromptResolver
  ) {
    return new DeclaredProfileUpdatePromptBuilder(systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver));
  }

  @Bean
  @ConditionalOnProperty(
      prefix = LearnerProfileAgentProperties.PREFIX,
      name = "enabled",
      havingValue = "true")
  @ConditionalOnMissingBean
  public DeclaredProfileUpdateAgentDefinition declaredProfileUpdateAgentDefinition(
      DeclaredProfileUpdatePromptBuilder promptBuilder
  ) {
    return new DeclaredProfileUpdateAgentDefinition(promptBuilder);
  }

  @Bean
  @ConditionalOnBean({
      LearnerProfileQueryService.class,
      LearnerProfileUpdateService.class,
      AgentRuntime.class,
      DeclaredProfileUpdateAgentDefinition.class
  })
  @ConditionalOnProperty(
      prefix = LearnerProfileAgentProperties.PREFIX,
      name = "enabled",
      havingValue = "true")
  @ConditionalOnMissingBean
  public DeclaredProfileUpdateService declaredProfileUpdateService(
      LearnerProfileQueryService queryService,
      LearnerProfileUpdateService updateService,
      @Lazy AgentRuntime agentRuntime,
      DeclaredProfileUpdatePromptBuilder promptBuilder,
      LearnerProfileAgentProperties properties
  ) {
    return new DeclaredProfileUpdateService(
        queryService,
        updateService,
        agentRuntime,
        promptBuilder,
        properties.getMaxStaleRetries(),
        properties.getResultSummaryMaxChars());
  }

  @Bean
  @ConditionalOnBean({
      DeclaredProfileUpdateService.class,
      DeclaredProfileUpdateAgentDefinition.class,
      ObjectMapper.class
  })
  @ConditionalOnMissingBean
  public UpdateLearnerDeclaredProfileAgentTool updateLearnerDeclaredProfileAgentTool(
      DeclaredProfileUpdateService updateService,
      ObjectMapper objectMapper
  ) {
    return new UpdateLearnerDeclaredProfileAgentTool(updateService, objectMapper);
  }

  @Bean
  @ConditionalOnBean({AgentRuntime.class, PracticeChatAgentDefinition.class})
  @ConditionalOnMissingBean
  public PracticeTurnOrchestrator practiceTurnOrchestrator(
      AgentRuntime agentRuntime
  ) {
    return new PracticeTurnOrchestrator(agentRuntime);
  }

  @Bean
  @ConditionalOnBean({
      PracticeSessionRepository.class,
      PracticeTurnOrchestrator.class
  })
  @ConditionalOnMissingBean
  public PracticeMessageStreamService practiceMessageStreamService(
      PracticeSessionRepository practiceSessionRepository,
      PracticeTurnOrchestrator orchestrator,
      ObjectProvider<UserAiPreferenceService> preferenceService
  ) {
    return new PracticeMessageStreamService(
        practiceSessionRepository,
        orchestrator,
        preferenceService.getIfAvailable(() -> new UserAiPreferenceService(UserAiPreferenceRepository.empty())));
  }

  @Bean
  @ConditionalOnMissingBean
  public UserAiPreferenceService userAiPreferenceService(ObjectProvider<UserAiPreferenceRepository> repository) {
    return new UserAiPreferenceService(repository.getIfAvailable(UserAiPreferenceRepository::empty));
  }

  private static final class OpsPracticeCodeReviewMetrics implements PracticeCodeReviewMetrics {

    private final LearningOpsRecorder learningOpsRecorder;
    private final PracticeCodeReviewMetrics delegate;

    private OpsPracticeCodeReviewMetrics(
        LearningOpsRecorder learningOpsRecorder,
        PracticeCodeReviewMetrics delegate
    ) {
      this.learningOpsRecorder = learningOpsRecorder;
      this.delegate = delegate;
    }

    @Override
    public void recordCompletionGate(PracticeCompletionGate gate) {
      delegate.recordCompletionGate(gate);
    }

    @Override
    public void recordReview(PracticeCodeReviewMetricStatus status) {
      delegate.recordReview(status);
      learningOpsRecorder.practiceCodeReview(toOpsStatus(status));
    }

    private OpsStatus toOpsStatus(PracticeCodeReviewMetricStatus status) {
      if (status == null) {
        return OpsStatus.FAILED;
      }
      return switch (status) {
        case COMPLETED -> OpsStatus.COMPLETED;
        case FAILED -> OpsStatus.FAILED;
        case UNREVIEWABLE -> OpsStatus.UNREVIEWABLE;
      };
    }
  }

  @Bean
  @ConditionalOnBean({
      CurrentUserIdProvider.class,
  })
  @ConditionalOnMissingBean
  public PracticeSessionController practiceSessionController(
      ObjectProvider<PracticeSessionService> practiceSessionService,
      ObjectProvider<PracticeMessageStreamService> streamService,
      CurrentUserIdProvider currentUserIdProvider,
      ObjectProvider<LlmStreamSseMapper> sseMapper,
      ApiSseProperties sseProperties
  ) {
    return new PracticeSessionController(
        practiceSessionService,
        streamService,
        currentUserIdProvider,
        sseMapper,
        sseProperties);
  }
}
