package org.congcong.algomentor.mentor.api.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.prompt.DefaultPromptAssembler;
import org.congcong.algomentor.agent.core.prompt.PromptAssembler;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssemblyPolicy;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTaskMessageRepository;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.ai.governance.completion.AiCompletionGateway;
import org.congcong.algomentor.ai.governance.completion.AiPassthroughCompletionGateway;
import org.congcong.algomentor.api.config.ApiSseProperties;
import org.congcong.algomentor.api.config.CodeReviewProfileConsumerProperties;
import org.congcong.algomentor.api.config.LearnerProfileAgentProperties;
import org.congcong.algomentor.api.config.LearnerProfileRecallProperties;
import org.congcong.algomentor.api.config.PracticeChatPromptProperties;
import org.congcong.algomentor.agent.persistence.postgres.config.AgentPostgresPersistenceConfiguration;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.api.controller.AgentConversationController;
import org.congcong.algomentor.api.controller.practice.PracticeSessionController;
import org.congcong.algomentor.api.problem.mapper.ProblemTagMapper;
import org.congcong.algomentor.api.practice.service.MyBatisTrustedProblemTagCatalog;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisCodeReviewProfileFactRepository;
import org.congcong.algomentor.api.service.AiActorResolver;
import org.congcong.algomentor.api.service.LlmStreamSseMapper;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunCoordinator;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceRepository;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceService;
import org.congcong.algomentor.mentor.application.practice.MicrometerPracticeCodeReviewMetrics;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemCatalog;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptProfileResolver;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptSectionProvider;
import org.congcong.algomentor.mentor.application.practice.PracticeCompletionGate;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentTool;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewCommitService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewMetrics;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewMetricStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewPermissionHook;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewPromptBuilder;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewStructuredOutputMapper;
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
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.review.MicrometerCodeReviewProfileMetrics;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdatePromptBuilder;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.tool.UpdateLearnerDeclaredProfileAgentTool;
import org.congcong.algomentor.mentor.application.review.PracticeCodeReviewObserver;
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.OpsStatus;
import org.congcong.algomentor.ops.observability.autoconfigure.OpsObservabilityAutoConfiguration;
import org.congcong.algomentor.queue.publisher.QueuePublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;

