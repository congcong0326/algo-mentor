package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentModelSelectorResolver;
import org.congcong.algomentor.agent.core.AgentRunner;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionBehavior;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionCoordinator;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionPlan;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionType;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionGuard;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHook;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHookChain;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionCheck;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionMetrics;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionResultFactory;
import org.congcong.algomentor.agent.core.permission.InMemoryAgentToolPermissionCoordinator;
import org.congcong.algomentor.agent.core.permission.NoopAgentToolPermissionMetrics;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.agent.core.tool.CalculatorTool;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.api.problem.tool.GetProblemStatementTool;
import org.congcong.algomentor.api.problem.tool.ListProblemFiltersTool;
import org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames;
import org.congcong.algomentor.api.problem.tool.SearchProblemsTool;
import org.congcong.algomentor.mentor.api.autoconfigure.AgentConversationApiAutoConfiguration;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentTool;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentToolNames;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewPermissionHook;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewService;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelDescriptor;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProvider;
import org.congcong.algomentor.llm.core.provider.LlmProviderCapabilities;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

class MentorAiConfigurationTest {

  private static final LlmProviderId TEST_PROVIDER = LlmProviderId.of("test-provider");
  private static final LlmModelId TEST_MODEL = LlmModelId.of("test-model");

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
      .withUserConfiguration(MentorAiConfiguration.class);

  @Test
  void createsGatewayAndAgentsFromGatewayProperties() {
    contextRunner
        .withUserConfiguration(FakeProviderConfig.class)
        .withPropertyValues(
            "algo-mentor.ai.gateway.default-provider=test-provider",
            "algo-mentor.ai.gateway.default-model=test-model")
        .run(context -> {
          LlmGateway gateway = context.getBean(LlmGateway.class);
          LlmCompletionResult result = gateway.complete(LlmCompletionRequest.builder()
              .modelSelector(new LlmModelSelector(null, null, Set.of(), null))
              .messages(List.of(LlmMessage.user("hello")))
              .build());

          assertThat(result.provider()).isEqualTo(TEST_PROVIDER);
          assertThat(result.model()).isEqualTo(TEST_MODEL);

          AgentRunner agentRunner = context.getBean(AgentRunner.class);
          agentRunner.run(new AgentRequest(List.of(LlmMessage.user("binary search"))));

          FakeProvider provider = context.getBean(FakeProvider.class);
          assertThat(provider.lastRequest.modelSelector().providerId()).contains(TEST_PROVIDER);
          assertThat(provider.lastRequest.modelSelector().modelId()).contains(TEST_MODEL);
          assertThat(provider.lastRequest.modelSelector().purpose()).isEqualTo("topic-explanation");
        });
  }

  @Test
  void allowsReplacingAgentModelSelectorResolver() {
    CustomModelSelectorResolverConfig.capturedUserId = null;
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withUserConfiguration(
            FakeProviderConfig.class,
            CustomModelSelectorResolverConfig.class,
            MentorAiConfiguration.class)
        .withPropertyValues(
            "algo-mentor.ai.gateway.default-provider=test-provider",
            "algo-mentor.ai.gateway.default-model=test-model")
        .run(context -> {
          AgentRunner agentRunner = context.getBean(AgentRunner.class);

          agentRunner.run(new AgentRequest(
              "run-1",
              "request-1",
              List.of(LlmMessage.user("hello")),
              Map.of(AgentRuntimeMetadataKeys.USER_ID, 42L)));

          FakeProvider provider = context.getBean(FakeProvider.class);
          assertThat(provider.lastRequest.modelSelector().providerId()).contains(TEST_PROVIDER);
          assertThat(provider.lastRequest.modelSelector().modelId()).contains(TEST_MODEL);
          assertThat(provider.lastRequest.modelSelector().purpose()).isEqualTo("custom-user-routing");
          assertThat(CustomModelSelectorResolverConfig.capturedUserId).isEqualTo(42L);
        });
  }

