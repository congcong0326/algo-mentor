package org.congcong.algomentor.llm.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.llm.core.provider.LlmProviderInstanceSpec;
import org.junit.jupiter.api.Test;

class OpenAiProviderAdapterTest {

  @Test
  void validatesStrictLocalOpenAiConfig() {
    OpenAiProviderAdapter adapter = new OpenAiProviderAdapter(config -> unsupportedClient());
    var config = JsonNodeFactory.instance.objectNode()
        .put("apiKey", "sk-test")
        .put("baseUrl", "https://api.openai.com/v1")
        .put("timeoutSeconds", 300)
        .put("maxRetries", 2);

    adapter.validateConfig(config);

    assertThat(adapter.providerType().value()).isEqualTo("openai");
    assertThat(adapter.supportedCapabilities()).isNotEmpty();
    assertThatThrownBy(() -> adapter.validateConfig(config.deepCopy().put("unknown", true)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported field");
  }

  @Test
  void createsClientFromTheInstanceConfigWithoutLeakingIt() {
    AtomicReference<OpenAiProviderConfig> captured = new AtomicReference<>();
    OpenAiProviderAdapter adapter = new OpenAiProviderAdapter(config -> {
      captured.set(config);
      return unsupportedClient();
    });
    var config = JsonNodeFactory.instance.objectNode()
        .put("apiKey", "sk-test")
        .put("baseUrl", "https://gateway.example.test/v1")
        .put("timeoutSeconds", 45)
        .put("maxRetries", 1);

    adapter.createClient(new LlmProviderInstanceSpec(
        11L, OpenAiProviderAdapter.PROVIDER_TYPE, config, Instant.parse("2026-07-27T00:00:00Z")));

    assertThat(captured.get()).isNotNull();
    assertThat(captured.get().toString()).doesNotContain("sk-test", "gateway.example.test");
  }

  private static OpenAiResponsesClient unsupportedClient() {
    return new OpenAiResponsesClient() {
      @Override
      public com.openai.models.responses.Response create(com.openai.models.responses.ResponseCreateParams params) {
        throw new UnsupportedOperationException();
      }

      @Override
      public com.openai.core.http.StreamResponse<com.openai.models.responses.ResponseStreamEvent> createStreaming(
          com.openai.models.responses.ResponseCreateParams params) {
        throw new UnsupportedOperationException();
      }
    };
  }
}
