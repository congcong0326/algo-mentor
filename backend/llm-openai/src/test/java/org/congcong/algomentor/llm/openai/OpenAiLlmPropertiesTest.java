package org.congcong.algomentor.llm.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.congcong.algomentor.llm.core.LlmCapability;
import org.congcong.algomentor.llm.core.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.LlmMessage;
import org.congcong.algomentor.llm.core.LlmModelId;
import org.congcong.algomentor.llm.core.LlmModelSelector;
import org.congcong.algomentor.llm.core.LlmProviderId;
import org.congcong.algomentor.llm.core.LlmRequest;
import org.congcong.algomentor.llm.core.LlmStreamHandler;
import org.junit.jupiter.api.Test;

class OpenAiLlmPropertiesTest {

  @Test
  void defaultsToDisabledProviderWithSafeRuntimeSettings() {
    OpenAiLlmProperties properties = new OpenAiLlmProperties();

    assertThat(properties.isEnabled()).isFalse();
    assertThat(properties.getApiKey()).isEmpty();
    assertThat(properties.getBaseUrl()).isEqualTo(URI.create("https://api.openai.com/v1"));
    assertThat(properties.getModel()).isEqualTo("gpt-5.2");
    assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(30));
    assertThat(properties.getMaxRetries()).isEqualTo(2);
  }

  @Test
  void disabledClientExposesOpenAiProviderCapabilities() {
    OpenAiLlmProperties properties = new OpenAiLlmProperties();
    OpenAiLlmClient client = new OpenAiLlmClient(properties);

    assertThat(client.id().value()).isEqualTo("openai");
    assertThat(client.models()).hasSize(1);
    assertThat(client.models().get(0).modelId().value()).isEqualTo("gpt-5.2");
    assertThat(client.capabilities().capabilities()).contains(
        LlmCapability.CHAT_COMPLETION,
        LlmCapability.STREAMING,
        LlmCapability.TOOL_CALLING,
        LlmCapability.STRUCTURED_OUTPUT,
        LlmCapability.JSON_SCHEMA_OUTPUT,
        LlmCapability.TOKEN_USAGE);
  }

  @Test
  void modelsReturnsImmutableList() {
    OpenAiLlmClient client = new OpenAiLlmClient(new OpenAiLlmProperties());

    assertThatThrownBy(() -> client.models().add(client.models().get(0)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void disabledProviderCompleteThrowsDisabledException() {
    OpenAiLlmClient client = new OpenAiLlmClient(new OpenAiLlmProperties());

    assertThatThrownBy(() -> client.complete(completionRequest()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("OpenAI LLM provider is disabled");
  }

  @Test
  void disabledProviderStreamThrowsDisabledException() {
    OpenAiLlmClient client = new OpenAiLlmClient(new OpenAiLlmProperties());

    assertThatThrownBy(() -> client.stream(completionRequest()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("OpenAI LLM provider is disabled");
  }

  @Test
  void enabledProviderCompleteThrowsUnimplementedException() {
    OpenAiLlmProperties properties = new OpenAiLlmProperties();
    properties.setEnabled(true);
    OpenAiLlmClient client = new OpenAiLlmClient(properties);

    assertThatThrownBy(() -> client.complete(completionRequest()))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("OpenAI completion wiring is not implemented yet");
  }

  @Test
  void legacyCompleteAdaptsThroughProviderCompleteWhenDisabled() {
    OpenAiLlmClient client = new OpenAiLlmClient(new OpenAiLlmProperties());

    assertThatThrownBy(() -> client.complete(LlmRequest.userPrompt("gpt-5.2", "hello")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("OpenAI LLM provider is disabled");
  }

  @Test
  void legacyStreamReportsDisabledProviderErrorToHandler() {
    OpenAiLlmClient client = new OpenAiLlmClient(new OpenAiLlmProperties());
    RecordingStreamHandler handler = new RecordingStreamHandler();

    client.stream(LlmRequest.userPrompt("gpt-5.2", "hello"), handler);

    assertThat(handler.error)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("OpenAI LLM provider is disabled");
  }

  private static LlmCompletionRequest completionRequest() {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(LlmProviderId.of("openai"), LlmModelId.of("gpt-5.2")))
        .messages(List.of(LlmMessage.user("hello")))
        .build();
  }

  private static final class RecordingStreamHandler implements LlmStreamHandler {
    private Throwable error;

    @Override
    public void onChunk(String content) {
    }

    @Override
    public void onComplete() {
    }

    @Override
    public void onError(Throwable error) {
      this.error = error;
    }
  }
}
