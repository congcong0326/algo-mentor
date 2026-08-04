package org.congcong.algomentor.llm.deepseek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.llm.core.provider.LlmProviderInstanceSpec;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.openai.compatible.OpenAiCompatibleConnectionConfig;
import org.congcong.algomentor.llm.openai.compatible.OpenAiCompatibleResponsesClient;
import org.junit.jupiter.api.Test;

class DeepSeekProviderAdapterTest {

  @Test
  void exposesDeepSeekIdentityTemplateAndCreatesAProfiledSharedClient() {
    AtomicReference<OpenAiCompatibleConnectionConfig> captured = new AtomicReference<>();
    DeepSeekProviderAdapter adapter = new DeepSeekProviderAdapter(config -> {
      captured.set(config);
      return unsupportedClient();
    });

    assertThat(adapter.providerType().value()).isEqualTo("deepseek");
    assertThat(adapter.defaultConfig()).isEqualTo(JsonNodeFactory.instance.objectNode()
        .put("apiKey", "").put("baseUrl", "https://api.deepseek.com").put("timeoutSeconds", 300).put("maxRetries", 2));
    adapter.createClient(new LlmProviderInstanceSpec(
        1L, DeepSeekProviderAdapter.PROVIDER_TYPE, config(), Instant.parse("2026-08-03T00:00:00Z")));
    assertThat(captured.get().toString()).doesNotContain("test-key", "gateway.example.test");
  }

  @Test
  void rejectsMismatchedInstanceBeforeClientFactory() {
    AtomicInteger factoryCalls = new AtomicInteger();
    DeepSeekProviderAdapter adapter = new DeepSeekProviderAdapter(config -> {
      factoryCalls.incrementAndGet();
      return unsupportedClient();
    });

    assertThatThrownBy(() -> adapter.createClient(new LlmProviderInstanceSpec(
        1L, LlmProviderType.of("openai"), config(), Instant.parse("2026-08-03T00:00:00Z"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("deepseek");
    assertThat(factoryCalls.get()).isZero();
  }

  private static com.fasterxml.jackson.databind.node.ObjectNode config() {
    return JsonNodeFactory.instance.objectNode()
        .put("apiKey", "test-key")
        .put("baseUrl", "https://gateway.example.test")
        .put("timeoutSeconds", 45)
        .put("maxRetries", 1);
  }

  private static OpenAiCompatibleResponsesClient unsupportedClient() {
    return new OpenAiCompatibleResponsesClient() {
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
