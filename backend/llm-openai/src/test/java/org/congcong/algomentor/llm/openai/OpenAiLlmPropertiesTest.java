package org.congcong.algomentor.llm.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.llm.core.LlmCapability;
import org.congcong.algomentor.llm.core.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.LlmErrorCode;
import org.congcong.algomentor.llm.core.LlmException;
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
        .isInstanceOfSatisfying(LlmException.class, exception -> {
          assertThat(exception.code()).isEqualTo(LlmErrorCode.PROVIDER_UNAVAILABLE);
          assertThat(exception.provider()).isEqualTo(LlmProviderId.of("openai"));
          assertThat(exception.model()).isEqualTo(LlmModelId.of("gpt-5.2"));
          assertThat(exception.retryable()).isFalse();
          assertThat(exception).hasMessage("OpenAI LLM provider is disabled");
        });
  }

  @Test
  void disabledProviderStreamThrowsDisabledException() {
    OpenAiLlmClient client = new OpenAiLlmClient(new OpenAiLlmProperties());

    assertThatThrownBy(() -> client.stream(completionRequest()))
        .isInstanceOfSatisfying(LlmException.class, exception -> {
          assertThat(exception.code()).isEqualTo(LlmErrorCode.PROVIDER_UNAVAILABLE);
          assertThat(exception.provider()).isEqualTo(LlmProviderId.of("openai"));
          assertThat(exception.model()).isEqualTo(LlmModelId.of("gpt-5.2"));
          assertThat(exception.retryable()).isFalse();
          assertThat(exception).hasMessage("OpenAI LLM provider is disabled");
        });
  }

  @Test
  void enabledProviderCompleteThrowsUnimplementedException() {
    OpenAiLlmProperties properties = new OpenAiLlmProperties();
    properties.setEnabled(true);
    OpenAiLlmClient client = new OpenAiLlmClient(properties);

    assertThatThrownBy(() -> client.complete(completionRequest()))
        .isInstanceOfSatisfying(LlmException.class, exception -> {
          assertThat(exception.code()).isEqualTo(LlmErrorCode.PROVIDER_UNAVAILABLE);
          assertThat(exception.provider()).isEqualTo(LlmProviderId.of("openai"));
          assertThat(exception.model()).isEqualTo(LlmModelId.of("gpt-5.2"));
          assertThat(exception.retryable()).isFalse();
          assertThat(exception).hasMessage("OpenAI completion wiring is not implemented yet");
        });
  }

  @Test
  void enabledProviderStreamThrowsUnimplementedException() {
    OpenAiLlmProperties properties = new OpenAiLlmProperties();
    properties.setEnabled(true);
    OpenAiLlmClient client = new OpenAiLlmClient(properties);

    assertThatThrownBy(() -> client.stream(completionRequest()))
        .isInstanceOfSatisfying(LlmException.class, exception -> {
          assertThat(exception.code()).isEqualTo(LlmErrorCode.PROVIDER_UNAVAILABLE);
          assertThat(exception.provider()).isEqualTo(LlmProviderId.of("openai"));
          assertThat(exception.model()).isEqualTo(LlmModelId.of("gpt-5.2"));
          assertThat(exception.retryable()).isFalse();
          assertThat(exception).hasMessage("OpenAI streaming wiring is not implemented yet");
        });
  }

  @Test
  void legacyCompleteAdaptsThroughProviderCompleteWhenDisabled() {
    OpenAiLlmClient client = new OpenAiLlmClient(new OpenAiLlmProperties());

    assertThatThrownBy(() -> client.complete(LlmRequest.userPrompt("gpt-5.2", "hello")))
        .isInstanceOfSatisfying(LlmException.class, exception -> {
          assertThat(exception.code()).isEqualTo(LlmErrorCode.PROVIDER_UNAVAILABLE);
          assertThat(exception.provider()).isEqualTo(LlmProviderId.of("openai"));
          assertThat(exception.model()).isEqualTo(LlmModelId.of("gpt-5.2"));
          assertThat(exception.retryable()).isFalse();
          assertThat(exception).hasMessage("OpenAI LLM provider is disabled");
        });
  }

  @Test
  void legacyStreamReportsDisabledProviderErrorToHandler() {
    OpenAiLlmClient client = new OpenAiLlmClient(new OpenAiLlmProperties());
    RecordingStreamHandler handler = new RecordingStreamHandler();

    client.stream(LlmRequest.userPrompt("gpt-5.2", "hello"), handler);

    assertThat(handler.error)
        .isInstanceOfSatisfying(LlmException.class, exception -> {
          assertThat(exception.code()).isEqualTo(LlmErrorCode.PROVIDER_UNAVAILABLE);
          assertThat(exception.provider()).isEqualTo(LlmProviderId.of("openai"));
          assertThat(exception.model()).isEqualTo(LlmModelId.of("gpt-5.2"));
          assertThat(exception.retryable()).isFalse();
          assertThat(exception).hasMessage("OpenAI LLM provider is disabled");
        });
  }

  @Test
  void legacyStreamReportsUnimplementedProviderErrorToHandler() {
    OpenAiLlmProperties properties = new OpenAiLlmProperties();
    properties.setEnabled(true);
    OpenAiLlmClient client = new OpenAiLlmClient(properties);
    RecordingStreamHandler handler = new RecordingStreamHandler();

    client.stream(LlmRequest.userPrompt("gpt-5.2", "hello"), handler);

    assertThat(handler.error)
        .isInstanceOfSatisfying(LlmException.class, exception -> {
          assertThat(exception.code()).isEqualTo(LlmErrorCode.PROVIDER_UNAVAILABLE);
          assertThat(exception.provider()).isEqualTo(LlmProviderId.of("openai"));
          assertThat(exception.model()).isEqualTo(LlmModelId.of("gpt-5.2"));
          assertThat(exception.retryable()).isFalse();
          assertThat(exception).hasMessage("OpenAI streaming wiring is not implemented yet");
        });
  }

  @Test
  void completeUsesConfiguredModelWhenRequestSelectorDoesNotSpecifyModel() {
    OpenAiLlmProperties properties = new OpenAiLlmProperties();
    properties.setModel("gpt-configured");
    OpenAiLlmClient client = new OpenAiLlmClient(properties);
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.requiring(Set.of(LlmCapability.CHAT_COMPLETION)))
        .messages(List.of(LlmMessage.user("hello")))
        .build();

    assertThatThrownBy(() -> client.complete(request))
        .isInstanceOfSatisfying(LlmException.class, exception -> {
          assertThat(exception.provider()).isEqualTo(LlmProviderId.of("openai"));
          assertThat(exception.model()).isEqualTo(LlmModelId.of("gpt-configured"));
          assertThat(exception).hasMessage("OpenAI LLM provider is disabled");
        });
  }

  @Test
  void validatesBaseUrlIsNotNull() {
    OpenAiLlmProperties properties = new OpenAiLlmProperties();

    assertThatThrownBy(() -> properties.setBaseUrl(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("OpenAI base URL must not be null");
  }

  @Test
  void validatesModelIsNotBlankAndTrimsValue() {
    OpenAiLlmProperties properties = new OpenAiLlmProperties();

    assertThatThrownBy(() -> properties.setModel(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("OpenAI model must not be blank");
    assertThatThrownBy(() -> properties.setModel(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("OpenAI model must not be blank");

    properties.setModel(" gpt-5.2-mini ");

    assertThat(properties.getModel()).isEqualTo("gpt-5.2-mini");
  }

  @Test
  void validatesTimeoutIsPositive() {
    OpenAiLlmProperties properties = new OpenAiLlmProperties();

    assertThatThrownBy(() -> properties.setTimeout(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("OpenAI timeout must be positive");
    assertThatThrownBy(() -> properties.setTimeout(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("OpenAI timeout must be positive");
    assertThatThrownBy(() -> properties.setTimeout(Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("OpenAI timeout must be positive");
  }

  @Test
  void validatesMaxRetriesIsNotNegative() {
    OpenAiLlmProperties properties = new OpenAiLlmProperties();

    assertThatThrownBy(() -> properties.setMaxRetries(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("OpenAI max retries must not be negative");
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
