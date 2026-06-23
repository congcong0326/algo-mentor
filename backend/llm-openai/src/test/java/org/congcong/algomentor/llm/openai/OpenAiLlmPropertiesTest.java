package org.congcong.algomentor.llm.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OpenAiLlmPropertiesTest {

  @Test
  void hasSafeDefaults() {
    OpenAiLlmProperties properties = new OpenAiLlmProperties();

    assertThat(properties.isEnabled()).isFalse();
    assertThat(properties.getApiKey()).isEmpty();
    assertThat(properties.getBaseUrl()).isEqualTo(URI.create("https://api.openai.com/v1"));
    assertThat(properties.getModel()).isEqualTo("gpt-5.2");
    assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(30));
    assertThat(properties.getStreamTimeout()).isEqualTo(Duration.ofMinutes(5));
    assertThat(properties.getMaxRetries()).isEqualTo(2);
    properties.validate();
  }

  @Test
  void rejectsInvalidValues() {
    OpenAiLlmProperties enabledWithoutKey = new OpenAiLlmProperties();
    enabledWithoutKey.setEnabled(true);
    assertThatThrownBy(enabledWithoutKey::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("OpenAI API key must not be blank when provider is enabled");

    OpenAiLlmProperties blankModel = new OpenAiLlmProperties();
    blankModel.setModel(" ");
    assertThatThrownBy(blankModel::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("OpenAI model must not be blank");

    OpenAiLlmProperties badTimeout = new OpenAiLlmProperties();
    badTimeout.setTimeout(Duration.ZERO);
    assertThatThrownBy(badTimeout::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("OpenAI timeout must be positive");

    OpenAiLlmProperties badStreamTimeout = new OpenAiLlmProperties();
    badStreamTimeout.setStreamTimeout(Duration.ZERO);
    assertThatThrownBy(badStreamTimeout::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("OpenAI stream timeout must be positive");

    OpenAiLlmProperties negativeRetries = new OpenAiLlmProperties();
    negativeRetries.setMaxRetries(-1);
    assertThatThrownBy(negativeRetries::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("OpenAI max retries must not be negative");
  }

  @Test
  void bindsOpenAiSettingsFromSpringConfiguration() {
    new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues(
            "algo-mentor.ai.openai.enabled=true",
            "algo-mentor.ai.openai.api-key=test-key",
            "algo-mentor.ai.openai.base-url=https://example.test/v1",
            "algo-mentor.ai.openai.model=gpt-test",
            "algo-mentor.ai.openai.timeout=45s",
            "algo-mentor.ai.openai.stream-timeout=4m",
            "algo-mentor.ai.openai.max-retries=3")
        .run(context -> {
          OpenAiLlmProperties properties = context.getBean(OpenAiLlmProperties.class);

          assertThat(properties.isEnabled()).isTrue();
          assertThat(properties.getApiKey()).isEqualTo("test-key");
          assertThat(properties.getBaseUrl()).hasToString("https://example.test/v1");
          assertThat(properties.getModel()).isEqualTo("gpt-test");
          assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(45));
          assertThat(properties.getStreamTimeout()).isEqualTo(Duration.ofMinutes(4));
          assertThat(properties.getMaxRetries()).isEqualTo(3);
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(OpenAiLlmProperties.class)
  static class TestConfig {
  }
}
