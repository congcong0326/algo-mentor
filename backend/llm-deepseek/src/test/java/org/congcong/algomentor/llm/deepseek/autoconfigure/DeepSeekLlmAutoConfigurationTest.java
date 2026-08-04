package org.congcong.algomentor.llm.deepseek.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.congcong.algomentor.llm.deepseek.DeepSeekProviderAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DeepSeekLlmAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(DeepSeekLlmAutoConfiguration.class));

  @Test
  void registersOneAdapterAndHonorsUserOverride() {
    contextRunner.run(context -> assertThat(context).hasSingleBean(DeepSeekProviderAdapter.class));
    contextRunner.withBean(DeepSeekProviderAdapter.class, DeepSeekProviderAdapter::new)
        .run(context -> assertThat(context).hasSingleBean(DeepSeekProviderAdapter.class));
  }
}
