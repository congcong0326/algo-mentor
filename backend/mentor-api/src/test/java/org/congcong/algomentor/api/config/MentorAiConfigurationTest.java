package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentRunner;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.tool.CalculatorTool;
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

  @Configuration(proxyBeanMethods = false)
  static class FakeProviderConfig {

    @Bean
    FakeProvider fakeProvider() {
      return new FakeProvider();
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
}
