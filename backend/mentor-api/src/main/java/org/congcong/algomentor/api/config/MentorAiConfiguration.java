package org.congcong.algomentor.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentCancellationToken;
import org.congcong.algomentor.agent.core.AgentLlmRequestFactory;
import org.congcong.algomentor.agent.core.AgentLoopDefaults;
import org.congcong.algomentor.agent.core.AgentLoopEngine;
import org.congcong.algomentor.agent.core.AgentLoopInterceptor;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.AgentModelSelectorResolver;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.DefaultAgentModelSelectorResolver;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
import org.congcong.algomentor.agent.core.compaction.RunMessageCompactor;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactor;
import org.congcong.algomentor.agent.core.execution.AgentExecutor;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionAuthorization;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionCheck;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionCoordinator;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionPlan;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionResult;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionType;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionException;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionGuard;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHook;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHookChain;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionMetrics;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionResultFactory;
import org.congcong.algomentor.agent.core.permission.InMemoryAgentToolPermissionCoordinator;
import org.congcong.algomentor.agent.core.permission.NoopAgentToolPermissionMetrics;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockReleaseObserver;
import org.congcong.algomentor.agent.core.runlock.InMemoryAgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.LocalAgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.core.tool.ReadToolResultTool;
import org.congcong.algomentor.agent.core.tool.CalculatorTool;
import org.congcong.algomentor.agent.core.toolresult.ToolResultReadGuard;
import org.congcong.algomentor.agent.core.toolresult.ToolResultStore;
import org.congcong.algomentor.agent.runtime.DefaultAgentRuntime;
import org.congcong.algomentor.agent.runtime.definition.AgentDefinitionRegistry;
import org.congcong.algomentor.agent.runtime.governance.AgentRuntimeGovernanceService;
import org.congcong.algomentor.ai.governance.accounting.AiAccountingLlmGateway;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallAccountingService;
import org.congcong.algomentor.ai.governance.execution.AiRunGovernanceService;
import org.congcong.algomentor.ai.governance.metrics.AiProviderCallMetricsLlmGateway;
import org.congcong.algomentor.api.agent.execution.ManagedAgentExecutor;
import org.congcong.algomentor.api.agent.execution.AgentExecutionBulkheadRegistry;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.api.problem.tool.GetProblemStatementTool;
import org.congcong.algomentor.api.problem.tool.ListProblemFiltersTool;
import org.congcong.algomentor.api.problem.tool.SearchProblemsTool;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionAgentDefinition;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanExtensionAgentDefinition;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftAgentDefinition;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateAgentDefinition;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewUpdateAgentDefinition;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentToolNames;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentDefinition;
import org.congcong.algomentor.mentor.application.practice.PracticeChatAgentDefinition;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.agent.core.AgentInvocationTargetResolver;
import org.congcong.algomentor.ai.governance.routing.AiRunInvocationTargetStore;
import org.congcong.algomentor.llm.core.gateway.DynamicLlmGateway;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    AgentCompactionProperties.class,
    AgentExecutorProperties.class,
    AgentToolPermissionProperties.class,
    ApiSseProperties.class,
    PracticeRealtimeStreamProperties.class,
    PracticeCodeReviewProperties.class
})
public class MentorAiConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
    return builder.build();
  }

  @Bean(destroyMethod = "shutdown")
  @ConditionalOnProperty(
      prefix = MentorConfigurationKeys.AGENT_RUNTIME_PREFIX,
      name = MentorConfigurationKeys.ENABLED,
      havingValue = MentorConfigurationKeys.TRUE,
      matchIfMissing = false)
  @ConditionalOnMissingBean(AgentExecutor.class)
  public ManagedAgentExecutor agentExecutor(
      AgentExecutorProperties properties,
      AgentExecutionBulkheadRegistry bulkheadRegistry,
      ObjectProvider<MeterRegistry> meterRegistry
  ) {
    return new ManagedAgentExecutor(properties, bulkheadRegistry, meterRegistry.getIfAvailable());
  }

  @Bean
  @ConditionalOnMissingBean(LlmGateway.class)
  public LlmGatewayDelegate llmGatewayDelegate() {
    return new LlmGatewayDelegate(new DynamicLlmGateway());
  }

  @Bean
  @ConditionalOnMissingBean(LlmGateway.class)
  public LlmGateway llmGateway(
      LlmGatewayDelegate delegate,
      ObjectProvider<AiLlmCallAccountingService> accountingServiceProvider,
      ObjectProvider<MeterRegistry> meterRegistryProvider
  ) {
    LlmGateway gateway = delegate.gateway();
    MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
    if (meterRegistry != null) {
      gateway = new AiProviderCallMetricsLlmGateway(gateway, meterRegistry);
    }
    AiLlmCallAccountingService accountingService = accountingServiceProvider.getIfAvailable();
    return accountingService == null
        ? gateway
        : new AiAccountingLlmGateway(gateway, accountingService);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentModelSelectorResolver agentModelSelectorResolver() {
    return new DefaultAgentModelSelectorResolver();
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentLlmRequestFactory agentLlmRequestFactory(
      AgentModelSelectorResolver modelSelectorResolver,
      ObjectProvider<AiRunInvocationTargetStore> invocationTargetStoreProvider
  ) {
    AiRunInvocationTargetStore invocationTargetStore = invocationTargetStoreProvider.getIfAvailable();
    return new AgentLlmRequestFactory(
        LlmModelSelector.requiring(java.util.Set.of()),
        modelSelectorResolver,
        invocationTargetStore == null ? AgentInvocationTargetResolver.none() : invocationTargetStore);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentToolRegistry agentToolRegistry(
      List<AgentTool> tools,
      PracticeCodeReviewProperties practiceCodeReviewProperties) {
    AgentToolRegistry registry = AgentToolRegistry.of(tools);
    if (practiceCodeReviewProperties.isEnabled()
        && registry.find(PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW).isEmpty()) {
      throw new IllegalStateException(
          "Practice Code Review is enabled but required AgentTool is not registered: "
              + PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW);
    }
    return registry;
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentRunLockManager agentRunLockManager() {
    return new InMemoryAgentRunLockManager();
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentRunLockOwnerProvider agentRunLockOwnerProvider() {
    return new LocalAgentRunLockOwnerProvider();
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentRunLockReleaseObserver agentRunLockReleaseObserver(AgentRunLockManager lockManager) {
    return new AgentRunLockReleaseObserver(lockManager);
  }

  @Bean
  @ConditionalOnMissingBean
  public ToolResultCompactionPolicy toolResultCompactionPolicy(AgentCompactionProperties properties) {
    return properties.toPolicy();
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentToolPermissionResultFactory agentToolPermissionResultFactory(ObjectMapper objectMapper) {
    return new AgentToolPermissionResultFactory(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentToolPermissionMetrics agentToolPermissionMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
    MeterRegistry registry = meterRegistry.getIfAvailable();
    if (registry == null) {
      return NoopAgentToolPermissionMetrics.INSTANCE;
    }
    return new MicrometerAgentToolPermissionMetrics(registry);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentToolPermissionHookChain agentToolPermissionHookChain(
      List<AgentToolPermissionHook> hooks,
      AgentToolPermissionProperties properties,
      AgentToolPermissionMetrics metrics
  ) {
    properties.validate();
    return new AgentToolPermissionHookChain(properties.isEnabled() ? hooks : List.of(), metrics);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentToolPermissionCoordinator agentToolPermissionCoordinator(
      AgentToolPermissionResultFactory resultFactory,
      AgentToolPermissionProperties properties,
      AgentToolPermissionMetrics metrics
  ) {
    properties.validate();
    if (!properties.isEnabled()) {
      return new DefaultAllowAgentToolPermissionCoordinator();
    }
    return new InMemoryAgentToolPermissionCoordinator(
        resultFactory,
        properties.getTimeout(),
        java.time.Clock.systemUTC(),
        metrics);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentToolPermissionGuard agentToolPermissionGuard(
      AgentToolPermissionHookChain hookChain,
      AgentToolPermissionCoordinator coordinator
  ) {
    return new AgentToolPermissionGuard(hookChain, coordinator);
  }

  @Bean
  @ConditionalOnMissingBean(name = "readToolResultTool")
  public ReadToolResultTool readToolResultTool(
      ToolResultStore toolResultStore,
      ToolResultCompactionPolicy policy,
      ObjectProvider<ToolResultReadGuard> readGuard
  ) {
    return new ReadToolResultTool(toolResultStore, policy, readGuard.getIfAvailable(() -> ToolResultReadGuard.NOOP));
  }

  @Bean
  @ConditionalOnMissingBean(name = "calculatorTool")
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      prefix = MentorConfigurationKeys.CALCULATOR_TOOL_PREFIX,
      name = MentorConfigurationKeys.ENABLED,
      havingValue = MentorConfigurationKeys.TRUE,
      matchIfMissing = true)
  public CalculatorTool calculatorTool() {
    return new CalculatorTool();
  }

  @Bean
  @ConditionalOnMissingBean(name = "listProblemFiltersTool")
  @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(ProblemService.class)
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      prefix = MentorConfigurationKeys.PROBLEM_FILTERS_TOOL_PREFIX,
      name = MentorConfigurationKeys.ENABLED,
      havingValue = MentorConfigurationKeys.TRUE,
      matchIfMissing = true)
  public ListProblemFiltersTool listProblemFiltersTool(ProblemService problemService) {
    return new ListProblemFiltersTool(problemService);
  }

  @Bean
  @ConditionalOnMissingBean(name = "searchProblemsTool")
  @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(ProblemService.class)
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      prefix = MentorConfigurationKeys.PROBLEM_SEARCH_TOOL_PREFIX,
      name = MentorConfigurationKeys.ENABLED,
      havingValue = MentorConfigurationKeys.TRUE,
      matchIfMissing = true)
  public SearchProblemsTool searchProblemsTool(ProblemService problemService) {
    return new SearchProblemsTool(problemService);
  }

  @Bean
  @ConditionalOnMissingBean(name = "getProblemStatementTool")
  @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(ProblemService.class)
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      prefix = MentorConfigurationKeys.PROBLEM_STATEMENT_TOOL_PREFIX,
      name = MentorConfigurationKeys.ENABLED,
      havingValue = MentorConfigurationKeys.TRUE,
      matchIfMissing = true)
  public GetProblemStatementTool getProblemStatementTool(ProblemService problemService) {
    return new GetProblemStatementTool(problemService);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentLoopEngine agentLoopEngine(
      LlmGateway llmGateway,
      AgentLlmRequestFactory requestFactory,
      List<AgentLoopObserver> observers,
      List<AgentLoopInterceptor> interceptors,
      ToolResultCompactionPolicy toolResultPolicy,
      ToolResultStore toolResultStore,
      ObjectMapper objectMapper,
      AgentToolPermissionGuard permissionGuard
  ) {
    ToolResultCompactor toolResultCompactor = new ToolResultCompactor(
        objectMapper,
        toolResultPolicy,
        toolResultStore);
    return new AgentLoopEngine(
        llmGateway,
        requestFactory,
        observers,
        interceptors,
        toolResultCompactor,
        new RunMessageCompactor(objectMapper, toolResultCompactor),
        objectMapper,
        permissionGuard);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentDefinitionRegistry agentDefinitionRegistry(
      ObjectProvider<PracticeChatAgentDefinition> practiceDefinitionProvider,
      ObjectProvider<PracticeCodeReviewAgentDefinition> practiceCodeReviewDefinitionProvider,
      ObjectProvider<DeclaredProfileUpdateAgentDefinition> declaredProfileUpdateDefinitionProvider,
      ObjectProvider<LearnerMemoryCodeReviewUpdateAgentDefinition> learnerMemoryCodeReviewUpdateDefinitionProvider,
      ObjectProvider<LearningPlanDraftAgentDefinition> learningPlanDraftDefinitionProvider,
      ObjectProvider<LearningPlanDraftRevisionAgentDefinition> learningPlanRevisionDefinitionProvider,
      ObjectProvider<LearningPlanExtensionAgentDefinition> learningPlanExtensionDefinitionProvider,
      AgentToolRegistry agentToolRegistry
  ) {
    List<org.congcong.algomentor.agent.core.runtime.definition.AgentDefinition<?>> definitions =
        new java.util.ArrayList<>();
    practiceDefinitionProvider.ifAvailable(definitions::add);
    practiceCodeReviewDefinitionProvider.ifAvailable(definitions::add);
    declaredProfileUpdateDefinitionProvider.ifAvailable(definitions::add);
    learnerMemoryCodeReviewUpdateDefinitionProvider.ifAvailable(definitions::add);
    learningPlanDraftDefinitionProvider.ifAvailable(definitions::add);
    learningPlanRevisionDefinitionProvider.ifAvailable(definitions::add);
    learningPlanExtensionDefinitionProvider.ifAvailable(definitions::add);
    definitions.forEach(definition -> definition.allowedToolNames().forEach(toolName -> {
      if (agentToolRegistry.find(toolName).isEmpty()) {
        throw new IllegalStateException(
            "Agent Definition requires an unregistered tool: " + definition.key().value() + "/" + toolName);
      }
    }));
    return new AgentDefinitionRegistry(definitions);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = MentorConfigurationKeys.AGENT_RUNTIME_PREFIX,
      name = MentorConfigurationKeys.ENABLED,
      havingValue = MentorConfigurationKeys.TRUE,
      matchIfMissing = false)
  @ConditionalOnMissingBean
  public AgentExecutionBulkheadRegistry agentExecutionBulkheadRegistry(
      AgentDefinitionRegistry definitionRegistry,
      AgentExecutorProperties properties,
      ObjectProvider<MeterRegistry> meterRegistry
  ) {
    return new AgentExecutionBulkheadRegistry(definitionRegistry, properties, meterRegistry.getIfAvailable());
  }

  @Bean
  @Lazy
  @ConditionalOnProperty(
      prefix = MentorConfigurationKeys.AGENT_RUNTIME_PREFIX,
      name = MentorConfigurationKeys.ENABLED,
      havingValue = MentorConfigurationKeys.TRUE,
      matchIfMissing = false)
  @ConditionalOnMissingBean
  public AgentRuntimeGovernanceService agentRuntimeGovernanceService(AiRunGovernanceService governanceService) {
    return new AgentRuntimeGovernanceService(governanceService);
  }

  @Bean
  @Lazy
  @ConditionalOnProperty(
      prefix = MentorConfigurationKeys.AGENT_RUNTIME_PREFIX,
      name = MentorConfigurationKeys.ENABLED,
      havingValue = MentorConfigurationKeys.TRUE,
      matchIfMissing = false)
  @ConditionalOnMissingBean(AgentRuntime.class)
  public AgentRuntime agentRuntime(
      AgentDefinitionRegistry definitionRegistry,
      AgentRuntimeGovernanceService governanceService,
      AgentConversationRepository conversationRepository,
      AgentLoopEngine loopEngine,
      AgentToolRegistry agentToolRegistry,
      AgentExecutor agentExecutor,
      @Value("${" + MentorConfigurationKeys.AGENT_MAX_STEPS + ":" + AgentLoopDefaults.DEFAULT_MAX_STEPS + "}")
      int maxStepsHardLimit
  ) {
    return new DefaultAgentRuntime(
        definitionRegistry,
        governanceService,
        conversationRepository,
        loopEngine,
        agentToolRegistry,
        agentExecutor,
        maxStepsHardLimit);
  }

  @Bean
  @ConditionalOnMissingBean
  public ContextAssembler contextAssembler() {
    return new ContextAssembler();
  }

  private static final class DefaultAllowAgentToolPermissionCoordinator implements AgentToolPermissionCoordinator {

    @Override
    public AgentToolPermissionAuthorization authorize(
        AgentToolPermissionCheck check,
        AgentToolPermissionDecisionPlan plan,
        AgentCancellationToken cancellationToken,
        EventPublisher eventPublisher
    ) {
      return new AgentToolPermissionAuthorization.Allowed(AgentToolPermissionDecisionPlan.allow("disabled"));
    }

    @Override
    public AgentToolPermissionDecisionResult decide(
        String permissionRequestId,
        AgentToolPermissionDecisionType decision,
        String reason,
        long userId
    ) {
      throw new AgentToolPermissionException(
          AgentToolPermissionException.Code.NOT_FOUND,
          "Agent tool permission is disabled");
    }
  }

  static final class LlmGatewayDelegate {

    private final LlmGateway gateway;

    private LlmGatewayDelegate(LlmGateway gateway) {
      this.gateway = gateway;
    }

    private LlmGateway gateway() {
      return gateway;
    }
  }

  private static final class MicrometerAgentToolPermissionMetrics implements AgentToolPermissionMetrics {

    private static final String UNKNOWN = "unknown";

    private final MeterRegistry registry;

    private MicrometerAgentToolPermissionMetrics(MeterRegistry registry) {
      this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public void recordHookDecision(
        String toolName,
        org.congcong.algomentor.agent.core.permission.AgentToolPermissionBehavior behavior,
        String policySource
    ) {
      Counter.builder(HOOK_DECISIONS)
          .tag(TAG_TOOL_NAME, safeTag(toolName))
          .tag(TAG_BEHAVIOR, behavior == null ? UNKNOWN : behavior.name())
          .tag(TAG_POLICY_SOURCE, safeTag(policySource))
          .register(registry)
          .increment();
    }

    @Override
    public void recordPermissionRequest(
        String toolName,
        String policySource
    ) {
      Counter.builder(PERMISSION_REQUESTS)
          .tag(TAG_TOOL_NAME, safeTag(toolName))
          .tag(TAG_POLICY_SOURCE, safeTag(policySource))
          .register(registry)
          .increment();
    }

    @Override
    public void recordUserDecision(
        String toolName,
        AgentToolPermissionDecisionType decision
    ) {
      Counter.builder(USER_DECISIONS)
          .tag(TAG_TOOL_NAME, safeTag(toolName))
          .tag(TAG_DECISION, decision == null ? UNKNOWN : decision.name())
          .register(registry)
          .increment();
    }

    @Override
    public void recordTimeout(String toolName) {
      Counter.builder(TIMEOUTS)
          .tag(TAG_TOOL_NAME, safeTag(toolName))
          .register(registry)
          .increment();
    }

    @Override
    public void recordLatency(
        String toolName,
        String outcome,
        Duration latency
    ) {
      Timer.builder(LATENCY)
          .tag(TAG_TOOL_NAME, safeTag(toolName))
          .tag(TAG_OUTCOME, safeTag(outcome))
          .register(registry)
          .record(nonNegative(latency));
    }

    @Override
    public void recordHighPermissionExecution(
        String toolName,
        String policySource
    ) {
      Counter.builder(HIGH_PERMISSION_EXECUTION)
          .tag(TAG_TOOL_NAME, safeTag(toolName))
          .tag(TAG_POLICY_SOURCE, safeTag(policySource))
          .register(registry)
          .increment();
    }

    private static String safeTag(String value) {
      if (value == null || value.isBlank()) {
        return UNKNOWN;
      }
      return value;
    }

    private static Duration nonNegative(Duration latency) {
      if (latency == null || latency.isNegative()) {
        return Duration.ZERO;
      }
      return latency;
    }
  }

  private static final class UnconfiguredLlmGateway implements LlmGateway {

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw unconfigured();
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw unconfigured();
    }

    private LlmException unconfigured() {
      return new LlmException(
          LlmErrorCode.INVALID_REQUEST,
          "AI provider is not configured. Enable a provider and configure credentials to use explanations.");
    }
  }
}
