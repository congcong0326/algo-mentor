package org.congcong.algomentor.api.config;

import java.util.List;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRunner;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.tool.CalculatorTool;
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

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LlmGatewayProperties.class)
public class MentorAiConfiguration {

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
    return new AgentRunner(llmGateway, gatewayProperties.defaultSelector("topic-explanation"));
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentToolRegistry agentToolRegistry(List<AgentTool> tools) {
    return AgentToolRegistry.of(tools);
  }

  @Bean
  @ConditionalOnMissingBean(name = "calculatorTool")
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      prefix = "algo-mentor.agent.tools.calculator",
      name = "enabled",
      havingValue = "true",
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
      @Value("${algo-mentor.agent.tool-choice:auto}") String toolChoice,
      @Value("${algo-mentor.agent.specific-tool-name:}") String specificToolName,
      @Value("${algo-mentor.agent.max-steps:4}") int maxSteps
  ) {
    return new AgentLoopRunner(
        llmGateway,
        gatewayProperties.defaultSelector("topic-explanation"),
        agentToolRegistry,
        toToolChoice(toolChoice, specificToolName),
        maxSteps,
        observers,
        List.of());
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
