package org.congcong.algomentor.llm.openai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.congcong.algomentor.llm.openai.OpenAiLlmProperties;
import org.congcong.algomentor.llm.openai.OpenAiLlmProvider;
import org.congcong.algomentor.llm.openai.OpenAiResponsesClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class OpenAiLlmAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(OpenAiLlmAutoConfiguration.class));

  @Test
  void alwaysBindsOpenAiProperties() {
    contextRunner
        .withPropertyValues(
            "algo-mentor.ai.openai.model=gpt-test",
            "algo-mentor.ai.openai.timeout=20s",
            "algo-mentor.ai.openai.stream-timeout=2m")
        .run(context -> {
          OpenAiLlmProperties properties = context.getBean(OpenAiLlmProperties.class);

          assertThat(properties.getModel()).isEqualTo("gpt-test");
          assertThat(properties.getTimeout()).hasToString("PT20S");
          assertThat(properties.getStreamTimeout()).hasToString("PT2M");
          assertThat(context).doesNotHaveBean(OpenAiResponsesClient.class);
          assertThat(context).doesNotHaveBean(OpenAiLlmProvider.class);
        });
  }

  @Test
  void createsProviderWhenEnabledAndClientExists() {
    contextRunner
        .withUserConfiguration(FakeClientConfig.class)
        .withPropertyValues(
            "algo-mentor.ai.openai.enabled=true",
            "algo-mentor.ai.openai.api-key=test-key")
        .run(context -> assertThat(context).hasSingleBean(OpenAiLlmProvider.class));
  }

  @Test
  void doesNotCreateProviderWhenDisabledEvenIfClientExists() {
    contextRunner
        .withUserConfiguration(FakeClientConfig.class)
        .run(context -> assertThat(context).doesNotHaveBean(OpenAiLlmProvider.class));
  }

  @Configuration(proxyBeanMethods = false)
  static class FakeClientConfig {

    @Bean
    OpenAiResponsesClient openAiResponsesClient() {
      return new FakeOpenAiResponsesClient();
    }
  }

  static class FakeOpenAiResponsesClient implements OpenAiResponsesClient {

    @Override
    public com.openai.models.responses.Response create(com.openai.models.responses.ResponseCreateParams params) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public com.openai.core.http.StreamResponse<com.openai.models.responses.ResponseStreamEvent> createStreaming(
        com.openai.models.responses.ResponseCreateParams params) {
      throw new UnsupportedOperationException("not used");
    }
  }
}
