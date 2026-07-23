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
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentModelSelectorResolver;
import org.congcong.algomentor.agent.core.AgentRunner;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.DefaultAgentModelSelectorResolver;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
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
import org.congcong.algomentor.agent.core.tool.ReadToolResultTool;
import org.congcong.algomentor.agent.core.tool.CalculatorTool;
import org.congcong.algomentor.agent.core.toolresult.ToolResultStore;
import org.congcong.algomentor.ai.governance.accounting.AiAccountingLlmGateway;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallAccountingService;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.completion.AiCompletionGateway;
import org.congcong.algomentor.ai.governance.completion.AiGovernedCompletionService;
import org.congcong.algomentor.ai.governance.completion.AiPassthroughCompletionGateway;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimePolicyService;
import org.congcong.algomentor.api.agent.execution.ManagedAgentExecutor;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.api.problem.tool.GetProblemStatementTool;
import org.congcong.algomentor.api.problem.tool.ListProblemFiltersTool;
import org.congcong.algomentor.api.problem.tool.SearchProblemsTool;
import org.congcong.algomentor.mentor.application.ExplainTopicUseCase;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentToolNames;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.gateway.DefaultLlmGatewayFactory;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.gateway.LlmGatewayFactory;
import org.congcong.algomentor.llm.core.provider.LlmProvider;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    LlmGatewayProperties.class,
    AgentCompactionProperties.class,
    AgentExecutorProperties.class,
    AgentToolPermissionProperties.class,
    ApiSseProperties.class,
    PracticeCodeReviewProperties.class
})
public class MentorAiConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
    return builder.build();
  }

  @Bean(destroyMethod = "shutdown")
  @ConditionalOnMissingBean(AgentExecutor.class)
  public ManagedAgentExecutor agentExecutor(
      AgentExecutorProperties properties,
      ObjectProvider<MeterRegistry> meterRegistry
  ) {
    return new ManagedAgentExecutor(properties, meterRegistry.getIfAvailable());
  }

  @Bean
  @ConditionalOnMissingBean
  public LlmGatewayFactory llmGatewayFactory() {
    return new DefaultLlmGatewayFactory();
  }

  @Bean
  @ConditionalOnMissingBean(LlmGateway.class)
  public LlmGatewayDelegate llmGatewayDelegate(
      List<LlmProvider> providers,
      LlmGatewayProperties gatewayProperties,
      LlmGatewayFactory gatewayFactory
  ) {
    if (providers.isEmpty()) {
      return new LlmGatewayDelegate(new UnconfiguredLlmGateway());
    }
    return new LlmGatewayDelegate(gatewayFactory.create(providers, gatewayProperties.toOptions()));
  }

  @Bean
  @ConditionalOnMissingBean(LlmGateway.class)
  public LlmGateway llmGateway(
      LlmGatewayDelegate delegate,
      ObjectProvider<AiLlmCallAccountingService> accountingServiceProvider
  ) {
    AiLlmCallAccountingService accountingService = accountingServiceProvider.getIfAvailable();
    return accountingService == null
        ? delegate.gateway()
        : new AiAccountingLlmGateway(delegate.gateway(), accountingService);
  }

  @Bean
  @ConditionalOnMissingBean
  public AiCompletionGateway aiCompletionGateway(
      LlmGateway llmGateway,
      ObjectProvider<AiRunAdmissionService> admissionServiceProvider,
      ObjectProvider<AiRunLifecycleService> lifecycleServiceProvider,
      ObjectProvider<AiPurposePolicyResolver> policyResolverProvider,
      ObjectProvider<AiRuntimePolicyService> runtimePolicyServiceProvider
  ) {
    AiRunAdmissionService admissionService = admissionServiceProvider.getIfAvailable();
    AiRunLifecycleService lifecycleService = lifecycleServiceProvider.getIfAvailable();
    AiPurposePolicyResolver policyResolver = policyResolverProvider.getIfAvailable();
    AiRuntimePolicyService runtimePolicyService = runtimePolicyServiceProvider.getIfAvailable();
    if (admissionService == null || lifecycleService == null || policyResolver == null || runtimePolicyService == null) {
      return new AiPassthroughCompletionGateway(llmGateway);
    }
    return new AiGovernedCompletionService(
        llmGateway,
        admissionService,
        lifecycleService,
        policyResolver,
        runtimePolicyService);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentModelSelectorResolver agentModelSelectorResolver() {
    return new DefaultAgentModelSelectorResolver();
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentLlmRequestFactory agentLlmRequestFactory(
      LlmGatewayProperties gatewayProperties,
      AgentModelSelectorResolver modelSelectorResolver
  ) {
    return new AgentLlmRequestFactory(
        gatewayProperties.defaultSelector(MentorPurposes.TOPIC_EXPLANATION),
        modelSelectorResolver);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentRunner agentRunner(LlmGateway llmGateway, AgentLlmRequestFactory requestFactory) {
    return new AgentRunner(llmGateway, requestFactory);
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
  @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(ToolResultStore.class)
  public ReadToolResultTool readToolResultTool(
      ToolResultStore toolResultStore,
      ToolResultCompactionPolicy policy
  ) {
    return new ReadToolResultTool(toolResultStore, policy);
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
  public AgentLoopRunner agentLoopRunner(
      LlmGateway llmGateway,
      AgentLlmRequestFactory requestFactory,
      AgentToolRegistry agentToolRegistry,
      List<AgentLoopObserver> observers,
      @Value("${" + MentorConfigurationKeys.AGENT_TOOL_CHOICE + ":auto}") String toolChoice,
      @Value("${" + MentorConfigurationKeys.AGENT_SPECIFIC_TOOL_NAME + ":}") String specificToolName,
      @Value("${" + MentorConfigurationKeys.AGENT_MAX_STEPS + ":" + AgentLoopDefaults.DEFAULT_MAX_STEPS + "}") int maxSteps,
      ToolResultCompactionPolicy toolResultPolicy,
      org.springframework.beans.factory.ObjectProvider<ToolResultStore> toolResultStore,
      ObjectMapper objectMapper,
      AgentToolPermissionGuard permissionGuard,
      AgentExecutor agentExecutor
  ) {
    return new AgentLoopRunner(
        llmGateway,
        requestFactory,
        agentToolRegistry,
        toToolChoice(toolChoice, specificToolName),
        maxSteps,
        observers,
        List.of(),
        toolResultPolicy,
        toolResultStore.getIfAvailable(),
        objectMapper,
        permissionGuard,
        agentExecutor);
  }

  @Bean
  @ConditionalOnMissingBean
  public ContextAssembler contextAssembler() {
    return new ContextAssembler();
  }

  @Bean
  @ConditionalOnMissingBean
  public ExplainTopicUseCase explainTopicUseCase(
      AgentRunner agentRunner,
      AgentLoopRunner agentLoopRunner
  ) {
    return new ExplainTopicUseCase(agentRunner, agentLoopRunner);
  }

  private LlmToolChoice toToolChoice(String value, String specificToolName) {
    String normalized = value == null ? "auto" : value.trim().toLowerCase();
    return switch (normalized) {
      case "auto" -> LlmToolChoice.auto();
      case "none" -> LlmToolChoice.none();
      case "required" -> LlmToolChoice.required();
      case "specific" -> LlmToolChoice.specific(specificToolName);
      default -> throw new IllegalArgumentException("Unsupported agent tool choice: " + value);
    };
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
