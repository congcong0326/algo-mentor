package org.congcong.algomentor.llm.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.gateway.DefaultLlmGatewayFactory;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.gateway.LlmGatewayOptions;
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

class DefaultLlmGatewayFactoryTest {

  private static final LlmProviderId OPENAI = LlmProviderId.of("openai");
  private static final LlmModelId GPT_TEST = LlmModelId.of("gpt-test");

  private final DefaultLlmGatewayFactory factory = new DefaultLlmGatewayFactory();

  @Test
  void createsGatewayFromConfiguredDefaults() {
    FakeProvider provider = new FakeProvider(OPENAI, GPT_TEST);
    LlmGateway gateway = factory.create(List.of(provider), new LlmGatewayOptions(OPENAI, GPT_TEST));

    LlmCompletionResult result = gateway.complete(LlmCompletionRequest.builder()
        .modelSelector(new LlmModelSelector(null, null, Set.of(), "topic-explanation"))
        .messages(List.of(LlmMessage.user("hello")))
        .build());

    assertThat(result.provider()).isEqualTo(OPENAI);
    assertThat(result.model()).isEqualTo(GPT_TEST);
    assertThat(provider.lastRequest.modelSelector().providerId()).contains(OPENAI);
    assertThat(provider.lastRequest.modelSelector().modelId()).contains(GPT_TEST);
    assertThat(provider.lastRequest.modelSelector().purpose()).isEqualTo("topic-explanation");
  }

  @Test
  void rejectsUnknownDefaultProvider() {
    assertThatThrownBy(() -> factory.create(
        List.of(new FakeProvider(OPENAI, GPT_TEST)),
        new LlmGatewayOptions(LlmProviderId.of("missing"), GPT_TEST)))
        .isInstanceOfSatisfying(LlmException.class, exception ->
            assertThat(exception.code()).isEqualTo(LlmErrorCode.INVALID_REQUEST));
  }

  @Test
  void rejectsUnknownDefaultModel() {
    assertThatThrownBy(() -> factory.create(
        List.of(new FakeProvider(OPENAI, GPT_TEST)),
        new LlmGatewayOptions(OPENAI, LlmModelId.of("missing"))))
        .isInstanceOfSatisfying(LlmException.class, exception ->
            assertThat(exception.code()).isEqualTo(LlmErrorCode.INVALID_REQUEST));
  }

  @Test
  void rejectsEmptyProviders() {
    assertThatThrownBy(() -> factory.create(List.of(), new LlmGatewayOptions(OPENAI, GPT_TEST)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM gateway providers must not be empty");
  }

  @Test
  void rejectsNullOptions() {
    assertThatThrownBy(() -> factory.create(List.of(new FakeProvider(OPENAI, GPT_TEST)), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM gateway options must not be null");
  }

  private static final class FakeProvider implements LlmProvider {
    private final LlmProviderId providerId;
    private final LlmModelId modelId;
    private LlmCompletionRequest lastRequest;

    private FakeProvider(LlmProviderId providerId, LlmModelId modelId) {
      this.providerId = providerId;
      this.modelId = modelId;
    }

    @Override
    public LlmProviderId id() {
      return providerId;
    }

    @Override
    public LlmProviderCapabilities capabilities() {
      return new LlmProviderCapabilities(
          Set.of(LlmCapability.CHAT_COMPLETION),
          Map.of(modelId.value(), descriptor()));
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
          providerId,
          modelId,
          Map.of());
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("stream not used");
    }

    private LlmModelDescriptor descriptor() {
      return new LlmModelDescriptor(
          providerId,
          modelId,
          modelId.value(),
          Set.of(LlmCapability.CHAT_COMPLETION),
          0,
          0,
          LlmGenerationOptions.defaults(),
          Map.of());
    }
  }
}
