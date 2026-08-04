package org.congcong.algomentor.llm.deepseek.autoconfigure;

import org.congcong.algomentor.llm.deepseek.DeepSeekProviderAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class DeepSeekLlmAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(DeepSeekProviderAdapter.class)
  public DeepSeekProviderAdapter deepSeekProviderAdapter() {
    return new DeepSeekProviderAdapter();
  }
}
