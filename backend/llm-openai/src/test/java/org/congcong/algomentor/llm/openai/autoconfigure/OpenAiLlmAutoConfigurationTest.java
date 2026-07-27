package org.congcong.algomentor.llm.openai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.congcong.algomentor.llm.openai.OpenAiProviderAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpenAiLlmAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(OpenAiLlmAutoConfiguration.class));

  @Test
  void registersOnlyTheDynamicAdapter() {
    contextRunner
        .run(context -> {
          assertThat(context).hasSingleBean(OpenAiProviderAdapter.class);
        });
  }
}