@AutoConfiguration(after = {
    AgentPostgresPersistenceConfiguration.class,
    OpsObservabilityAutoConfiguration.class
})
@EnableConfigurationProperties({
    LearnerProfileAgentProperties.class,
    LearnerProfileRecallProperties.class,
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
      ObjectProvider<LearnerProfileRecallService> learnerProfileRecallService
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
          learnerProfilePromptSectionProvider);
    }
    return new AgentConversationService(
        conversationRepository,
        contextAssembler,
        practiceContextPolicy,
        null,
        null,
        practicePromptAssembler,
        learnerProfileRecallService.getIfAvailable(),
        learnerProfilePromptSectionProvider);
  }

  @Bean("practiceChatPromptAssembler")
  @ConditionalOnMissingBean(name = "practiceChatPromptAssembler")
  public PromptAssembler practiceChatPromptAssembler(
      PracticeChatPromptProperties promptProperties,
      LearnerProfilePromptSectionProvider learnerProfilePromptSectionProvider
  ) {
    return new DefaultPromptAssembler(
        new PracticeChatPromptProfileResolver(promptProperties.getTotalTokenBudget()),
        java.util.List.of(new PracticeChatPromptSectionProvider(), learnerProfilePromptSectionProvider));
  }

  @Bean
  @ConditionalOnMissingBean
  public LearnerProfilePromptSectionProvider learnerProfilePromptSectionProvider(
      LearnerProfileRecallProperties properties
  ) {
    return new LearnerProfilePromptSectionProvider(properties.getMaxTokenBudget());
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
  public CodeReviewProfilePromptBuilder codeReviewProfilePromptBuilder() {
    return new CodeReviewProfilePromptBuilder();
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
      AiCompletionGateway.class
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
      AiCompletionGateway completionGateway,
      CodeReviewProfilePromptBuilder promptBuilder,
      CodeReviewProfileStructuredOutputMapper outputMapper,
      CodeReviewProfileMetrics metrics,
      CodeReviewProfileConsumerProperties properties
  ) {
    return new CodeReviewProfileUpdateService(
        factRepository,
        queryService,
        updateService,
        completionGateway,
        promptBuilder,
        outputMapper,
        properties.getMaxStaleRetries(),
        metrics);
  }

  @Bean
  @ConditionalOnBean({
      ObjectMapper.class,
      CodeReviewProfileFactRepository.class,
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
      AgentLoopRunner.class,
      AgentRunLockManager.class,
      AgentRunLockOwnerProvider.class
  })
  @ConditionalOnMissingBean
  public AgentConversationRunCoordinator agentConversationRunCoordinator(
      AgentConversationService conversationService,
      AgentLoopRunner agentLoopRunner,
      AgentRunLockManager lockManager,
      AgentRunLockOwnerProvider lockOwnerProvider
  ) {
    return new AgentConversationRunCoordinator(
        conversationService,
        agentLoopRunner,
        lockManager,
        lockOwnerProvider);
  }

  @Bean
  @ConditionalOnBean({
      AgentConversationRunCoordinator.class,
      LlmStreamSseMapper.class,
      AiActorResolver.class,
      AiRunAdmissionService.class
  })
  @ConditionalOnMissingBean
  public AgentConversationController agentConversationController(
      AgentConversationRunCoordinator runCoordinator,
      LlmStreamSseMapper sseMapper,
      AiActorResolver actorResolver,
      AiRunAdmissionService admissionService
  ) {
    return new AgentConversationController(runCoordinator, sseMapper, actorResolver, admissionService);
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
      ObjectProvider<PracticeCodeReviewMetrics> reviewMetrics
  ) {
    return new PracticeSessionService(
        learningPlanRepository,
        problemCatalog,
        practiceSessionRepository,
        agentTaskMessageRepository,
        reviewRepository.getIfAvailable(PracticeCodeReviewRepository::empty),
        reviewMetrics.getIfAvailable(() -> PracticeCodeReviewMetrics.NOOP));
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
  @ConditionalOnBean({
      LlmGateway.class,
      PracticeCodeReviewRepository.class
  })
  @ConditionalOnMissingBean
  public PracticeCodeReviewPromptBuilder practiceCodeReviewPromptBuilder() {
    return new PracticeCodeReviewPromptBuilder();
  }

  @Bean
  @ConditionalOnBean({
      LlmGateway.class,
      PracticeCodeReviewRepository.class
  })
  @ConditionalOnMissingBean
  public PracticeCodeReviewStructuredOutputMapper practiceCodeReviewStructuredOutputMapper() {
    return new PracticeCodeReviewStructuredOutputMapper();
  }

  @Bean
  @ConditionalOnBean({PracticeCodeReviewRepository.class, QueuePublisher.class})
  @ConditionalOnMissingBean
  public PracticeCodeReviewCommitService practiceCodeReviewCommitService(
      PracticeCodeReviewRepository reviewRepository, QueuePublisher queuePublisher) {
    return new PracticeCodeReviewCommitService(reviewRepository, queuePublisher);
  }

  @Bean
  @ConditionalOnBean({
      LlmGateway.class,
      PracticeCodeReviewRepository.class,
      PracticeCodeReviewCommitService.class,
      PracticeCodeReviewPromptBuilder.class,
      PracticeCodeReviewStructuredOutputMapper.class
  })
  @ConditionalOnMissingBean
  public PracticeCodeReviewService practiceCodeReviewService(
      PracticeCodeReviewRepository reviewRepository,
      PracticeCodeReviewCommitService commitService,
      LlmGateway llmGateway,
      PracticeCodeReviewPromptBuilder promptBuilder,
      PracticeCodeReviewStructuredOutputMapper outputMapper,
      ObjectProvider<PracticeCodeReviewMetrics> metrics,
      ObjectProvider<PracticeCodeReviewObserver> observer,
      ObjectProvider<AiCompletionGateway> completionGateway
  ) {
    return new PracticeCodeReviewService(
        reviewRepository,
        commitService,
        completionGateway.getIfAvailable(() -> new AiPassthroughCompletionGateway(llmGateway)),
        promptBuilder,
        outputMapper,
        metrics.getIfAvailable(() -> PracticeCodeReviewMetrics.NOOP),
        observer.getIfAvailable(() -> PracticeCodeReviewObserver.NOOP));
  }

  @Bean
  @ConditionalOnBean(ProblemTagMapper.class)
  @ConditionalOnMissingBean
  public TrustedProblemTagCatalog trustedProblemTagCatalog(ProblemTagMapper mapper) {
    return new MyBatisTrustedProblemTagCatalog(mapper);
  }

  @Bean
  @ConditionalOnBean({
      PracticeSessionRepository.class,
      AgentTurnMessageLookupRepository.class,
      PracticeCodeReviewService.class,
      ObjectMapper.class,
      TrustedProblemTagCatalog.class
  })
  @ConditionalOnMissingBean
  public PracticeCodeReviewAgentTool practiceCodeReviewAgentTool(
      PracticeSessionRepository practiceSessionRepository,
      AgentTurnMessageLookupRepository turnMessageLookupRepository,
      PracticeCodeReviewService reviewService,
      ObjectMapper objectMapper,
      TrustedProblemTagCatalog trustedProblemTagCatalog
  ) {
    return new PracticeCodeReviewAgentTool(
        practiceSessionRepository,
        turnMessageLookupRepository,
        reviewService,
        objectMapper,
        trustedProblemTagCatalog);
  }

  @Bean
  @ConditionalOnMissingBean
  public DeclaredProfileUpdatePromptBuilder declaredProfileUpdatePromptBuilder() {
    return new DeclaredProfileUpdatePromptBuilder();
  }

  @Bean
  @ConditionalOnBean({
      LearnerProfileQueryService.class,
      LearnerProfileUpdateService.class,
      AiCompletionGateway.class
  })
  @ConditionalOnProperty(
      prefix = LearnerProfileAgentProperties.PREFIX,
      name = "enabled",
      havingValue = "true")
  @ConditionalOnMissingBean
  public DeclaredProfileUpdateService declaredProfileUpdateService(
      LearnerProfileQueryService queryService,
      LearnerProfileUpdateService updateService,
      AiCompletionGateway completionGateway,
      DeclaredProfileUpdatePromptBuilder promptBuilder,
      LearnerProfileAgentProperties properties
  ) {
    return new DeclaredProfileUpdateService(
        queryService,
        updateService,
        completionGateway,
        promptBuilder,
        properties.getMaxStaleRetries(),
        properties.getResultSummaryMaxChars());
  }

  @Bean
  @ConditionalOnBean({DeclaredProfileUpdateService.class, ObjectMapper.class})
  @ConditionalOnMissingBean
  public UpdateLearnerDeclaredProfileAgentTool updateLearnerDeclaredProfileAgentTool(
      DeclaredProfileUpdateService updateService,
      ObjectMapper objectMapper
  ) {
    return new UpdateLearnerDeclaredProfileAgentTool(updateService, objectMapper);
  }

  @Bean
  @ConditionalOnBean({
      PracticeSessionRepository.class,
      AgentTurnMessageLookupRepository.class
  })
  @ConditionalOnMissingBean
  public PracticeCodeReviewPermissionHook practiceCodeReviewPermissionHook(
      PracticeSessionRepository practiceSessionRepository,
      AgentTurnMessageLookupRepository turnMessageLookupRepository
  ) {
    return new PracticeCodeReviewPermissionHook(practiceSessionRepository, turnMessageLookupRepository);
  }

  @Bean
  @ConditionalOnBean({
      PracticeSessionRepository.class,
      AgentConversationRunCoordinator.class
  })
  @ConditionalOnMissingBean
  public PracticeTurnOrchestrator practiceTurnOrchestrator(
      PracticeSessionRepository practiceSessionRepository,
      AgentConversationRunCoordinator runCoordinator
  ) {
    return new PracticeTurnOrchestrator(
        practiceSessionRepository,
        runCoordinator);
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
      ObjectProvider<AiActorResolver> actorResolver,
      ObjectProvider<AiRunAdmissionService> admissionService,
      ObjectProvider<LlmStreamSseMapper> sseMapper,
      ApiSseProperties sseProperties
  ) {
    return new PracticeSessionController(
        practiceSessionService,
        streamService,
        currentUserIdProvider,
        actorResolver,
        admissionService,
        sseMapper,
        sseProperties);
  }
}
