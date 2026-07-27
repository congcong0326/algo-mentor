package org.congcong.algomentor.llm.openai.autoconfigure;

import org.congcong.algomentor.llm.openai.OpenAiProviderAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class OpenAiLlmAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(OpenAiProviderAdapter.class)
  public OpenAiProviderAdapter openAiProviderAdapter() {
    return new OpenAiProviderAdapter();
  }
}
