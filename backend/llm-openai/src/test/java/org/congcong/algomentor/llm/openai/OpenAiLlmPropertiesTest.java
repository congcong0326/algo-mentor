package org.congcong.algomentor.llm.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
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
        org.congcong.algomentor.llm.core.LlmCapability.CHAT_COMPLETION,
        org.congcong.algomentor.llm.core.LlmCapability.STREAMING,
        org.congcong.algomentor.llm.core.LlmCapability.TOOL_CALLING,
        org.congcong.algomentor.llm.core.LlmCapability.STRUCTURED_OUTPUT,
        org.congcong.algomentor.llm.core.LlmCapability.JSON_SCHEMA_OUTPUT,
        org.congcong.algomentor.llm.core.LlmCapability.TOKEN_USAGE);
  }
}