  @Test
  void createsUnconfiguredGatewayWhenNoProviderIsRegistered() {
    contextRunner.run(context -> {
      LlmGateway gateway = context.getBean(LlmGateway.class);

      assertThatThrownBy(() -> gateway.complete(LlmCompletionRequest.builder()
          .modelSelector(new LlmModelSelector(null, null, Set.of(), null))
          .messages(List.of(LlmMessage.user("hello")))
          .build()))
          .isInstanceOfSatisfying(LlmException.class, exception -> {
            assertThat(exception.code()).isEqualTo(LlmErrorCode.INVALID_REQUEST);
            assertThat(exception).hasMessage(
                "AI provider is not configured. Enable a provider and configure credentials to use explanations.");
          });
    });
  }

  @Test
  void registersCalculatorToolByDefault() {
    contextRunner.run(context -> {
      AgentToolRegistry registry = context.getBean(AgentToolRegistry.class);

      assertThat(context).hasSingleBean(CalculatorTool.class);
      assertThat(registry.specs()).extracting(spec -> spec.name()).contains("calculator");
    });
  }

  @Test
  void registersToolPermissionBeansAndPassesGuardToRunner() {
    contextRunner
        .withPropertyValues(
            "algo-mentor.agent.tool-permission.timeout=25ms",
            "algo-mentor.agent.tool-permission.cleanup-interval=75ms")
        .run(context -> {
          assertThat(context).hasSingleBean(AgentToolPermissionResultFactory.class);
          assertThat(context).hasSingleBean(AgentToolPermissionMetrics.class);
          assertThat(context).hasSingleBean(AgentToolPermissionHookChain.class);
          assertThat(context).hasSingleBean(AgentToolPermissionCoordinator.class);
          assertThat(context).hasSingleBean(AgentToolPermissionGuard.class);
          assertThat(context.getBean(AgentToolPermissionMetrics.class))
              .isSameAs(NoopAgentToolPermissionMetrics.INSTANCE);

          AgentToolPermissionCoordinator coordinator = context.getBean(AgentToolPermissionCoordinator.class);
          assertThat(coordinator).isInstanceOf(InMemoryAgentToolPermissionCoordinator.class);
          assertThat(ReflectionTestUtils.getField(coordinator, "timeout")).isEqualTo(Duration.ofMillis(25));
          assertThat(context.getBean(AgentToolPermissionProperties.class).getCleanupInterval())
              .isEqualTo(Duration.ofMillis(75));
          assertThat(ReflectionTestUtils.getField(
              context.getBean(AgentLoopRunner.class),
              "permissionGuard")).isSameAs(context.getBean(AgentToolPermissionGuard.class));
        });
  }

