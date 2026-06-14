package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AiProviderPropertiesTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(TestConfig.class)
      .withPropertyValues(
          "algo-mentor.ai.openai.enabled=true",
          "algo-mentor.ai.openai.api-key=test-key",
          "algo-mentor.ai.openai.base-url=https://example.test/v1",
          "algo-mentor.ai.openai.model=gpt-test",
          "algo-mentor.ai.openai.timeout=45s",
          "algo-mentor.ai.openai.max-retries=3");

  @Test
  void bindsOpenAiSettingsFromConfiguration() {
    contextRunner.run(context -> {
      AiProviderProperties properties = context.getBean(AiProviderProperties.class);

      assertThat(properties.isEnabled()).isTrue();
      assertThat(properties.getApiKey()).isEqualTo("test-key");
      assertThat(properties.getBaseUrl()).hasToString("https://example.test/v1");
      assertThat(properties.getModel()).isEqualTo("gpt-test");
      assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(45));
      assertThat(properties.getMaxRetries()).isEqualTo(3);
    });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AiProviderProperties.class)
  static class TestConfig {
  }
}

