package org.congcong.algomentor.mentor.api.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTaskMessageRepository;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.api.config.ApiSseProperties;
import org.congcong.algomentor.agent.persistence.postgres.config.AgentPostgresPersistenceConfiguration;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.api.controller.AgentConversationController;
import org.congcong.algomentor.api.controller.practice.PracticeSessionController;
import org.congcong.algomentor.api.service.AiActorResolver;
import org.congcong.algomentor.api.service.LlmStreamSseMapper;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunCoordinator;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.practice.CodeReviewTurnCapability;
import org.congcong.algomentor.mentor.application.practice.MicrometerPracticeCodeReviewMetrics;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemCatalog;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewMetrics;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewPromptBuilder;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewStructuredOutputMapper;
import org.congcong.algomentor.mentor.application.practice.PracticeMessageStreamService;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionService;
import org.congcong.algomentor.mentor.application.practice.PracticeTurnCapabilityRegistry;
import org.congcong.algomentor.mentor.application.practice.PracticeTurnClassifier;
import org.congcong.algomentor.mentor.application.practice.PracticeTurnOrchestrator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AgentPostgresPersistenceConfiguration.class)
public class AgentConversationApiAutoConfiguration {

  @Bean
  @ConditionalOnBean({AgentConversationRepository.class, ContextAssembler.class})
  @ConditionalOnMissingBean
  public AgentConversationService agentConversationService(
      AgentConversationRepository conversationRepository,
      ContextAssembler contextAssembler,
      ObjectProvider<LearningPlanRepository> learningPlanRepository,
      ObjectProvider<PracticeChatProblemCatalog> practiceProblemCatalog
  ) {
    LearningPlanRepository planRepository = learningPlanRepository.getIfAvailable();
    PracticeChatProblemCatalog problemCatalog = practiceProblemCatalog.getIfAvailable();
    if (planRepository != null && problemCatalog != null) {
      return new AgentConversationService(conversationRepository, contextAssembler, planRepository, problemCatalog);
    }
    return new AgentConversationService(conversationRepository, contextAssembler);
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
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnMissingBean
  public PracticeCodeReviewMetrics practiceCodeReviewMetrics(MeterRegistry registry) {
    return new MicrometerPracticeCodeReviewMetrics(registry);
  }

  @Bean
  @ConditionalOnMissingBean
  public PracticeTurnClassifier practiceTurnClassifier() {
    return new PracticeTurnClassifier();
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
  @ConditionalOnBean({
      LlmGateway.class,
      PracticeCodeReviewRepository.class,
      PracticeCodeReviewPromptBuilder.class,
      PracticeCodeReviewStructuredOutputMapper.class
  })
  @ConditionalOnMissingBean
  public PracticeCodeReviewService practiceCodeReviewService(
      PracticeCodeReviewRepository reviewRepository,
      LlmGateway llmGateway,
      PracticeCodeReviewPromptBuilder promptBuilder,
      PracticeCodeReviewStructuredOutputMapper outputMapper
  ) {
    return new PracticeCodeReviewService(reviewRepository, llmGateway, promptBuilder, outputMapper);
  }

  @Bean
  @ConditionalOnBean(PracticeCodeReviewService.class)
  @ConditionalOnMissingBean
  public CodeReviewTurnCapability codeReviewTurnCapability(
      PracticeCodeReviewService reviewService,
      ObjectProvider<PracticeCodeReviewMetrics> reviewMetrics
  ) {
    return new CodeReviewTurnCapability(
        reviewService,
        reviewMetrics.getIfAvailable(() -> PracticeCodeReviewMetrics.NOOP));
  }

  @Bean
  @ConditionalOnMissingBean
  public PracticeTurnCapabilityRegistry practiceTurnCapabilityRegistry(
      ObjectProvider<CodeReviewTurnCapability> codeReviewCapability
  ) {
    CodeReviewTurnCapability capability = codeReviewCapability.getIfAvailable();
    return new PracticeTurnCapabilityRegistry(capability == null ? List.of() : List.of(capability));
  }

  @Bean
  @ConditionalOnBean({
      PracticeSessionRepository.class,
      AgentConversationRunCoordinator.class,
      AgentTurnMessageLookupRepository.class,
      PracticeTurnClassifier.class,
      PracticeTurnCapabilityRegistry.class,
      PracticeChatProblemCatalog.class
  })
  @ConditionalOnMissingBean
  public PracticeTurnOrchestrator practiceTurnOrchestrator(
      PracticeSessionRepository practiceSessionRepository,
      AgentConversationRunCoordinator runCoordinator,
      AgentTurnMessageLookupRepository turnMessageLookupRepository,
      PracticeTurnClassifier classifier,
      PracticeTurnCapabilityRegistry capabilityRegistry,
      PracticeChatProblemCatalog problemCatalog
  ) {
    return new PracticeTurnOrchestrator(
        practiceSessionRepository,
        runCoordinator,
        turnMessageLookupRepository,
        classifier,
        capabilityRegistry,
        problemCatalog);
  }

  @Bean
  @ConditionalOnBean({
      PracticeSessionRepository.class,
      PracticeTurnOrchestrator.class
  })
  @ConditionalOnMissingBean
  public PracticeMessageStreamService practiceMessageStreamService(
      PracticeSessionRepository practiceSessionRepository,
      PracticeTurnOrchestrator orchestrator
  ) {
    return new PracticeMessageStreamService(practiceSessionRepository, orchestrator);
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