  @Test
  void toolPermissionMetricsUsesMeterRegistryWhenAvailable() {
    contextRunner
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(context -> {
          assertThat(context).hasSingleBean(AgentToolPermissionMetrics.class);
          assertThat(context.getBean(AgentToolPermissionMetrics.class))
              .isNotInstanceOf(NoopAgentToolPermissionMetrics.class);

          AgentToolPermissionMetrics metrics = context.getBean(AgentToolPermissionMetrics.class);
          metrics.recordHookDecision(
              "submit_practice_code_review",
              AgentToolPermissionBehavior.ASK,
              "practice-review");
          metrics.recordPermissionRequest("submit_practice_code_review", "practice-review");
          metrics.recordUserDecision("submit_practice_code_review", AgentToolPermissionDecisionType.ALLOW);
          metrics.recordTimeout("submit_practice_code_review");
          metrics.recordLatency(
              "submit_practice_code_review",
              AgentToolPermissionMetrics.OUTCOME_ALLOW,
              Duration.ofMillis(42));
          metrics.recordHighPermissionExecution("submit_practice_code_review", "practice-review");

          MeterRegistry registry = context.getBean(MeterRegistry.class);
          assertThat(registry.get(AgentToolPermissionMetrics.HOOK_DECISIONS)
              .tag(AgentToolPermissionMetrics.TAG_TOOL_NAME, "submit_practice_code_review")
              .tag(AgentToolPermissionMetrics.TAG_BEHAVIOR, "ASK")
              .tag(AgentToolPermissionMetrics.TAG_POLICY_SOURCE, "practice-review")
              .counter()
              .count()).isEqualTo(1.0);
          assertThat(registry.get(AgentToolPermissionMetrics.PERMISSION_REQUESTS)
              .tag(AgentToolPermissionMetrics.TAG_TOOL_NAME, "submit_practice_code_review")
              .tag(AgentToolPermissionMetrics.TAG_POLICY_SOURCE, "practice-review")
              .counter()
              .count()).isEqualTo(1.0);
          assertThat(registry.get(AgentToolPermissionMetrics.USER_DECISIONS)
              .tag(AgentToolPermissionMetrics.TAG_TOOL_NAME, "submit_practice_code_review")
              .tag(AgentToolPermissionMetrics.TAG_DECISION, "ALLOW")
              .counter()
              .count()).isEqualTo(1.0);
          assertThat(registry.get(AgentToolPermissionMetrics.TIMEOUTS)
              .tag(AgentToolPermissionMetrics.TAG_TOOL_NAME, "submit_practice_code_review")
              .counter()
              .count()).isEqualTo(1.0);
          assertThat(registry.get(AgentToolPermissionMetrics.LATENCY)
              .tag(AgentToolPermissionMetrics.TAG_TOOL_NAME, "submit_practice_code_review")
              .tag(AgentToolPermissionMetrics.TAG_OUTCOME, AgentToolPermissionMetrics.OUTCOME_ALLOW)
              .timer()
              .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(42.0);
          assertThat(registry.get(AgentToolPermissionMetrics.HIGH_PERMISSION_EXECUTION)
              .tag(AgentToolPermissionMetrics.TAG_TOOL_NAME, "submit_practice_code_review")
              .tag(AgentToolPermissionMetrics.TAG_POLICY_SOURCE, "practice-review")
              .counter()
              .count()).isEqualTo(1.0);
        });
  }

  @Test
  void disabledToolPermissionKeepsRunnerGuardButIgnoresBusinessHooks() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withUserConfiguration(DenyPermissionHookConfig.class, MentorAiConfiguration.class)
        .withPropertyValues("algo-mentor.agent.tool-permission.enabled=false")
        .run(context -> {
          AgentToolPermissionHookChain hookChain = context.getBean(AgentToolPermissionHookChain.class);

          assertThat(hookChain.hooks()).isEmpty();
          assertThat(context.getBean(AgentToolPermissionCoordinator.class))
              .isNotInstanceOf(InMemoryAgentToolPermissionCoordinator.class);
          assertThat(ReflectionTestUtils.getField(
              context.getBean(AgentLoopRunner.class),
              "permissionGuard")).isSameAs(context.getBean(AgentToolPermissionGuard.class));
        });
  }

