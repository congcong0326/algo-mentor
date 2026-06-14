package org.congcong.algomentor.api.config;

import java.util.List;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRunner;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.gateway.DefaultLlmGateway;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProvider;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.openai.OpenAiLlmProperties;
import org.congcong.algomentor.llm.openai.OpenAiLlmProvider;
import org.congcong.algomentor.mentor.application.ExplainTopicUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MentorAiConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LlmGateway llmGateway(List<LlmProvider> providers, OpenAiLlmProperties openAiProperties) {
    if (providers.isEmpty()) {
      return new UnconfiguredLlmGateway();
    }
    return new DefaultLlmGateway(
        providers,
        OpenAiLlmProvider.PROVIDER_ID,
        LlmModelId.of(openAiProperties.getModel()));
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentRunner agentRunner(LlmGateway llmGateway, OpenAiLlmProperties openAiProperties) {
    return new AgentRunner(llmGateway, openAiProperties.getModel());
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentToolRegistry agentToolRegistry(List<AgentTool> tools) {
    return AgentToolRegistry.of(tools);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentLoopRunner agentLoopRunner(
      LlmGateway llmGateway,
      OpenAiLlmProperties openAiProperties,
      AgentToolRegistry agentToolRegistry,
      @Value("${algo-mentor.agent.max-steps:4}") int maxSteps
  ) {
    return new AgentLoopRunner(llmGateway, openAiProperties.getModel(), agentToolRegistry, maxSteps);
  }

  @Bean
  @ConditionalOnMissingBean
  public ExplainTopicUseCase explainTopicUseCase(
      AgentRunner agentRunner,
      AgentLoopRunner agentLoopRunner
  ) {
    return new ExplainTopicUseCase(agentRunner, agentLoopRunner);
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
          "AI provider is not configured. Enable OpenAI and provide OPENAI_API_KEY to use explanations.");
    }
  }
}
