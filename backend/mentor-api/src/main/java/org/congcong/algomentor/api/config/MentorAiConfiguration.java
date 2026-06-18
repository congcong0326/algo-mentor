package org.congcong.algomentor.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRunner;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.tool.ReadToolResultTool;
import org.congcong.algomentor.agent.core.tool.CalculatorTool;
import org.congcong.algomentor.agent.core.toolresult.ToolResultStore;
import org.congcong.algomentor.mentor.application.ExplainTopicUseCase;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({LlmGatewayProperties.class, AgentCompactionProperties.class})
public class MentorAiConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
    return builder.build();
  }

  @Bean
  @ConditionalOnMissingBean
  public LlmGatewayFactory llmGatewayFactory() {
    return new DefaultLlmGatewayFactory();
  }

  @Bean
  @ConditionalOnMissingBean
  public LlmGateway llmGateway(
      List<LlmProvider> providers,
      LlmGatewayProperties gatewayProperties,
      LlmGatewayFactory gatewayFactory
  ) {
    if (providers.isEmpty()) {
      return new UnconfiguredLlmGateway();
    }
    return gatewayFactory.create(providers, gatewayProperties.toOptions());
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentRunner agentRunner(LlmGateway llmGateway, LlmGatewayProperties gatewayProperties) {
    return new AgentRunner(llmGateway, gatewayProperties.defaultSelector(MentorPurposes.TOPIC_EXPLANATION));
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentToolRegistry agentToolRegistry(List<AgentTool> tools) {
    return AgentToolRegistry.of(tools);
  }

  @Bean
  @ConditionalOnMissingBean
  public ToolResultCompactionPolicy toolResultCompactionPolicy(AgentCompactionProperties properties) {
    return properties.toPolicy();
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
  @ConditionalOnMissingBean
  public AgentLoopRunner agentLoopRunner(
      LlmGateway llmGateway,
      LlmGatewayProperties gatewayProperties,
      AgentToolRegistry agentToolRegistry,
      List<AgentLoopObserver> observers,
      @Value("${" + MentorConfigurationKeys.AGENT_TOOL_CHOICE + ":auto}") String toolChoice,
      @Value("${" + MentorConfigurationKeys.AGENT_SPECIFIC_TOOL_NAME + ":}") String specificToolName,
      @Value("${" + MentorConfigurationKeys.AGENT_MAX_STEPS + ":4}") int maxSteps,
      ToolResultCompactionPolicy toolResultPolicy,
      org.springframework.beans.factory.ObjectProvider<ToolResultStore> toolResultStore,
      ObjectMapper objectMapper
  ) {
    return new AgentLoopRunner(
        llmGateway,
        gatewayProperties.defaultSelector(MentorPurposes.TOPIC_EXPLANATION),
        agentToolRegistry,
        toToolChoice(toolChoice, specificToolName),
        maxSteps,
        observers,
        List.of(),
        toolResultPolicy,
        toolResultStore.getIfAvailable(),
        objectMapper);
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
