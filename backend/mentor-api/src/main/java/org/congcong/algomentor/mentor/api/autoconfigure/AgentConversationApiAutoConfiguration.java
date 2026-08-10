package org.congcong.algomentor.mentor.api.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.congcong.algomentor.agent.core.prompt.DefaultPromptAssembler;
import org.congcong.algomentor.agent.core.prompt.PromptAssembler;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssemblyPolicy;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTaskMessageRepository;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.agent.core.tool.ReadToolResultTool;
import org.congcong.algomentor.agent.core.toolresult.ToolResultReadGuard;
import org.congcong.algomentor.api.config.ApiSseProperties;
import org.congcong.algomentor.api.config.LearnerMemoryCodeReviewConsumerProperties;
import org.congcong.algomentor.api.config.LearnerMemoryRecallProperties;
import org.congcong.algomentor.api.config.LearnerMemoryDeclaredUpdateProperties;
import org.congcong.algomentor.api.config.MentorConfigurationKeys;
import org.congcong.algomentor.api.config.PracticeCodeReviewProperties;
import org.congcong.algomentor.api.config.PracticeChatLearningStateProperties;
import org.congcong.algomentor.api.config.PracticeChatCoachSummaryProperties;
import org.congcong.algomentor.api.config.PracticeChatPromptProperties;
import org.congcong.algomentor.api.config.PracticeChatReviewTrajectoryProperties;
import org.congcong.algomentor.agent.persistence.postgres.config.AgentPostgresPersistenceConfiguration;
import org.congcong.algomentor.ai.governance.autoconfigure.AiGovernanceAutoConfiguration;
import org.congcong.algomentor.api.controller.practice.PracticeSessionController;
import org.congcong.algomentor.api.problem.mapper.ProblemTagMapper;
import org.congcong.algomentor.api.practice.service.MyBatisTrustedProblemTagCatalog;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryCodeReviewFactRepository;
import org.congcong.algomentor.api.profile.repository.MyBatisCodeReviewHistoryRepository;
import org.congcong.algomentor.api.service.LlmStreamSseMapper;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceRepository;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceService;
import org.congcong.algomentor.mentor.application.practice.GetCurrentProblemLearningStateAgentTool;
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
import org.congcong.algomentor.mentor.application.practice.ProposeCurrentProblemCoachSummaryAgentTool;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTagCatalog;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryProposalRepository;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryProposalService;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimQueryService;
import org.congcong.algomentor.mentor.application.profile.evidence.repository.LearnerMemoryEvidenceRepository;
import org.congcong.algomentor.mentor.application.profile.operation.service.LearnerMemoryAtomicApplyService;
import org.congcong.algomentor.mentor.application.profile.run.repository.LearnerMemoryUpdateRunRepository;
import org.congcong.algomentor.mentor.application.profile.run.service.LearnerMemoryUpdateRunLifecycleService;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryDirectHitSelector;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallBootstrapBuilder;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallPromptSectionProvider;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallService;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemorySectionCatalog;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewBatchConsumer;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewFactRepository;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistoryRepository;
import org.congcong.algomentor.mentor.application.profile.review.history.ReviewTrajectoryService;
import org.congcong.algomentor.mentor.application.profile.review.history.SubmissionVersionDiffService;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;
import org.congcong.algomentor.mentor.application.profile.observability.MicrometerLearnerMemoryMetrics;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewPromptBuilder;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewStructuredOutputMapper;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewUpdateAgentDefinition;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewUpdateService;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdatePromptBuilder;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateAgentDefinition;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateService;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardRepository;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteRepository;
import org.congcong.algomentor.mentor.application.profile.tool.UpdateLearnerDeclaredProfileAgentTool;
import org.congcong.algomentor.mentor.application.profile.tool.CompareSubmissionVersionsAgentTool;
import org.congcong.algomentor.mentor.application.profile.tool.GetCodeReviewEvidenceAgentTool;
import org.congcong.algomentor.mentor.application.profile.tool.GetLearnerMemoryEvidenceAgentTool;
import org.congcong.algomentor.mentor.application.profile.tool.GetProblemReviewTrajectoryAgentTool;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryRunScopeRegistry;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryToolResultReadGuard;
import org.congcong.algomentor.mentor.application.profile.tool.PracticeChatReviewTrajectoryScopeService;
import org.congcong.algomentor.mentor.application.profile.tool.ReadLearnerMemorySectionAgentTool;
import org.congcong.algomentor.mentor.application.profile.tool.SearchLearnerMemoryAgentTool;
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.OpsStatus;
import org.congcong.algomentor.ops.observability.autoconfigure.OpsObservabilityAutoConfiguration;
import org.congcong.algomentor.queue.config.PersistentQueueAutoConfiguration;
import org.congcong.algomentor.queue.config.PersistentQueueWorkerAutoConfiguration;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;
import org.congcong.algomentor.queue.runtime.PersistentQueueWorkerManager;
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
    PersistentQueueWorkerAutoConfiguration.class,
    OpsObservabilityAutoConfiguration.class
})
@Import(PracticeCodeReviewConfiguration.class)
@EnableConfigurationProperties({
    LearnerMemoryDeclaredUpdateProperties.class,
    LearnerMemoryRecallProperties.class,
    PracticeCodeReviewProperties.class,
    PracticeChatLearningStateProperties.class,
    PracticeChatCoachSummaryProperties.class,
    PracticeChatPromptProperties.class,
    PracticeChatReviewTrajectoryProperties.class,
    LearnerMemoryCodeReviewConsumerProperties.class
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
      LearnerMemoryRecallPromptSectionProvider learnerMemoryRecallPromptSectionProvider,
      ObjectProvider<LearnerMemoryRecallService> learnerMemoryRecallService,
      ObjectProvider<ManagedSystemPromptResolver> systemPromptResolver,
      ObjectProvider<PracticeChatReviewTrajectoryScopeService> reviewTrajectoryScopeService
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
          learnerMemoryRecallService.getIfAvailable(),
          learnerMemoryRecallPromptSectionProvider,
          systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver),
          reviewTrajectoryScopeService.getIfAvailable());
    }
    return new AgentConversationService(
        conversationRepository,
        contextAssembler,
        practiceContextPolicy,
        null,
        null,
        practicePromptAssembler,
        learnerMemoryRecallService.getIfAvailable(),
        learnerMemoryRecallPromptSectionProvider,
        systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver),
        reviewTrajectoryScopeService.getIfAvailable());
  }

  @Bean("practiceChatPromptAssembler")
  @ConditionalOnMissingBean(name = "practiceChatPromptAssembler")
  public PromptAssembler practiceChatPromptAssembler(
      PracticeChatPromptProperties promptProperties,
      LearnerMemoryRecallPromptSectionProvider learnerMemoryRecallPromptSectionProvider,
      ObjectProvider<ManagedSystemPromptResolver> systemPromptResolver
  ) {
    return new DefaultPromptAssembler(
        new PracticeChatPromptProfileResolver(promptProperties.getTotalTokenBudget()),
        java.util.List.of(new PracticeChatPromptSectionProvider(
            systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver)), learnerMemoryRecallPromptSectionProvider));
  }

  @Bean
  @ConditionalOnMissingBean
  public LearnerMemorySectionCatalog learnerMemorySectionCatalog() {
    return new LearnerMemorySectionCatalog();
  }

  @Bean
  @ConditionalOnMissingBean
  public LearnerMemoryDirectHitSelector learnerMemoryDirectHitSelector() {
    return new LearnerMemoryDirectHitSelector();
  }

  @Bean
  @ConditionalOnMissingBean
  public LearnerMemoryRecallBootstrapBuilder learnerMemoryRecallBootstrapBuilder(
      LearnerMemoryRecallProperties properties
  ) {
    return new LearnerMemoryRecallBootstrapBuilder(properties.getBootstrapTokenBudget());
  }

  @Bean
  @ConditionalOnMissingBean
  public LearnerMemoryRecallPromptSectionProvider learnerMemoryRecallPromptSectionProvider(
      LearnerMemoryRecallBootstrapBuilder bootstrapBuilder,
      ObjectProvider<ManagedSystemPromptResolver> systemPromptResolver,
      ObjectProvider<LearnerMemoryMetrics> metrics
  ) {
    return new LearnerMemoryRecallPromptSectionProvider(
        bootstrapBuilder,
        systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver),
        metrics.getIfAvailable(() -> LearnerMemoryMetrics.NOOP));
  }

  @Bean
  @ConditionalOnBean(LearnerMemoryClaimQueryService.class)
  @ConditionalOnProperty(
      prefix = LearnerMemoryRecallProperties.PREFIX,
      name = "enabled",
      havingValue = "true")
  @ConditionalOnMissingBean
  public LearnerMemoryRecallService learnerMemoryRecallService(
      LearnerMemoryClaimQueryService claimQueryService,
      ObjectProvider<TrustedProblemTagCatalog> trustedProblemTagCatalog,
      LearnerMemorySectionCatalog sectionCatalog,
      LearnerMemoryDirectHitSelector directHitSelector,
      LearnerMemoryRunScopeRegistry scopeRegistry,
      ObjectProvider<LearnerMemoryMetrics> metrics
  ) {
    return new LearnerMemoryRecallService(
        claimQueryService,
        trustedProblemTagCatalog.getIfAvailable(TrustedProblemTagCatalog::empty),
        sectionCatalog,
        directHitSelector,
        scopeRegistry,
        metrics.getIfAvailable(() -> LearnerMemoryMetrics.NOOP));
  }

  @Bean
  @ConditionalOnBean({PracticeCodeReviewMapper.class, ObjectMapper.class})
  @ConditionalOnMissingBean
  public LearnerMemoryCodeReviewFactRepository learnerMemoryCodeReviewFactRepository(
      PracticeCodeReviewMapper mapper,
      ObjectMapper objectMapper
  ) {
    return new MyBatisLearnerMemoryCodeReviewFactRepository(mapper, objectMapper);
  }

  @Bean
  @ConditionalOnBean({PracticeCodeReviewMapper.class, ObjectMapper.class})
  @ConditionalOnMissingBean
  public CodeReviewHistoryRepository codeReviewHistoryRepository(
      PracticeCodeReviewMapper mapper,
      ObjectMapper objectMapper
  ) {
    return new MyBatisCodeReviewHistoryRepository(mapper, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearnerMemoryRunScopeRegistry learnerMemoryRunScopeRegistry() {
    return new LearnerMemoryRunScopeRegistry();
  }

  @Bean
  @ConditionalOnBean({LearnerMemoryRunScopeRegistry.class, CodeReviewHistoryRepository.class})
  @ConditionalOnProperty(
      prefix = PracticeChatReviewTrajectoryProperties.PREFIX,
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  @ConditionalOnMissingBean
  public PracticeChatReviewTrajectoryScopeService practiceChatReviewTrajectoryScopeService(
      LearnerMemoryRunScopeRegistry scopeRegistry
  ) {
    return new PracticeChatReviewTrajectoryScopeService(scopeRegistry);
  }

  @Bean
  @ConditionalOnBean({
      PracticeSessionRepository.class,
      AgentTurnMessageLookupRepository.class,
      CodeReviewHistoryRepository.class,
      ReviewCardRepository.class,
      UserProblemNoteRepository.class
  })
  @ConditionalOnProperty(
      prefix = PracticeChatLearningStateProperties.PREFIX,
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  @ConditionalOnMissingBean
  public GetCurrentProblemLearningStateAgentTool getCurrentProblemLearningStateAgentTool(
      PracticeSessionRepository sessionRepository,
      AgentTurnMessageLookupRepository turnMessageLookupRepository,
      CodeReviewHistoryRepository reviewHistoryRepository,
      ReviewCardRepository reviewCardRepository,
      UserProblemNoteRepository noteRepository
  ) {
    return new GetCurrentProblemLearningStateAgentTool(
        sessionRepository,
        turnMessageLookupRepository,
        reviewHistoryRepository,
        reviewCardRepository,
        noteRepository);
  }

  @Bean
  @ConditionalOnBean({
      CoachSummaryProposalRepository.class,
      PracticeSessionRepository.class,
      UserProblemNoteRepository.class
  })
  @ConditionalOnProperty(
      prefix = PracticeChatCoachSummaryProperties.PREFIX,
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  @ConditionalOnMissingBean
  public CoachSummaryProposalService coachSummaryProposalService(
      CoachSummaryProposalRepository proposalRepository,
      PracticeSessionRepository sessionRepository,
      UserProblemNoteRepository noteRepository
  ) {
    return new CoachSummaryProposalService(
        proposalRepository, sessionRepository, noteRepository, Clock.systemUTC());
  }

  @Bean
  @ConditionalOnBean({PracticeSessionRepository.class, CoachSummaryProposalService.class})
  @ConditionalOnProperty(
      prefix = PracticeChatCoachSummaryProperties.PREFIX,
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  @ConditionalOnMissingBean
  public ProposeCurrentProblemCoachSummaryAgentTool proposeCurrentProblemCoachSummaryAgentTool(
      PracticeSessionRepository sessionRepository,
      CoachSummaryProposalService proposalService
  ) {
    return new ProposeCurrentProblemCoachSummaryAgentTool(sessionRepository, proposalService);
  }

  @Bean
  @ConditionalOnBean(LearnerMemoryRunScopeRegistry.class)
  @ConditionalOnMissingBean(ToolResultReadGuard.class)
  public ToolResultReadGuard learnerMemoryToolResultReadGuard(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      ObjectProvider<LearnerMemoryMetrics> metrics) {
    return new LearnerMemoryToolResultReadGuard(scopeRegistry, metrics.getIfAvailable(() -> LearnerMemoryMetrics.NOOP));
  }

  @Bean
  @ConditionalOnBean(LearnerMemoryRecallService.class)
  @ConditionalOnMissingBean
  public SearchLearnerMemoryAgentTool searchLearnerMemoryAgentTool(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      ObjectProvider<LearnerMemoryMetrics> metrics) {
    return new SearchLearnerMemoryAgentTool(scopeRegistry, metrics.getIfAvailable(() -> LearnerMemoryMetrics.NOOP));
  }

  @Bean
  @ConditionalOnBean(LearnerMemoryRecallService.class)
  @ConditionalOnMissingBean
  public ReadLearnerMemorySectionAgentTool readLearnerMemorySectionAgentTool(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      ObjectProvider<LearnerMemoryMetrics> metrics
  ) {
    return new ReadLearnerMemorySectionAgentTool(scopeRegistry, metrics.getIfAvailable(() -> LearnerMemoryMetrics.NOOP));
  }

  @Bean
  @ConditionalOnBean({LearnerMemoryRecallService.class, LearnerMemoryEvidenceRepository.class})
  @ConditionalOnMissingBean
  public GetLearnerMemoryEvidenceAgentTool getLearnerMemoryEvidenceAgentTool(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      LearnerMemoryEvidenceRepository evidenceRepository,
      ObjectProvider<LearnerMemoryMetrics> metrics
  ) {
    return new GetLearnerMemoryEvidenceAgentTool(
        scopeRegistry, evidenceRepository, metrics.getIfAvailable(() -> LearnerMemoryMetrics.NOOP));
  }

  @Bean
  @ConditionalOnBean({LearnerMemoryRunScopeRegistry.class, CodeReviewHistoryRepository.class})
  @ConditionalOnMissingBean
  public ReviewTrajectoryService reviewTrajectoryService() {
    return new ReviewTrajectoryService();
  }

  @Bean
  @ConditionalOnBean({
      LearnerMemoryRunScopeRegistry.class,
      CodeReviewHistoryRepository.class,
      ReviewTrajectoryService.class
  })
  @ConditionalOnMissingBean
  public GetProblemReviewTrajectoryAgentTool getProblemReviewTrajectoryAgentTool(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      CodeReviewHistoryRepository historyRepository,
      ReviewTrajectoryService trajectoryService,
      ObjectProvider<LearnerMemoryMetrics> metrics
  ) {
    return new GetProblemReviewTrajectoryAgentTool(
        scopeRegistry, historyRepository, trajectoryService, metrics.getIfAvailable(() -> LearnerMemoryMetrics.NOOP));
  }

  @Bean
  @ConditionalOnBean({LearnerMemoryRunScopeRegistry.class, CodeReviewHistoryRepository.class})
  @ConditionalOnMissingBean
  public GetCodeReviewEvidenceAgentTool getCodeReviewEvidenceAgentTool(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      CodeReviewHistoryRepository historyRepository,
      ObjectProvider<LearnerMemoryMetrics> metrics
  ) {
    return new GetCodeReviewEvidenceAgentTool(
        scopeRegistry, historyRepository, metrics.getIfAvailable(() -> LearnerMemoryMetrics.NOOP));
  }

  @Bean
  @ConditionalOnBean({LearnerMemoryRunScopeRegistry.class, CodeReviewHistoryRepository.class})
  @ConditionalOnMissingBean
  public SubmissionVersionDiffService submissionVersionDiffService() {
    return new SubmissionVersionDiffService();
  }

  @Bean
  @ConditionalOnBean({
      LearnerMemoryRunScopeRegistry.class,
      CodeReviewHistoryRepository.class,
      SubmissionVersionDiffService.class,
      ReviewTrajectoryService.class
  })
  @ConditionalOnMissingBean
  public CompareSubmissionVersionsAgentTool compareSubmissionVersionsAgentTool(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      CodeReviewHistoryRepository historyRepository,
      SubmissionVersionDiffService diffService,
      ReviewTrajectoryService trajectoryService,
      ObjectProvider<LearnerMemoryMetrics> metrics
  ) {
    return new CompareSubmissionVersionsAgentTool(
        scopeRegistry, historyRepository, diffService, trajectoryService,
        metrics.getIfAvailable(() -> LearnerMemoryMetrics.NOOP));
  }

  @Bean
  @ConditionalOnMissingBean
  public LearnerMemoryCodeReviewPromptBuilder learnerMemoryCodeReviewPromptBuilder(
      ObjectProvider<ManagedSystemPromptResolver> systemPromptResolver
  ) {
    return new LearnerMemoryCodeReviewPromptBuilder(systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver));
  }

  @Bean
  @ConditionalOnBean(LearnerMemoryRunScopeRegistry.class)
  @ConditionalOnProperty(
      prefix = LearnerMemoryCodeReviewConsumerProperties.PREFIX,
      name = "enabled",
      havingValue = "true")
  @ConditionalOnMissingBean
  public LearnerMemoryCodeReviewUpdateAgentDefinition learnerMemoryCodeReviewUpdateAgentDefinition(
      LearnerMemoryCodeReviewPromptBuilder promptBuilder,
      LearnerMemoryRunScopeRegistry scopeRegistry
  ) {
    return new LearnerMemoryCodeReviewUpdateAgentDefinition(promptBuilder, scopeRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearnerMemoryCodeReviewStructuredOutputMapper learnerMemoryCodeReviewStructuredOutputMapper() {
    return new LearnerMemoryCodeReviewStructuredOutputMapper();
  }

  @Bean
  @ConditionalOnMissingBean
  public LearnerMemoryMetrics learnerMemoryMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
    MeterRegistry registry = meterRegistry.getIfAvailable();
    return registry == null ? LearnerMemoryMetrics.NOOP : new MicrometerLearnerMemoryMetrics(registry);
  }

  @Bean
  @ConditionalOnBean({
      LearnerMemoryCodeReviewFactRepository.class,
      CodeReviewHistoryRepository.class,
      LearnerMemoryClaimQueryService.class,
      LearnerMemoryEvidenceRepository.class,
      LearnerMemoryUpdateRunRepository.class,
      LearnerMemoryAtomicApplyService.class,
      LearnerMemoryUpdateRunLifecycleService.class,
      AgentRuntime.class,
      LearnerMemoryCodeReviewUpdateAgentDefinition.class
  })
  @ConditionalOnProperty(
      prefix = LearnerMemoryCodeReviewConsumerProperties.PREFIX,
      name = "enabled",
      havingValue = "true")
  @ConditionalOnMissingBean
  public LearnerMemoryCodeReviewUpdateService learnerMemoryCodeReviewUpdateService(
      LearnerMemoryCodeReviewFactRepository factRepository,
      CodeReviewHistoryRepository historyRepository,
      LearnerMemoryClaimQueryService claimQueryService,
      LearnerMemoryEvidenceRepository evidenceRepository,
      LearnerMemoryUpdateRunRepository updateRunRepository,
      LearnerMemoryAtomicApplyService atomicApplyService,
      LearnerMemoryUpdateRunLifecycleService runLifecycleService,
      @Lazy AgentRuntime agentRuntime,
      LearnerMemoryCodeReviewStructuredOutputMapper outputMapper,
      LearnerMemoryMetrics metrics,
      LearnerMemoryCodeReviewConsumerProperties properties
  ) {
    return new LearnerMemoryCodeReviewUpdateService(
        factRepository,
        historyRepository,
        claimQueryService,
        evidenceRepository,
        updateRunRepository,
        atomicApplyService,
        runLifecycleService,
        agentRuntime,
        outputMapper,
        properties.getMaxStaleRetries(),
        metrics);
  }

  @Bean
  @ConditionalOnBean({
      ObjectMapper.class,
      LearnerMemoryCodeReviewFactRepository.class,
      LearnerMemoryCodeReviewUpdateAgentDefinition.class,
      LearnerMemoryCodeReviewUpdateService.class,
      QueueMessageRepository.class,
      PersistentQueueWorkerManager.class
  })
  @ConditionalOnProperty(
      prefix = LearnerMemoryCodeReviewConsumerProperties.PREFIX,
      name = "enabled",
      havingValue = "true")
  @ConditionalOnMissingBean
  public LearnerMemoryCodeReviewBatchConsumer learnerMemoryCodeReviewBatchConsumer(
      ObjectMapper objectMapper,
      LearnerMemoryCodeReviewFactRepository factRepository,
      LearnerMemoryCodeReviewUpdateService updateService,
      LearnerMemoryMetrics metrics
  ) {
    return new LearnerMemoryCodeReviewBatchConsumer(objectMapper, factRepository, updateService, metrics);
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
      ObjectProvider<UpdateLearnerDeclaredProfileAgentTool> declaredProfileTool,
      ObjectProvider<SearchLearnerMemoryAgentTool> searchLearnerMemoryTool,
      ObjectProvider<ReadLearnerMemorySectionAgentTool> readLearnerMemorySectionTool,
      ObjectProvider<GetLearnerMemoryEvidenceAgentTool> learnerMemoryEvidenceTool,
      ObjectProvider<GetCurrentProblemLearningStateAgentTool> learningStateTool,
      ObjectProvider<ProposeCurrentProblemCoachSummaryAgentTool> coachSummaryTool,
      ObjectProvider<GetProblemReviewTrajectoryAgentTool> reviewTrajectoryTool,
      ObjectProvider<PracticeChatReviewTrajectoryScopeService> reviewTrajectoryScopeService,
      ObjectProvider<ReadToolResultTool> readToolResultTool
  ) {
    java.util.List<String> toolNames = new java.util.ArrayList<>();
    practiceCodeReviewTool.ifAvailable(tool -> toolNames.add(tool.spec().name()));
    declaredProfileTool.ifAvailable(tool -> toolNames.add(tool.spec().name()));
    searchLearnerMemoryTool.ifAvailable(tool -> toolNames.add(tool.spec().name()));
    readLearnerMemorySectionTool.ifAvailable(tool -> toolNames.add(tool.spec().name()));
    learnerMemoryEvidenceTool.ifAvailable(tool -> toolNames.add(tool.spec().name()));
    learningStateTool.ifAvailable(tool -> toolNames.add(tool.spec().name()));
    coachSummaryTool.ifAvailable(tool -> toolNames.add(tool.spec().name()));
    if (reviewTrajectoryScopeService.getIfAvailable() != null) {
      reviewTrajectoryTool.ifAvailable(tool -> toolNames.add(tool.spec().name()));
    }
    readToolResultTool.ifAvailable(tool -> toolNames.add(tool.spec().name()));
    return new PracticeChatAgentDefinition(
        new PracticeChatRunAdapter(conversationService, lockManager, lockOwnerProvider),
        toolNames);
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
      ObjectProvider<ManagedSystemPromptResolver> systemPromptResolver,
      ObjectProvider<CoachSummaryProposalService> coachSummaryProposalService
  ) {
    return new PracticeSessionService(
        learningPlanRepository,
        problemCatalog,
        practiceSessionRepository,
        agentTaskMessageRepository,
        reviewRepository.getIfAvailable(PracticeCodeReviewRepository::empty),
        reviewMetrics.getIfAvailable(() -> PracticeCodeReviewMetrics.NOOP),
        systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver),
        coachSummaryProposalService.getIfAvailable());
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
      prefix = LearnerMemoryDeclaredUpdateProperties.PREFIX,
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
      LearnerMemoryClaimQueryService.class,
      LearnerMemoryEvidenceRepository.class,
      LearnerMemoryUpdateRunRepository.class,
      LearnerMemoryAtomicApplyService.class,
      LearnerMemoryUpdateRunLifecycleService.class,
      AgentTurnMessageLookupRepository.class,
      AgentRuntime.class,
      DeclaredProfileUpdateAgentDefinition.class
  })
  @ConditionalOnProperty(
      prefix = LearnerMemoryDeclaredUpdateProperties.PREFIX,
      name = "enabled",
      havingValue = "true")
  @ConditionalOnMissingBean
  public DeclaredProfileUpdateService declaredProfileUpdateService(
      LearnerMemoryClaimQueryService claimQueryService,
      LearnerMemoryEvidenceRepository evidenceRepository,
      LearnerMemoryUpdateRunRepository updateRunRepository,
      LearnerMemoryAtomicApplyService atomicApplyService,
      LearnerMemoryUpdateRunLifecycleService runLifecycleService,
      AgentTurnMessageLookupRepository turnMessageLookupRepository,
      @Lazy AgentRuntime agentRuntime,
      DeclaredProfileUpdatePromptBuilder promptBuilder,
      LearnerMemoryDeclaredUpdateProperties properties,
      ObjectProvider<LearnerMemoryMetrics> metrics
  ) {
    return new DeclaredProfileUpdateService(
        claimQueryService,
        evidenceRepository,
        updateRunRepository,
        atomicApplyService,
        runLifecycleService,
        turnMessageLookupRepository,
        agentRuntime,
        promptBuilder,
        properties.getMaxStaleRetries(),
        properties.getResultSummaryMaxChars(),
        metrics.getIfAvailable(() -> LearnerMemoryMetrics.NOOP));
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