  @Test
  void collectsAutoConfiguredPracticeReviewToolAndPermissionHook() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class,
            AgentConversationApiAutoConfiguration.class))
        .withUserConfiguration(PracticeReviewToolCollectionConfig.class, MentorAiConfiguration.class)
        .run(context -> {
          AgentToolRegistry registry = context.getBean(AgentToolRegistry.class);
          AgentToolPermissionHookChain hookChain = context.getBean(AgentToolPermissionHookChain.class);

          assertThat(context).hasSingleBean(PracticeCodeReviewAgentTool.class);
          assertThat(context).hasSingleBean(PracticeCodeReviewPermissionHook.class);
          assertThat(registry.specs()).extracting(spec -> spec.name())
              .contains(PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW);
          assertThat(hookChain.hooks())
              .anySatisfy(hook -> assertThat(hook).isInstanceOf(PracticeCodeReviewPermissionHook.class));
        });
  }

  @Test
  void objectMapperSerializesApiResponseTimestamp() {
    contextRunner.run(context -> {
      ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

      JsonNode json = objectMapper.valueToTree(ApiResponse.success("ready"));

      assertThat(json.path("timestamp").isTextual()).isTrue();
      assertThat(json.path("timestamp").asText()).endsWith("Z");
    });
  }

  @Test
  void allowsDisablingCalculatorTool() {
    contextRunner
        .withPropertyValues("algo-mentor.agent.tools.calculator.enabled=false")
        .run(context -> {
          AgentToolRegistry registry = context.getBean(AgentToolRegistry.class);

          assertThat(context).doesNotHaveBean(CalculatorTool.class);
          assertThat(registry.specs()).isEmpty();
        });
  }

  @Test
  void registersProblemToolsByDefaultWhenProblemServiceExists() {
    problemToolContextRunner()
        .run(context -> {
          AgentToolRegistry registry = context.getBean(AgentToolRegistry.class);

          assertThat(context).hasSingleBean(ListProblemFiltersTool.class);
          assertThat(context).hasSingleBean(SearchProblemsTool.class);
          assertThat(context).hasSingleBean(GetProblemStatementTool.class);
          assertThat(registry.specs()).extracting(spec -> spec.name())
              .contains(
                  ProblemAgentToolNames.LIST_PROBLEM_FILTERS,
                  ProblemAgentToolNames.SEARCH_PROBLEMS,
                  ProblemAgentToolNames.GET_PROBLEM_STATEMENT);
        });
  }

  @Test
  void allowsDisablingIndividualProblemTools() {
    problemToolContextRunner()
        .withPropertyValues("algo-mentor.agent.tools.problem-search.enabled=false")
        .run(context -> {
          AgentToolRegistry registry = context.getBean(AgentToolRegistry.class);

          assertThat(context).hasSingleBean(ListProblemFiltersTool.class);
          assertThat(context).doesNotHaveBean(SearchProblemsTool.class);
          assertThat(context).hasSingleBean(GetProblemStatementTool.class);
          assertThat(registry.specs()).extracting(spec -> spec.name())
              .contains(ProblemAgentToolNames.LIST_PROBLEM_FILTERS)
              .doesNotContain(ProblemAgentToolNames.SEARCH_PROBLEMS);
        });
  }

  private ApplicationContextRunner problemToolContextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withUserConfiguration(FakeProblemToolConfig.class, MentorAiConfiguration.class);
  }

  @Configuration(proxyBeanMethods = false)
  static class FakeProviderConfig {

    @Bean
    FakeProvider fakeProvider() {
      return new FakeProvider();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomModelSelectorResolverConfig {
    private static Long capturedUserId;

    @Bean
    AgentModelSelectorResolver agentModelSelectorResolver() {
      return context -> {
        capturedUserId = context.trustedUserId();
        return new LlmModelSelector(null, null, Set.of(), "custom-user-routing");
      };
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class DenyPermissionHookConfig {

    @Bean
    AgentToolPermissionHook denyPermissionHook() {
      return new AgentToolPermissionHook() {
        @Override
        public int order() {
          return 1;
        }

        @Override
        public AgentToolPermissionDecisionPlan evaluate(AgentToolPermissionCheck check) {
          return AgentToolPermissionDecisionPlan.deny("blocked", "test-policy");
        }
      };
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class PracticeReviewToolCollectionConfig {

    @Bean
    PracticeSessionRepository practiceSessionRepository() {
      return mock(PracticeSessionRepository.class);
    }

    @Bean
    AgentTurnMessageLookupRepository agentTurnMessageLookupRepository() {
      return mock(AgentTurnMessageLookupRepository.class);
    }

    @Bean
    PracticeCodeReviewService practiceCodeReviewService() {
      return mock(PracticeCodeReviewService.class);
    }
  }

  static class FakeProvider implements LlmProvider {
    private LlmCompletionRequest lastRequest;

    @Override
    public LlmProviderId id() {
      return TEST_PROVIDER;
    }

    @Override
    public LlmProviderCapabilities capabilities() {
      return new LlmProviderCapabilities(
          Set.of(LlmCapability.CHAT_COMPLETION),
          Map.of(TEST_MODEL.value(), descriptor()));
    }

    @Override
    public List<LlmModelDescriptor> models() {
      return List.of(descriptor());
    }

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      this.lastRequest = request;
      return new LlmCompletionResult(
          LlmMessage.assistant("ok"),
          List.of(),
          null,
          LlmFinishReason.STOP,
          LlmUsage.empty(),
          TEST_PROVIDER,
          TEST_MODEL,
          Map.of());
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("stream not used");
    }

    private LlmModelDescriptor descriptor() {
      return new LlmModelDescriptor(
          TEST_PROVIDER,
          TEST_MODEL,
          TEST_MODEL.value(),
          Set.of(LlmCapability.CHAT_COMPLETION),
          0,
          0,
          LlmGenerationOptions.defaults(),
          Map.of());
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class FakeProblemToolConfig {

    @Bean
    ProblemService problemService() {
      return mock(ProblemService.class);
    }
  }
}
